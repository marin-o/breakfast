package com.iron.breakfast.food

import com.iron.breakfast.domain.NutritionInput
import com.iron.breakfast.domain.ResolvedNutrition
import com.iron.breakfast.domain.parseDecimal

// The food form's pure decisions, unit-tested: what a refused save points at, and what Back does.

/** A personal-food form field that a refused save points at. */
internal enum class FoodFormField { NAME, BARCODE, BASIS, KCAL, PROTEIN, CARBS, FAT }

/** Why a personal-food save is refused: the notice, and the field to move focus to. */
internal data class FoodSaveRefusal(val message: String, val field: FoodFormField)

/**
 * The personal-food save checks, in order: a structured number that is not a number, name and kcal, the barcode,
 * the local bounds (kcal 0 to 10000, macros 0 to 1000 per basis), then a positive basis. Null when the food may save.
 */
internal fun foodSaveRefusal(name: String, input: NutritionInput, resolved: ResolvedNutrition, barcodeError: String?): FoodSaveRefusal? {
    val invalidNumbers = "Nutrition values must be finite and nonnegative."
    if (resolved.invalid) {
        val field = listOf(
            FoodFormField.KCAL to input.calories, FoodFormField.PROTEIN to input.protein, FoodFormField.CARBS to input.carbs,
            FoodFormField.FAT to input.fat, FoodFormField.BASIS to input.basisAmount,
        ).firstOrNull { (_, text) -> text.isNotBlank() && parseDecimal(text) == null }?.first ?: FoodFormField.KCAL
        return FoodSaveRefusal(invalidNumbers, field)
    }
    if (name.isBlank()) return FoodSaveRefusal("Name and calories are required.", FoodFormField.NAME)
    val kcal = resolved.calories ?: return FoodSaveRefusal("Name and calories are required.", FoodFormField.KCAL)
    if (barcodeError != null) return FoodSaveRefusal(barcodeError, FoodFormField.BARCODE)
    if (!kcal.isFinite() || kcal < 0.0 || kcal > 10000.0) return FoodSaveRefusal(invalidNumbers, FoodFormField.KCAL)
    listOf(FoodFormField.PROTEIN to resolved.protein, FoodFormField.CARBS to resolved.carbohydrates, FoodFormField.FAT to resolved.fat)
        .firstOrNull { (_, value) -> value != null && (!value.isFinite() || value < 0.0 || value > 1000.0) }
        ?.let { return FoodSaveRefusal(invalidNumbers, it.first) }
    if (!resolved.basisAmount.isFinite() || resolved.basisAmount <= 0.0) return FoodSaveRefusal("Enter a positive nutrition basis.", FoodFormField.BASIS)
    return null
}

/** What Back or the arrow does on New food or Edit food. */
internal enum class FoodFormBack { IGNORE, ASK, CLOSE }

/** While a save runs, Back waits for it; a changed form asks before discarding; an unchanged one closes. */
internal fun foodFormBack(saving: Boolean, dirty: Boolean): FoodFormBack = when {
    saving -> FoodFormBack.IGNORE
    dirty -> FoodFormBack.ASK
    else -> FoodFormBack.CLOSE
}
