package com.iron.breakfast.domain

import java.util.Locale

class FoodMatcher(
    private val catalog: List<FoodRecord>,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val abbreviations = mapOf(
        "pb" to "peanut butter",
        "oats" to "rolled oats",
        "tbsp" to "tablespoon",
        "tsp" to "teaspoon",
        "ml" to "milliliter",
        "g" to "gram"
    )

    private class IndexedFood(
        val food: FoodRecord,
        val names: String,
        val category: String,
        val nameTokens: Set<String>,
        val canonicalNames: String,
        val canonicalTokens: Set<String>,
        /** Words of the name and aliases only: the vocabulary a typo or a loose spelling is checked against. */
        val nameWords: Set<String>,
        val canonicalNameWords: Set<String>,
        val looseNameWords: Set<String>,
        val categoryWords: Set<String>,
        val normalizedNames: Set<String>,
        val normalizedAliases: Set<String>
    )

    private val indexedCatalog = catalog.map { food ->
        val names = (food.allNames() + listOfNotNull(food.preparation, food.sourceDescription))
            .joinToString(" ").lowercase(Locale.ROOT)
        val canonicalNames = Transliteration.canonical(names)
        val nameWords = tokens(food.allNames().joinToString(" ")).toSet()
        val categoryWords = tokens(food.category).toSet()
        IndexedFood(
            food = food,
            names = names,
            category = food.category.lowercase(Locale.ROOT),
            nameTokens = tokens(names).toSet(),
            canonicalNames = canonicalNames,
            canonicalTokens = tokens(canonicalNames).toSet(),
            nameWords = nameWords,
            categoryWords = categoryWords,
            canonicalNameWords = nameWords.mapTo(mutableSetOf(), Transliteration::canonical),
            looseNameWords = nameWords.mapTo(mutableSetOf(), Transliteration::loose),
            normalizedNames = food.allNames().mapTo(mutableSetOf(), ::normalize),
            normalizedAliases = food.aliases.mapTo(mutableSetOf(), ::normalize)
        )
    }

    /** One input token with everything the tiers need, computed once per call rather than once per food. */
    private class QueryToken(val raw: String) {
        val canonical = Transliteration.canonical(raw)
        val loose = Transliteration.loose(raw)
        val stems = stems(raw)
        val canonicalStems = stems(canonical)
        val typoAllowance = typoAllowance(raw)
        val canonicalTypoAllowance = typoAllowance(canonical)
        val isShortWord = raw.length < 3 && raw.all { it.isLetter() }
        val isModifier = raw in MODIFIERS || raw.endsWith("%") || raw.matches(NUMERIC_TOKEN_PATTERN)
    }

    /** How one input token matched one food: its weight, and whether a typo's edit is at the first letter. */
    private class TokenHit(val weight: Double, val firstLetterEdit: Boolean, val prefix: Boolean = false)

    fun suggestions(phrase: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 3): List<RankedFood> {
        val inputTokens = tokens(expandAbbreviations(phrase))
        if (inputTokens.isEmpty()) return emptyList()
        val queries = inputTokens.map(::QueryToken)
        val normalizedPhrase = normalize(phrase)
        // A plural phrase that is a food's whole name or alias once singular ("tomatoes" is "tomato").
        // Only when the typed phrase is not already an exact name or alias of some food, so exact forms still win.
        val stemmedPhrase = queries.joinToString(" ") { query -> query.stems.firstOrNull() ?: query.raw }
        val exactSomewhere = indexedCatalog.any { normalizedPhrase in it.normalizedNames || normalizedPhrase in it.normalizedAliases }
        val explicitModifiers = queries.filter { it.isModifier }.map { it.raw }
        val identityQueries = queries.filterNot { it.isModifier }
        val now = nowMs()

        return indexedCatalog.mapNotNull { indexed ->
            val food = indexed.food
            if (explicitModifiers.any { it !in indexed.names && it !in indexed.category }) return@mapNotNull null
            val hits = queries.map { hit(it, indexed) }
            val identityHits = hits.filterIndexed { index, _ -> !queries[index].isModifier }
            val exactName = normalizedPhrase in indexed.normalizedNames
            if (identityQueries.isNotEmpty() && identityHits.all { it == null }) return@mapNotNull null
            if (identityQueries.isEmpty() && !exactName) return@mapNotNull null

            val matched = hits.filterNotNull()
            if (matched.isEmpty()) return@mapNotNull null
            val aliasExact = normalizedPhrase in indexed.normalizedAliases
            val stemmedName = !exactSomewhere && (stemmedPhrase in indexed.normalizedNames || stemmedPhrase in indexed.normalizedAliases)
            val tokenScore = hits.sumOf { it?.weight ?: 0.0 } / inputTokens.size * 10.0
            val preferenceScore = (preferences[food.id]?.score(now) ?: 0.0) * 3.0
            val explicitScore = when {
                aliasExact -> 100.0
                stemmedName -> STEMMED_NAME_SCORE
                else -> 0.0
            }
            val categoryScore = if (inputTokens.any { it in indexed.category }) 0.8 else 0.0
            val originScore = if (food.origin == FoodOrigin.CUSTOM) 2.0 else 0.0
            RankedFood(
                food = food,
                score = explicitScore + tokenScore + categoryScore + originScore + preferenceScore,
                reasons = buildList {
                    if (aliasExact) add("explicit alias")
                    if (stemmedName) add("plural form of a name")
                    add("${matched.size}/${inputTokens.size} name/category tokens")
                    if (preferenceScore > 0) add("explicit preference")
                    if (originScore > 0) add("custom food")
                    if (explicitModifiers.isNotEmpty()) add("modifier constrained")
                },
                // Weak: every matched token is a typo or a loose spelling, so the food is only a choice, not a resolution.
                weakMatch = matched.all { it.weight <= TYPO_WEIGHT },
                // A segment resolves on its own only when every identity token matches; a partial match is a choice.
                fullMatch = if (identityQueries.isEmpty()) exactName else identityHits.all { it != null },
                wholeMatch = if (identityQueries.isEmpty()) exactName else identityHits.all { it != null && !it.prefix },
                firstLetterEdit = matched.any { it.firstLetterEdit },
            )
        }.sortedWith(compareByDescending<RankedFood> { it.score }.thenBy { it.food.name }).take(limit)
    }

    /**
     * How one input token matches a food, by tier: an exact form (a substring of the names), a singular form that is a
     * whole name word, the other script, a typo, then the loose spelling. Null when no tier matches.
     */
    private fun hit(query: QueryToken, indexed: IndexedFood): TokenHit? {
        val raw = query.raw
        // A short word (under three letters) names a food only as a whole word: "m" must not find "Grandma's".
        if (query.isShortWord) {
            return if (raw in indexed.nameWords || raw in indexed.categoryWords || query.canonical in indexed.canonicalNameWords) EXACT_HIT else null
        }
        if (raw in indexed.names || raw in indexed.category) {
            // A whole word of the name is exact; a part of a longer word ("ban" in banana) is a prefix match.
            return if (raw in indexed.nameWords || raw in indexed.categoryWords) EXACT_HIT else PREFIX_HIT
        }
        if (query.stems.any { it in indexed.nameTokens }) return STEM_HIT
        if (query.canonical in indexed.canonicalNames || query.canonicalStems.any { it in indexed.canonicalTokens }) {
            return if (query.canonical in indexed.canonicalNameWords) TRANSLITERATED_HIT else TokenHit(TRANSLITERATED_WEIGHT, false, prefix = true)
        }
        if (query.typoAllowance > 0) {
            indexed.nameWords.firstOrNull { withinEditDistance(raw, it, query.typoAllowance) }
                ?.let { return TokenHit(TYPO_WEIGHT, it.first() != raw.first()) }
        }
        if (query.canonicalTypoAllowance > 0) {
            indexed.canonicalNameWords.firstOrNull { withinEditDistance(query.canonical, it, query.canonicalTypoAllowance) }
                ?.let { return TokenHit(TYPO_WEIGHT, it.first() != query.canonical.first()) }
        }
        if (query.loose.length >= MIN_LOOSE_LENGTH && query.loose in indexed.looseNameWords) return LOOSE_HIT
        return null
    }

    fun best(phrase: String, preferences: Map<String, Preference> = emptyMap()): RankedFood? =
        suggestions(phrase, preferences, 1).firstOrNull()

    /** True when [phrase] is exactly one food's name or alias, ignoring case and stop words. */
    fun isExactName(phrase: String): Boolean {
        val normalizedPhrase = normalize(phrase)
        return normalizedPhrase.isNotEmpty() &&
            indexedCatalog.any { normalizedPhrase in it.normalizedNames || normalizedPhrase in it.normalizedAliases }
    }

    /** True when [phrase] is one food's name or alias, or one food matches every word of it. Used to keep names whole. */
    fun covers(phrase: String): Boolean {
        if (isExactName(phrase)) return true
        val queries = tokens(expandAbbreviations(phrase)).map(::QueryToken)
        if (queries.isEmpty()) return false
        return indexedCatalog.any { indexed -> queries.all { hit(it, indexed) != null } }
    }

    /** True when some food names [word] as a whole word, through any tier except a prefix of a longer word. */
    fun matchesWholeWord(word: String): Boolean {
        val queries = tokens(expandAbbreviations(word)).map(::QueryToken)
        if (queries.isEmpty()) return false
        return indexedCatalog.any { indexed -> queries.all { hit(it, indexed)?.let { h -> !h.prefix } == true } }
    }

    /**
     * The foods a word being typed may complete: those with a name or alias word that starts with it, the same way the
     * matcher folds Cyrillic, Latin and diacritics. Ranked by the saved preference, then by name. Nothing is preselected.
     */
    fun startingWith(fragment: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 4): List<RankedFood> {
        val word = tokens(expandAbbreviations(fragment)).lastOrNull() ?: return emptyList()
        val canonical = Transliteration.canonical(word)
        val now = nowMs()
        return indexedCatalog.mapNotNull { indexed ->
            val starts = indexed.nameWords.any { it.startsWith(word) } || indexed.canonicalNameWords.any { it.startsWith(canonical) }
            if (!starts) return@mapNotNull null
            // The saved preference ranks first; then a food whose whole name starts with the fragment, before one where
            // only a later word does.
            val namesStart = indexed.food.allNames().any { name ->
                name.lowercase(Locale.ROOT).startsWith(word) || Transliteration.canonical(name).startsWith(canonical)
            }
            Triple(
                RankedFood(
                    food = indexed.food,
                    score = (preferences[indexed.food.id]?.score(now) ?: 0.0) * 3.0,
                    reasons = listOf("starts with $word"),
                ),
                namesStart,
                indexed.food.name,
            )
        }.sortedWith(
            compareByDescending<Triple<RankedFood, Boolean, String>> { it.first.score }
                .thenByDescending { it.second }
                .thenBy { it.third }
        ).take(limit).map { it.first }
    }

    fun expandAbbreviations(phrase: String): String = phrase.lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .joinToString(" ") { abbreviations[it] ?: it }

    private fun tokens(value: String): List<String> = value
        .lowercase(Locale.ROOT)
        // An apostrophe joins its word ("Jerry's" is one word), so its "s" is never a word of its own.
        .replace("'", "").replace("’", "")
        .replace(TOKEN_CHARACTER_PATTERN, " ")
        .split(WHITESPACE_PATTERN)
        .filter { it.isNotBlank() && it !in STOP_WORDS }

    private fun normalize(value: String): String = tokens(expandAbbreviations(value)).joinToString(" ")

    companion object {
        private const val EXACT_WEIGHT = 1.0
        private const val STEM_WEIGHT = 0.95
        private const val TRANSLITERATED_WEIGHT = 0.9
        private const val TYPO_WEIGHT = 0.6
        private const val LOOSE_WEIGHT = 0.5
        private const val MIN_STEM_LENGTH = 3
        private const val MIN_LOOSE_LENGTH = 4
        private const val STEMMED_NAME_SCORE = 50.0
        private val EXACT_HIT = TokenHit(EXACT_WEIGHT, false)
        private val PREFIX_HIT = TokenHit(EXACT_WEIGHT, false, prefix = true)
        private val STEM_HIT = TokenHit(STEM_WEIGHT, false)
        private val TRANSLITERATED_HIT = TokenHit(TRANSLITERATED_WEIGHT, false)
        private val LOOSE_HIT = TokenHit(LOOSE_WEIGHT, false)
        private val STOP_WORDS = ShorthandLexicon.stopWords
        private val TOKEN_CHARACTER_PATTERN = Regex("[^\\p{L}\\p{N}.%]+")
        private val WHITESPACE_PATTERN = Regex("\\s+")
        private val NUMERIC_TOKEN_PATTERN = Regex("\\d+(?:[.]\\d+)?")
        private val MODIFIERS = setOf(
            "oat", "oats", "small", "large", "smooth", "crunchy", "raw", "cooked", "roasted", "grilled",
            "boneless", "skinless", "drained", "canned", "yellow", "green", "red", "orange", "white",
            "roma", "grape", "baby", "mature", "light", "restaurant", "2.8", "3.2", "домашно", "домашен"
        )

        /** Singular forms to try for a token: "eggs" gives "egg", "berries" gives "berry". Never shorter than three letters. */
        private fun stems(token: String): List<String> {
            val candidates = buildList {
                if (token.endsWith("ies")) add(token.dropLast(3) + "y")
                if (token.endsWith("es")) add(token.dropLast(2))
                if (token.endsWith("s") && !token.endsWith("ss")) add(token.dropLast(1))
            }
            return candidates.filter { it.length >= MIN_STEM_LENGTH }.distinct()
        }

        /** Edits allowed for a typo: none under six letters, one for six to eight, two from nine on. Letters only. */
        private fun typoAllowance(token: String): Int = when {
            token.length < 6 || !token.all { it.isLetter() } -> 0
            token.length < 9 -> 1
            else -> 2
        }

        /** True when [a] and [b] are within [max] single-letter edits (insert, delete, substitute). Stops early when it cannot. */
        private fun withinEditDistance(a: String, b: String, max: Int): Boolean {
            if (kotlin.math.abs(a.length - b.length) > max) return false
            var previous = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val current = IntArray(b.length + 1)
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                }
                if (current.min() > max) return false
                previous = current
            }
            return previous[b.length] <= max
        }
    }
}
