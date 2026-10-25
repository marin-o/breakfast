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

    /**
     * The words of a food's names and aliases in one script, read the way a typed token is (lowercase, apostrophes
     * joined, diacritics folded): [names] for a substring match, [words] for a whole word, a stem or a typo.
     */
    private class NameWords(val words: Set<String>) {
        val names: String = words.joinToString(" ")
    }

    /** Only a food's name and aliases are matched; its category, preparation and source text are not. */
    private class IndexedFood(
        val food: FoodRecord,
        /** Every name word, whatever its script: what a token with no letters (a number, "2.8") is matched against. */
        val all: NameWords,
        /** The name words by script. A token is matched only against the words in its own script. */
        val byScript: Map<TextScript, NameWords>,
        val normalizedNames: Set<String>,
        val normalizedAliases: Set<String>
    ) {
        fun inScriptOf(query: QueryToken): NameWords? = when (query.script) {
            TextScript.NONE -> all
            TextScript.MIXED -> null
            else -> byScript[query.script]
        }
    }

    private val indexedCatalog = catalog.map { food ->
        val words = food.allNames().flatMap(::tokens).toSet()
        IndexedFood(
            food = food,
            all = NameWords(words),
            byScript = words.groupBy(::scriptOf).mapValues { NameWords(it.value.toSet()) },
            normalizedNames = food.allNames().mapTo(mutableSetOf(), ::normalize),
            normalizedAliases = food.aliases.mapTo(mutableSetOf(), ::normalize)
        )
    }

    /** One input token with everything the tiers need, computed once per call rather than once per food. */
    private class QueryToken(val raw: String) {
        val script = scriptOf(raw)
        val stems = stems(raw)
        val typoAllowance = typoAllowance(raw)
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
            if (explicitModifiers.any { it !in indexed.all.names }) return@mapNotNull null
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
            val originScore = if (food.origin == FoodOrigin.CUSTOM) 2.0 else 0.0
            RankedFood(
                food = food,
                score = explicitScore + tokenScore + originScore + preferenceScore,
                reasons = buildList {
                    if (aliasExact) add("explicit alias")
                    if (stemmedName) add("plural form of a name")
                    add("${matched.size}/${inputTokens.size} name tokens")
                    if (preferenceScore > 0) add("explicit preference")
                    if (originScore > 0) add("custom food")
                    if (explicitModifiers.isNotEmpty()) add("modifier constrained")
                },
                // Weak: every matched token is a typo, so the food is only a choice, not a resolution.
                weakMatch = matched.all { it.weight <= TYPO_WEIGHT },
                // A segment resolves on its own only when every identity token matches; a partial match is a choice.
                fullMatch = if (identityQueries.isEmpty()) exactName else identityHits.all { it != null },
                wholeMatch = if (identityQueries.isEmpty()) exactName else identityHits.all { it != null && !it.prefix },
                firstLetterEdit = matched.any { it.firstLetterEdit },
                exact = exactName || aliasExact || stemmedName,
            )
            // An exact name or alias ranks first; a learned preference only orders matches of equal standing.
        }.sortedWith(compareByDescending<RankedFood> { it.exact }.thenByDescending { it.score }.thenBy { it.food.name }).take(limit)
    }

    /**
     * How one input token matches a food, within its own script: an exact form (a substring of the names, whole or a
     * prefix), a singular form that is a whole name word, or a typo of a name word. Null when no tier matches, and
     * always null for a token that mixes scripts ("bаnana" with a Cyrillic а): it is neither Latin nor Cyrillic.
     */
    private fun hit(query: QueryToken, indexed: IndexedFood): TokenHit? {
        val raw = query.raw
        val scope = indexed.inScriptOf(query) ?: return null
        // A short word (under three letters) names a food only as a whole word: "m" must not find "Grandma's".
        if (query.isShortWord) {
            return if (raw in scope.words) EXACT_HIT else null
        }
        if (raw in scope.names) {
            // A whole word of the name is exact; a part of a longer word ("ban" in banana) is a prefix match.
            return if (raw in scope.words) EXACT_HIT else PREFIX_HIT
        }
        if (query.stems.any { it in scope.words }) return STEM_HIT
        if (query.typoAllowance > 0) {
            scope.words.firstOrNull { withinEditDistance(raw, it, query.typoAllowance) }
                ?.let { return TokenHit(TYPO_WEIGHT, it.first() != raw.first()) }
        }
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
     * The foods a word being typed may complete. Suggestions never cross scripts: the fragment matches only the names
     * and aliases in the script it was typed (diacritics folded within that script), so "cev" does not find Чевап and
     * "bu" does not find a Cyrillic "бут". Ranked by the saved preference, then a name that starts with the fragment
     * before one where only a later word does, then name.
     */
    fun startingWith(fragment: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 4): List<RankedFood> {
        val word = tokens(expandAbbreviations(fragment)).lastOrNull() ?: return emptyList()
        val script = scriptOf(word)
        if (script == TextScript.MIXED) return emptyList()
        val cyrillic = script == TextScript.CYRILLIC
        val folded = foldDiacritics(word)
        val now = nowMs()
        // Scripts never cross, word by word: a fragment matches only the name words in its own script, so "Протеин bar"
        // is found by "про" and by "bar", and a food with no word in that script is not suggested at all.
        val inScript = { nameWord: String -> scriptOf(nameWord).let { it != TextScript.MIXED && (it == TextScript.CYRILLIC) == cyrillic } }
        val starts = { nameWord: String -> inScript(nameWord) && foldDiacritics(nameWord).startsWith(folded) }
        return catalog.mapNotNull { food ->
            val names = food.allNames().map(::tokens)
            if (names.none { words -> words.any(starts) }) return@mapNotNull null
            val namesStart = names.any { words -> words.firstOrNull()?.let(starts) == true }
            Triple(
                RankedFood(
                    food = food,
                    score = (preferences[food.id]?.score(now) ?: 0.0) * 3.0,
                    reasons = listOf("starts with $word"),
                ),
                namesStart,
                food.name,
            )
        }.sortedWith(
            compareByDescending<Triple<RankedFood, Boolean, String>> { it.first.score }
                .thenByDescending { it.second }
                .thenBy { it.third }
        ).take(limit).map { it.first }
    }

    /** Lowercase with the Latin diacritics folded (č and ć to c, š to s, ž to z, đ to d). */
    private fun foldDiacritics(text: String): String = text.lowercase(Locale.ROOT)
        .replace('č', 'c').replace('ć', 'c').replace('š', 's').replace('ž', 'z').replace('đ', 'd')

    fun expandAbbreviations(phrase: String): String = phrase.lowercase(Locale.ROOT)
        .split(WHITESPACE)
        .joinToString(" ") { abbreviations[it] ?: it }

    private fun tokens(value: String): List<String> = value
        .lowercase(Locale.ROOT)
        // An apostrophe joins its word ("Jerry's" is one word), so its "s" is never a word of its own.
        .replace("'", "").replace("’", "")
        .let { foldDiacritics(it) }
        .replace(TOKEN_CHARACTER_PATTERN, " ")
        .split(WHITESPACE_PATTERN)
        .filter { it.isNotBlank() && it !in STOP_WORDS }

    private fun normalize(value: String): String = tokens(expandAbbreviations(value)).joinToString(" ")

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private const val EXACT_WEIGHT = 1.0
        private const val STEM_WEIGHT = 0.95
        private const val TYPO_WEIGHT = 0.6
        private const val MIN_STEM_LENGTH = 3
        private const val STEMMED_NAME_SCORE = 50.0
        private val EXACT_HIT = TokenHit(EXACT_WEIGHT, false)
        private val PREFIX_HIT = TokenHit(EXACT_WEIGHT, false, prefix = true)
        private val STEM_HIT = TokenHit(STEM_WEIGHT, false)
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

/** The script a word is written in. Matching never crosses scripts, and a word that mixes them matches nothing. */
internal enum class TextScript { NONE, LATIN, CYRILLIC, MIXED }

/** The script of [text]'s letters: [TextScript.NONE] with no Latin or Cyrillic letter, [TextScript.MIXED] with both. */
internal fun scriptOf(text: String): TextScript {
    var latin = false
    var cyrillic = false
    for (c in text) {
        if (!c.isLetter()) continue
        when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.LATIN -> latin = true
            Character.UnicodeScript.CYRILLIC -> cyrillic = true
            else -> Unit
        }
    }
    return when {
        latin && cyrillic -> TextScript.MIXED
        latin -> TextScript.LATIN
        cyrillic -> TextScript.CYRILLIC
        else -> TextScript.NONE
    }
}
