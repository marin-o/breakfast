package com.bosa.breakfast.search

import androidx.compose.runtime.mutableStateMapOf
import com.bosa.breakfast.data.OffSearchResult
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.suspendRunCatching
import com.bosa.breakfast.ui.countLabel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the Open Food Facts search for one unmatched food stands. */
internal sealed interface OffPartState {
    data object Searching : OffPartState
    data class Found(val foods: List<FoodRecord>) : OffPartState
    data object NoResults : OffPartState
    data object Offline : OffPartState
    data object Failed : OffPartState
}

/**
 * The Open Food Facts search behind the Log's unmatched foods, one food at a time, in order. It runs only when asked
 * ([start], from Add), never while typing. An answer is kept for the draft, so a name is fetched once ([clear] forgets);
 * a failure is not, so the next Add tries it again, and nothing retries on its own.
 */
internal class OffPartSearch(
    private val off: OffSearchService,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Says for TalkBack how a part's search ended, once it has. */
    private val announce: (String) -> Unit = {},
) {
    private val states = mutableStateMapOf<String, OffPartState>()
    private val waiting = ArrayDeque<String>()
    private var running: String? = null
    private var job: Job? = null

    fun stateOf(name: String): OffPartState? = states[name]

    /** Searches [names] that have no answer yet; what was running stops. */
    fun start(names: List<String>) {
        cancel()
        val todo = names.distinct().filter { states[it] !is OffPartState.Found && states[it] != OffPartState.NoResults }
        todo.forEach { states[it] = OffPartState.Searching }
        waiting.addAll(todo)
        run()
    }

    private fun run() {
        if (waiting.isEmpty()) return
        job = scope.launch {
            while (waiting.isNotEmpty()) {
                val name = waiting.first()
                running = name
                val answer = if (!offQuerySendable(name)) OffPartState.NoResults else
                    withContext(io) { suspendRunCatching { off.search(name) } }.fold(
                        onSuccess = { result -> stateOf(name, result) },
                        onFailure = { OffPartState.Failed },
                    )
                states[name] = answer
                waiting.removeFirstOrNull()
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
     * one starts. Answers stay.
     */
    fun retain(names: List<String>) {
        val runningGone = running?.let { it !in names } == true
        if (runningGone) { job?.cancel(); running = null }
        waiting.removeAll { it !in names }
        states.entries.removeAll { it.value == OffPartState.Searching && it.key !in names }
        if (runningGone) run()
    }

    /** The Log closed or a new Add began: the searches running stop; the answers they got stay. */
    fun cancel() {
        job?.cancel()
        job = null
        running = null
        waiting.clear()
        states.entries.removeAll { it.value == OffPartState.Searching }
    }

    /** The Log closed: nothing runs and nothing is kept. */
    fun clear() {
        cancel()
        states.clear()
    }
}
