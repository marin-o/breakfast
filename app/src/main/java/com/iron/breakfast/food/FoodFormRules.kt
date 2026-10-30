package com.iron.breakfast.food

import com.iron.breakfast.domain.canonicalBarcode
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.NutritionInput
import com.iron.breakfast.domain.sameBarcode
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.domain.ResolvedNutrition
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.resolveNutrition

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

/** What the create form resolves to and whether it can be saved. Calories are required. */
internal data class CreateFoodState(
    val nutrition: ResolvedNutrition,
    val caloriesMissing: Boolean,
    /** The red ring and "Required: kcal" tag: shown once the user has started on nutrition. */
    val showRequired: Boolean,
    val canSave: Boolean,
)

internal fun createFoodState(input: NutritionInput, name: String, barcodeError: String?): CreateFoodState {
    val nutrition = resolveNutrition(input)
    val missing = nutrition.calories == null
    val touched = listOf(input.shorthand, input.calories, input.protein, input.carbs, input.fat).any { it.isNotBlank() }
    return CreateFoodState(nutrition, missing, showRequired = missing && touched, canSave = !missing && name.isNotBlank() && barcodeError == null)
}

/**
 * What is wrong with the [barcode] in a food form, or null: blank is no barcode, and a food being edited ([editingId])
 * may keep the code it has in any spelling. The field shows this as it is typed, and Save refuses by it.
 */
internal fun foodBarcodeError(barcode: String, foods: List<FoodRecord>, editingId: String?): String? {
    val code = barcode.trim()
    return if (keptBarcode(code, foods, editingId) != null) null else validateBarcodeInput(code, foods, editingId)
}

/** What a saved food stores for [barcode]: the spelling it already has when unchanged, else the canonical form. */
internal fun storedBarcodeFor(barcode: String, foods: List<FoodRecord>, editingId: String?): String =
    keptBarcode(barcode, foods, editingId) ?: canonicalBarcode(barcode) ?: barcode

/** The code the edited food already has, when [code] is it in any spelling; null when it is new or changed. */
private fun keptBarcode(code: String, foods: List<FoodRecord>, editingId: String?): String? {
    val original = editingId?.let { id -> foods.firstOrNull { it.id == id }?.barcode?.trim() } ?: return null
    return original.takeIf { code == it || sameBarcode(code, it) }
}

/** New food marks a code from a scan, and has no Clear or rescan then; Edit food never does (M-Food-Edit). */
internal fun barcodeMarkedFromScan(fromScan: Boolean, editingId: String?): Boolean = fromScan && editingId == null
