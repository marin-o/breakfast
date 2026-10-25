package com.iron.breakfast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionInput
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.splitAliases
import kotlinx.serialization.Serializable

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

    /** The values typed into the form, as they are now. */
    fun fields() = FoodDraftFields(
        name, brand, barcode, shorthand, calories, protein, carbs, fat, basisAmount, basisUnit,
        aliases, aliasInput, servings, servingUnit, servingAmount, servingLabel,
    )

    /** True when any typed value differs from [baseline], the form as it opened. Opening a field changes nothing. */
    fun isDirty(baseline: FoodDraftFields): Boolean = fields() != baseline

    /** The whole form, values and flags, so it can be put back as it was after the process is killed. */
    fun state() = FoodDraftState(
        fields(), barcodeFromScan, fromOff, caloriesEdited, proteinEdited, carbsEdited, fatEdited, basisEdited,
        aliasFieldOpen, servingFormOpen,
    )

    fun restore(state: FoodDraftState) {
        val f = state.fields
        name = f.name; brand = f.brand; barcode = f.barcode; shorthand = f.shorthand
        calories = f.calories; protein = f.protein; carbs = f.carbs; fat = f.fat
        basisAmount = f.basisAmount; basisUnit = f.basisUnit
        aliases = f.aliases; aliasInput = f.aliasInput
        servings = f.servings; servingUnit = f.servingUnit; servingAmount = f.servingAmount; servingLabel = f.servingLabel
        barcodeFromScan = state.barcodeFromScan; fromOff = state.fromOff
        caloriesEdited = state.caloriesEdited; proteinEdited = state.proteinEdited; carbsEdited = state.carbsEdited
        fatEdited = state.fatEdited; basisEdited = state.basisEdited
        aliasFieldOpen = state.aliasFieldOpen; servingFormOpen = state.servingFormOpen
    }
}

/** The food form's typed values. Opening the alias or serving field and the "from scan" tag are not part of them. */
@Serializable
data class FoodDraftFields(
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val shorthand: String = "",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val basisAmount: String = "100",
    val basisUnit: String = "g",
    val aliases: List<String> = emptyList(),
    val aliasInput: String = "",
    val servings: List<ServingConversion> = emptyList(),
    val servingUnit: MeasureUnit = MeasureUnit.PIECE,
    val servingAmount: String = "",
    val servingLabel: String = "",
)

/** [FoodDraftFields] plus the form's own flags: what [FoodDraft.restore] needs to show the form as it was. */
@Serializable
data class FoodDraftState(
    val fields: FoodDraftFields = FoodDraftFields(),
    val barcodeFromScan: Boolean = false,
    val fromOff: Boolean = false,
    val caloriesEdited: Boolean = false,
    val proteinEdited: Boolean = false,
    val carbsEdited: Boolean = false,
    val fatEdited: Boolean = false,
    val basisEdited: Boolean = false,
    val aliasFieldOpen: Boolean = false,
    val servingFormOpen: Boolean = false,
)

/** A stored number as the form shows it: as written, never in exponent form ("5.0E-4" would edit into 5.04). */
private fun Double.fieldText(): String = toString().let { if ('E' in it) java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString() else it }
