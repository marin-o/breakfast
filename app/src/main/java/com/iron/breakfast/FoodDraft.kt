package com.iron.breakfast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionInput
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.splitAliases

/**
 * Holds the personal-food form (create and edit) in the ViewModel so it survives the barcode
 * scanner replacing the composition. Both food forms bind to the same single draft.
 */
class FoodDraft {
    var name by mutableStateOf("")
    var brand by mutableStateOf("")
    var barcode by mutableStateOf("")
    /** The barcode was seeded by a scan; the create form tags it "from scan" until it is edited. */
    var barcodeFromScan by mutableStateOf(false)
    var shorthand by mutableStateOf("")
    var calories by mutableStateOf("")
    var protein by mutableStateOf("")
    var carbs by mutableStateOf("")
    var fat by mutableStateOf("")
    var caloriesEdited by mutableStateOf(false)
    var proteinEdited by mutableStateOf(false)
    var carbsEdited by mutableStateOf(false)
    var fatEdited by mutableStateOf(false)
    var basisEdited by mutableStateOf(false)
    var basisAmount by mutableStateOf("100")
    var basisUnit by mutableStateOf("g")
    var fromOff by mutableStateOf(false)
    var aliases by mutableStateOf<List<String>>(emptyList())
    var aliasInput by mutableStateOf("")
    var aliasFieldOpen by mutableStateOf(false)
    var servings by mutableStateOf<List<ServingConversion>>(emptyList())
    var servingFormOpen by mutableStateOf(false)
    var servingUnit by mutableStateOf(MeasureUnit.PIECE)
    var servingAmount by mutableStateOf("")
    var servingLabel by mutableStateOf("")

    fun reset() = load(null)

    fun nutritionInput() = NutritionInput(shorthand, calories, protein, carbs, fat, basisAmount, basisUnit)

    fun load(food: FoodRecord?) {
        name = food?.name.orEmpty(); brand = food?.brand.orEmpty(); barcode = food?.barcode.orEmpty(); shorthand = ""
        calories = food?.nutrition?.values?.calories?.fieldText().orEmpty()
        protein = food?.nutrition?.values?.protein?.fieldText().orEmpty()
        carbs = food?.nutrition?.values?.carbohydrates?.fieldText().orEmpty()
        fat = food?.nutrition?.values?.fat?.fieldText().orEmpty()
        basisAmount = food?.nutrition?.amount?.fieldText() ?: "100"
        basisUnit = food?.nutrition?.unit?.label ?: "g"
        fromOff = false; barcodeFromScan = false
        caloriesEdited = false; proteinEdited = false; carbsEdited = false; fatEdited = false; basisEdited = false
        aliases = food?.aliases.orEmpty(); aliasInput = ""; aliasFieldOpen = false
        servings = food?.householdServings().orEmpty()
        servingFormOpen = false; servingUnit = MeasureUnit.PIECE; servingAmount = ""; servingLabel = ""
    }

    /** Aliases already added plus any typed but not yet confirmed in the alias field. */
    fun allAliases(): List<String> =
        (aliases + splitAliases(aliasInput)).distinctBy { it.lowercase() }

    fun commitAlias() {
        aliases = allAliases(); aliasInput = ""; aliasFieldOpen = false
    }

    fun commitServing(): Boolean {
        val base = servingAmount.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        val label = servingLabel.trim().ifBlank { null }
        servings = servings.filterNot { it.unit == servingUnit && it.label == label } + ServingConversion(servingUnit, base, "personal serving", label = label)
        servingFormOpen = false; servingAmount = ""; servingLabel = ""
        return true
    }

    fun cancelServing() {
        servingFormOpen = false; servingAmount = ""; servingLabel = ""
    }
}

/** A stored number as the form shows it: as written, never in exponent form ("5.0E-4" would edit into 5.04). */
private fun Double.fieldText(): String = toString().let { if ('E' in it) java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString() else it }
