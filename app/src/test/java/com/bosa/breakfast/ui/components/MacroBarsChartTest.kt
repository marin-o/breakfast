package com.bosa.breakfast.ui.components

import com.bosa.breakfast.domain.DayBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroBarsChartTest {
    private fun logged(day: String, protein: Double, carbs: Double, fat: Double, unknown: Double, target: Double? = null) =
        DayBar(day, true, protein + carbs + fat + unknown, protein, carbs, fat, unknown, target)
    private fun empty(day: String, target: Double? = null) = DayBar(day, false, 0.0, 0.0, 0.0, 0.0, 0.0, target)

    @Test fun aLoggedDayDrawsItsSegmentsAndNoSlot() {
        val data = macroBarsData(listOf(logged("2026-10-26", 20.0, 0.0, 0.0, 480.0)), showGoal = true)
        assertEquals(listOf(20.0), data.protein)
        assertEquals(listOf(480.0), data.unknown)
        assertEquals(listOf(0.0), data.slot)
    }

    @Test fun anUnloggedDayIsAnEmptySlot() {
        val data = macroBarsData(listOf(logged("2026-10-26", 400.0, 800.0, 800.0, 0.0), empty("2026-10-27")), showGoal = true)
        assertEquals(0.0, data.protein[1])
        assertEquals(data.axis.top * 0.2, data.slot[1])
    }

    @Test fun theAxisHoldsTheTallestBarAndTheHighestGoal() {
        val bars = macroBarsData(listOf(logged("2026-10-26", 500.0, 1000.0, 1300.0, 0.0, target = 2300.0)), showGoal = true)
        assertEquals(KcalAxis(3000.0, 1000.0), bars.axis)
        val goal = macroBarsData(listOf(logged("2026-10-26", 100.0, 100.0, 100.0, 0.0, target = 3500.0)), showGoal = true)
        assertTrue(goal.axis.top >= 3500.0, "${goal.axis}")
    }

    @Test fun aWeekWithNothingStillHasAnAxis() {
        assertEquals(KcalAxis(2000.0, 500.0), macroBarsData(List(7) { empty("2026-10-2$it") }, showGoal = false).axis)
    }

    @Test fun eachDayHasItsOwnGoalAndTrackingOnlyHasNone() {
        val days = listOf(logged("2026-10-26", 1.0, 1.0, 1.0, 0.0, target = 2000.0), empty("2026-10-27", target = 1900.0), empty("2026-10-28"))
        assertEquals(listOf(2000.0, 1900.0, null), macroBarsData(days, showGoal = true).targets)
        assertEquals(listOf(null, null, null), macroBarsData(days, showGoal = false).targets)
    }

    @Test fun axisStepsStayFew() {
        for (kcal in listOf(50.0, 399.0, 1200.0, 2300.0, 7999.0, 20000.0)) {
            val axis = kcalAxis(listOf(kcal))
            assertTrue(axis.top >= kcal, "$axis")
            assertTrue(axis.top / axis.step <= 5, "$axis")
        }
    }

    @Test fun aLargerFontLabelsEveryOtherDay() {
        assertEquals(1, dayLabelsEvery(1f))
        assertEquals(1, dayLabelsEvery(1.3f))
        assertEquals(2, dayLabelsEvery(2f))
    }
}
