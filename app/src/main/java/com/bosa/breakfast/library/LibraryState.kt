package com.bosa.breakfast.library

import com.bosa.breakfast.domain.LatestSearch
import com.bosa.breakfast.domain.lastLoggedEntry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.LOGGED
import com.bosa.breakfast.app.DiarySession
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.LogPlanner
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.RankedFood
import com.bosa.breakfast.domain.SavedMeal
import com.bosa.breakfast.domain.ShorthandParser
import com.bosa.breakfast.domain.TypedPart
import com.bosa.breakfast.domain.libraryRecencyOrder
import com.bosa.breakfast.domain.mealMarkerSlots
import com.bosa.breakfast.resolve.ResolveSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Library reads and writes; the view model adapts `BreakfastEngine`. */
internal interface LibraryStore {
    /** The foods whose name matches [query]. */
    suspend fun foodsMatching(query: String): List<FoodRecord>
    /** The planner over the current foods, for the bar's parts. */
    fun planner(): LogPlanner
    /** The local matches for a food phrase, best first. */
    fun search(phrase: String): List<RankedFood>
    /** Stores [food] as needed and logs it; [servingName] names the serving for [MeasureUnit.SERVING]. */
    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?)
    /** Saves typed [text] to [meal] on [dayKey]: what resolves is logged, the rest comes back unresolved. */
    suspend fun save(text: String, meal: MealSlot, dayKey: String): SavedMeal
    /** Deletes the food for good; entries keep their own name and numbers. */
    suspend fun deleteFood(id: String)
}

/**
 * The Library: the list, its bar, the food page, and logging from it.
 *
 * - **List:** whether it is [open], the meal it adds to ([target]: the meal it was opened from or for, null on the
 *   Library tab), the [query] the list is filtered by and the results ([foods], for [resultsQuery]). A refresh runs in
 *   [scope], its read on [io], and applies on [main] only
 *   while it still matches what is shown ([LatestSearch]). A quantified batch's foods are looked up on
 *   [compute] (the matcher, as the resolver's search) and the batch applies on [main].
 * - **Bar:** [input], the bar's own text, never the Log draft. Done ([submitInput]) searches, logs a quantified batch
 *   (asking for a meal when none is known: [batchMealPickerOpen]), or saves a mixed batch (asking first:
 *   [mixedMealPickerOpen]), whose unmatched parts go to the post-save handling ([afterSave]) with the bar as their
 *   [ResolveSource].
 * - **Food page:** [selectedFood], the row picker ([expandedFoodId]), logging ([log]), and Delete food, which asks
 *   first ([deleteRequest]) and deletes for good.
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
    private val compute: CoroutineDispatcher,
    private val selectedDay: () -> String,
    private val resolvingPart: () -> Boolean,
    private val onPartPicked: (FoodRecord) -> Unit,
    /** [food] was deleted: a form editing it closes. */
    private val onDeleted: (food: FoodRecord) -> Unit,
    /** Post-save handling: the parts a mixed batch could not match, for [meal] (see `ResolveFlow.afterSave`). */
    private val afterSave: (unresolved: List<String>, meal: MealSlot, source: ResolveSource, typedAtSave: String) -> Unit,
    /** A food was logged to the meal and day the user chose: "Logged!" (see `Confirmations`). */
    private val confirmLogged: () -> Unit,
) : ResolveSource {
    var open by mutableStateOf(false); private set
    var target by mutableStateOf<MealSlot?>(null); private set
    var input by mutableStateOf(""); private set
    var query by mutableStateOf(""); private set
    var foods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    /** The query [foods] was computed for; the no-match screen waits until it equals [query]. */
    var resultsQuery by mutableStateOf<String?>(null); private set
    var selectedFood by mutableStateOf<FoodRecord?>(null); private set
    var expandedFoodId by mutableStateOf<String?>(null); private set
    var batchMealPickerOpen by mutableStateOf(false); private set
    /** A mixed batch without a meal: asks which meal, then saves the bar's text. */
    var mixedMealPickerOpen by mutableStateOf(false); private set
    /** The food "Delete food" asks about (the confirmation is open); null otherwise. */
    var deleteRequest by mutableStateOf<FoodRecord?>(null); private set
    private var pendingBatch: List<LibraryLogDraft> = emptyList()
    private val refreshing = LatestSearch<List<FoodRecord>>(scope, io, main)
    private val parser = ShorthandParser()
    /** The batch's parts that stay in the bar beside what the queue leaves. */
    private var keptParts: List<String> = emptyList()

    /** Opens the list for [target], filtered by [query], with [bar] in the bar (the query unless told otherwise). */
    fun show(target: MealSlot?, query: String, bar: String = query) {
        open = true
        this.target = target
        input = bar
        this.query = query
        refresh()
    }

    /** The food page for [food], opened from outside the list (an entry's "Open in Library"). */
    fun showFood(food: FoodRecord?) { selectedFood = food }

    /** Closes the Library and everything open in it; a refresh still running no longer applies. */
    fun close() {
        refreshing.cancel()
        open = false
        target = null
        selectedFood = null
        expandedFoodId = null
        pendingBatch = emptyList()
        batchMealPickerOpen = false
        deleteRequest = null
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
        val captured = query
        val capturedEntries = session.entries
        refreshing.launch(
            load = { libraryRecencyOrder(store.foodsMatching(captured), capturedEntries) },
            wanted = { open && captured == query },
        ) { ordered ->
            foods = ordered
            resultsQuery = captured
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

    fun lastLoggedEntry(food: FoodRecord): MealEntry? = lastLoggedEntry(session.entries, food.id)


    /**
     * Logs [food] to [target], in [unit] ([servingName] names the serving for [MeasureUnit.SERVING]); while resolving, it
     * is the part's pick instead and keeps the part's typed amount and meal.
     */
    fun log(food: FoodRecord, target: MealSlot, amount: Double, unit: MeasureUnit, servingName: String? = null) {
        if (!session.canWrite) return
        if (resolvingPart()) { onPartPicked(food); return }
        logBatch(listOf(libraryLogDraft(food, target, amount, unit, servingName)), target)
    }

    private fun pick(food: FoodRecord) {
        if (!session.canWrite) return
        onPartPicked(food)
    }

    private fun logBatch(drafts: List<LibraryLogDraft>, target: MealSlot) {
        // The write itself refuses while storage is closed or a write runs.
        if (drafts.isEmpty()) return
        val day = selectedDay()
        session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = { confirmLogged() },
            onFailure = { notices.show(it.message ?: "Could not log this food.") },
        ) { drafts.forEach { draft -> store.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, day, draft.servingName) } }
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
        if (raw.isBlank() || !session.canWrite) return
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
                mixedMealPickerOpen = true
                return
            }
            saveMixed(raw, effectiveTarget)
            return
        }
        scope.launch(compute) {
            val planner = store.planner()
            val drafts = segments.mapNotNull { segment ->
                store.search(segment.foodPhrase).firstOrNull()?.food?.let { food ->
                    // A serving of the food typed where a unit goes ("2 cans coke") logs that serving.
                    val (amount, measure) = planner.quantityOf(segment.raw, food)
                    LibraryLogDraft(food, amount, measure.unit, servingName = measure.name.takeIf { measure.unit == MeasureUnit.SERVING })
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
        if (raw.isBlank()) return
        saveMixed(raw, target)
    }

    /** A mixed batch: what resolves is logged; the rest goes to the resolve queue with the bar as its source. */
    private fun saveMixed(cleaned: String, target: MealSlot) {
        val capturedInput = input
        val day = selectedDay()
        session.write(
            change = { saved -> DiaryChange.BatchLogged(LOGGED, saved.entries) },
            onSuccess = { saved ->
                // A part only its unit keeps from logging is no unknown food: it stays in the bar, out of the resolver.
                val planner = store.planner()
                val (unknown, flagged) = saved.unresolvedSegments.partition(planner::needsAttention)
                keptParts = flagged
                afterSave(unknown, target, this@LibraryState, capturedInput)
            },
            onFailure = { notices.show(it.message ?: "Could not save this batch.") },
        ) { store.save(cleaned, target, day) }
    }

    /** "Delete food" (its page or Edit food): the confirmation opens first. Any stored food may go: nothing brings it back. */
    fun askDelete(food: FoodRecord) { deleteRequest = food }

    /** The bar's one part as typed, when it states an amount: what a USDA row tapped under it starts from. */
    fun typedPart(): TypedPart? = libraryInputSegments(input, store.planner()).singleOrNull()
        ?.takeIf { it.amount != null }?.let { TypedPart(it.foodPhrase, it.amount, it.unit) }

    fun cancelDelete() { deleteRequest = null }

    /**
     * The confirmation's "Delete food": the food goes for good, with its aliases, conversions and learned pick; its
     * page closes and a form editing it closes ([onDeleted]). Past entries keep their own name and numbers. No Undo.
     */
    fun confirmDelete() {
        val food = deleteRequest ?: return
        // A tap while another write runs is ignored, as elsewhere; the dialog stays for another tap.
        if (!session.canWrite) return
        deleteRequest = null
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = {
                if (selectedFood?.id == food.id) selectedFood = null
                if (expandedFoodId == food.id) expandedFoodId = null
                onDeleted(food)
            },
            onFailure = { notices.show(it.message ?: "Could not delete this food.") },
        ) { store.deleteFood(food.id) }
    }

    /** The bar as the resolve queue's source: what is left of a mixed batch's parts is written back here. */
    override val text: String get() = input
    override fun putBack(left: String) { input = (listOf(left).filter { it.isNotBlank() } + keptParts).joinToString(", ") }
}
