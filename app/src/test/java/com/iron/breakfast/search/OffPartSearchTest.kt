package com.iron.breakfast.search

import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.testFood
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OffPartSearchTest {
    private val asked = mutableListOf<String>()
    private val answers = mutableMapOf<String, OffSearchResult>()
    private var held: CompletableDeferred<Unit>? = null
    private val service = object : OffSearchService {
        override suspend fun search(query: String): OffSearchResult {
            asked += query
            held?.await()
            return answers[query] ?: OffSearchResult.Success(emptyList())
        }
    }
    private val search = OffPartSearch(service, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
    private val polenta = testFood("p", "Polenta")

    @Test fun aNameIsFetchedOnceAndNamesGoInOrder() {
        answers["polenta"] = OffSearchResult.Success(listOf(polenta))
        search.start(listOf("polenta", "argeta", "polenta"))
        search.start(listOf("polenta", "argeta"))
        assertEquals(listOf("polenta", "argeta"), asked)
        assertEquals(OffPartState.Found(listOf(polenta)), search.stateOf("polenta"))
        assertEquals(OffPartState.NoResults, search.stateOf("argeta"))
    }

    @Test fun nothingIsAskedUntilStarted() {
        assertEquals(emptyList<String>(), asked)
        assertNull(search.stateOf("polenta"))
    }

    @Test fun aCancelStopsTheRestAndForgetsWhatWasRunning() {
        held = CompletableDeferred()
        search.start(listOf("polenta", "argeta"))
        assertEquals(OffPartState.Searching, search.stateOf("polenta"))
        search.cancel()
        held!!.complete(Unit)
        assertEquals(listOf("polenta"), asked)
        assertNull(search.stateOf("polenta"))
        assertNull(search.stateOf("argeta"))
    }

    @Test fun offlineAndAnErrorFallBackAndTheNextAddTriesAgain() {
        answers["polenta"] = OffSearchResult.Unavailable
        answers["argeta"] = OffSearchResult.Error("x")
        search.start(listOf("polenta", "argeta"))
        assertEquals(OffPartState.Offline, search.stateOf("polenta"))
        assertEquals(OffPartState.Failed, search.stateOf("argeta"))
        search.start(listOf("polenta"))
        assertEquals(listOf("polenta", "argeta", "polenta"), asked)
    }

    @Test fun clearingForgetsTheAnswers() {
        search.start(listOf("polenta"))
        search.clear()
        search.start(listOf("polenta"))
        assertEquals(listOf("polenta", "polenta"), asked)
    }

    @Test fun resultsKeepToTheScriptTyped() {
        val cyrillic = testFood("c", "Полента")
        val both = testFood("b", "Polenta Полента")
        val mixed = testFood("m", "Pоlenta")
        val foods = listOf(polenta, cyrillic, both, mixed)
        assertEquals(listOf(polenta, both), offResultsInScript("polenta", foods))
        assertEquals(listOf(cyrillic, both), offResultsInScript("полента", foods))
        assertEquals(emptyList<Any>(), offResultsInScript("pоlenta", foods))
    }

    @Test fun aMixedWordIsNotSent() {
        search.start(listOf("pоlenta"))
        assertEquals(emptyList<String>(), asked)
        assertEquals(OffPartState.NoResults, search.stateOf("pоlenta"))
    }
}
