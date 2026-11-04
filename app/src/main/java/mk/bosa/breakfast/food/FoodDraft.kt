package mk.bosa.breakfast.food

import mk.bosa.breakfast.domain.isPositiveAmount
import mk.bosa.breakfast.domain.fieldText
import mk.bosa.breakfast.domain.fieldNumber
import mk.bosa.breakfast.domain.editableNumber
import mk.bosa.breakfast.domain.positiveAmountOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionInput
import mk.bosa.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import mk.bosa.breakfast.domain.ServingConversion
import mk.bosa.breakfast.domain.TypedFoodName
import mk.bosa.breakfast.domain.resolveNutrition
import mk.bosa.breakfast.domain.splitAliases
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
    /** The food this draft loaded, whose exact numbers its untouched fields stand for. */
    private var loaded: FoodRecord? = null
    var shorthand by mutableStateOf("")
    var calories by mutableStateOf("")
    var protein by mutableStateOf("")
    var carbs by mutableStateOf("")
    var fat by mutableStateOf("")
    var saturatedFat by mutableStateOf("")
    var sugars by mutableStateOf("")
    var fibre by mutableStateOf("")
    var salt by mutableStateOf("")
    var caloriesEdited by mutableStateOf(false)
    var proteinEdited by mutableStateOf(false)
    var carbsEdited by mutableStateOf(false)
    var fatEdited by mutableStateOf(false)
    var saturatedFatEdited by mutableStateOf(false)
    var sugarsEdited by mutableStateOf(false)
    var fibreEdited by mutableStateOf(false)
    var saltEdited by mutableStateOf(false)
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
    /** The basis the amounts in [servings] were added in; null when they are in the basis as it is now. */
    private var servingsBasis by mutableStateOf<MeasureUnit?>(null)
    /** What the serving form showed when Add serving opened it; until it changes, nothing was typed there. */
    private var servingOpenedWith by mutableStateOf<PendingServing?>(null)
    /** The name a serving word was split off and the serving's form is still open for: Cancel undoes the split. */
    private var nameSplit by mutableStateOf<NameSplit?>(null)
    /** The split Cancel undid: the name it put back is no edit. */
    private var splitUndone by mutableStateOf<NameSplit?>(null)

    fun reset() = load(null)

    /** The numbers as the save reads them: a field left as it loaded is the stored value, not its rounded display. */
    fun nutritionInput(): NutritionInput {
        val stored = loaded?.nutrition
        return NutritionInput(
            shorthand,
            fieldText(calories, stored?.values?.calories), fieldText(protein, stored?.values?.protein),
            fieldText(carbs, stored?.values?.carbohydrates), fieldText(fat, stored?.values?.fat),
            fieldText(basisAmount, stored?.amount), basisUnit,
            fieldText(saturatedFat, stored?.values?.saturatedFat), fieldText(sugars, stored?.values?.sugars),
            fieldText(fibre, stored?.values?.fibre), fieldText(salt, stored?.values?.salt),
        )
    }

    fun load(food: FoodRecord?) {
        loaded = food
        name = food?.name.orEmpty(); brand = food?.brand.orEmpty(); barcode = food?.barcode.orEmpty(); shorthand = ""
        calories = food?.nutrition?.values?.calories.let(::editableNumber)
        protein = food?.nutrition?.values?.protein.let(::editableNumber)
        carbs = food?.nutrition?.values?.carbohydrates.let(::editableNumber)
        fat = food?.nutrition?.values?.fat.let(::editableNumber)
        saturatedFat = food?.nutrition?.values?.saturatedFat.let(::editableNumber)
        sugars = food?.nutrition?.values?.sugars.let(::editableNumber)
        fibre = food?.nutrition?.values?.fibre.let(::editableNumber)
        salt = food?.nutrition?.values?.salt.let(::editableNumber)
        basisAmount = food?.nutrition?.amount?.let(::editableNumber) ?: "100"
        basisUnit = food?.nutrition?.unit?.label ?: "g"
        fromOff = false; barcodeFromScan = false
        caloriesEdited = false; proteinEdited = false; carbsEdited = false; fatEdited = false; basisEdited = false
        saturatedFatEdited = false; sugarsEdited = false; fibreEdited = false; saltEdited = false
        aliases = food?.aliases.orEmpty(); aliasInput = ""; aliasFieldOpen = false
        servings = food?.householdServings().orEmpty()
        density = food?.density.let(::editableNumber)
        servingFormOpen = false; servingUnit = MeasureUnit.PIECE; servingAmount = ""; servingLabel = ""; servingOpenedWith = null
        servingsBasis = null
        nameSplit = null; splitUndone = null
    }

    /** The basis Save stores: New food's shorthand ("per 100 ml") counts until the Basis field is changed. */
    fun basis(): MeasureUnit = resolveNutrition(nutritionInput()).basisUnit

    /**
     * [servings] with their amounts in [basis]: after the basis changed they go through the density, and with no density
     * they have no value in it, which is null. They are never read as the same number in the other unit.
     */
    fun servingsInBasis(): List<ServingConversion>? {
        val from = servingsBasis
        val to = basis()
        if (from == null || from == to || servings.isEmpty()) return servings
        val density = densityValue() ?: return null
        return servings.map { it.copy(baseUnitsPerServing = servingInBasis(it.baseUnitsPerServing, from, to, density)) }
    }

    /** The rows as the form lists them: in [basis] when they have a value in it, else as they were added. */
    fun shownServings(): List<ServingConversion> = servingsInBasis() ?: servings

    /** The unit of [shownServings]' amounts. */
    fun shownServingUnit(): MeasureUnit = servingsBasis.takeIf { servingsInBasis() == null } ?: basis()

    fun removeServing(index: Int) { servings = servings.filterIndexed { i, _ -> i != index } }

    /** The open form's amount is in the other of g and ml than the basis. */
    private fun servingCrossesFamily(): Boolean =
        positiveAmountOf(servingAmount) != null && servingUnit != basis() && (servingUnit == MeasureUnit.GRAM || servingUnit == MeasureUnit.MILLILITER)

    /** A serving in the form or in the rows has a value only through a density, and there is none. */
    private fun densityNeeded(): Boolean =
        density.isBlank() && (servingsInBasis() == null || (servingFormOpen && servingCrossesFamily()))

    /** The density typed: a positive number, or null (blank, or not one). */
    fun densityValue(): Double? = fieldNumber(density, loaded?.density)?.takeIf(::isPositiveAmount)

    /** A density that is not a positive number, or none where a serving needs one: it shows as an error and Save refuses it. */
    fun densityError(): Boolean = (density.isNotBlank() && densityValue() == null) || densityNeeded()

    /** Aliases already added plus any typed but not yet confirmed in the alias field. */
    fun allAliases(): List<String> =
        (aliases + splitAliases(aliasInput)).distinctBy { it.lowercase() }

    fun commitAlias() {
        aliases = allAliases(); aliasInput = ""; aliasFieldOpen = false
    }

    fun commitServing(): Boolean {
        val amount = positiveAmountOf(servingAmount) ?: return false
        // A row needs its name: without one, "1 g = 20 g" is no serving.
        val label = servingLabel.trim().ifBlank { return false }
        if (densityValue() == null && servingCrossesFamily()) return false
        val rows = servingsInBasis() ?: return false
        val basis = basis()
        // A serving is kept in the food's basis units: one typed in the other of g and ml goes through the density.
        val base = servingInBasis(amount, servingUnit, basis, densityValue())
        servings = rows.filterNot { it.name?.equals(label, ignoreCase = true) == true } + ServingConversion(servingUnit, base, "personal serving", label = label)
        servingsBasis = basis
        closeServing()
        return true
    }

    /**
     * Save with the serving form open: what it holds becomes a row, so nothing typed is lost. Null when Save may go on (a
     * form with no name and no amount holds nothing), else what Save says; the form stays as it is and nothing is saved.
     */
    fun commitPendingServing(): String? {
        if (!servingFormOpen) return null
        val amount = positiveAmountOf(servingAmount)
        return when {
            amount == null && servingLabel.isBlank() -> null
            amount == null -> POSITIVE_AMOUNT_MESSAGE
            servingLabel.isBlank() -> "Name is required"
            commitServing() -> null
            else -> POSITIVE_AMOUNT_MESSAGE
        }
    }

    /** Add serving: the form opens on [unit] with amount "0" and no name. */
    fun openServing(unit: MeasureUnit) {
        servingUnit = unit; servingAmount = "0"; servingLabel = ""; servingFormOpen = true
        servingOpenedWith = PendingServing(unit, "0", "")
    }

    /** A typed part's serving word: the name is the rest, and the form opens on the word with the amount left to type. */
    internal fun splitServing(split: TypedFoodName, word: String) {
        name = split.name
        val unit = basis()
        servingUnit = unit; servingAmount = ""; servingLabel = word; servingFormOpen = true
        servingOpenedWith = PendingServing(unit, "", word)
        nameSplit = NameSplit(split.name, split.typed)
    }

    /** Cancel puts the serving inputs back as the form loaded them, and a split name as it was typed, unless it was edited since. */
    fun cancelServing() {
        nameSplit?.takeIf { it.name == name }?.let { name = it.typed; splitUndone = it }
        closeServing()
    }

    private fun closeServing() {
        servingFormOpen = false; servingUnit = MeasureUnit.PIECE; servingAmount = ""; servingLabel = ""; servingOpenedWith = null
        nameSplit = null
    }

    /** The open serving form holds input the user typed or chose, not just what Add serving put there. */
    private fun servingTyped(): Boolean =
        servingFormOpen && PendingServing(servingUnit, servingAmount, servingLabel) != (servingOpenedWith ?: PendingServing(MeasureUnit.PIECE, "", ""))

    /** The values typed into the form, as they are now. */
    fun fields() = FoodDraftFields(
        name, brand, barcode, shorthand, calories, protein, carbs, fat, basisAmount, basisUnit,
        aliases, aliasInput, servings, servingUnit, servingAmount, servingLabel, density,
        saturatedFat, sugars, fibre, salt,
    )

    /**
     * True when any typed value differs from [baseline], the form as it opened. Opening a field changes nothing:
     * the pending serving inputs count only once typed into; an added serving counts through [servings].
     */
    fun isDirty(baseline: FoodDraftFields): Boolean {
        val now = fields().withoutPendingServing()
        val opened = baseline.withoutPendingServing()
        val afterUndo = splitUndone?.takeIf { it.name == baseline.name }?.let { opened.copy(name = it.typed) }
        return (now != opened && now != afterUndo) || servingTyped()
    }

    /** The whole form, values and flags, so it can be put back as it was after the process is killed. */
    fun state() = FoodDraftState(
        fields(), barcodeFromScan, fromOff, caloriesEdited, proteinEdited, carbsEdited, fatEdited, basisEdited,
        aliasFieldOpen, servingFormOpen, servingOpenedWith, servingsBasis, nameSplit, splitUndone,
        saturatedFatEdited, sugarsEdited, fibreEdited, saltEdited,
    )

    fun restore(state: FoodDraftState) {
        val f = state.fields
        name = f.name; brand = f.brand; barcode = f.barcode; shorthand = f.shorthand
        calories = f.calories; protein = f.protein; carbs = f.carbs; fat = f.fat
        saturatedFat = f.saturatedFat; sugars = f.sugars; fibre = f.fibre; salt = f.salt
        basisAmount = f.basisAmount; basisUnit = f.basisUnit
        aliases = f.aliases; aliasInput = f.aliasInput
        servings = f.servings; servingUnit = f.servingUnit; servingAmount = f.servingAmount; servingLabel = f.servingLabel
        density = f.density
        barcodeFromScan = state.barcodeFromScan; fromOff = state.fromOff
        caloriesEdited = state.caloriesEdited; proteinEdited = state.proteinEdited; carbsEdited = state.carbsEdited
        fatEdited = state.fatEdited; basisEdited = state.basisEdited
        saturatedFatEdited = state.saturatedFatEdited; sugarsEdited = state.sugarsEdited; fibreEdited = state.fibreEdited; saltEdited = state.saltEdited
        aliasFieldOpen = state.aliasFieldOpen; servingFormOpen = state.servingFormOpen; servingOpenedWith = state.servingOpenedWith
        servingsBasis = state.servingsBasis
        nameSplit = state.nameSplit; splitUndone = state.splitUndone
    }
}

/** A serving word split off a typed name: the [name] left, and the name as [typed]. */
@Serializable
data class NameSplit(val name: String, val typed: String)

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
    val saturatedFat: String = "",
    val sugars: String = "",
    val fibre: String = "",
    val salt: String = "",
)

/**
 * [amount] of [unit] in a food's [basis] unit: as it is in the same unit, through [density] (g in 100 ml) between g and
 * ml, else as typed (no density to convert with; [FoodDraft] asks for one before it gets here).
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
    val servingsBasis: MeasureUnit? = null,
    val nameSplit: NameSplit? = null,
    val splitUndone: NameSplit? = null,
    val saturatedFatEdited: Boolean = false,
    val sugarsEdited: Boolean = false,
    val fibreEdited: Boolean = false,
    val saltEdited: Boolean = false,
)
