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

    private data class IndexedFood(
        val food: FoodRecord,
        val names: String,
        val category: String,
        val nameTokens: Set<String>,
        val canonicalNames: String,
        val canonicalTokens: Set<String>,
        val normalizedNames: Set<String>,
        val normalizedAliases: Set<String>
    )

    private val indexedCatalog = catalog.map { food ->
        val names = (food.allNames() + listOfNotNull(food.preparation, food.sourceDescription))
            .joinToString(" ").lowercase(Locale.ROOT)
        val canonicalNames = Transliteration.canonical(names)
        IndexedFood(
            food = food,
            names = names,
            category = food.category.lowercase(Locale.ROOT),
            nameTokens = tokens(names).toSet(),
            canonicalNames = canonicalNames,
            canonicalTokens = tokens(canonicalNames).toSet(),
            normalizedNames = food.allNames().mapTo(mutableSetOf(), ::normalize),
            normalizedAliases = food.aliases.mapTo(mutableSetOf(), ::normalize)
        )
    }

    fun suggestions(phrase: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 3): List<RankedFood> {
        val expanded = expandAbbreviations(phrase)
        val inputTokens = tokens(expanded)
        if (inputTokens.isEmpty()) return emptyList()
        val normalizedPhrase = normalize(phrase)
        // A plural phrase that is a food's whole name or alias once singular ("tomatoes" is "tomato").
        // Only when the typed phrase is not already an exact name or alias of some food, so exact forms still win.
        val stemmedPhrase = inputTokens.joinToString(" ") { token -> stems(token).firstOrNull() ?: token }
        val exactSomewhere = indexedCatalog.any { normalizedPhrase in it.normalizedNames || normalizedPhrase in it.normalizedAliases }
        val explicitModifiers = inputTokens.filter { token ->
            token in MODIFIERS || token.endsWith("%") || token.matches(NUMERIC_TOKEN_PATTERN)
        }
        val identityTokens = inputTokens.filterNot { token ->
            token in MODIFIERS || token.endsWith("%") || token.matches(NUMERIC_TOKEN_PATTERN)
        }
        val now = nowMs()

        return indexedCatalog.mapNotNull { indexed ->
            val food = indexed.food
            if (explicitModifiers.any { it !in indexed.names && it !in indexed.category }) return@mapNotNull null
            val identityMatches = identityTokens.count { token -> tokenWeight(token, indexed) > 0.0 }
            val exactName = normalizedPhrase in indexed.normalizedNames
            if (identityTokens.isNotEmpty() && identityMatches == 0) return@mapNotNull null
            if (identityTokens.isEmpty() && !exactName) return@mapNotNull null

            val weights = inputTokens.map { token -> tokenWeight(token, indexed) }
            val matchingTokens = weights.count { it > 0.0 }
            if (matchingTokens == 0) return@mapNotNull null
            val aliasExact = normalizedPhrase in indexed.normalizedAliases
            val stemmedName = !exactSomewhere && (stemmedPhrase in indexed.normalizedNames || stemmedPhrase in indexed.normalizedAliases)
            val tokenScore = weights.sum() / inputTokens.size * 10.0
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
                    if (matchingTokens > 0) add("${matchingTokens}/${inputTokens.size} name/category tokens")
                    if (preferenceScore > 0) add("explicit preference")
                    if (originScore > 0) add("custom food")
                    if (explicitModifiers.isNotEmpty()) add("modifier constrained")
                }
            )
        }.sortedWith(compareByDescending<RankedFood> { it.score }.thenBy { it.food.name }).take(limit)
    }

    /**
     * How well one input token matches a food. An exact form (as a substring of the names) scores 1.0; a singular
     * form of a plural token that is a whole name word scores [STEM_WEIGHT]. Zero when the token does not match.
     */
    private fun tokenWeight(token: String, indexed: IndexedFood): Double = when {
        token in indexed.names || token in indexed.category -> EXACT_WEIGHT
        stems(token).any { it in indexed.nameTokens } -> STEM_WEIGHT
        transliterated(token, indexed) -> TRANSLITERATED_WEIGHT
        else -> 0.0
    }

    /** The token in the other script: "pindzur" finds Пинџур and "кисело" finds "kiselo mleko". */
    private fun transliterated(token: String, indexed: IndexedFood): Boolean {
        val canonical = Transliteration.canonical(token)
        return canonical in indexed.canonicalNames || stems(canonical).any { it in indexed.canonicalTokens }
    }

    /** Singular forms to try for a token: "eggs" gives "egg", "berries" gives "berry". Never shorter than three letters. */
    private fun stems(token: String): List<String> {
        val candidates = buildList {
            if (token.endsWith("ies")) add(token.dropLast(3) + "y")
            if (token.endsWith("es")) add(token.dropLast(2))
            if (token.endsWith("s") && !token.endsWith("ss")) add(token.dropLast(1))
        }
        return candidates.filter { it.length >= MIN_STEM_LENGTH }.distinct()
    }

    fun best(phrase: String, preferences: Map<String, Preference> = emptyMap()): RankedFood? =
        suggestions(phrase, preferences, 1).firstOrNull()

    /** True when [phrase] is exactly one food's name or alias, ignoring case and stop words. */
    fun isExactName(phrase: String): Boolean {
        val normalizedPhrase = normalize(phrase)
        return normalizedPhrase.isNotEmpty() &&
            indexedCatalog.any { normalizedPhrase in it.normalizedNames || normalizedPhrase in it.normalizedAliases }
    }

    /** True when [phrase] is one food's name or alias, or one food contains every word of it. Used to keep names whole. */
    fun covers(phrase: String): Boolean {
        if (isExactName(phrase)) return true
        val normalizedPhrase = normalize(phrase)
        if (normalizedPhrase.isEmpty()) return false
        val inputTokens = tokens(expandAbbreviations(phrase))
        return inputTokens.isNotEmpty() && indexedCatalog.any { indexed ->
            inputTokens.all { token -> tokenWeight(token, indexed) > 0.0 }
        }
    }

    fun expandAbbreviations(phrase: String): String = phrase.lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .joinToString(" ") { abbreviations[it] ?: it }

    private fun tokens(value: String): List<String> = value
        .lowercase(Locale.ROOT)
        .replace(TOKEN_CHARACTER_PATTERN, " ")
        .split(WHITESPACE_PATTERN)
        .filter { it.isNotBlank() && it !in STOP_WORDS }

    private fun normalize(value: String): String = tokens(expandAbbreviations(value)).joinToString(" ")

    companion object {
        private const val EXACT_WEIGHT = 1.0
        private const val STEM_WEIGHT = 0.95
        private const val TRANSLITERATED_WEIGHT = 0.9
        private const val MIN_STEM_LENGTH = 3
        private const val STEMMED_NAME_SCORE = 50.0
        private val STOP_WORDS = ShorthandLexicon.stopWords
        private val TOKEN_CHARACTER_PATTERN = Regex("[^\\p{L}\\p{N}.%]+")
        private val WHITESPACE_PATTERN = Regex("\\s+")
        private val NUMERIC_TOKEN_PATTERN = Regex("\\d+(?:[.]\\d+)?")
        private val MODIFIERS = setOf(
            "oat", "oats", "small", "large", "smooth", "crunchy", "raw", "cooked", "roasted", "grilled",
            "boneless", "skinless", "drained", "canned", "yellow", "green", "red", "orange", "white",
            "roma", "grape", "baby", "mature", "light", "restaurant", "2.8", "3.2", "домашно", "домашен"
        )
    }
}
