package mk.bosa.breakfast.domain

/** What a personal-food form currently holds: the shorthand text plus the structured "Full form" fields. */
data class NutritionInput(
    val shorthand: String = "",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val basisAmount: String = "100",
    val basisUnit: String = "g",
)

/** The numbers a form resolves to. A structured field wins over the shorthand; [calories] null means required and missing. */
data class ResolvedNutrition(
    val calories: Double?,
    val protein: Double?,
    val carbohydrates: Double?,
    val fat: Double?,
    val basisAmount: Double,
    val basisUnit: MeasureUnit,
    /** True when a structured field holds text that is not a number; it is never read as unknown. */
    val invalid: Boolean = false,
)

/** Recognised numbers in the shorthand, as character ranges to print bold (number and its "g"). */
data class ShorthandMatch(val value: Double, val range: IntRange)

data class ParsedShorthand(
    val calories: ShorthandMatch? = null,
    val protein: ShorthandMatch? = null,
    val carbohydrates: ShorthandMatch? = null,
    val fat: ShorthandMatch? = null,
    val basisAmount: ShorthandMatch? = null,
    val basisUnit: MeasureUnit? = null,
) {
    val recognisedRanges: List<IntRange>
        get() = listOfNotNull(calories, protein, carbohydrates, fat, basisAmount).map { it.range }.sortedBy { it.first }
}

private const val NumberPattern = "([0-9]+(?:[.,][0-9]+)?)"

private fun labelledNumber(shorthand: String, label: String): ShorthandMatch? {
    // The label is a whole word, so "2 cups" is not carbs and "3 fiber" is not fat.
    val match = Regex("(?iu)$NumberPattern(\\s*g)?\\s*(?:$label)(?!\\p{L})").find(shorthand) ?: return null
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val end = (match.groups[2] ?: match.groups[1])!!.range.last
    return ShorthandMatch(value, match.groups[1]!!.range.first..end)
}

/**
 * Reads "per 100g: 120 kcal, 2g protein, 20g carbs, 8g fat", or the same with p, c and f ("2p 20c 8f"). Labels may come
 * in any order and every
 * part is optional. The labelled-number rule is the one the personal-food form always used; the
 * optional "per 100 g / per 250 ml" basis is the only addition.
 */
fun parseNutritionShorthand(shorthand: String): ParsedShorthand {
    val basis = Regex("(?iu)\\bper\\s+$NumberPattern\\s*(g|grams?|ml)\\b").find(shorthand)
    val basisAmount = basis?.let { match ->
        match.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { ShorthandMatch(it, match.groups[1]!!.range.first..match.groups[2]!!.range.last) }
    }
    return ParsedShorthand(
        calories = labelledNumber(shorthand, "kcals?"),
        protein = labelledNumber(shorthand, "p|proteins?"),
        carbohydrates = labelledNumber(shorthand, "c|carbs?|carbohydrates?"),
        fat = labelledNumber(shorthand, "f|fats?"),
        basisAmount = basisAmount,
        basisUnit = basis?.takeIf { basisAmount != null }?.let { if (it.groupValues[2].lowercase() == "ml") MeasureUnit.MILLILITER else MeasureUnit.GRAM },
    )
}

/**
 * Structured fields win field by field. The basis comes from a "per N g" in the shorthand when present,
 * otherwise from the structured basis fields (100 g by default).
 */
fun resolveNutrition(input: NutritionInput): ResolvedNutrition {
    val parsed = parseNutritionShorthand(input.shorthand)
    val structuredBasisChanged = parseDecimal(input.basisAmount) != 100.0 || input.basisUnit.trim().lowercase() != "g"
    val useShorthandBasis = parsed.basisAmount != null && !structuredBasisChanged
    val fields = listOf(input.calories, input.protein, input.carbs, input.fat, input.basisAmount)
    return ResolvedNutrition(
        calories = parseDecimal(input.calories) ?: parsed.calories?.value,
        protein = parseDecimal(input.protein) ?: parsed.protein?.value,
        carbohydrates = parseDecimal(input.carbs) ?: parsed.carbohydrates?.value,
        fat = parseDecimal(input.fat) ?: parsed.fat?.value,
        basisAmount = (if (useShorthandBasis) parsed.basisAmount?.value else null) ?: parseDecimal(input.basisAmount) ?: 100.0,
        basisUnit = (if (useShorthandBasis) parsed.basisUnit else null) ?: if (input.basisUnit.trim().lowercase() == "ml") MeasureUnit.MILLILITER else MeasureUnit.GRAM,
        invalid = fields.any { it.isNotBlank() && parseDecimal(it) == null },
    )
}
