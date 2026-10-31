package com.iron.breakfast.food

import com.iron.breakfast.domain.formBack
import com.iron.breakfast.domain.FormBack
import com.iron.breakfast.scan.ProductReviewOrigin
import com.iron.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.iron.breakfast.resolve.ReviewLogPlan
import com.iron.breakfast.resolve.ResolveQueueView
import com.iron.breakfast.resolve.queueLogPlan

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
 *   while a part is being resolved ([resolvingPart]: of the Log's draft, which logs it, or of a recipe's ingredients; the
 *   food becomes that part's pick, with no notice, so nothing covers the next step). A queue
 *   without a meal refuses the save.
 * - **Process death:** [savedForm] is what the coordinator mirrors into `SavedStateHandle` and [restore] puts back; not
 *   while a part or a queue is being resolved, since what Save does there would not come back.
 *
 * Inputs from the screens around it are explicit: the day ([selectedDay]), the meal open beneath ([openMeal]), the resolve
 * queue ([queue]), a part being resolved ([resolvingPart]). Opening and closing tell the coordinator ([onOpen], [onClose]); a save hands its contribution
 * candidate to [offerContribution] and then reports to [onSaved]. Writes go through [session].
 */
internal class FoodForm(
    private val session: DiarySession,
    private val store: FoodFormStore,
    private val notices: Notices,
    private val selectedDay: () -> String,
    private val openMeal: () -> MealSlot?,
    private val queue: () -> ResolveQueueView?,
    /** A part is being resolved (of the Log's draft, or of a recipe's ingredients): a new food saved here is its pick. */
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
    /** A new food was saved and logged to the meal the user chose: "Logged!" (see `Confirmations`). */
    private val confirmLogged: () -> Unit,
    /** A food made for a logging scan or search was saved: it goes on to the review a known product gets. */
    private val onSavedToLog: (FoodRecord, ProductReviewOrigin) -> Unit = { _, _ -> },
) {
    /** This New food is for a logging review ([open]): Save stores it alone and [onSavedToLog] takes it on. */
    private var reviewAfterSave: ProductReviewOrigin? = null
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
    fun open(name: String = "", brand: String = "", barcode: String = "", fromResolver: Boolean = false, fromOff: Boolean = false, reviewAfterSave: ProductReviewOrigin? = null) {
        onOpen(fromResolver)
        this.reviewAfterSave = reviewAfterSave
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
        reviewAfterSave = null
        editingFoodId = food.id
        draft.load(food)
        baseline = draft.fields()
        discardOpen = false
        open = true
    }

    /** Back or the arrow: a changed form asks before it is discarded; while a save runs, nothing happens. */
    fun leave() {
        when (formBack(saving = session.busy, dirty = draft.isDirty(baseline))) {
            FormBack.IGNORE -> Unit
            FormBack.ASK -> discardOpen = true
            FormBack.CLOSE -> close()
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

    /** [foodId] was deleted: an Edit food of it closes, so it cannot store the food again; another form stays. */
    fun foodDeleted(foodId: String) { if (editingFoodId == foodId) close() }

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
    fun savedForm(): SavedFoodForm? = if (!open || resolvingPart() || queue() != null) null else SavedFoodForm(editingFoodId, draft.state(), baseline, reviewAfterSave)

    /** Puts back a form saved before the process was killed. */
    fun restore(form: SavedFoodForm) {
        draft.restore(form.draft)
        baseline = form.baseline
        editingFoodId = form.editingFoodId
        reviewAfterSave = form.reviewAfterSave
        open = true
    }

    /** Save on New food or Edit food. */
    fun save() {
        // A tap while the save runs is ignored, not reported as invalid.
        if (!session.canWrite) return
        val name = draft.name; val brand = draft.brand; val barcode = draft.barcode.trim()
        val aliasList = draft.allAliases(); val servingList = draft.servings
        val input = draft.nutritionInput()
        val resolved = resolveNutrition(input)
        val editingId = editingFoodId
        val foods = session.foods
        val barcodeError = foodBarcodeError(barcode, foods, editingId)
        // A refused save names the problem and moves focus to the field to fix (New food reads the request).
        foodSaveRefusal(name, input, resolved, barcodeError)?.let { refusal ->
            notices.show(refusal.message)
            if (editingId == null) focus = FoodFormFocus(refusal.field, ++focusSerial)
            return
        }
        val kcal = resolved.calories ?: return
        // "100 ml =" a number that is not a positive one: the field shows it, and nothing is saved.
        if (draft.densityError()) { notices.show(POSITIVE_AMOUNT_MESSAGE); return }
        val density = draft.densityValue()
        // A new or changed code is stored in its canonical form; an unchanged one keeps the spelling it has.
        val storedBarcode = storedBarcodeFor(barcode, foods, editingId)
        val basisAmount = resolved.basisAmount
        val basisUnit = resolved.basisUnit
        val nutrition = NutritionValues(kcal, resolved.protein, resolved.carbohydrates, resolved.fat)
        val resolveQueue = queue()
        val thenReview = reviewAfterSave.takeIf { editingId == null && !resolvingPart() && (resolveQueue == null || it == ProductReviewOrigin.LOG_PART) }
        val plan = if (thenReview != null) null else foodSaveTarget(resolvingPart(), resolveQueue, openMeal())
        val saveTarget = plan?.meal
        val resolvesQueue = plan?.advancesQueue == true
        if (resolveQueue != null && saveTarget == null && thenReview == null) {
            notices.show("Choose a meal before saving this food.")
            return
        }
        val forPart = editingId == null && resolvingPart()
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
                when {
                    editingId != null -> notices.show("Personal food updated for future logs.")
                    saveTarget != null -> confirmLogged()
                    thenReview != null || forPart -> Unit
                    else -> notices.show("Saved to your food library.")
                }
                reviewAfterSave = null
                onSaved(saved, editingId == null, resolvesQueue)
                if (thenReview != null) onSavedToLog(saved, thenReview)
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) {
            if (editingId != null) {
                val original = session.foods.firstOrNull { it.id == editingId } ?: error("That personal food is no longer available.")
                store.updateFood(editedCustomFoodRecord(original, name, brand, storedBarcode, newFood.aliases, nutrition, basisAmount, basisUnit, aliases = aliasList, servings = servingList, density = density))
                original
            } else if (saveTarget != null) store.saveFoodAndLog(newFood, saveTarget, day)
            else store.saveFood(newFood)
        }
    }
}

/**
 * Where Save logs a new food, or null to save it alone. While a part is being resolved ([resolvingPart]) it is never
 * logged: it becomes the part's pick, and the Log's Add or the recipe's Save takes it from there. Otherwise the rule product review follows too
 * ([queueLogPlan]): while a resolve [queue] is being resolved its meal wins and the queue advances; else the meal open
 * beneath, if any.
 */
internal fun foodSaveTarget(resolvingPart: Boolean, queue: ResolveQueueView?, openMeal: MealSlot?): ReviewLogPlan? =
    if (resolvingPart) null else queueLogPlan(queueActive = queue != null, queue?.meal, openMeal)
