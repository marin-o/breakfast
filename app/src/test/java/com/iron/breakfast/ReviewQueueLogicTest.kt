package com.iron.breakfast

import com.iron.breakfast.domain.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [queueLogPlan] is the one rule product review's Add and the food form's Save follow. */
class ReviewQueueLogicTest {
    @Test
    fun activeResolveQueueForcesItsMealAndAdvances() {
        val plan = queueLogPlan(queueActive = true, queueMeal = MealSlot.LUNCH, meal = MealSlot.DINNER)
        assertEquals(ReviewLogPlan(MealSlot.LUNCH, true), plan)
    }

    @Test
    fun dailyResolveQueueUsesItsMealWhenNoMealIsSelected() {
        val plan = queueLogPlan(queueActive = true, queueMeal = MealSlot.BREAKFAST, meal = null)
        assertEquals(ReviewLogPlan(MealSlot.BREAKFAST, true), plan)
        assertTrue(plan!!.advancesQueue)
    }

    @Test
    fun outsideAResolveQueueTheChosenMealIsUsedAndNothingAdvances() {
        assertEquals(ReviewLogPlan(MealSlot.DINNER, false), queueLogPlan(false, MealSlot.LUNCH, MealSlot.DINNER))
        assertEquals("no meal chosen or open: nowhere to log", null, queueLogPlan(false, null, null))
    }

    @Test
    fun anActiveQueueWithoutAMealLogsNowhere() {
        // Not a state the app reaches (ResolveFlowTest.anActiveQueueAlwaysHasItsMeal); both screens refuse alike.
        assertEquals(null, queueLogPlan(true, null, MealSlot.LUNCH))
        assertEquals(null, queueLogPlan(true, null, null))
    }

    @Test
    fun noMatchShowsOnlyForAFinishedEmptySearchWithNothingOverIt() {
        assertTrue(isLibraryNoMatch(true, true, "barley", "barley", true, false))
        assertFalse(isLibraryNoMatch(false, true, "barley", "barley", true, false))
        assertFalse(isLibraryNoMatch(true, false, "barley", "barley", true, false))
        assertFalse(isLibraryNoMatch(true, true, "  ", "  ", true, false))
        assertFalse("results still for an older query", isLibraryNoMatch(true, true, "barley", "bar", true, false))
        assertFalse(isLibraryNoMatch(true, true, "barley", "barley", false, false))
        assertFalse("another screen is on top", isLibraryNoMatch(true, true, "barley", "barley", true, true))
    }

    @Test
    fun resolvePositionCountsFromOneAndNeverReadsBelowWhatIsLeft() {
        assertEquals(1 to 3, resolvePosition(3, 3))
        assertEquals(2 to 3, resolvePosition(3, 2))
        assertEquals(3 to 3, resolvePosition(3, 1))
        assertEquals(1 to 2, resolvePosition(1, 2))
        assertEquals(1 to 1, resolvePosition(0, 1))
    }

    @Test
    fun librarySaveIsHiddenDuringResolveQueue() {
        assertFalse(canSaveProductToLibrary(null, queueActive = true))
        assertTrue(canSaveProductToLibrary(null, queueActive = false))
        assertFalse(canSaveProductToLibrary(MealSlot.DINNER, queueActive = false))
    }

    @Test
    fun staleOffResponsesRequireCurrentOpenQueryAndGeneration() {
        assertTrue(shouldApplyOffSearchResult(3, 3, "oats", "oats", true))
        assertFalse(shouldApplyOffSearchResult(2, 3, "oats", "oats", true))
        assertFalse(shouldApplyOffSearchResult(3, 3, "oats", "milk", true))
        assertFalse(shouldApplyOffSearchResult(3, 3, "oats", "oats", false))
    }

    @Test
    fun staleLibraryResponsesRequireCurrentOpenQueryAndGeneration() {
        assertTrue(shouldApplyLibraryRefresh(4, 4, "oats", "oats", true))
        assertFalse(shouldApplyLibraryRefresh(3, 4, "oats", "oats", true))
        assertFalse(shouldApplyLibraryRefresh(4, 4, "oats", "milk", true))
        assertFalse(shouldApplyLibraryRefresh(4, 4, "oats", "oats", false))
    }

    @Test
    fun scannerFailureKeepsManualRouteOpen() {
        assertEquals(ScannerFailureTransition(true, true), scannerUnavailableTransition())
    }
}
