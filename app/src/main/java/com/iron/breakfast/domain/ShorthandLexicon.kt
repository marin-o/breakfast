package com.iron.breakfast.domain

import java.util.Locale

/** The words the shorthand reads as structure rather than food: meal markers, number words and filler words. */
object ShorthandLexicon {
    private val mealWords = mapOf(
        "breakfast" to MealSlot.BREAKFAST,
        "lunch" to MealSlot.LUNCH,
        "dinner" to MealSlot.DINNER,
        "snacks" to MealSlot.SNACKS,
        "snack" to MealSlot.SNACKS,
    )

    /** Number words and their amounts, English and Macedonian. "a half" is read through "half" with its article. */
    private val wordAmounts: Map<String, Double> = linkedMapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "a couple" to 2.0, "a couple of" to 2.0, "couple" to 2.0,
        "half" to 0.5, "quarter" to 0.25,
        "еден" to 1.0, "една" to 1.0, "едно" to 1.0, "два" to 2.0, "две" to 2.0, "три" to 3.0,
        "четири" to 4.0, "пет" to 5.0, "половина" to 0.5,
    )

    /** Words that read as a half or a quarter. They may take a leading "a" and a trailing "a" ("half a tbsp"). */
    private val fractionWords = setOf("half", "quarter", "половина")

    /** Words that never name a food on their own. "a" is here so it can never be matched as a food token. */
    val stopWords: Set<String> = setOf("a", "the", "with", "and", "of", "my")

    /** Words that join two foods in one line. Only whole words count; "with" and "со" are food text. */
    val explicitSeparators: Set<String> = setOf("+", "&", "and", "и")

    /** The meal a whole segment names, such as "lunch". Null for anything else. */
    fun mealSlotOf(text: String): MealSlot? = mealWords[text.trim().lowercase(Locale.ROOT)]

    /** The amount a number word stands for ("two" is 2, "half" is 0.5). Null when [text] is not a number word. */
    fun wordAmount(text: String): Double? = wordAmounts[normalizeWords(text)]

    /** True for "half", "quarter" and the Macedonian half, which take the fraction phrasing. */
    fun isFractionWord(text: String): Boolean = normalizeWords(text) in fractionWords

    /** A regex alternation of the number words that are not fractions, longest first, so "a couple of" wins over "a". */
    fun wordAlternation(): String = wordAmounts.keys
        .filter { it !in fractionWords }
        .sortedByDescending { it.length }
        .joinToString("|") { key -> key.split(' ').joinToString("\\s+") { Regex.escape(it) } }

    private fun normalizeWords(text: String): String = text.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).joinToString(" ")
}
