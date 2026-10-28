package com.iron.breakfast.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.ui.plain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The entry writes an edit makes; the view model adapts `BreakfastEngine`. Both re-read the row in their transaction. */
internal interface EntryEditStore {
    /** Quick edit: [text] is "amount unit", read strictly; a text that does not read is a [ReplacementResult.Failure]. */
    suspend fun replaceAmountUnit(entryId: String, text: String): ReplacementResult
    /** Full edit: every editable field at once. */
    suspend fun replaceEntry(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String): ReplacementResult
}

/**
 * A saved entry: its detail screen, and its two edits.
 *
 * - **Detail:** [selectedEntry], kept by id ([saveEntryId], so it comes back after process death) and looked up again
 *   in the diary after every reload ([diaryReloaded]).
 * - **Quick edit** (amount and unit) and **full edit** (food, amount, unit, meal, day; [pickerOpen] chooses the food).
 *   Each opening is a new session: a result that arrives for a session no longer open reloads the diary
 *   ([DiaryChange.EntryEditedAfterClose]) and changes nothing on screen. The quick edit has its own [text], the full
 *   edit takes its values when saved, and both show their own [error] until what is being edited changes.
 * - A full edit's save owns the gate until it ends: it is not cancelled by leaving ([cancel] waits for it), and it
 *   finishes even if its job is cancelled.
 *
 * The screens around it belong to the coordinator: an edit shows its entry's meal beneath it ([onBegin]), and when it
 * ends ([onEnd]) an edit opened from Daily ([openMeal] null) returns there. Writes go through [session]; the hint
 * timer runs in [scope] on [sleep].
 */
internal class EntryEdit(
    private val session: DiarySession,
    private val store: EntryEditStore,
    private val scope: CoroutineScope,
    /** The meal open beneath, null on Daily. */
    private val openMeal: () -> MealSlot?,
    /** An edit opened for an entry of this meal: the meal is shown beneath it. */
    private val onBegin: (MealSlot) -> Unit,
    /** The edit ended; [returnToDaily] when it was opened from Daily. */
    private val onEnd: (returnToDaily: Boolean) -> Unit,
    /** The detail's entry id restored after process death. */
    initialEntryId: String?,
    /** Keeps the detail's entry id for process death (null: no detail). */
    private val saveEntryId: (String?) -> Unit,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    var selectedEntry by mutableStateOf<MealEntry?>(null); private set
    private var selectedEntryId: String? = initialEntryId

    var mode by mutableStateOf(EntryEditMode.NONE); private set
    var editingEntryId by mutableStateOf<String?>(null); private set
    var editingFoodName by mutableStateOf<String?>(null); private set
    var editingFoodId by mutableStateOf<String?>(null); private set
    var pickerOpen by mutableStateOf(false); private set
    /** The quick edit's hint, for its first 2.5 seconds. */
    var hintVisible by mutableStateOf(false); private set
    /** The quick edit's "amount unit", as it opened and then as last saved. */
    var text by mutableStateOf(""); private set
    /** Why the last save of this edit was refused; cleared when what is edited changes ([inputChanged]). */
    var error by mutableStateOf<String?>(null); private set
    private var editSession = 0L
    private var returnToDaily = false
    private var hintSession = 0L
    private var fullSaveJob: Job? = null
    private val calculator = NutritionCalculator()

    fun openDetail(entry: MealEntry) {
        // A double tap is resolved by combinedClickable into quick edit. If a delayed single-tap callback arrives after
        // that, it must not reopen the detail route over the edit sheet.
        if (mode != EntryEditMode.NONE) return
        selectedEntry = entry
        selectedEntryId = entry.id
        saveEntryId(entry.id)
    }

    fun closeDetail() { selectedEntry = null; selectedEntryId = null; saveEntryId(null) }

    /** The diary was reloaded: the detail shows the entry as stored, or closes when it is gone. */
    fun diaryReloaded() { selectedEntry = selectedEntryId?.let { id -> session.entries.firstOrNull { it.id == id } } }

    fun beginQuick(entry: MealEntry) {
        if (!begin(entry, EntryEditMode.QUICK)) return
        text = "${entry.enteredAmount.plain()} ${entry.enteredUnit.displayLabel()}"
        hintVisible = true
        val hint = ++hintSession
        scope.launch {
            sleep(QUICK_HINT_MS)
            if (hintSession == hint && mode == EntryEditMode.QUICK) hintVisible = false
        }
    }

    fun beginFull(entry: MealEntry) {
        if (!begin(entry, EntryEditMode.FULL)) return
        pickerOpen = false
        hintSession += 1
        hintVisible = false
    }

    private fun begin(entry: MealEntry, editMode: EntryEditMode): Boolean {
        if (session.busy) return false
        closeDetail()
        returnToDaily = openMeal() == null
        onBegin(entry.mealSlot)
        editSession += 1
        editingEntryId = entry.id
        editingFoodId = entry.foodId
        editingFoodName = entry.foodName
        mode = editMode
        error = null
        return true
    }

    /**
     * Back, the arrow, a successful save, or a screen the coordinator opens over it: the edit ends. Refused (false)
     * while a full edit's save runs, which owns its edit until it ends. Leaves `busy` alone: only the write that set it
     * clears it, so a quick-edit save in flight still blocks a second write.
     */
    fun cancel(): Boolean {
        if (mode == EntryEditMode.FULL && fullSaveJob?.isActive == true) return false
        fullSaveJob?.cancel()
        fullSaveJob = null
        editSession += 1
        mode = EntryEditMode.NONE
        editingEntryId = null
        editingFoodName = null
        editingFoodId = null
        pickerOpen = false
        text = ""
        hintSession += 1
        hintVisible = false
        error = null
        val toDaily = returnToDaily
        returnToDaily = false
        onEnd(toDaily)
        return true
    }

    /** The amount, unit, food, meal or date being edited changed, so an earlier save error no longer applies. */
    fun inputChanged() { if (mode != EntryEditMode.NONE) error = null }

    fun editingEntry(): MealEntry? = editingEntryId?.let { id -> session.entries.firstOrNull { it.id == id } }
    fun editingFood(): FoodRecord? = editingFoodId?.let { id -> session.foods.firstOrNull { it.id == id } }

    /** What the edited values come to, for the sheet's preview; null when the food is gone. */
    fun preview(foodId: String, amount: Double, unit: MeasureUnit): Calculation? =
        session.foods.firstOrNull { it.id == foodId }?.let { calculator.calculate(it, amount, unit) }

    fun openFoodPicker() { pickerOpen = true }
    fun closeFoodPicker() { pickerOpen = false }
    fun chooseFood(food: FoodRecord) {
        editingFoodId = food.id
        editingFoodName = food.name
        pickerOpen = false
    }

    /** Save in quick edit (or the keyboard's Done): [value] ("amount unit") becomes the edit's [text] and is saved. */
    fun saveQuick(value: String) {
        if (mode != EntryEditMode.QUICK) return
        text = value
        // A tap while a save runs is ignored, not reported as invalid.
        if (!session.ready || session.busy) return
        if (text.isBlank()) { cancel(); return }
        val id = editingEntryId ?: return
        val edit = editSession
        val saved = text
        fun stillEditing() = mode == EntryEditMode.QUICK && editingEntryId == id && editSession == edit
        // A result for an edit no longer open reloads the diary and nothing else.
        session.write(
            change = { result -> editChange(result, stillOpen = stillEditing()) },
            onSuccess = { result ->
                if (stillEditing()) when (result) {
                    is ReplacementResult.Success -> cancel()
                    is ReplacementResult.Failure -> error = result.reason
                }
            },
            onFailure = { if (stillEditing()) error = it.message ?: "Could not edit this entry." },
        ) { store.replaceAmountUnit(id, saved) }
    }

    /** Save in full edit. */
    fun saveFull(foodId: String, amount: String, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
        val id = editingEntryId ?: return
        val edit = editSession
        // A tap while the save runs is ignored, not reported as invalid.
        if (!session.ready || session.busy) return
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0 || foodId.isBlank()) {
            error = "Enter a positive amount and choose a food."
            return
        }
        fullSaveJob?.cancel()
        fun stillEditing() = editSession == edit && editingEntryId == id
        // This save owns the gate until it ends, whatever the edit session is now; a result for an edit no longer open
        // reloads the diary and nothing else.
        fullSaveJob = session.write(
            change = { result -> editChange(result, stillOpen = stillEditing()) },
            nonCancellable = true,
            onSuccess = { result ->
                if (stillEditing()) when (result) {
                    is ReplacementResult.Success -> { fullSaveJob = null; cancel() }
                    is ReplacementResult.Failure -> error = result.reason
                }
            },
            onFailure = { failure -> if (stillEditing()) error = failure.message ?: "Could not save this entry." },
        ) { store.replaceEntry(id, foodId, parsedAmount, unit, meal, dayKey) }
    }

    /** What a saved edit committed: nothing when refused; an edit, or one whose edit had closed meanwhile. */
    private fun editChange(result: ReplacementResult, stillOpen: Boolean): DiaryChange? = when {
        result !is ReplacementResult.Success -> null
        stillOpen -> DiaryChange.EntryEdited
        else -> DiaryChange.EntryEditedAfterClose
    }

    private companion object {
        const val QUICK_HINT_MS = 2_500L
    }
}

/** The units an entry of [food] can be edited in: its default, its basis and its servings. */
internal fun entryUnits(food: FoodRecord): List<MeasureUnit> =
    (listOf(food.defaultUnit, food.nutrition.unit) + food.conversions.map { it.unit }).filter { it != MeasureUnit.UNKNOWN }.distinct()
