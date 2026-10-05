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

    fun suggestions(phrase: String, preferences: Map<String, Preference> = emptyMap(), limit: Int = 3): List<RankedFood> {
        val expanded = expandAbbreviations(phrase)
        val inputTokens = tokens(expanded)
        if (inputTokens.isEmpty()) return emptyList()

        return catalog.mapNotNull { food ->
            val names = (food.allNames() + listOfNotNull(food.preparation, food.sourceDescription))
                .joinToString(" ").lowercase(Locale.ROOT)
            val category = food.category.lowercase(Locale.ROOT)
            val explicitModifiers = inputTokens.filter { token ->
                token in MODIFIERS || token.endsWith("%") || token.matches(Regex("\\d+(?:[.]\\d+)?"))
            }
            if (explicitModifiers.any { it !in names && it !in category }) return@mapNotNull null
            val identityTokens = inputTokens.filterNot { it in MODIFIERS || it.endsWith("%") || it.matches(Regex("\\d+(?:[.]\\d+)?")) }
            val identityMatches = identityTokens.count { token -> token in names || token in category }
            val exactName = food.allNames().any { normalize(it) == normalize(phrase) }
            if (identityTokens.isNotEmpty() && identityMatches == 0) return@mapNotNull null
            if (identityTokens.isEmpty() && !exactName) return@mapNotNull null

            val matchingTokens = inputTokens.count { token -> token in names || token in category }
            if (matchingTokens == 0) return@mapNotNull null
            val aliasExact = food.aliases.any { normalize(it) == normalize(phrase) }
            val tokenScore = matchingTokens.toDouble() / inputTokens.size * 10.0
            val preferenceScore = (preferences[food.id]?.score(nowMs()) ?: 0.0) * 3.0
            val explicitScore = if (aliasExact) 100.0 else 0.0
            val categoryScore = if (inputTokens.any { it in category }) 0.8 else 0.0
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
        .replace(Regex("[^\\p{L}\\p{N}.%]+"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in STOP_WORDS }

    private fun normalize(value: String): String = tokens(expandAbbreviations(value)).joinToString(" ")

    companion object {
        private val STOP_WORDS = setOf("a", "the", "with", "and", "of", "my")
        private val MODIFIERS = setOf(
            "oat", "oats", "small", "large", "smooth", "crunchy", "raw", "cooked", "roasted", "grilled",
            "boneless", "skinless", "drained", "canned", "yellow", "green", "red", "orange", "white",
            "roma", "grape", "baby", "mature", "light", "restaurant", "2.8", "3.2", "домашно", "домашен"
        )
    }
}
