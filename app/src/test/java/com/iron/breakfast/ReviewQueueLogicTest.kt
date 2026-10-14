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
}
