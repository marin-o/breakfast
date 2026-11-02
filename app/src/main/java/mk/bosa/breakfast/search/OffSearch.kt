package mk.bosa.breakfast.search

import mk.bosa.breakfast.scan.ProductReviewOrigin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mk.bosa.breakfast.data.OffSearchResult
import mk.bosa.breakfast.domain.FoodRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Open Food Facts search; `OpenFoodFactsClient` implements it. A cancel disconnects the request in flight. */
interface OffSearchService {
    /** Takes a slot of the search limit, waiting for one when the limit is reached. */
    suspend fun search(query: String): OffSearchResult

    /** A slot of the search limit if one is free now, for [searchWithSlot]; false when the limit is reached. It never waits. */
    fun takeSearchSlot(): Boolean = true

    /** [search] with the slot [takeSearchSlot] already took. */
    suspend fun searchWithSlot(query: String): OffSearchResult = search(query)
}

/**
 * The Open Food Facts search screen: its query, results, progress and message.
 *
 * A search runs on [io] in [scope]; a new query, a new search or closing cancels the one running, and a result is
 * applied only while it is still the latest search, for the query still typed, with the screen still open
 * ([shouldApplyOffSearchResult]).
 *
 * The screens around it are not its business. Opening and closing tell the coordinator through [onOpen] (whether it
 * was opened from the resolver) and [onClose]; a picked product goes to [onChoose] (product review).
 *
 * Opened for "Not right?", the food being corrected is not offered ([leavesOut]), and Back from the review of a pick
 * comes back here with its results ([returnFromReview]).
 */
internal class OffSearch(
    private val off: OffSearchService,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Storage is open; a search is refused before. */
    private val ready: () -> Boolean,
    private val onOpen: (fromResolver: Boolean) -> Unit,
    private val onClose: () -> Unit,
    private val onChoose: (FoodRecord, ProductReviewOrigin?) -> Unit,
    /** [food] is the one the entry being corrected already is. */
    private val isFoodBeingCorrected: (FoodRecord) -> Boolean = { false },
) {
    var open by mutableStateOf(false); private set
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    /** True when the last search failed for lack of a connection (not for any other reason). */
    var unavailable by mutableStateOf(false); private set
    private var job: Job? = null
    private var generation = 0L
    /** A pick of a correction gave way to its review, which Back returns from. */
    private var leftForReview = false

    /** What this search was opened for, when a logging review opened it: what it picks or creates goes on to that. */
    var reviewOrigin by mutableStateOf<ProductReviewOrigin?>(null); private set

    /** This search is for "Not right?", and [food] is what the entry already is: it isn't offered as what it should have been. */
    fun leavesOut(food: FoodRecord): Boolean = reviewOrigin == ProductReviewOrigin.CORRECTION && isFoodBeingCorrected(food)

    /** Opens the search on [prefill]; [fromResolver] when the resolver opened it, so closing returns there. */
    fun open(prefill: String? = null, fromResolver: Boolean = false, reviewOrigin: ProductReviewOrigin? = null) {
        this.reviewOrigin = reviewOrigin
        drop()
        busy = false
        onOpen(fromResolver)
        open = true
        query = prefill ?: ""
        results = emptyList()
        message = null
        unavailable = false
        leftForReview = false
    }

    /** The arrow, Back or Cancel: the search running stops. */
    fun close() {
        drop()
        busy = false
        open = false
        leftForReview = false
        onClose()
    }

    /** Another screen took the search's place (the food form, product review): a search running or waiting for its slot stops. */
    fun hide() {
        drop()
        busy = false
        open = false
        leftForReview = false
    }

    /** Back from the review of a correction's pick: the search shows again as it was left. Reached any other way, nothing happens. */
    fun returnFromReview() {
        if (!leftForReview) return
        leftForReview = false
        open = true
    }

    /** Typing: the running search stops and the previous results go. */
    fun onQuery(value: String) {
        drop()
        busy = false
        query = value
        results = emptyList()
        message = null
        unavailable = false
    }

    /** A result row: the search gives way to product review for [food]. */
    fun choose(food: FoodRecord) {
        hide()
        leftForReview = reviewOrigin == ProductReviewOrigin.CORRECTION
        onChoose(food, reviewOrigin)
    }

    fun submit() {
        if (!ready() || query.isBlank() || busy) return
        val captured = query
        val mine = ++generation
        job?.cancel()
        message = null
        unavailable = false
        // What the script rule can show nothing for is not sent.
        if (!offQuerySendable(captured)) {
            results = emptyList()
            message = "No usable result for \"$captured\"."
            return
        }
        busy = true
        job = scope.launch {
            val result = withContext(io) { off.search(captured) }
            if (shouldApplyOffSearchResult(mine, generation, captured, query, open)) {
                when (result) {
                    is OffSearchResult.Success -> {
                        results = foodsInScript(captured, result.foods).filterNot(::leavesOut)
                        message = if (results.isEmpty()) "No usable result for \"$captured\"." else null
                    }
                    OffSearchResult.Unavailable -> {
                        unavailable = true
                        results = emptyList()
                        message = "Open Food Facts unavailable; try again or add manually."
                    }
                    is OffSearchResult.Error -> {
                        results = emptyList()
                        message = result.message
                    }
                }
            }
            if (mine == generation) busy = false
        }
    }

    /** Stops the running search and makes any result it still delivers stale. */
    private fun drop() {
        job?.cancel()
        generation++
    }
}
