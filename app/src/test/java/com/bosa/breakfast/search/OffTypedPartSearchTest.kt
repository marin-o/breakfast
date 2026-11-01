package com.bosa.breakfast.search

import com.bosa.breakfast.data.OFF_SEARCHES_PER_MINUTE
import com.bosa.breakfast.data.OffSearchResult
import com.bosa.breakfast.data.RollingLimit
import com.bosa.breakfast.testFood
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The part being typed in [OffPartSearch]: the button, the pause with the setting on, and the limit. */
class OffTypedPartSearchTest {
    private val asked = mutableListOf<String>()
    private val answers = mutableMapOf<String, OffSearchResult>()
    private val held = mutableMapOf<String, CompletableDeferred<Unit>>()
    private var now = 0L
    private val limit = RollingLimit(OFF_SEARCHES_PER_MINUTE, 60_000L, { now }, { CompletableDeferred<Unit>().await() })
    private val service = object : OffSearchService {
        override suspend fun search(query: String): OffSearchResult { limit.take(); return searchWithSlot(query) }
        override fun takeSearchSlot() = limit.tryTake()
        override suspend fun searchWithSlot(query: String): OffSearchResult {
            asked += query
            held[query]?.await()
            return answers[query] ?: OffSearchResult.Success(emptyList())
        }
    }
    private val rests = mutableListOf<Pair<Long, CompletableDeferred<Unit>>>()
    private val search = OffPartSearch(
        service, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Dispatchers.Unconfined,
        sleep = { ms -> CompletableDeferred<Unit>().also { rests += ms to it }.await() },
    )
    private val sarma = testFood("s", "Sarma")

    private fun rest() = rests.removeAt(rests.lastIndex).second.complete(Unit)
    private fun useUpTheLimit() = repeat(OFF_SEARCHES_PER_MINUTE) { assertTrue(limit.tryTake()) }

    // ---- The button ----

    @Test fun aPressSearchesAtOnceAndKeepsTheAnswer() {
        answers["sarma"] = OffSearchResult.Success(listOf(sarma))
        search.typed("sarma", searchAsTyped = false)
        assertEquals("nothing goes without a press", emptyList<String>(), asked)
        search.press("sarma")
        assertEquals(listOf("sarma"), asked)
        assertEquals(OffPartState.Found(listOf(sarma)), search.stateOf("sarma"))
        search.press("sarma")
        assertEquals("answered: not asked twice", 1, asked.size)
    }

    @Test fun aPressSearchesAgainAfterAFailure() {
        answers["sarma"] = OffSearchResult.Unavailable
        search.press("sarma")
        assertEquals(OffPartState.Offline, search.stateOf("sarma"))
        answers["sarma"] = OffSearchResult.Error("x")
        search.press("sarma")
        assertEquals(OffPartState.Failed, search.stateOf("sarma"))
        assertEquals(2, asked.size)
    }

    @Test fun aPressOnWhatCannotBeSentSendsNothing() {
        search.press("&")
        assertEquals(emptyList<String>(), asked)
        assertEquals(OffPartState.NoResults, search.stateOf("&"))
    }

    @Test fun anAnswerForOlderTextIsNotKeptAndItsSearchStops() {
        held["sarma"] = CompletableDeferred()
        search.press("sarma")
        assertEquals(OffPartState.Searching, search.stateOf("sarma"))
        search.typed("sarma x", searchAsTyped = false)
        assertNull(search.stateOf("sarma"))
        answers["sarma"] = OffSearchResult.Success(listOf(sarma))
        held.getValue("sarma").complete(Unit)
        assertNull(search.stateOf("sarma"))
    }

    @Test fun anAnswerStaysWhenTheTextComesBackToIt() {
        search.press("sarma")
        search.typed("sarma x", searchAsTyped = true)
        search.typed("sarma", searchAsTyped = true)
        assertEquals("the pause armed for the new text is gone, none for the old", 1, rests.size)
        assertEquals(OffPartState.NoResults, search.stateOf("sarma"))
        assertEquals(1, asked.size)
    }

    // ---- With the queue ----

    @Test fun aPressWaitsForTheSearchRunningAndLeavesAddsSearchesAlone() {
        held["polenta"] = CompletableDeferred()
        search.start(listOf("polenta", "argeta"))
        search.press("sarma")
        assertEquals("one at a time", listOf("polenta"), asked)
        assertEquals(OffPartState.Searching, search.stateOf("argeta"))
        held.getValue("polenta").complete(Unit)
        assertEquals("the press goes next", listOf("polenta", "sarma", "argeta"), asked)
    }

    @Test fun movingOnFromAPartLeavesAddsSearchesAlone() {
        held["polenta"] = CompletableDeferred()
        search.start(listOf("polenta"))
        search.typed("sarma", searchAsTyped = false)
        search.typed("sar", searchAsTyped = false)
        assertEquals(OffPartState.Searching, search.stateOf("polenta"))
        held.getValue("polenta").complete(Unit)
        assertEquals(listOf("polenta"), asked)
    }

    @Test fun anEditOfTheQueueLeavesTheTypedSearchAlone() {
        held["sarma"] = CompletableDeferred()
        search.press("sarma")
        search.retain(listOf("polenta"))
        assertEquals(OffPartState.Searching, search.stateOf("sarma"))
        held.getValue("sarma").complete(Unit)
        assertEquals(OffPartState.NoResults, search.stateOf("sarma"))
    }

    // ---- The pause ----

    @Test fun withTheSettingOffNothingIsEvenWaitedFor() {
        search.typed("sarma", searchAsTyped = false)
        assertEquals(emptyList<Pair<Long, Any>>(), rests)
        assertEquals(emptyList<String>(), asked)
    }

    @Test fun onlyTheTextTheTypingRestsOnIsSearchedAfterTwoSeconds() {
        search.typed("sar", searchAsTyped = true)
        val first = rests.single().second
        search.typed("sarm", searchAsTyped = true)
        search.typed("sarma", searchAsTyped = true)
        assertEquals(2_000L, rests.last().first)
        first.complete(Unit)
        assertEquals("not before the pause, and not for older text", emptyList<String>(), asked)
        rest()
        assertEquals(listOf("sarma"), asked)
    }

    @Test fun aSearchAlreadyAnsweredIsNotRepeated() {
        search.typed("sarma", searchAsTyped = true)
        rest()
        search.typed("sarma x", searchAsTyped = false)
        search.typed("sarma", searchAsTyped = true)
        assertEquals(emptyList<Pair<Long, Any>>(), rests)
        assertEquals(1, asked.size)
    }

    @Test fun aFailureIsNotRetriedByThePauseButThePressDoes() {
        answers["sarma"] = OffSearchResult.Unavailable
        search.typed("sarma", searchAsTyped = true)
        rest()
        search.typed("sarma x", searchAsTyped = false)
        search.typed("sarma", searchAsTyped = true)
        assertEquals(1, asked.size)
        search.press("sarma")
        assertEquals(2, asked.size)
    }

    @Test fun whatIsRefusedOrUnderThreeLettersIsNotSearchedByItself() {
        listOf("sa", "7up", "pоlenta", "&&&", "").forEach { search.typed(it, searchAsTyped = true) }
        assertEquals(emptyList<Pair<Long, Any>>(), rests)
        assertFalse(offSearchesByItself("sa"))
        assertTrue(offSearchesByItself("сар"))
    }

    @Test fun aPressDuringThePauseSearchesOnce() {
        search.typed("sarma", searchAsTyped = true)
        val pause = rests.single().second
        search.press("sarma")
        pause.complete(Unit)
        assertEquals(listOf("sarma"), asked)
    }

    @Test fun leavingTheScreenAndClearingEndThePause() {
        search.typed("sarma", searchAsTyped = true)
        search.stopResting()
        rests.single().second.complete(Unit)
        search.typed("kefir", searchAsTyped = true)
        search.clear()
        rest()
        assertEquals(emptyList<String>(), asked)
    }

    @Test fun aPauseEndingBehindAHeldAddSearchStopsAndNeverSends() {
        held["polenta"] = CompletableDeferred()
        search.start(listOf("polenta"))
        search.typed("sarma", searchAsTyped = true)
        rest()
        assertNull("no row", search.stateOf("sarma"))
        held.getValue("polenta").complete(Unit)
        assertEquals("it did not wait for its turn", listOf("polenta"), asked)
        now += 60_000L
        assertEquals(listOf("polenta"), asked)
        search.press("sarma")
        assertEquals("the button still can", listOf("polenta", "sarma"), asked)
    }

    @Test fun aPauseEndingBehindARunningSearchTakesNoSlot() {
        held["polenta"] = CompletableDeferred()
        search.start(listOf("polenta"))
        search.typed("sarma", searchAsTyped = true)
        rest()
        repeat(OFF_SEARCHES_PER_MINUTE - 1) { assertTrue("only the Add search took one", limit.tryTake()) }
        assertFalse(limit.tryTake())
    }

    @Test fun afterScanNothingTypedSendsOffScreen() {
        held["polenta"] = CompletableDeferred()
        search.start(listOf("polenta"))
        search.typed("sarma", searchAsTyped = true)
        rest()
        search.typed("kefir", searchAsTyped = true)
        search.stopResting()
        held.getValue("polenta").complete(Unit)
        rests.forEach { it.second.complete(Unit) }
        assertEquals("what Add asked goes on, nothing typed does", listOf("polenta"), asked)
    }

    @Test fun comingBackDoesNotSendTheAnsweredTextAgain() {
        search.typed("sarma", searchAsTyped = true)
        rest()
        search.stopResting()
        search.typed("sarma", searchAsTyped = true)
        assertEquals("not even a pause", emptyList<Pair<Long, Any>>(), rests)
        assertEquals(listOf("sarma"), asked)
    }

    @Test fun closingKeepsWhatWasSentAndOnlyClearingTheDraftForgetsIt() {
        answers["sarma"] = OffSearchResult.Success(listOf(sarma))
        search.typed("sarma", searchAsTyped = true)
        rest()
        search.clear()
        assertNull(search.stateOf("sarma"))
        search.typed("sarma", searchAsTyped = true)
        assertEquals("no pause after a close", emptyList<Pair<Long, Any>>(), rests)
        search.press("sarma")
        assertEquals("a press searches again", 2, asked.size)
        assertEquals(OffPartState.Found(listOf(sarma)), search.stateOf("sarma"))
        search.clearDraft()
        search.typed("sarma", searchAsTyped = true)
        rest()
        assertEquals("a new draft may send it", 3, asked.size)
    }

    @Test fun aPressShowsItsRowAtOnceWhateverItsAnswerWas() {
        held["sarma"] = CompletableDeferred()
        search.press("sarma")
        assertEquals("searching, with the send still out", OffPartState.Searching, search.stateOf("sarma"))
        held.getValue("sarma").complete(Unit)
        search.press("sarma")
        assertEquals("answered: the row is still there to scroll to", OffPartState.NoResults, search.stateOf("sarma"))
    }

    // ---- The limit ----

    @Test fun atTheLimitThePauseStopsQuietlyAndNothingWaits() {
        useUpTheLimit()
        search.typed("sarma", searchAsTyped = true)
        rest()
        assertEquals(emptyList<String>(), asked)
        assertNull("no error and no row", search.stateOf("sarma"))
        now += 60_000L
        assertEquals("nothing was queued for when a slot is free", emptyList<String>(), asked)
        search.press("sarma")
        assertEquals("the button can try later", listOf("sarma"), asked)
    }

    @Test fun everySearchThePauseSendsUsesASlot() {
        repeat(OFF_SEARCHES_PER_MINUTE + 1) { index ->
            search.typed("sarma$index", searchAsTyped = true)
            rest()
        }
        assertEquals(OFF_SEARCHES_PER_MINUTE, asked.size)
    }

    @Test fun thePressUsesASlotToo() {
        repeat(OFF_SEARCHES_PER_MINUTE) { search.press("sarma$it") }
        assertFalse(limit.tryTake())
    }

    @Test fun clearingForgetsTheAnswers() {
        search.press("sarma")
        search.clear()
        assertNull(search.stateOf("sarma"))
    }
}
