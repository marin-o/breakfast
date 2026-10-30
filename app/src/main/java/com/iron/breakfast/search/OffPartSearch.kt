package com.iron.breakfast.search

import androidx.compose.runtime.mutableStateMapOf
import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.TextScript
import com.iron.breakfast.domain.matchWords
import com.iron.breakfast.domain.sameScript
import com.iron.breakfast.domain.scriptOf
import com.iron.breakfast.suspendRunCatching
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
 * The results of [foods] a search for [name] may show, by the script rule: a word that mixes scripts finds nothing, and
 * a product is shown only with a name word in a script [name] was typed in. A name with no letters shows them all.
 */
internal fun offResultsInScript(name: String, foods: List<FoodRecord>): List<FoodRecord> {
    val scripts = matchWords(name).map(::scriptOf)
    if (TextScript.MIXED in scripts) return emptyList()
    return foods.filter { food ->
        matchWords(food.name).any { word -> scripts.any { sameScript(it, scriptOf(word)) } }
    }
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
) {
    private val states = mutableStateMapOf<String, OffPartState>()
    private var job: Job? = null

    fun stateOf(name: String): OffPartState? = states[name]

    /** Searches [names] that have no answer yet; what was running stops. */
    fun start(names: List<String>) {
        cancel()
        val todo = names.distinct().filter { states[it] !is OffPartState.Found && states[it] != OffPartState.NoResults }
        if (todo.isEmpty()) return
        todo.forEach { states[it] = OffPartState.Searching }
        job = scope.launch {
            for (name in todo) {
                // A word that mixes scripts can show nothing, so it is not sent.
                val answer = if (TextScript.MIXED in matchWords(name).map(::scriptOf)) OffPartState.NoResults else
                    withContext(io) { suspendRunCatching { off.search(name) } }.fold(
                        onSuccess = { result -> stateOf(name, result) },
                        onFailure = { OffPartState.Failed },
                    )
                states[name] = answer
            }
        }
    }

    private fun stateOf(name: String, result: OffSearchResult): OffPartState = when (result) {
        is OffSearchResult.Success -> offResultsInScript(name, result.foods).let { if (it.isEmpty()) OffPartState.NoResults else OffPartState.Found(it) }
        OffSearchResult.Unavailable -> OffPartState.Offline
        is OffSearchResult.Error -> OffPartState.Failed
    }

    /** The draft changed or was left: the search running stops; the answers it got stay. */
    fun cancel() {
        job?.cancel()
        job = null
        states.entries.removeAll { it.value == OffPartState.Searching }
    }

    /** The Log closed: nothing runs and nothing is kept. */
    fun clear() {
        cancel()
        states.clear()
    }
}
