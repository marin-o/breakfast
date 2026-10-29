package com.iron.breakfast.food

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionInput
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.splitAliases
import com.iron.breakfast.domain.parseDecimal
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
    /** "100 ml =" this many g: the food's density, as typed; blank when unknown. */
    var density by mutableStateOf("")
    /** What the serving form showed when Add serving opened it; until it changes, nothing was typed there. */
    private var servingOpenedWith by mutableStateOf<PendingServing?>(null)

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
        density = food?.density?.fieldText().orEmpty()
        servingFormOpen = false; servingUnit = MeasureUnit.PIECE; servingAmount = ""; servingLabel = ""; servingOpenedWith = null
    }

    /** The density typed: a positive number, or null (blank, or not one). */
    fun densityValue(): Double? = parseDecimal(density)?.takeIf { it.isFinite() && it > 0.0 }

    /** Something typed as the density that is not a positive number: it shows as an error and Save refuses it. */
    fun densityError(): Boolean = density.isNotBlank() && densityValue() == null

    /** Aliases already added plus any typed but not yet confirmed in the alias field. */
    fun allAliases(): List<String> =
        (aliases + splitAliases(aliasInput)).distinctBy { it.lowercase() }

    fun commitAlias() {
        aliases = allAliases(); aliasInput = ""; aliasFieldOpen = false
    }

    fun commitServing(): Boolean {
        val amount = servingAmount.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        val label = servingLabel.trim().ifBlank { null }
        // A serving is kept in the food's basis units: one typed in the other of g and ml goes through the density.
        val base = servingInBasis(amount, servingUnit, if (basisUnit == MeasureUnit.MILLILITER.label) MeasureUnit.MILLILITER else MeasureUnit.GRAM, densityValue())
        servings = servings.filterNot { it.unit == servingUnit && it.label == label } + ServingConversion(servingUnit, base, "personal serving", label = label)
        closeServing()
        return true
    }

    /** Add serving: the form opens on [unit] with amount "0" and no name. */
    fun openServing(unit: MeasureUnit) {
        servingUnit = unit; servingAmount = "0"; servingLabel = ""; servingFormOpen = true
        servingOpenedWith = PendingServing(unit, "0", "")
    }

    /** Cancel puts the serving inputs back as the form loaded them. */
    fun cancelServing() = closeServing()

    private fun closeServing() {
        servingFormOpen = false; servingUnit = MeasureUnit.PIECE; servingAmount = ""; servingLabel = ""; servingOpenedWith = null
    }

    /** The open serving form holds input the user typed or chose, not just what Add serving put there. */
    private fun servingTyped(): Boolean =
        servingFormOpen && PendingServing(servingUnit, servingAmount, servingLabel) != (servingOpenedWith ?: PendingServing(MeasureUnit.PIECE, "", ""))

    /** The values typed into the form, as they are now. */
    fun fields() = FoodDraftFields(
        name, brand, barcode, shorthand, calories, protein, carbs, fat, basisAmount, basisUnit,
        aliases, aliasInput, servings, servingUnit, servingAmount, servingLabel, density,
    )

    /**
     * True when any typed value differs from [baseline], the form as it opened. Opening a field changes nothing:
     * the pending serving inputs count only once typed into; an added serving counts through [servings].
     */
    fun isDirty(baseline: FoodDraftFields): Boolean =
        fields().withoutPendingServing() != baseline.withoutPendingServing() || servingTyped()

    /** The whole form, values and flags, so it can be put back as it was after the process is killed. */
    fun state() = FoodDraftState(
        fields(), barcodeFromScan, fromOff, caloriesEdited, proteinEdited, carbsEdited, fatEdited, basisEdited,
        aliasFieldOpen, servingFormOpen, servingOpenedWith,
    )

    fun restore(state: FoodDraftState) {
        val f = state.fields
        name = f.name; brand = f.brand; barcode = f.barcode; shorthand = f.shorthand
        calories = f.calories; protein = f.protein; carbs = f.carbs; fat = f.fat
        basisAmount = f.basisAmount; basisUnit = f.basisUnit
        aliases = f.aliases; aliasInput = f.aliasInput
        servings = f.servings; servingUnit = f.servingUnit; servingAmount = f.servingAmount; servingLabel = f.servingLabel
        density = f.density
        barcodeFromScan = state.barcodeFromScan; fromOff = state.fromOff
        caloriesEdited = state.caloriesEdited; proteinEdited = state.proteinEdited; carbsEdited = state.carbsEdited
        fatEdited = state.fatEdited; basisEdited = state.basisEdited
        aliasFieldOpen = state.aliasFieldOpen; servingFormOpen = state.servingFormOpen; servingOpenedWith = state.servingOpenedWith
    }
}

/** The serving form's three inputs, as Add serving filled them. */
@Serializable
data class PendingServing(val unit: MeasureUnit, val amount: String, val label: String)

private fun FoodDraftFields.withoutPendingServing() = copy(servingUnit = MeasureUnit.PIECE, servingAmount = "", servingLabel = "")

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
    val density: String = "",
)

/**
 * [amount] of [unit] in a food's [basis] unit: as it is in the same unit, through [density] (g in 100 ml) between g and
 * ml, else as typed (no density to convert with).
 */
internal fun servingInBasis(amount: Double, unit: MeasureUnit, basis: MeasureUnit, density: Double?): Double = when {
    unit == basis || density == null -> amount
    unit == MeasureUnit.GRAM && basis == MeasureUnit.MILLILITER -> amount * 100.0 / density
    unit == MeasureUnit.MILLILITER && basis == MeasureUnit.GRAM -> amount * density / 100.0
    else -> amount
}

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
    val servingOpenedWith: PendingServing? = null,
)

/** A stored number as the form shows it: as written, never in exponent form ("5.0E-4" would edit into 5.04). */
private fun Double.fieldText(): String = toString().let { if ('E' in it) java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString() else it }
