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
        val normalizedNames: Set<String>,
        val normalizedAliases: Set<String>
    )

    private val indexedCatalog = catalog.map { food ->
        IndexedFood(
            food = food,
            names = (food.allNames() + listOfNotNull(food.preparation, food.sourceDescription))
                .joinToString(" ").lowercase(Locale.ROOT),
            category = food.category.lowercase(Locale.ROOT),
            normalizedNames = food.allNames().mapTo(mutableSetOf(), ::normalize),
            normalizedAliases = food.aliases.mapTo(mutableSetOf(), ::normalize)
        )
    }

    fun suggestions(phrase: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 3): List<RankedFood> {
        val expanded = expandAbbreviations(phrase)
        val inputTokens = tokens(expanded)
        if (inputTokens.isEmpty()) return emptyList()
        val normalizedPhrase = normalize(phrase)
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
            val identityMatches = identityTokens.count { token -> token in indexed.names || token in indexed.category }
            val exactName = normalizedPhrase in indexed.normalizedNames
            if (identityTokens.isNotEmpty() && identityMatches == 0) return@mapNotNull null
            if (identityTokens.isEmpty() && !exactName) return@mapNotNull null

            val matchingTokens = inputTokens.count { token -> token in indexed.names || token in indexed.category }
            if (matchingTokens == 0) return@mapNotNull null
            val aliasExact = normalizedPhrase in indexed.normalizedAliases
            val tokenScore = matchingTokens.toDouble() / inputTokens.size * 10.0
            val preferenceScore = (preferences[food.id]?.score(now) ?: 0.0) * 3.0
            val explicitScore = if (aliasExact) 100.0 else 0.0
            val categoryScore = if (inputTokens.any { it in indexed.category }) 0.8 else 0.0
            val originScore = if (food.origin == FoodOrigin.CUSTOM) 2.0 else 0.0
            RankedFood(
                food = food,
                score = explicitScore + tokenScore + categoryScore + originScore + preferenceScore,
                reasons = buildList {
                    if (aliasExact) add("explicit alias")
                    if (matchingTokens > 0) add("${matchingTokens}/${inputTokens.size} name/category tokens")
                    if (preferenceScore > 0) add("explicit preference")
                    if (originScore > 0) add("custom food")
                    if (explicitModifiers.isNotEmpty()) add("modifier constrained")
                }
            )
        }.sortedWith(compareByDescending<RankedFood> { it.score }.thenBy { it.food.name }).take(limit)
    }

    fun best(phrase: String, preferences: Map<String, Preference> = emptyMap()): RankedFood? =
        suggestions(phrase, preferences, 1).firstOrNull()

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
        private val STOP_WORDS = setOf("a", "the", "with", "and", "of", "my")
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
