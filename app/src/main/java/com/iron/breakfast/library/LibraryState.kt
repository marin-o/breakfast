package com.iron.breakfast.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.SavedMeal
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.libraryRecencyOrder
import com.iron.breakfast.domain.mealMarkerSlots
import com.iron.breakfast.isLibraryNoMatch
import com.iron.breakfast.resolve.ResolveSource
import com.iron.breakfast.shouldApplyLibraryRefresh
import com.iron.breakfast.ui.label
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Library reads and writes; the view model adapts `BreakfastEngine`. */
internal interface LibraryStore {
    /** The foods whose name matches [query]; archived ones too when [includeArchived]. */
    suspend fun foodsMatching(query: String, includeArchived: Boolean): List<FoodRecord>
    /** The planner over the current foods, for the bar's parts. */
    fun planner(): LogPlanner
    /** The local matches for a food phrase, best first. */
    fun search(phrase: String): List<RankedFood>
    /** Stores [food] as needed and logs it. */
    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String)
    /** Saves typed [text] to [meal] on [dayKey]: what resolves is logged, the rest comes back unresolved. */
    suspend fun save(text: String, meal: MealSlot, dayKey: String): SavedMeal
    suspend fun archiveFood(id: String)
    suspend fun restoreFood(id: String)
}

/**
 * The Library: the list, its bar, the food page, and logging from it.
 *
 * - **List:** whether it is [open], the meal it adds to ([target]: the meal it was opened from or for, null on the
 *   Library tab), the [query] the list is filtered by and the results ([foods], for [resultsQuery]), active foods or
 *   only the Archived ones ([archivedOnly]). A refresh runs in [scope], its read on [io], and applies on [main] only
 *   while it still matches what is shown ([shouldApplyLibraryRefresh]).
 * - **Bar:** [input], the bar's own text, never the Log draft. Done ([submitInput]) searches, logs a quantified batch
 *   (asking for a meal when none is known: [batchMealPickerOpen]), or saves a mixed batch (asking first:
 *   [mixedMealPickerOpen]), whose unmatched parts go to the post-save handling ([afterSave]) with the bar as their
 *   [ResolveSource].
 * - **Food page:** [selectedFood], the row picker ([expandedFoodId]), logging ([log]), Archive and Restore.
 *
 * While a Log part is being resolved here ([resolvingPart]), a food tapped or added is that part's pick
 * ([onPartPicked]) and Done only searches. Writes go through [session].
 */
internal class LibraryState(
    private val session: DiarySession,
    private val store: LibraryStore,
    private val notices: Notices,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val main: CoroutineDispatcher,
    private val selectedDay: () -> String,
    private val resolvingPart: () -> Boolean,
    private val onPartPicked: (FoodRecord) -> Unit,
    /** [food] was archived: a form editing it closes. */
    private val onArchived: (food: FoodRecord) -> Unit,
    /** Post-save handling: the parts a mixed batch could not match, for [meal] (see `ResolveFlow.afterSave`). */
    private val afterSave: (unresolved: List<String>, meal: MealSlot, source: ResolveSource, typedAtSave: String) -> Unit,
) : ResolveSource {
    var open by mutableStateOf(false); private set
    var target by mutableStateOf<MealSlot?>(null); private set
    var input by mutableStateOf(""); private set
    var query by mutableStateOf(""); private set
    /** True only on the explicit Archived foods destination; active foods are never mixed in. */
    var archivedOnly by mutableStateOf(false); private set
    var foods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    /** The query [foods] was computed for; the no-match screen waits until it equals [query]. */
    var resultsQuery by mutableStateOf<String?>(null); private set
    var selectedFood by mutableStateOf<FoodRecord?>(null); private set
    var expandedFoodId by mutableStateOf<String?>(null); private set
    var batchMealPickerOpen by mutableStateOf(false); private set
    /** A mixed batch without a meal: asks which meal, then saves the bar's text. */
    var mixedMealPickerOpen by mutableStateOf(false); private set
    private var pendingBatch: List<LibraryLogDraft> = emptyList()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private val parser = ShorthandParser()

    /** Opens the list for [target], filtered by [query], with [bar] in the bar (the query unless told otherwise). */
    fun show(target: MealSlot?, query: String, archivedOnly: Boolean, bar: String = query) {
        open = true
        this.target = target
        input = bar
        this.query = query
        this.archivedOnly = archivedOnly
        refresh()
    }

    /** The food page for [food], opened from outside the list (an entry's "Open in Library"). */
    fun showFood(food: FoodRecord?) { selectedFood = food }

    /** Closes the Library and everything open in it; a refresh still running no longer applies. */
    fun close() {
        refreshJob?.cancel()
        refreshGeneration++
        open = false
        target = null
        selectedFood = null
        expandedFoodId = null
        pendingBatch = emptyList()
        batchMealPickerOpen = false
        archivedOnly = false
        query = ""
        resultsQuery = null
        input = ""
    }

    /** The library's search: the bar shows it and the list filters by it, so both always read the same text. */
    fun onQuery(value: String) {
        if (open) input = value
        query = value
        refresh()
    }

    /** The bar's typing: the list filters by its food text, without a typed amount. */
    fun onInput(value: String) {
        input = value
        if (open) {
            query = librarySearchText(value, parser)
            refresh()
        }
    }

    fun refresh() {
        if (!session.ready) return
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        val captured = query
        val capturedArchivedOnly = archivedOnly
        val capturedEntries = session.entries
        refreshJob = scope.launch(io) {
            val matching = store.foodsMatching(captured, includeArchived = capturedArchivedOnly)
            val ordered = libraryRecencyOrder(
                if (capturedArchivedOnly) matching.filter { it.archived } else matching,
                capturedEntries,
            )
            withContext(main) {
                if (shouldApplyLibraryRefresh(generation, refreshGeneration, captured, query, capturedArchivedOnly, archivedOnly, open)) {
                    foods = ordered
                    resultsQuery = captured
                }
            }
        }
    }

    /** A search that finished with nothing and has no other screen over it: the "No local match" screen. */
    fun noMatch(otherScreenOpen: Boolean): Boolean =
        isLibraryNoMatch(session.ready, open, query, resultsQuery, foods.isEmpty(), otherScreenOpen)

    /** A row tapped: its food page; while resolving, the tap is the pick, like its +. */
    fun openFood(food: FoodRecord) {
        if (resolvingPart()) { pick(food); return }
        selectedFood = food
    }

    fun closeFood() { selectedFood = null }

    fun toggleFoodPicker(food: FoodRecord) { expandedFoodId = if (expandedFoodId == food.id) null else food.id }
    fun collapseFoodPicker() { expandedFoodId = null }

    fun lastLoggedEntry(food: FoodRecord): MealEntry? = session.entries.asSequence()
        .filter { it.foodId == food.id }
        .maxByOrNull { it.savedAtEpochMs }

    fun invalidAmount() { notices.show("Enter a positive amount and a supported unit such as g, ml, tbsp, tsp, or piece.") }

    /** Logs [food] to [target]; while resolving, it is the part's pick instead and keeps the part's typed amount and meal. */
    fun log(food: FoodRecord, target: MealSlot, amount: Double, unit: MeasureUnit) {
        if (!session.ready || session.busy || food.archived) return
        if (resolvingPart()) { onPartPicked(food); return }
        logBatch(listOf(libraryLogDraft(food, target, amount, unit)), target)
    }

    private fun pick(food: FoodRecord) {
        if (!session.ready || session.busy || food.archived) return
        onPartPicked(food)
    }

    private fun logBatch(drafts: List<LibraryLogDraft>, target: MealSlot) {
        if (!session.ready || session.busy || drafts.isEmpty()) return
        val day = selectedDay()
        session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = { notices.show("Added ${drafts.size} food${if (drafts.size == 1) "" else "s"} to ${target.label()} on $day") },
            onFailure = { notices.show(it.message ?: "Could not log this food.") },
        ) { drafts.forEach { draft -> store.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, day) } }
    }

    fun chooseBatchMeal(target: MealSlot) {
        val drafts = pendingBatch
        pendingBatch = emptyList()
        batchMealPickerOpen = false
        logBatch(drafts, target)
    }

    fun dismissBatchMealPicker() {
        pendingBatch = emptyList()
        batchMealPickerOpen = false
    }

    /** Done in the bar. */
    fun submitInput() {
        val raw = input.trim()
        if (raw.isBlank() || !session.ready || session.busy) return
        // While a part is being resolved, Done searches; it never logs.
        if (resolvingPart()) { onQuery(raw); return }
        val markers = mealMarkerSlots(raw)
        if (markers.size > 1) { notices.show("Choose one meal marker."); return }
        val effectiveTarget = markers.singleOrNull() ?: target
        val segments = libraryInputSegments(raw, store.planner())
        if (segments.isEmpty() || segments.all { it.amount == null }) {
            onQuery(segments.joinToString(", ") { it.foodPhrase })
            input = ""
            return
        }
        if (segments.any { it.amount == null }) {
            if (effectiveTarget == null) {
                pendingBatch = emptyList()
                notices.show("Choose a meal before adding a mixed batch with an unquantified food.")
                mixedMealPickerOpen = true
                return
            }
            saveMixed(raw, effectiveTarget)
            return
        }
        scope.launch {
            val drafts = segments.mapNotNull { segment ->
                store.search(segment.foodPhrase).firstOrNull()?.food?.let { food ->
                    LibraryLogDraft(food, segment.amount!!, segment.unit ?: food.defaultUnit)
                }
            }
            withContext(main) {
                if (drafts.size != segments.size) {
                    onQuery(segments.joinToString(", ") { it.foodPhrase })
                    notices.show("One or more foods are not in your local library.")
                    return@withContext
                }
                if (effectiveTarget != null) logBatch(drafts, effectiveTarget) else {
                    pendingBatch = drafts
                    batchMealPickerOpen = true
                }
                input = ""
            }
        }
    }

    fun dismissMixedMealPicker() { mixedMealPickerOpen = false }

    /** A mixed batch without a meal: the chosen meal saves the bar's text, as it does when the meal is known. */
    fun chooseMixedMeal(target: MealSlot) {
        mixedMealPickerOpen = false
        val raw = input.trim()
        if (raw.isBlank() || !session.ready || session.busy) return
        saveMixed(raw, target)
    }

    /** A mixed batch: what resolves is logged; the rest goes to the resolve queue with the bar as its source. */
    private fun saveMixed(cleaned: String, target: MealSlot) {
        val capturedInput = input
        val day = selectedDay()
        session.write(
            change = { saved -> DiaryChange.BatchLogged("${saved.entries.size} food${if (saved.entries.size == 1) "" else "s"} logged to ${target.label()}", saved.entries) },
            onSuccess = { saved -> afterSave(saved.unresolvedSegments, target, this@LibraryState, capturedInput) },
            onFailure = { notices.show(it.message ?: "Could not save this batch.") },
        ) { store.save(cleaned, target, day) }
    }

    fun archive(food: FoodRecord) {
        if (!session.ready || session.busy || food.archived || food.origin != FoodOrigin.CUSTOM) return
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { selectedFood = null; onArchived(food); notices.show("Food archived; saved diary entries remain readable.") },
            onFailure = { notices.show(it.message ?: "Could not archive this food.") },
        ) { store.archiveFood(food.id) }
    }

    fun restore(food: FoodRecord) {
        if (!session.ready || session.busy || !food.archived) return
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { selectedFood = session.foods.firstOrNull { it.id == food.id }; notices.show("Food restored to your library.") },
            onFailure = { notices.show(it.message ?: "Could not restore this food.") },
        ) { store.restoreFood(food.id) }
    }

    /** The bar as the resolve queue's source: what is left of a mixed batch's parts is written back here. */
    override val text: String get() = input
    override fun putBack(left: String) { input = left }
}
