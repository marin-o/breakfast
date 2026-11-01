package com.iron.breakfast.today

import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.ReplacementResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchFeedbackTest {
    private val yogurt = FoodRecord("yogurt", "Yogurt", emptyList(), "dairy", FoodOrigin.CUSTOM, NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(60.0, 4.0, 5.0, 3.0)))

    private class FakeStore(private val alternative: FoodRecord) : MatchStore {
        val reinforced = mutableListOf<String>()
        val replaced = mutableListOf<Pair<String, String>>()
        var replacement: (MealEntry?) -> ReplacementResult = { ReplacementResult.Success(it ?: entry("x")) }
        var reinforceFailure: Exception? = null
        override fun alternativesFor(entry: MealEntry) = listOf(RankedFood(alternative, 1.0, emptyList()))
        override suspend fun replaceEntry(entryId: String, foodId: String): ReplacementResult {
            replaced += entryId to foodId
            return replacement(null)
        }
        override suspend fun replaceEntryWith(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, servingName: String?, meal: com.iron.breakfast.domain.MealSlot, dayKey: String): ReplacementResult =
            replaceEntry(entryId, foodId)
        override suspend fun reinforce(foodId: String) { reinforceFailure?.let { throw it }; reinforced += foodId }
    }

    private val diary = OpenDiary()
    private val store = FakeStore(yogurt)
    private val notices = Notices()
    private val time = FakeTime()
    private var resolverOpen = false
    private val feedback = MatchFeedback(diary.session, store, notices, { resolverOpen }, time.clock, diary.scope, time.sleep)
        .also { diary.session.onChange(it::onChange) }

    private val approximate = entry("approx", approximate = true)
    private val exact = entry("exact")

    private fun logBatch() {
        diary.commit(DiaryChange.BatchLogged("2 foods added to Lunch", listOf(approximate, exact)))
        diary.entryReads = 0
    }

    private fun tick(times: Int = 1) = repeat(times) { time.advance(MatchFeedback.TICK_MS) }

    @Test fun aBatchOpensFeedbackForItsApproximateEntriesOnly() {
        logBatch()
        assertTrue(feedback.isOpen(approximate))
        assertFalse(feedback.isOpen(exact))
        assertEquals(listOf(yogurt), feedback.alternatives(approximate).map { it.food })
        assertTrue(feedback.alternatives(exact).isEmpty())
    }

    @Test fun aBatchWithoutApproximateEntriesRunsNoTimer() {
        diary.commit(DiaryChange.BatchLogged("1 food added to Lunch", listOf(exact)))
        assertTrue(feedback.ids.isEmpty())
        assertFalse(time.sleeping)
    }

    @Test fun feedbackExpiresAtTheEndOfItsWindow() {
        logBatch()
        tick(29)
        assertTrue(feedback.isOpen(approximate))
        tick()
        assertFalse(feedback.isOpen(approximate))
        assertTrue(feedback.ids.isEmpty())
        assertFalse(time.sleeping)
        assertTrue(feedback.alternatives(approximate).isEmpty())
    }

    @Test fun feedbackIsHeldWhileTheResolverIsOpen() {
        logBatch()
        tick(10)
        resolverOpen = true
        tick(120)
        assertTrue(feedback.isOpen(approximate))
        resolverOpen = false
        // The 20 seconds that were left when the resolver opened.
        tick(19)
        assertTrue(feedback.isOpen(approximate))
        tick()
        assertFalse(feedback.isOpen(approximate))
    }

    @Test fun aNewBatchStartsAFreshWindow() {
        logBatch()
        tick(20)
        val next = entry("next", approximate = true)
        diary.commit(DiaryChange.BatchLogged("1 food added to Lunch", listOf(next)))
        assertFalse(feedback.isOpen(approximate))
        tick(29)
        assertTrue(feedback.isOpen(next))
        tick()
        assertFalse(feedback.isOpen(next))
    }

    @Test fun confirmLearnsTheFoodAndClosesTheStripWithoutAReload() {
        logBatch()
        feedback.confirm(approximate)
        assertEquals(listOf(approximate.foodId), store.reinforced)
        assertFalse(feedback.isOpen(approximate))
        assertEquals(DiaryChange.MatchConfirmed, diary.published.last())
        assertEquals(0, diary.entryReads)
    }

    @Test fun nothingIsConfirmedOrPickedOnceFeedbackEnded() {
        logBatch()
        tick(30)
        feedback.confirm(approximate)
        feedback.chooseAlternative(approximate, yogurt)
        assertTrue(store.reinforced.isEmpty())
        assertTrue(store.replaced.isEmpty())
    }

    @Test fun aPickReplacesTheEntryLearnsItClosesTheStripAndKeepsTheBatchUndo() {
        // B4 owner decision: a "Not right?" pick keeps the batch Undo.
        val undoTime = FakeTime()
        val undo = UndoController(diary.session, NoUndoStore, notices, { 15_000L }, undoTime.clock, diary.scope, announce = {}, sleep = undoTime.sleep)
        diary.session.onChange(undo::onChange)
        logBatch()
        val offer = undo.offer
        feedback.chooseAlternative(approximate, yogurt)
        assertEquals(listOf(approximate.id to yogurt.id), store.replaced)
        assertEquals(listOf(yogurt.id), store.reinforced)
        assertFalse(feedback.isOpen(approximate))
        assertEquals(DiaryChange.MatchCorrected, diary.published.last())
        assertEquals(1, diary.entryReads)
        assertTrue(offer != null && offer === undo.offer)
    }

    @Test fun aRefusedPickSaysWhyAndChangesNothing() {
        logBatch()
        store.replacement = { ReplacementResult.Failure("That food has no gram weight.") }
        feedback.chooseAlternative(approximate, yogurt)
        assertEquals("That food has no gram weight.", notices.message)
        assertTrue(store.reinforced.isEmpty())
        assertTrue(feedback.isOpen(approximate))
        assertEquals(0, diary.entryReads)
    }

    @Test fun aPickWhoseLearningFailedIsStillReloadedAndLeavesTheStripSilently() {
        // B11 review: the entry was replaced, so the diary must show it, though learning the pick threw.
        logBatch()
        store.reinforceFailure = IllegalStateException("prefs")
        feedback.chooseAlternative(approximate, yogurt)
        assertEquals(DiaryChange.MatchCorrected, diary.published.last())
        assertEquals(1, diary.entryReads)
        assertTrue(feedback.isOpen(approximate))
        assertNull(notices.message)
        assertFalse(diary.session.busy)
    }

    @Test fun replacingTheDiaryEndsFeedback() {
        logBatch()
        diary.commit(DiaryChange.DiaryReplaced)
        assertFalse(feedback.isOpen(approximate))
        assertFalse(time.sleeping)
    }

    @Test fun otherWritesLeaveFeedbackAlone() {
        logBatch()
        diary.commit(DiaryChange.FoodsLogged)
        diary.commit(DiaryChange.EntryEdited)
        assertTrue(feedback.isOpen(approximate))
    }

    private object NoUndoStore : UndoStore {
        override suspend fun deleteEntry(id: String) {}
        override suspend fun restoreEntry(entry: MealEntry) {}
        override suspend fun deleteEntries(entries: List<MealEntry>) {}
    }
}
