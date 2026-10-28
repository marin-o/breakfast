package com.iron.breakfast.resolve

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.TypedPart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the resolver reads and writes; the view model adapts `BreakfastEngine`. */
internal interface ResolveStore {
    /** A part as the Log reads it ("mleko200g" is mleko, 200 g). */
    fun typed(raw: String): TypedPart
    /** The resolver's local search, ranked. */
    fun search(query: String): List<RankedFood>
    /** Stores [food] as needed and logs it. */
    suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String)
}

/** Where a queue's parts were typed (the Log draft, the Library bar): what is left of them is shown there. */
internal interface ResolveSource {
    /** The text typed there now. */
    val text: String
    /** Shows [left], the parts not resolved yet, where they were typed. */
    fun putBack(left: String)
}

/**
 * "Not in your foods", in its two forms.
 *
 * - **The resolve frame (Log ↔ Library):** a part of the Log draft sent to the Library ([beginPart]). While [part] is
 *   set, picking a food anywhere it can be picked (the Library, product review, a New food) is that part's pick
 *   ([finishPart]); leaving the Library without one goes back to the Log as it was ([cancelPart]). Both end through
 *   [returnToLog], the one way back.
 * - **The unknown queue:** the parts a save could not match ([afterSave], the post-save handling of every save that
 *   logs typed text), resolved one at a time in the resolver ([resolverOpen]) for the queue's [queueMeal]. A screen
 *   opened from the resolver (the Open Food Facts search, the food form, product review) sees the queue as a
 *   [ResolveQueueView] ([queueView]), reports how it ended ([childLogged], [childClosed]) and comes back to the
 *   resolver while parts are left. What is left goes back where the parts were typed ([ResolveSource]).
 * - **The resolver's search:** [query], [results], ranked in [scope] on [compute]; a result applies only while its
 *   query is still the one typed, and an advance cancels a search still running.
 *
 * Writes go through [session]. The screens around it are the coordinator's: the Library it opens ([openLibrary]) and
 * the Log it returns to ([returnToLog]).
 */
internal class ResolveFlow(
    private val session: DiarySession,
    private val store: ResolveStore,
    private val notices: Notices,
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
    private val selectedDay: () -> String,
    /** Opens the Library on [meal], searching [query], for the part being resolved. */
    private val openLibrary: (meal: MealSlot, query: String) -> Unit,
    /** The resolve frame ended: the Log comes back where it was, with [pick] in place of the part (null: as it was). */
    private val returnToLog: (request: LogResolveRequest, pick: FoodRecord?) -> Unit,
) {
    /** The Log part being resolved in the Library, if any. */
    var part by mutableStateOf<LogResolveRequest?>(null); private set
    val resolvingPart: Boolean get() = part != null

    /** The parts left to resolve as typed; the first is the one being resolved. */
    var parts by mutableStateOf<List<String>>(emptyList()); private set
    var queueMeal by mutableStateOf<MealSlot?>(null); private set
    var queueTotal by mutableStateOf(0); private set
    /** True while the queue is being resolved, also from a screen the resolver opened. */
    var continuation by mutableStateOf(false); private set
    var resolverOpen by mutableStateOf(false); private set
    /** Where the queue's parts were typed; null once it ended or was abandoned. */
    private var source: ResolveSource? = null

    var query by mutableStateOf(""); private set
    var searchOpen by mutableStateOf(false); private set
    var results by mutableStateOf<List<RankedFood>>(emptyList()); private set
    private var searchJob: Job? = null

    /** Sends a "Not in your foods" part to the Library, opened on [meal] and searching [query]. */
    fun beginPart(request: LogResolveRequest, meal: MealSlot, query: String) {
        part = request
        openLibrary(meal, query)
    }

    /** Finish resolve: [food] is the part's pick, and the Log comes back with it. */
    fun finishPart(food: FoodRecord) {
        val request = part ?: return
        part = null
        returnToLog(request, food)
    }

    /** Leaving the Library without a pick: the Log comes back as it was. False when no part is being resolved. */
    fun cancelPart(): Boolean {
        val request = part ?: return false
        part = null
        returnToLog(request, null)
        return true
    }

    val queueActive: Boolean get() = continuation && parts.isNotEmpty()

    /** The queue while it is being resolved, for the screens opened from it; its current part as the Log read it. */
    fun queueView(): ResolveQueueView? =
        if (queueActive) ResolveQueueView(queueMeal, parts, queueTotal, store.typed(parts.first())) else null

    /**
     * Post-save handling, for every save that logs typed text (Add on the Log, the Library's mixed batch): the parts it
     * could not match ([unresolved]) are shown where they were typed, unless that text changed since the save started
     * ([typedAtSave]), and become the queue for [meal], which the resolver opens on. Nothing left ends any queue.
     */
    fun afterSave(unresolved: List<String>, meal: MealSlot, source: ResolveSource, typedAtSave: String) {
        if (source.text == typedAtSave) source.putBack(unresolved.joinToString(", "))
        val left = unresolved.isNotEmpty()
        parts = unresolved
        queueTotal = unresolved.size
        queueMeal = meal.takeIf { left }
        continuation = left
        resolverOpen = left
        this.source = source.takeIf { left }
    }

    /** The queue and the resolver end; what is left stays where it was typed. */
    fun abandon() {
        parts = emptyList()
        queueMeal = null
        queueTotal = 0
        continuation = false
        resolverOpen = false
        source = null
    }

    /** Back or the arrow on the resolver. */
    fun close() { searchJob?.cancel(); abandon(); searchOpen = false }

    /**
     * A screen opened over the resolver's place (the Open Food Facts search, the food form): it replaces the resolver;
     * opened from elsewhere ([fromResolver] false) it abandons a queue.
     */
    fun childOpened(fromResolver: Boolean) {
        if (!fromResolver && parts.isNotEmpty()) abandon()
        continuation = fromResolver
        resolverOpen = false
    }

    /** Return to resolver: a screen opened from it closed without resolving; the resolver comes back while parts are left. */
    fun childClosed() { if (queueActive) resolverOpen = true else continuation = false }

    /** A screen opened from the resolver logged its food; [advancesQueue] when that resolved the queue's part. */
    fun childLogged(advancesQueue: Boolean) { if (advancesQueue) advance() else continuation = false }

    /** The food form saved: a queued part it resolved moves the queue on; a new food made for a Log part is its pick. */
    fun foodSaved(food: FoodRecord, newFood: Boolean, advancesQueue: Boolean) {
        childLogged(advancesQueue)
        if (resolvingPart && newFood) finishPart(food)
    }

    /** "Search your foods": the search field opens on the current part's name. */
    fun beginSearch() {
        searchOpen = true
        if (query.isBlank()) query = currentName()
        onQuery(query)
    }

    /** The current part's food text, for the searches opened from the resolver. */
    fun currentName(): String = parts.firstOrNull()?.let { typed(it).foodPhrase.trim().ifBlank { it.trim() } }.orEmpty()

    /** A part as the Log read it: its food text, typed amount and unit. */
    fun typed(raw: String): TypedPart = store.typed(raw)

    /** The resolver's search, ranked off the main thread; a result is shown only while its query is still the one typed. */
    fun onQuery(value: String) {
        query = value
        searchJob?.cancel()
        // The previous query's rows go at once, so none can be tapped for the new text while it is ranked.
        results = emptyList()
        if (value.isBlank()) return
        searchJob = scope.launch {
            val found = withContext(compute) { store.search(value) }
            if (query == value) results = found
        }
    }

    /** A local match tapped in the resolver: the part is logged with its typed amount, and the queue moves on. */
    fun resolve(food: FoodRecord) {
        val raw = parts.firstOrNull() ?: return
        val meal = queueMeal.takeIf { queueActive } ?: return
        val typed = typed(raw)
        val amount = typed.amount ?: food.defaultAmount
        val unit = typed.unit ?: food.defaultUnit
        if (!session.ready || session.busy) return
        val day = selectedDay()
        session.write(
            change = DiaryChange.QueuedPartLogged,
            onSuccess = { advance() },
            onFailure = { notices.show(it.message ?: "Could not resolve this food.") },
        ) { store.cacheFoodAndLog(food, amount, unit, meal, day) }
    }

    /** Leaves the current part unlogged and moves to the next one. */
    fun skip() { if (parts.isNotEmpty()) advance() }

    /**
     * The current part is done: the next one is resolved, and what is left shows where the parts were typed. A queue
     * abandoned while the write that resolved its part ran has no [source] any more, so nothing is written back.
     */
    private fun advance() {
        // A search still running was ranked before this food was added; it must not fill the next part's list.
        searchJob?.cancel()
        parts = parts.drop(1)
        source?.putBack(parts.joinToString(", "))
        query = ""
        results = emptyList()
        searchOpen = false
        resolverOpen = parts.isNotEmpty()
        if (parts.isEmpty()) {
            queueMeal = null
            continuation = false
            source = null
        }
    }
}
