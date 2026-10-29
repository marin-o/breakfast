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

    /**
     * How a unit word reads: the stored unit, how many of that unit one word stands for ("kg" is 1000 g), and the label
     * an amount converted that way is shown with ("kg").
     */
    data class UnitSpec(val unit: MeasureUnit, val factor: Double = 1.0, val label: String? = null)

    private val units: Map<String, UnitSpec> = mapOf(
        "g" to UnitSpec(MeasureUnit.GRAM), "gr" to UnitSpec(MeasureUnit.GRAM), "gram" to UnitSpec(MeasureUnit.GRAM),
        "grams" to UnitSpec(MeasureUnit.GRAM), "г" to UnitSpec(MeasureUnit.GRAM), "гр" to UnitSpec(MeasureUnit.GRAM),
        "грам" to UnitSpec(MeasureUnit.GRAM), "kg" to UnitSpec(MeasureUnit.GRAM, 1000.0, "kg"), "кг" to UnitSpec(MeasureUnit.GRAM, 1000.0, "kg"),
        "oz" to UnitSpec(MeasureUnit.GRAM, OUNCE_G, "oz"), "ounce" to UnitSpec(MeasureUnit.GRAM, OUNCE_G, "oz"),
        "ounces" to UnitSpec(MeasureUnit.GRAM, OUNCE_G, "oz"), "lb" to UnitSpec(MeasureUnit.GRAM, POUND_G, "lb"),
        "lbs" to UnitSpec(MeasureUnit.GRAM, POUND_G, "lb"), "pound" to UnitSpec(MeasureUnit.GRAM, POUND_G, "lb"),
        "pounds" to UnitSpec(MeasureUnit.GRAM, POUND_G, "lb"),
        "ml" to UnitSpec(MeasureUnit.MILLILITER), "milliliter" to UnitSpec(MeasureUnit.MILLILITER),
        "milliliters" to UnitSpec(MeasureUnit.MILLILITER), "millilitre" to UnitSpec(MeasureUnit.MILLILITER),
        "millilitres" to UnitSpec(MeasureUnit.MILLILITER), "мл" to UnitSpec(MeasureUnit.MILLILITER),
        "cl" to UnitSpec(MeasureUnit.MILLILITER, 10.0, "cl"), "dl" to UnitSpec(MeasureUnit.MILLILITER, 100.0, "dl"),
        "l" to UnitSpec(MeasureUnit.MILLILITER, 1000.0, "l"), "litre" to UnitSpec(MeasureUnit.MILLILITER, 1000.0, "l"),
        "litres" to UnitSpec(MeasureUnit.MILLILITER, 1000.0, "l"), "liter" to UnitSpec(MeasureUnit.MILLILITER, 1000.0, "l"),
        "liters" to UnitSpec(MeasureUnit.MILLILITER, 1000.0, "l"), "л" to UnitSpec(MeasureUnit.MILLILITER, 1000.0, "l"),
        "fl oz" to UnitSpec(MeasureUnit.MILLILITER, FLUID_OUNCE_ML, "fl oz"),
        "cup" to UnitSpec(MeasureUnit.CUP), "cups" to UnitSpec(MeasureUnit.CUP),
        "tbsp" to UnitSpec(MeasureUnit.TABLESPOON), "tablespoon" to UnitSpec(MeasureUnit.TABLESPOON),
        "tablespoons" to UnitSpec(MeasureUnit.TABLESPOON), "лажица" to UnitSpec(MeasureUnit.TABLESPOON),
        "лажици" to UnitSpec(MeasureUnit.TABLESPOON),
        "tsp" to UnitSpec(MeasureUnit.TEASPOON), "teaspoon" to UnitSpec(MeasureUnit.TEASPOON),
        "teaspoons" to UnitSpec(MeasureUnit.TEASPOON), "лажиче" to UnitSpec(MeasureUnit.TEASPOON),
        "лажичка" to UnitSpec(MeasureUnit.TEASPOON),
        "piece" to UnitSpec(MeasureUnit.PIECE), "pieces" to UnitSpec(MeasureUnit.PIECE), "pc" to UnitSpec(MeasureUnit.PIECE),
        "pcs" to UnitSpec(MeasureUnit.PIECE), "slice" to UnitSpec(MeasureUnit.PIECE), "slices" to UnitSpec(MeasureUnit.PIECE),
        "парче" to UnitSpec(MeasureUnit.PIECE), "парчиња" to UnitSpec(MeasureUnit.PIECE),
    )

    /** The unit a word reads as, with its factor. Null for anything that is not a unit. */
    fun unitSpec(text: String?): UnitSpec? = text?.trim()?.lowercase(Locale.ROOT)?.replace(WHITESPACE, " ")?.let { units[it] }

    /** The factor a converted unit's [label] ("kg", "fl oz") stands for: what an amount in g or ml is divided by to show it. */
    fun factorOf(label: String): Double? = units.values.firstOrNull { it.label == label }?.factor

    /**
     * Unit words that came with measurements (cup, oz, lb, fl oz). A food whose name holds one ("Cup noodles") keeps
     * it as part of the name, so it logs as it did before they were units.
     */
    val nameUnitWords: Set<String> = setOf("cup", "cups", "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds", "fl oz")

    private const val OUNCE_G = 28.35
    private const val POUND_G = 453.6
    private const val FLUID_OUNCE_ML = 29.57

    /** A regex alternation of the unit words, longest first. The parser and the segmenter both read it. */
    fun unitAlternation(): String = units.keys.sortedByDescending { it.length }.joinToString("|") { key -> key.split(' ').joinToString("\\s+") { Regex.escape(it) } }

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
