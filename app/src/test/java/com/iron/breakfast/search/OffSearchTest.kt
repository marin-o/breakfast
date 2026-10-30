package com.iron.breakfast.search

import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffSearchTest {
    /** Each search waits until the test answers it; a cancelled one is recorded. */
    private class FakeOff : OffSearchService {
        val asked = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val answers = mutableMapOf<String, CompletableDeferred<OffSearchResult>>()
        override suspend fun search(query: String): OffSearchResult {
            asked += query
            val answer = CompletableDeferred<OffSearchResult>().also { answers[query] = it }
            try { return answer.await() } catch (e: CancellationException) { cancelled += query; throw e }
        }
        fun answer(query: String, result: OffSearchResult) { answers.getValue(query).complete(result) }
    }

    private val off = FakeOff()
    private var ready = true
    private val events = mutableListOf<String>()
    private val chosen = mutableListOf<FoodRecord>()
    private val search = OffSearch(
        off, CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), Dispatchers.Unconfined, { ready },
        onOpen = { events += "open fromResolver=$it" }, onClose = { events += "close" }, onChoose = { food, _ -> chosen += food },
    )

    private fun product(name: String) = FoodRecord(name, name, emptyList(), "off", FoodOrigin.GENERIC, NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(50.0, null, null, null)))

    @Test fun theSearchScreenKeepsToTheScriptTyped() {
        search.open("млеко")
        search.submit()
        off.answer("млеко", OffSearchResult.Success(listOf(product("Milk"), product("Млеко"))))
        assertEquals(listOf("Млеко"), search.results.map { it.name })
    }

    @Test fun theSearchScreenSendsNoMixedWord() {
        search.open("mлeko")
        search.submit()
        assertEquals(false, search.busy)
        assertEquals("No usable result for \"mлeko\".", search.message)
    }

    @Test fun openingTellsTheCoordinatorAndStartsFromThePrefill() {
        search.open("kefir", fromResolver = true)
        assertTrue(search.open)
        assertEquals("kefir", search.query)
        assertEquals(listOf("open fromResolver=true"), events)
        search.close()
        assertFalse(search.open)
        assertEquals(listOf("open fromResolver=true", "close"), events)
    }

    @Test fun aResultShowsForTheQueryItWasFor() {
        search.open("kefir")
        search.submit()
        assertTrue(search.busy)
        off.answer("kefir", OffSearchResult.Success(listOf(product("Kefir 1%"))))
        assertFalse(search.busy)
        assertEquals(listOf("Kefir 1%"), search.results.map { it.name })
        assertNull(search.message)
    }

    @Test fun noResultUnavailableAndErrorEachSaySo() {
        search.open("kefir")
        search.submit()
        off.answer("kefir", OffSearchResult.Success(emptyList()))
        assertEquals("No usable result for \"kefir\".", search.message)
        search.onQuery("ayran")
        search.submit()
        off.answer("ayran", OffSearchResult.Unavailable)
        assertTrue(search.unavailable)
        assertEquals("Open Food Facts unavailable; try again or add manually.", search.message)
    }

    @Test fun typingCancelsTheRunningSearchAndDropsItsAnswer() {
        search.open("kefir")
        search.submit()
        search.onQuery("ayran")
        assertEquals(listOf("kefir"), off.cancelled)
        assertFalse(search.busy)
        assertTrue(search.results.isEmpty())
        search.submit()
        off.answer("ayran", OffSearchResult.Success(listOf(product("Ayran"))))
        assertEquals(listOf("Ayran"), search.results.map { it.name })
    }

    @Test fun closingCancelsTheRunningSearch() {
        search.open("kefir")
        search.submit()
        search.close()
        assertEquals(listOf("kefir"), off.cancelled)
        assertFalse(search.busy)
    }

    @Test fun aSearchHiddenByAnotherScreenIsNotApplied() {
        search.open("kefir")
        search.submit()
        search.hide()
        off.answer("kefir", OffSearchResult.Success(listOf(product("Kefir 1%"))))
        assertTrue(search.results.isEmpty())
        assertFalse(search.busy)
    }

    @Test fun reopeningASearchHiddenWhileRunningIsNotStuckBusy() {
        search.open("kefir")
        search.submit()
        search.hide()
        search.open("ayran")
        assertEquals(listOf("kefir"), off.cancelled)
        assertFalse(search.busy)
        search.submit()
        off.answer("ayran", OffSearchResult.Success(listOf(product("Ayran"))))
        assertEquals(listOf("Ayran"), search.results.map { it.name })
    }

    @Test fun aSecondSubmitWhileOneRunsAndASearchBeforeStorageAreRefused() {
        search.open("kefir")
        search.submit()
        search.submit()
        assertEquals(listOf("kefir"), off.asked)
        search.close()
        ready = false
        search.open("kefir")
        search.submit()
        assertEquals(listOf("kefir"), off.asked)
        search.onQuery(" ")
        ready = true
        search.submit()
        assertEquals(listOf("kefir"), off.asked)
    }

    @Test fun choosingAResultHandsItToReviewAndClosesTheSearch() {
        search.open("kefir")
        search.submit()
        val kefir = product("Kefir 1%")
        off.answer("kefir", OffSearchResult.Success(listOf(kefir)))
        search.choose(kefir)
        assertEquals(listOf(kefir), chosen)
        assertFalse(search.open)
        // Hidden, not closed: the coordinator's return rule is not run.
        assertEquals(listOf("open fromResolver=false"), events)
    }
}
