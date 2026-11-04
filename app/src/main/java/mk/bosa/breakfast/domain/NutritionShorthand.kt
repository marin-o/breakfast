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
    val saturatedFat: String = "",
    val sugars: String = "",
    val fibre: String = "",
    val salt: String = "",
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
    val saturatedFat: Double? = null,
    val sugars: Double? = null,
    val fibre: Double? = null,
    val salt: Double? = null,
) {
    /** The numbers as a food keeps them; null while kcal is missing. */
    fun values(): NutritionValues? = calories?.let { NutritionValues(it, protein, carbohydrates, fat, saturatedFat, sugars, fibre, salt) }
}

/** Recognised numbers in the shorthand, as character ranges to print bold (number and its "g"). */
data class ShorthandMatch(val value: Double, val range: IntRange)

data class ParsedShorthand(
    val calories: ShorthandMatch? = null,
    val protein: ShorthandMatch? = null,
    val carbohydrates: ShorthandMatch? = null,
    val fat: ShorthandMatch? = null,
    val basisAmount: ShorthandMatch? = null,
    val basisUnit: MeasureUnit? = null,
    val saturatedFat: ShorthandMatch? = null,
    val sugars: ShorthandMatch? = null,
    val fibre: ShorthandMatch? = null,
    val salt: ShorthandMatch? = null,
) {
    val recognisedRanges: List<IntRange>
        get() = listOfNotNull(calories, protein, carbohydrates, fat, basisAmount, saturatedFat, sugars, fibre, salt).map { it.range }.sortedBy { it.first }
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
 * Reads "per 100g: 120 kcal, 2g protein, 20g carbs, 8g fat", or the same with p, c and f ("2p 20c 8f"), and saturated
 * fat ("2 sat fat", "2 sat fats", "2 satfat", "2 sat f", "2satf", "2g saturated", "2 saturates"), sugars ("5 sugar"),
 * fibre ("3 fibre", "3 fiber") and salt ("1 salt"). Labels may come in any order and every part is optional. The number comes first and
 * the label is a whole word, so a saturated fat's "fat" or "f" never also fills Fat, and there is no single letter for
 * the four (an s could be any of three). Salt only: sodium is not read. The optional "per 100 g / per 250 ml" basis is
 * the only other addition to the labelled-number rule the personal-food form always used.
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
        saturatedFat = labelledNumber(shorthand, "sat\\s+fats|sat\\s*fat|sat\\s*f|saturated(?:\\s+fat)?|saturates"),
        sugars = labelledNumber(shorthand, "sugars?"),
        fibre = labelledNumber(shorthand, "fibres?|fibers?"),
        salt = labelledNumber(shorthand, "salt"),
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
    val fields = listOf(input.calories, input.protein, input.carbs, input.fat, input.basisAmount, input.saturatedFat, input.sugars, input.fibre, input.salt)
    return ResolvedNutrition(
        calories = parseDecimal(input.calories) ?: parsed.calories?.value,
        protein = parseDecimal(input.protein) ?: parsed.protein?.value,
        carbohydrates = parseDecimal(input.carbs) ?: parsed.carbohydrates?.value,
        fat = parseDecimal(input.fat) ?: parsed.fat?.value,
        basisAmount = (if (useShorthandBasis) parsed.basisAmount?.value else null) ?: parseDecimal(input.basisAmount) ?: 100.0,
        basisUnit = (if (useShorthandBasis) parsed.basisUnit else null) ?: if (input.basisUnit.trim().lowercase() == "ml") MeasureUnit.MILLILITER else MeasureUnit.GRAM,
        invalid = fields.any { it.isNotBlank() && parseDecimal(it) == null },
        saturatedFat = parseDecimal(input.saturatedFat) ?: parsed.saturatedFat?.value,
        sugars = parseDecimal(input.sugars) ?: parsed.sugars?.value,
        fibre = parseDecimal(input.fibre) ?: parsed.fibre?.value,
        salt = parseDecimal(input.salt) ?: parsed.salt?.value,
    )
}
