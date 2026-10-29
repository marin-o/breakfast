package com.iron.breakfast.domain

import java.util.Locale

/** The words the shorthand reads as structure rather than food: meal markers, number words and filler words. */
object ShorthandLexicon {
    private val WHITESPACE = Regex("\\s+")
    private val mealWords = mapOf(
        "breakfast" to MealSlot.BREAKFAST,
        "lunch" to MealSlot.LUNCH,
        "dinner" to MealSlot.DINNER,
        "snacks" to MealSlot.SNACKS,
        "snack" to MealSlot.SNACKS,
        // Macedonian, Cyrillic and Latin. A Latin č, š or ž is read without its mark ("ruček" is "rucek").
        "појадок" to MealSlot.BREAKFAST,
        "pojadok" to MealSlot.BREAKFAST,
        "доручек" to MealSlot.BREAKFAST,
        "dorucek" to MealSlot.BREAKFAST,
        "ручек" to MealSlot.LUNCH,
        "rucek" to MealSlot.LUNCH,
        "вечера" to MealSlot.DINNER,
        "vecera" to MealSlot.DINNER,
        "ужина" to MealSlot.SNACKS,
        "uzina" to MealSlot.SNACKS,
        "закуска" to MealSlot.SNACKS,
        "zakuska" to MealSlot.SNACKS,
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

    /** How a unit word reads: the stored unit, and how many of that unit one word stands for ("kg" is 1000 g). */
    data class UnitSpec(val unit: MeasureUnit, val factor: Double = 1.0)

    private val units: Map<String, UnitSpec> = mapOf(
        "g" to UnitSpec(MeasureUnit.GRAM), "gr" to UnitSpec(MeasureUnit.GRAM), "gram" to UnitSpec(MeasureUnit.GRAM),
        "grams" to UnitSpec(MeasureUnit.GRAM), "г" to UnitSpec(MeasureUnit.GRAM), "гр" to UnitSpec(MeasureUnit.GRAM),
        "грам" to UnitSpec(MeasureUnit.GRAM), "kg" to UnitSpec(MeasureUnit.GRAM, 1000.0), "кг" to UnitSpec(MeasureUnit.GRAM, 1000.0),
        "ml" to UnitSpec(MeasureUnit.MILLILITER), "milliliter" to UnitSpec(MeasureUnit.MILLILITER),
        "milliliters" to UnitSpec(MeasureUnit.MILLILITER), "millilitre" to UnitSpec(MeasureUnit.MILLILITER),
        "millilitres" to UnitSpec(MeasureUnit.MILLILITER), "мл" to UnitSpec(MeasureUnit.MILLILITER),
        "cl" to UnitSpec(MeasureUnit.MILLILITER, 10.0), "dl" to UnitSpec(MeasureUnit.MILLILITER, 100.0),
        "l" to UnitSpec(MeasureUnit.MILLILITER, 1000.0), "litre" to UnitSpec(MeasureUnit.MILLILITER, 1000.0),
        "litres" to UnitSpec(MeasureUnit.MILLILITER, 1000.0), "liter" to UnitSpec(MeasureUnit.MILLILITER, 1000.0),
        "liters" to UnitSpec(MeasureUnit.MILLILITER, 1000.0), "л" to UnitSpec(MeasureUnit.MILLILITER, 1000.0),
        "tbsp" to UnitSpec(MeasureUnit.TABLESPOON), "tablespoon" to UnitSpec(MeasureUnit.TABLESPOON),
        "tablespoons" to UnitSpec(MeasureUnit.TABLESPOON), "лажица" to UnitSpec(MeasureUnit.TABLESPOON),
        "лажици" to UnitSpec(MeasureUnit.TABLESPOON),
        "tsp" to UnitSpec(MeasureUnit.TEASPOON), "teaspoon" to UnitSpec(MeasureUnit.TEASPOON),
        "teaspoons" to UnitSpec(MeasureUnit.TEASPOON), "лажиче" to UnitSpec(MeasureUnit.TEASPOON),
        "лажичка" to UnitSpec(MeasureUnit.TEASPOON),
        "piece" to UnitSpec(MeasureUnit.PIECE), "pieces" to UnitSpec(MeasureUnit.PIECE), "pc" to UnitSpec(MeasureUnit.PIECE),
        "pcs" to UnitSpec(MeasureUnit.PIECE), "slice" to UnitSpec(MeasureUnit.PIECE), "slices" to UnitSpec(MeasureUnit.PIECE),
        "парче" to UnitSpec(MeasureUnit.PIECE), "парчиња" to UnitSpec(MeasureUnit.PIECE),
        // A recipe's portion ("2 portions chili"); a food has no such unit, so for a food the part is not counted.
        "portion" to UnitSpec(MeasureUnit.PORTION), "portions" to UnitSpec(MeasureUnit.PORTION),
    )

    /** The unit a word reads as, with its factor. Null for anything that is not a unit (cups, for one, stay unsupported). */
    fun unitSpec(text: String?): UnitSpec? = text?.trim()?.lowercase(Locale.ROOT)?.let { units[it] }

    /** A regex alternation of the unit words, longest first. The parser and the segmenter both read it. */
    fun unitAlternation(): String = units.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    /** Words that never name a food on their own. "a" is here so it can never be matched as a food token. */
    val stopWords: Set<String> = setOf("a", "the", "with", "and", "of", "my")

    /** Words that join two foods in one line. Only whole words count; "with" and "со" are food text. */
    val explicitSeparators: Set<String> = setOf("+", "&", "and", "и")

    /** The meal a whole segment names, such as "lunch". Null for anything else. */
    fun mealSlotOf(text: String): MealSlot? = mealWords[
        text.trim().lowercase(Locale.ROOT).replace('č', 'c').replace('š', 's').replace('ž', 'z')
    ]

    /** The amount a number word stands for ("two" is 2, "half" is 0.5). Null when [text] is not a number word. */
    fun wordAmount(text: String): Double? = wordAmounts[normalizeWords(text)]

    /** True for "half", "quarter" and the Macedonian half, which take the fraction phrasing. */
    fun isFractionWord(text: String): Boolean = normalizeWords(text) in fractionWords

    /** A regex alternation of the number words that are not fractions, longest first, so "a couple of" wins over "a". */
    fun wordAlternation(): String = wordAmounts.keys
        .filter { it !in fractionWords }
        .sortedByDescending { it.length }
        .joinToString("|") { key -> key.split(' ').joinToString("\\s+") { Regex.escape(it) } }

    private fun normalizeWords(text: String): String = text.trim().lowercase(Locale.ROOT).split(WHITESPACE).joinToString(" ")
}
