package mk.bosa.breakfast.search

import androidx.compose.runtime.mutableStateMapOf
import mk.bosa.breakfast.data.OffSearchResult
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.suspendRunCatching
import mk.bosa.breakfast.ui.countLabel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long the typing rests before the part being typed is searched, with "Search Open Food Facts as I type" on. */
internal const val OFF_TYPING_REST_MS = 2_000L

/** Where the Open Food Facts search for one unmatched food stands. */
internal sealed interface OffPartState {
    data object Searching : OffPartState
    data class Found(val foods: List<FoodRecord>) : OffPartState
    data object NoResults : OffPartState
    data object Offline : OffPartState
    data object Failed : OffPartState
}

/**
 * The Open Food Facts search behind the Log: the foods Add left unmatched ([start]), and the part being typed, on a press
 * ([press]) or, only with "Search Open Food Facts as I type" on, once the typing rests ([typed]). One food at a time, in
 * order, a press first. An answer is kept for the draft, so a name is fetched once ([clear] forgets); a failure is not,
 * so the next Add or press tries it again, and nothing retries on its own. What was sent is remembered until the draft
 * is cleared ([clearDraft]), so the pause never sends a name twice, even after the Log closed and its answers went.
 *
 * Every send goes through the [OffSearchService] limit of `OFF_SEARCHES_PER_MINUTE`: Add and a press wait for a slot. The
 * pause never waits: when it ends, its search goes at once or not at all, with nothing running and a slot free. Moving on
 * from a part stops what was typed for it.
 */
internal class OffPartSearch(
    private val off: OffSearchService,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Says for TalkBack how a part's search ended, once it has. */
    private val announce: (String) -> Unit = {},
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** One search to run: [typed] for the part being typed rather than for Add; [holdsSlot] when it already took its slot of the limit. */
    private class Ask(val name: String, val typed: Boolean = false, val holdsSlot: Boolean = false)

    private val states = mutableStateMapOf<String, OffPartState>()
    private val waiting = ArrayDeque<Ask>()
    /** Every name sent for this draft; only [clearDraft] empties it. */
    private val sent = mutableSetOf<String>()
    private var running: Ask? = null
    private var job: Job? = null
    private var rest: Job? = null
    private var typing = ""

    fun stateOf(name: String): OffPartState? = states[name]

    /** Searches [names] that have no answer yet; what was running stops. */
    fun start(names: List<String>) {
        cancel()
        val todo = names.distinct().filter { !isAnswered(it) }
        todo.forEach { states[it] = OffPartState.Searching }
        waiting.addAll(todo.map { Ask(it) })
        run()
    }

    /**
     * The part being typed reads [phrase] now, blank for none. A search for what it read before stops. With [searchAsTyped]
     * on, [phrase] is searched once the typing has rested, unless it was already asked or sent; with it off, nothing is sent.
     */
    fun typed(phrase: String, searchAsTyped: Boolean) {
        stopResting()
        if (phrase != typing) {
            dropTyped()
            typing = phrase
        }
        if (!searchAsTyped || !offSearchesByItself(phrase) || phrase in states || phrase in sent) return
        rest = scope.launch {
            sleep(OFF_TYPING_REST_MS)
            // Decided now, and never again: without a free moment and a slot it was never here, and it shows nothing.
            if (running != null || waiting.isNotEmpty() || !off.takeSearchSlot()) return@launch
            run(Ask(phrase, typed = true, holdsSlot = true))
        }
    }

    /** "Find more matches": [phrase], the part being typed, goes next, unless it has an answer or is on its way. */
    fun press(phrase: String) {
        typed(phrase, searchAsTyped = false)
        if (isAnswered(phrase) || states[phrase] == OffPartState.Searching) return
        waiting.removeAll { it.name == phrase }
        states[phrase] = OffPartState.Searching
        waiting.addFirst(Ask(phrase, typed = true))
        run()
    }

    /** A pause being waited out no longer applies: the Log left the screen, or the typing went on. */
    fun stopResting() {
        rest?.cancel()
        rest = null
    }

    private fun isAnswered(name: String) = states[name].let { it is OffPartState.Found || it == OffPartState.NoResults }

    /** What was asked for the part being typed stops; what Add asked goes on. */
    private fun dropTyped() {
        waiting.filter { it.typed }.forEach { forgetSearching(it.name) }
        waiting.removeAll { it.typed }
        val stopped = running?.takeIf { it.typed } ?: return
        job?.cancel()
        running = null
        forgetSearching(stopped.name)
        run()
    }

    private fun forgetSearching(name: String) {
        if (states[name] == OffPartState.Searching) states.remove(name)
    }

    /** Works through [waiting], after [first] when there is one. */
    private fun run(first: Ask? = null) {
        if (running != null || (first == null && waiting.isEmpty())) return
        job = scope.launch {
            var next = first
            while (true) {
                val ask = next ?: waiting.removeFirstOrNull() ?: break
                next = null
                val name = ask.name
                running = ask
                sent += name
                states[name] = OffPartState.Searching
                val answer = if (!offQuerySendable(name)) OffPartState.NoResults else
                    withContext(io) { suspendRunCatching { if (ask.holdsSlot) off.searchWithSlot(name) else off.search(name) } }.fold(
                        onSuccess = { result -> stateOf(name, result) },
                        onFailure = { OffPartState.Failed },
                    )
                states[name] = answer
                announce("$name: ${spoken(answer)}")
            }
            running = null
        }
    }

    private fun spoken(state: OffPartState): String = when (state) {
        is OffPartState.Found -> "${countLabel(state.foods.size, "result", "results")} from Open Food Facts"
        OffPartState.NoResults -> offStatusText(OffSearchStatus.NoResults).first
        OffPartState.Offline -> offStatusText(OffSearchStatus.Offline).first
        else -> offStatusText(OffSearchStatus.Failed).first
    }

    private fun stateOf(name: String, result: OffSearchResult): OffPartState = when (result) {
        is OffSearchResult.Success -> foodsInScript(name, result.foods).let { if (it.isEmpty()) OffPartState.NoResults else OffPartState.Found(it) }
        OffSearchResult.Unavailable -> OffPartState.Offline
        is OffSearchResult.Error -> OffPartState.Failed
    }

    /**
     * The draft changed and only [names] are left: a search for one gone stops, the others go on as Add asked, and no new
     * one starts. Answers stay, and so does what was asked for the part being typed, which [typed] looks after.
     */
    fun retain(names: List<String>) {
        fun gone(name: String) = name !in names && name != typing
        val runningGone = running?.let { gone(it.name) } == true
        if (runningGone) { job?.cancel(); running = null }
        waiting.removeAll { gone(it.name) }
        states.entries.removeAll { it.value == OffPartState.Searching && gone(it.key) }
        if (runningGone) run()
    }

    /** The Log closed or a new Add began: the searches running stop; the answers they got stay. */
    fun cancel() {
        stopResting()
        job?.cancel()
        job = null
        running = null
        waiting.clear()
        states.entries.removeAll { it.value == OffPartState.Searching }
    }

    /** The Log closed: nothing runs and no answer is kept. What was sent stays remembered, so reopening sends nothing by itself. */
    fun clear() {
        cancel()
        states.clear()
        typing = ""
    }

    /** The draft was cleared: as [clear], and every name may be sent again. */
    fun clearDraft() {
        clear()
        sent.clear()
    }
}
