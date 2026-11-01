package com.bosa.breakfast.resolve

import com.bosa.breakfast.domain.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [queueLogPlan], the one rule product review's Add and the food form's Save follow, and [resolvePosition]. */
class ResolveRulesTest {
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
    fun resolvePositionCountsFromOneAndNeverReadsBelowWhatIsLeft() {
        assertEquals(1 to 3, resolvePosition(3, 3))
        assertEquals(2 to 3, resolvePosition(3, 2))
        assertEquals(3 to 3, resolvePosition(3, 1))
        assertEquals(1 to 2, resolvePosition(1, 2))
        assertEquals(1 to 1, resolvePosition(0, 1))
    }
}
