package com.iron.breakfast.domain

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
    val match = Regex("(?i)$NumberPattern(\\s*g)?\\s*$label").find(shorthand) ?: return null
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val end = (match.groups[2] ?: match.groups[1])!!.range.last
    return ShorthandMatch(value, match.groups[1]!!.range.first..end)
}

/**
 * Reads "per 100g: 120 kcal, 2g protein, 20g carbs, 8g fat". Labels may come in any order and every
 * part is optional. The labelled-number rule is the one the personal-food form always used; the
 * optional "per 100 g / per 250 ml" basis is the only addition.
 */
fun parseNutritionShorthand(shorthand: String): ParsedShorthand {
    val basis = Regex("(?i)\\bper\\s+$NumberPattern\\s*(g|grams?|ml)\\b").find(shorthand)
    val basisAmount = basis?.let { match ->
        match.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { ShorthandMatch(it, match.groups[1]!!.range.first..match.groups[2]!!.range.last) }
    }
    return ParsedShorthand(
        calories = labelledNumber(shorthand, "kcal"),
        protein = labelledNumber(shorthand, "protein"),
        carbohydrates = labelledNumber(shorthand, "(?:carb|carbs)"),
        fat = labelledNumber(shorthand, "fat"),
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
    val structuredBasisChanged = input.basisAmount.toDoubleOrNull() != 100.0 || input.basisUnit.trim().lowercase() != "g"
    val useShorthandBasis = parsed.basisAmount != null && !structuredBasisChanged
    return ResolvedNutrition(
        calories = input.calories.toDoubleOrNull() ?: parsed.calories?.value,
        protein = input.protein.toDoubleOrNull() ?: parsed.protein?.value,
        carbohydrates = input.carbs.toDoubleOrNull() ?: parsed.carbohydrates?.value,
        fat = input.fat.toDoubleOrNull() ?: parsed.fat?.value,
        basisAmount = (if (useShorthandBasis) parsed.basisAmount?.value else null) ?: input.basisAmount.toDoubleOrNull() ?: 100.0,
        basisUnit = (if (useShorthandBasis) parsed.basisUnit else null) ?: if (input.basisUnit.trim().lowercase() == "ml") MeasureUnit.MILLILITER else MeasureUnit.GRAM,
    )
}
