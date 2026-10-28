package com.iron.breakfast.resolve

import com.iron.breakfast.QueueDispatcher
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.today.OpenDiary
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun food(id: String, name: String = id) = FoodRecord(
    id, name, emptyList(), "test", FoodOrigin.CUSTOM,
    NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 1.0, 1.0)),
    defaultAmount = 50.0, defaultUnit = MeasureUnit.GRAM,
)

internal fun ranked(id: String) = RankedFood(food(id), 1.0, emptyList())

/** A [ResolveSource] the test reads: the text where a queue's parts were typed. */
internal class TypedText(override var text: String = "") : ResolveSource {
    val writes = mutableListOf<String>()
    override fun putBack(left: String) { text = left; writes += left }
}

class ResolveFlowTest {
    private class FakeStore : ResolveStore {
        val calls = mutableListOf<String>()
        val found = mutableMapOf<String, List<RankedFood>>()
        var hold: CompletableDeferred<Unit>? = null
        // "ayran200ml" reads as ayran, 200 ml, as the planner reads a glued amount.
        override fun typed(raw: String) = if (raw == "ayran200ml") TypedPart("ayran", 200.0, MeasureUnit.MILLILITER) else TypedPart(raw, null, null)
        override fun search(query: String) = found[query].orEmpty()
        override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
            hold?.await()
            calls += "log ${food.id} $amount ${unit.name} $meal $dayKey"
        }
    }

    private val diary = OpenDiary()
    private val store = FakeStore()
    private val notices = Notices()
    private val compute = QueueDispatcher()
    private val events = mutableListOf<String>()
    private val flow = ResolveFlow(
        diary.session, store, notices, diary.scope, compute,
        selectedDay = { "2026-10-28" },
        openLibrary = { meal, query -> events += "library $meal \"$query\"" },
        returnToLog = { request, pick -> events += "log ${request.part} pick=${pick?.id}" },
    )
    private val ayran = food("ayran", "Ayran")
    private val request = LogResolveRequest("2 eggs, ayran200ml", 8, 18, "ayran200ml", MealSlot.LUNCH, null, null)

    @Test fun aSaveWithUnmatchedPartsStartsTheQueueAndPutsThemBackWhereTheyWereTyped() {
        val draft = TypedText("2 eggs, ayran200ml, kefir")
        flow.afterSave(listOf("ayran200ml", "kefir"), MealSlot.BREAKFAST, draft, typedAtSave = "2 eggs, ayran200ml, kefir")
        assertEquals("ayran200ml, kefir", draft.text)
        assertTrue(flow.resolverOpen)
        assertTrue(flow.queueActive)
        assertEquals(MealSlot.BREAKFAST, flow.queueMeal)
        assertEquals(2, flow.queueTotal)
        val view = flow.queueView()!!
        assertEquals(listOf("ayran200ml", "kefir"), view.parts)
        assertEquals("the current part as the planner read it", TypedPart("ayran", 200.0, MeasureUnit.MILLILITER), view.current)
        assertEquals("ayran", flow.currentName())
    }

    @Test fun aSaveWhoseTextChangedMeanwhileLeavesTheNewText() {
        val draft = TypedText("2 eggs, kefir, banana")
        flow.afterSave(listOf("kefir"), MealSlot.LUNCH, draft, typedAtSave = "2 eggs, kefir")
        assertEquals("2 eggs, kefir, banana", draft.text)
        assertTrue("the queue starts all the same", flow.resolverOpen)
    }

    @Test fun aSaveThatMatchedEverythingClearsTheTextAndOpensNothing() {
        val draft = TypedText("2 eggs")
        flow.afterSave(emptyList(), MealSlot.LUNCH, draft, typedAtSave = "2 eggs")
        assertEquals("", draft.text)
        assertFalse(flow.resolverOpen)
        assertFalse(flow.continuation)
        assertNull(flow.queueMeal)
        assertNull(flow.queueView())
    }

    @Test fun aPickLogsThePartWithItsTypedAmountToTheQueuesMealAndMovesOn() {
        val draft = TypedText("ayran200ml, kefir")
        flow.afterSave(listOf("ayran200ml", "kefir"), MealSlot.DINNER, draft, "ayran200ml, kefir")
        flow.resolve(ayran)
        assertEquals(listOf("log ayran 200.0 MILLILITER DINNER 2026-10-28"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.QueuedPartLogged), diary.published)
        assertEquals(listOf("kefir"), flow.parts)
        assertEquals("kefir", draft.text)
        assertTrue(flow.resolverOpen)
        // The last part: the queue ends and the text it came from is empty.
        flow.resolve(food("kefir"))
        assertEquals("log kefir 50.0 GRAM DINNER 2026-10-28", store.calls.last())
        assertEquals("", draft.text)
        assertFalse(flow.resolverOpen)
        assertFalse(flow.continuation)
        assertNull(flow.queueMeal)
    }

    @Test fun skipMovesOnWithoutAWrite() {
        val bar = TypedText("kefir, ayran")
        flow.afterSave(listOf("kefir", "ayran"), MealSlot.LUNCH, bar, "kefir, ayran")
        flow.skip()
        assertEquals("ayran", bar.text)
        assertEquals(emptyList<String>(), store.calls)
        assertEquals(emptyList<DiaryChange>(), diary.published)
        assertEquals("the position counts on", "2 of 2", flow.queueView()?.position())
    }

    @Test fun returnToResolverBringsItBackWhilePartsAreLeft() {
        flow.afterSave(listOf("kefir"), MealSlot.LUNCH, TypedText("kefir"), "kefir")
        flow.childOpened(fromResolver = true)
        assertFalse("the screen opened from it takes its place", flow.resolverOpen)
        assertTrue(flow.queueActive)
        flow.childClosed()
        assertTrue(flow.resolverOpen)
    }

    @Test fun aScreenOpenedFromElsewhereAbandonsTheQueue() {
        val draft = TypedText("kefir")
        flow.afterSave(listOf("kefir"), MealSlot.LUNCH, draft, "kefir")
        flow.childOpened(fromResolver = false)
        assertEquals(emptyList<String>(), flow.parts)
        flow.childClosed()
        assertFalse(flow.resolverOpen)
        assertEquals("what was left stays where it was typed", "kefir", draft.text)
    }

    @Test fun aChildThatLoggedForTheQueueAdvancesItAndOneThatDidNotEndsTheContinuation() {
        val draft = TypedText("kefir, ayran")
        flow.afterSave(listOf("kefir", "ayran"), MealSlot.LUNCH, draft, "kefir, ayran")
        flow.childOpened(fromResolver = true)
        flow.childLogged(advancesQueue = true)
        assertEquals("ayran", draft.text)
        assertTrue(flow.resolverOpen)
        flow.childOpened(fromResolver = true)
        flow.childLogged(advancesQueue = false)
        assertFalse(flow.continuation)
        flow.childClosed()
        assertFalse("no return once it logged elsewhere", flow.resolverOpen)
    }

    /** Deliberate (B16): a queue abandoned while its pick was being written no longer empties the text it came from. */
    @Test fun aQueueAbandonedWhileItsPickIsWrittenLeavesTheTextAlone() {
        val draft = TypedText("kefir, ayran")
        flow.afterSave(listOf("kefir", "ayran"), MealSlot.LUNCH, draft, "kefir, ayran")
        val hold = CompletableDeferred<Unit>()
        store.hold = hold
        flow.resolve(food("kefir"))
        flow.close()
        hold.complete(Unit)
        assertEquals(listOf("log kefir 50.0 GRAM LUNCH 2026-10-28"), store.calls)
        assertEquals("kefir, ayran", draft.text)
        assertFalse(flow.resolverOpen)
        assertFalse(flow.continuation)
        // The same through a screen opened from the resolver.
        flow.afterSave(listOf("ayran"), MealSlot.LUNCH, draft, "kefir, ayran")
        flow.abandon()
        flow.childLogged(advancesQueue = true)
        assertEquals("ayran", draft.text)
    }

    @Test fun aQueueNoLongerContinuedIsNotResolvedOrSkipped() {
        val draft = TypedText("kefir, ayran")
        flow.afterSave(listOf("kefir", "ayran"), MealSlot.LUNCH, draft, "kefir, ayran")
        flow.childOpened(fromResolver = true)
        flow.childLogged(advancesQueue = false)
        assertEquals("parts are left", listOf("kefir", "ayran"), flow.parts)
        assertNull(flow.queueView())
        flow.resolve(food("kefir"))
        flow.skip()
        assertEquals(emptyList<String>(), store.calls)
        assertEquals(listOf("kefir", "ayran"), flow.parts)
        assertEquals("kefir, ayran", draft.text)
    }

    /** Deliberate (B16 review): Skip while a pick is written advanced the queue twice, dropping the next part unlogged. */
    @Test fun skipIsRefusedWhileAPickIsWritten() {
        val draft = TypedText("kefir, ayran, egg")
        flow.afterSave(listOf("kefir", "ayran", "egg"), MealSlot.LUNCH, draft, "kefir, ayran, egg")
        val hold = CompletableDeferred<Unit>()
        store.hold = hold
        flow.resolve(food("kefir"))
        flow.skip()
        assertEquals(listOf("kefir", "ayran", "egg"), flow.parts)
        hold.complete(Unit)
        assertEquals("only the pick moved the queue on", listOf("ayran", "egg"), flow.parts)
        assertEquals("ayran, egg", draft.text)
        flow.skip()
        assertEquals(listOf("egg"), flow.parts)
    }

    @Test fun aPickNeedsAQueueBeingResolved() {
        flow.resolve(ayran)
        flow.afterSave(listOf("ayran"), MealSlot.LUNCH, TypedText(), "")
        flow.childOpened(fromResolver = false)
        flow.resolve(ayran)
        assertEquals(emptyList<String>(), store.calls)
    }

    @Test fun theResolverSearchShowsOnlyTheResultsOfTheQueryStillTyped() {
        store.found["kef"] = listOf(ranked("kefir"))
        store.found["kefir"] = listOf(ranked("kefir"), ranked("kefir-light"))
        flow.onQuery("kef")
        flow.onQuery("kefir")
        compute.runAll()
        assertEquals(listOf("kefir", "kefir-light"), flow.results.map { it.food.id })
        flow.onQuery("")
        assertEquals(emptyList<RankedFood>(), flow.results)
    }

    @Test fun anAdvanceDropsASearchStillRunningForThePartBefore() {
        store.found["kefir"] = listOf(ranked("kefir"))
        flow.afterSave(listOf("kefir", "ayran"), MealSlot.LUNCH, TypedText(), "")
        flow.beginSearch()
        assertTrue(flow.searchOpen)
        assertEquals("kefir", flow.query)
        flow.skip()
        compute.runAll()
        assertEquals(emptyList<RankedFood>(), flow.results)
        assertEquals("", flow.query)
        assertFalse(flow.searchOpen)
    }

    @Test fun finishResolveReturnsToTheLogOnceWithThePick() {
        flow.beginPart(request, MealSlot.LUNCH, "ayran")
        assertEquals(listOf("library LUNCH \"ayran\""), events)
        assertTrue(flow.resolvingPart)
        flow.finishPart(ayran)
        assertFalse(flow.resolvingPart)
        assertEquals("log ayran200ml pick=ayran", events.last())
        flow.finishPart(ayran)
        assertFalse(flow.cancelPart())
        assertEquals("nothing more without a part", 2, events.size)
    }

    @Test fun leavingTheLibraryReturnsToTheLogAsItWas() {
        flow.beginPart(request, MealSlot.LUNCH, "ayran")
        assertTrue(flow.cancelPart())
        assertEquals("log ayran200ml pick=null", events.last())
        assertFalse(flow.resolvingPart)
    }

    /** B15: a New food made for a Log part is that part's pick; a food edited meanwhile is not. */
    @Test fun aNewFoodSavedWhileResolvingAPartIsItsPick() {
        flow.beginPart(request, MealSlot.LUNCH, "ayran")
        flow.foodSaved(food("edited"), newFood = false, advancesQueue = false)
        assertTrue(flow.resolvingPart)
        flow.foodSaved(ayran, newFood = true, advancesQueue = false)
        assertEquals("log ayran200ml pick=ayran", events.last())
        assertFalse(flow.resolvingPart)
    }

    @Test fun aSaveFailureKeepsThePartAndSaysSo() {
        store.hold = CompletableDeferred<Unit>().apply { completeExceptionally(IllegalStateException("disk full")) }
        val draft = TypedText("kefir")
        flow.afterSave(listOf("kefir"), MealSlot.LUNCH, draft, "kefir")
        flow.resolve(food("kefir"))
        assertEquals("disk full", notices.message)
        assertEquals(listOf("kefir"), flow.parts)
        assertEquals("kefir", draft.text)
    }

    @Test fun theSearchRunsOnTheComputeDispatcher() {
        // Nothing ranks until the compute dispatcher runs, so typing never waits on the matcher.
        store.found["kefir"] = listOf(ranked("kefir"))
        flow.onQuery("kefir")
        assertEquals(emptyList<RankedFood>(), flow.results)
        assertEquals(1, compute.pending)
        compute.runAll()
        assertEquals(1, flow.results.size)
    }
}
