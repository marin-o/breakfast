package com.iron.breakfast.domain

import java.util.Locale

/** The words the shorthand reads as structure rather than food: meal markers and filler words. */
object ShorthandLexicon {
    private val mealWords = mapOf(
        "breakfast" to MealSlot.BREAKFAST,
        "lunch" to MealSlot.LUNCH,
        "dinner" to MealSlot.DINNER,
        "snacks" to MealSlot.SNACKS,
        "snack" to MealSlot.SNACKS,
    )

    /** Words that never name a food on their own. "a" is here so it can never be matched as a food token. */
    val stopWords: Set<String> = setOf("a", "the", "with", "and", "of", "my")

    /** Words that join two foods in one line. Only whole words count; "with" and "со" are food text. */
    val explicitSeparators: Set<String> = setOf("+", "&", "and", "и")

    /** The meal a whole segment names, such as "lunch". Null for anything else. */
    fun mealSlotOf(text: String): MealSlot? = mealWords[text.trim().lowercase(Locale.ROOT)]
}
