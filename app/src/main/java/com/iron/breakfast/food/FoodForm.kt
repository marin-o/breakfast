package com.iron.breakfast.food

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.FoodFormBack
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Notices
import com.iron.breakfast.app.SavedFoodForm
import com.iron.breakfast.contribution.contributionLanguageFor
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.data.plausibleNutrition
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.canonicalBarcode
import com.iron.breakfast.domain.editedCustomFoodRecord
import com.iron.breakfast.domain.normalizeBarcode
import com.iron.breakfast.domain.resolveNutrition
import com.iron.breakfast.domain.sameBarcode
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.foodFormBack
import com.iron.breakfast.ReviewLogPlan
import com.iron.breakfast.foodSaveRefusal
import com.iron.breakfast.resolve.ResolveQueueView
import com.iron.breakfast.queueLogPlan
import com.iron.breakfast.ui.label

/** The food writes the form makes; the view model adapts `BreakfastEngine`. */
internal interface FoodFormStore {
    /** Edit food: the stored food replaced by [food]. */
    suspend fun updateFood(food: FoodRecord)
    /** New food without a meal; returns the food as stored. */
    suspend fun saveFood(food: NewFood): FoodRecord
    /** New food logged to [meal] on [dayKey] with its basis as the amount; returns the food as stored. */
    suspend fun saveFoodAndLog(food: NewFood, meal: MealSlot, dayKey: String): FoodRecord
}

/** A new personal food as the form saves it. [barcode] is the stored form ([canonicalBarcode]); [aliases] comma-joined. */
internal data class NewFood(
    val name: String,
    val brand: String,
    val barcode: String,
    val aliases: String,
    val nutrition: NutritionValues,
    val basisAmount: Double,
    val basisUnit: MeasureUnit,
    /** "Open Food Facts" for a form opened from an incomplete Open Food Facts result, else null. */
    val sourceName: String?,
    val sourceDescription: String,
)

/**
 * New food and Edit food: one form over one [draft].
 *
 * - **State:** whether it is [open], the food it edits ([editingFoodId], null for New food), the [draft] the screens bind
 *   their fields to, the baseline it opened with (Back asks before discarding only when the draft differs from it:
 *   [discardOpen]), and the field to move focus to after a refused save ([focus]). The barcode field's scan asks the
 *   scanner for one barcode ([startScanFor]); the scanner opens over the form and Back on it returns here.
 * - **Save targeting** ([foodSaveTarget]): an edit updates the food; a new food is logged too when there is a meal (the
 *   resolve queue's while one is being resolved, else the meal open beneath), and is saved alone otherwise, and always
 *   while a Log part is being resolved ([resolvingPart]: the part's pick goes into the draft, which logs it). A queue
 *   without a meal refuses the save.
 * - **Process death:** [savedForm] is what the coordinator mirrors into `SavedStateHandle` and [restore] puts back; not
 *   while a part or a queue is being resolved, since what Save does there would not come back.
 *
 * Inputs from the screens around it are explicit: the day ([selectedDay]), the meal open beneath ([openMeal]), the resolve
 * queue ([queue]), a Log part being resolved ([resolvingPart]). Opening and closing tell the coordinator ([onOpen], [onClose]); a save hands its contribution
 * candidate to [offerContribution] and then reports to [onSaved]. Writes go through [session].
 */
internal class FoodForm(
    private val session: DiarySession,
    private val store: FoodFormStore,
    private val notices: Notices,
    private val selectedDay: () -> String,
    private val openMeal: () -> MealSlot?,
    private val queue: () -> ResolveQueueView?,
    /** A Log part is being resolved in the Library: a new food saved here is that part's pick. */
    private val resolvingPart: () -> Boolean,
    private val offerContribution: (OffContributionDraft?) -> Unit,
    /** Opens the scanner for one barcode, which it hands to the callback (see [beginBarcodeRescan]). */
    private val startScanFor: (onBarcode: (String) -> Unit) -> Unit,
    /** The form opened; [fromResolver] when the resolver opened it. */
    private val onOpen: (fromResolver: Boolean) -> Unit,
    /** The form closed without a save. */
    private val onClose: () -> Unit,
    /** Saved: [food] as stored ([newFood] false: the food as it was before the edit), [advancesQueue] when it resolved a queued part. */
    private val onSaved: (food: FoodRecord, newFood: Boolean, advancesQueue: Boolean) -> Unit,
) {
    var open by mutableStateOf(false); private set
    var editingFoodId by mutableStateOf<String?>(null); private set
    val draft = FoodDraft()
    /** The form as it opened; Back asks before discarding only when [draft] differs from it. */
    private var baseline by mutableStateOf(FoodDraftFields())
    var discardOpen by mutableStateOf(false); private set
    /** The field New food moves focus to after a refused save; consumed by the form ([focusHandled]). */
    var focus by mutableStateOf<FoodFormFocus?>(null); private set
    private var focusSerial = 0L

    /** New food, prefilled; [fromOff] when Open Food Facts had the product without usable nutrition. */
    fun open(name: String = "", brand: String = "", barcode: String = "", fromResolver: Boolean = false, fromOff: Boolean = false) {
        onOpen(fromResolver)
        editingFoodId = null
        draft.reset()
        draft.name = name
        draft.brand = brand
        draft.barcode = normalizeBarcode(barcode).orEmpty()
        draft.barcodeFromScan = draft.barcode.isNotEmpty()
        draft.fromOff = fromOff
        baseline = draft.fields()
        discardOpen = false
        open = true
    }

    /** Edit food for [food]. */
    fun openEdit(food: FoodRecord) {
        editingFoodId = food.id
        draft.load(food)
        baseline = draft.fields()
        discardOpen = false
        open = true
    }

    /** Back or the arrow: a changed form asks before it is discarded; while a save runs, nothing happens. */
    fun leave() {
        when (foodFormBack(saving = session.busy, dirty = draft.isDirty(baseline))) {
            FoodFormBack.IGNORE -> Unit
            FoodFormBack.ASK -> discardOpen = true
            FoodFormBack.CLOSE -> close()
        }
    }

    fun keepEditing() { discardOpen = false }
    fun discard() { if (session.busy) return; discardOpen = false; close() }

    /** Closes the form without saving; the coordinator decides what shows next ([onClose]). */
    fun close() {
        open = false; editingFoodId = null; discardOpen = false; draft.reset()
        onClose()
    }

    /** Another screen took the form's place (a resolve ending, the search): it stops showing, nothing else. */
    fun hide() { open = false; discardOpen = false }

    /** A restored Edit food whose food is gone has nothing to edit. */
    fun closeIfFoodGone() {
        if (open && editingFoodId?.let { id -> session.foods.none { it.id == id } } == true) close()
    }

    fun commitServing() { draft.commitServing() }

    /** The barcode field's scan: the scanner opens over the form, and the barcode it reads comes back here, with no lookup. */
    fun beginBarcodeRescan() { startScanFor(::rescanned) }
    private fun rescanned(barcode: String) { draft.barcode = barcode; draft.barcodeFromScan = true }

    fun focusHandled() { focus = null }

    /** The open form for `SavedStateHandle`; null when closed or while resolving. */
    fun savedForm(): SavedFoodForm? = if (!open || resolvingPart() || queue() != null) null else SavedFoodForm(editingFoodId, draft.state(), baseline)

    /** Puts back a form saved before the process was killed. */
    fun restore(form: SavedFoodForm) {
        draft.restore(form.draft)
        baseline = form.baseline
        editingFoodId = form.editingFoodId
        open = true
    }

    /** Save on New food or Edit food. */
    fun save() {
        // A tap while the save runs is ignored, not reported as invalid.
        if (!session.ready || session.busy) return
        val name = draft.name; val brand = draft.brand; val barcode = draft.barcode.trim()
        val aliasList = draft.allAliases(); val servingList = draft.servings
        val input = draft.nutritionInput()
        val resolved = resolveNutrition(input)
        val editingId = editingFoodId
        val foods = session.foods
        val originalBarcode = editingId?.let { id -> foods.firstOrNull { it.id == id }?.barcode?.trim() }
        val barcodeUnchanged = barcode == originalBarcode || sameBarcode(barcode, originalBarcode)
        val barcodeError = if (barcodeUnchanged) null else validateBarcodeInput(barcode, foods, editingId)
        // A refused save names the problem and moves focus to the field to fix (New food reads the request).
        foodSaveRefusal(name, input, resolved, barcodeError)?.let { refusal ->
            notices.show(refusal.message)
            if (editingId == null) focus = FoodFormFocus(refusal.field, ++focusSerial)
            return
        }
        val kcal = resolved.calories ?: return
        // A new or changed code is stored in its canonical form; an unchanged one keeps the spelling it has.
        val storedBarcode = if (barcodeUnchanged && originalBarcode != null) originalBarcode else canonicalBarcode(barcode) ?: barcode
        val basisAmount = resolved.basisAmount
        val basisUnit = resolved.basisUnit
        val nutrition = NutritionValues(kcal, resolved.protein, resolved.carbohydrates, resolved.fat)
        val resolveQueue = queue()
        val plan = foodSaveTarget(resolvingPart(), resolveQueue, openMeal())
        val saveTarget = plan?.meal
        val resolvesQueue = plan?.advancesQueue == true
        if (resolveQueue != null && saveTarget == null) {
            notices.show("Choose a meal before saving this food.")
            return
        }
        val fromOff = draft.fromOff
        val candidate = if (fromOff) normalizeBarcode(barcode)?.let {
            OffContributionDraft(
                barcode = it, name = name.trim(), brand = brand.trim().ifBlank { null },
                basisAmount = basisAmount, basisUnit = basisUnit, nutrition = nutrition
            )
        }?.takeIf { plausibleNutrition(it.normalizedNutrition) } else null
        val newFood = NewFood(
            name, brand, storedBarcode, aliasList.joinToString(", "), nutrition, basisAmount, basisUnit,
            sourceName = fromOff.takeIf { it }?.let { "Open Food Facts" },
            sourceDescription = "Saved from an incomplete Open Food Facts barcode result",
        )
        val day = selectedDay()
        session.write(
            // A new food saved with a meal is a log (it ends Undo); a food saved or edited alone is not.
            change = { _: FoodRecord -> if (editingId == null && saveTarget != null) DiaryChange.FoodsLogged else DiaryChange.FoodsChanged },
            onSuccess = { saved ->
                open = false
                discardOpen = false
                editingFoodId = null
                draft.reset()
                offerContribution(candidate?.copy(languageCode = contributionLanguageFor(name), packageQuantityMl = null))
                notices.show(if (editingId != null) "Personal food updated for future logs." else if (saveTarget != null) "Saved to ${saveTarget.label()} and your food library." else "Saved to your food library.")
                onSaved(saved, editingId == null, resolvesQueue)
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) {
            if (editingId != null) {
                val original = session.foods.firstOrNull { it.id == editingId } ?: error("That personal food is no longer available.")
                store.updateFood(editedCustomFoodRecord(original, name, brand, storedBarcode, newFood.aliases, nutrition, basisAmount, basisUnit, aliases = aliasList, servings = servingList))
                original
            } else if (saveTarget != null) store.saveFoodAndLog(newFood, saveTarget, day)
            else store.saveFood(newFood)
        }
    }
}

/**
 * Where Save logs a new food, or null to save it alone. While a Log part is being resolved ([resolvingPart]) it is
 * never logged: it becomes the part's pick, and Add logs the draft. Otherwise the rule product review follows too
 * ([queueLogPlan]): while a resolve [queue] is being resolved its meal wins and the queue advances; else the meal open
 * beneath, if any.
 */
internal fun foodSaveTarget(resolvingPart: Boolean, queue: ResolveQueueView?, openMeal: MealSlot?): ReviewLogPlan? =
    if (resolvingPart) null else queueLogPlan(queueActive = queue != null, queue?.meal, openMeal)
