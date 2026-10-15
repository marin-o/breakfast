package com.iron.breakfast

import com.iron.breakfast.domain.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewQueueLogicTest {
    @Test
    fun activeResolveQueueForcesItsMealAndAdvances() {
        val plan = reviewLogPlan(resolutionContinuation = true, queueNotEmpty = true, queueTarget = MealSlot.LUNCH, chosen = MealSlot.DINNER)
        assertEquals(ReviewLogPlan(MealSlot.LUNCH, true), plan)
    }

    @Test
    fun dailyResolveQueueUsesItsMealWhenNoMealIsSelected() {
        assertEquals(
            ReviewLogPlan(MealSlot.BREAKFAST, true),
            resolverLogPlan(true, queueNotEmpty = true, queueTarget = MealSlot.BREAKFAST, currentMeal = null),
        )
        assertEquals(
            MealSlot.BREAKFAST,
            activeResolutionTarget(true, listOf("1 unknown"), MealSlot.BREAKFAST),
        )
    }

    @Test
    fun dailyCreateInResolveQueueUsesQueueMealAndAdvances() {
        val plan = resolverLogPlan(true, queueNotEmpty = true, queueTarget = MealSlot.LUNCH, currentMeal = null)
        assertEquals(ReviewLogPlan(MealSlot.LUNCH, true), plan)
        assertTrue(plan!!.advancesQueue)
    }

    @Test
    fun outsideAResolveQueueTheChosenMealIsUsedAndNothingAdvances() {
        assertEquals(ReviewLogPlan(MealSlot.DINNER, false), reviewLogPlan(false, true, MealSlot.LUNCH, MealSlot.DINNER))
        assertEquals(ReviewLogPlan(MealSlot.DINNER, false), reviewLogPlan(true, false, MealSlot.LUNCH, MealSlot.DINNER))
        assertEquals(ReviewLogPlan(MealSlot.DINNER, false), reviewLogPlan(true, true, null, MealSlot.DINNER))
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
        assertFalse(canSaveProductToLibrary(null, resolutionContinuation = true, queueNotEmpty = true))
        assertTrue(canSaveProductToLibrary(null, resolutionContinuation = false, queueNotEmpty = false))
        assertFalse(canSaveProductToLibrary(MealSlot.DINNER, resolutionContinuation = false, queueNotEmpty = false))
    }

    @Test
    fun staleOffResponsesRequireCurrentOpenQueryAndGeneration() {
        assertTrue(shouldApplyOffSearchResult(3, 3, "oats", "oats", true))
        assertFalse(shouldApplyOffSearchResult(2, 3, "oats", "oats", true))
        assertFalse(shouldApplyOffSearchResult(3, 3, "oats", "milk", true))
        assertFalse(shouldApplyOffSearchResult(3, 3, "oats", "oats", false))
    }

    @Test
    fun staleLibraryResponsesRequireCurrentOpenQueryGenerationAndArchiveMode() {
        assertTrue(shouldApplyLibraryRefresh(4, 4, "oats", "oats", false, false, true))
        assertFalse(shouldApplyLibraryRefresh(3, 4, "oats", "oats", false, false, true))
        assertFalse(shouldApplyLibraryRefresh(4, 4, "oats", "milk", false, false, true))
        assertFalse(shouldApplyLibraryRefresh(4, 4, "oats", "oats", false, true, true))
        assertFalse(shouldApplyLibraryRefresh(4, 4, "oats", "oats", false, false, false))
    }

    @Test
    fun scannerFailureKeepsManualRouteOpen() {
        assertEquals(ScannerFailureTransition(true, true), scannerUnavailableTransition())
    }
}
