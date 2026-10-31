package com.iron.breakfast.ui.components

import com.iron.breakfast.domain.WeightPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightChartTest {
    private fun point(day: String, kg: Double) = WeightPoint(day, kg, 0L)

    private fun assertAxisHolds(axis: KgAxis, vararg values: Double) {
        values.forEach { assertTrue(it > axis.min && it < axis.max, "$it in $axis") }
        assertEquals(0.0, axis.min % axis.step, "$axis")
        assertEquals(0.0, axis.max % axis.step, "$axis")
        assertTrue(axis.max > axis.min, "$axis")
        assertTrue((axis.max - axis.min) / axis.step <= 6, "$axis")
    }

    @Test fun noPointsHaveNoChart() {
        assertNull(weightChartData(emptyList(), 75.0))
    }

    @Test fun pointsOnKeysThatAreNoDatesAreLeftOut() {
        assertNull(weightChartData(listOf(point("not a day", 80.0)), null))
        assertEquals(1, weightChartData(listOf(point("not a day", 80.0), point("2026-10-12", 80.0)), null)?.xs?.size)
    }

    @Test fun pointsAreOldestFirstAndSpacedByRealTime() {
        val data = assertNotNull(weightChartData(listOf(point("2026-10-12", 79.0), point("2026-10-01", 81.0), point("2026-10-02", 80.0)), null))
        assertEquals(listOf(0.0, 1.0, 11.0), data.xs.map { it - data.xs.first() })
        assertEquals(listOf(81.0, 80.0, 79.0), data.ys)
        assertEquals(data.xs.first(), data.minX)
        assertEquals(data.xs.last(), data.maxX)
    }

    @Test fun onePointSitsInTheMiddleOfItsOwnDate() {
        val data = assertNotNull(weightChartData(listOf(point("2026-10-12", 80.0)), null))
        assertEquals(data.xs.single() - 1, data.minX)
        assertEquals(data.xs.single() + 1, data.maxX)
        assertEquals(listOf(data.xs.single()), dateTicks(data, maxLabels = 5))
    }

    @Test fun equalValuesGetARange() {
        val axis = kgAxis(listOf(80.0, 80.0))
        assertEquals(KgAxis(79.0, 81.0, 1.0), axis)
    }

    @Test fun theAxisIncludesATargetOutsideThePoints() {
        val data = assertNotNull(weightChartData(listOf(point("2026-10-12", 80.0), point("2026-10-13", 90.0)), 70.0))
        assertAxisHolds(data.axis, 80.0, 90.0, 70.0)
    }

    @Test fun ticksAreRoundWhateverTheValues() {
        assertAxisHolds(kgAxis(listOf(82.3, 82.7)), 82.3, 82.7)
        assertAxisHolds(kgAxis(listOf(60.0, 140.0)), 60.0, 140.0)
        assertAxisHolds(kgAxis(listOf(495.0, 500.0)), 495.0, 500.0)
        assertAxisHolds(kgAxis(listOf(20.0, 500.0)), 20.0, 500.0)
        assertEquals(1.0, kgAxis(listOf(82.3, 82.7)).step)
    }

    @Test fun theAxisNeverGoesBelowZero() {
        assertEquals(0.0, kgAxis(listOf(1.0, 500.0)).min)
        assertEquals(0.0, kgAxis(listOf(1.0)).min)
    }

    @Test fun dateTicksIncludeBothEndsAreWholeDaysAndNeverExceedTheRoom() {
        val data = assertNotNull(weightChartData(listOf(point("2026-01-01", 80.0), point("2026-12-31", 80.0)), null))
        val ticks = dateTicks(data, maxLabels = 4)
        assertEquals(4, ticks.size)
        assertEquals(data.minX, ticks.first())
        assertEquals(data.maxX, ticks.last())
        assertTrue(ticks.all { it % 1.0 == 0.0 })
        assertEquals(ticks, ticks.sorted().distinct())
    }

    @Test fun dateTicksNeverRepeatADayOrAskForMoreThanTheDaysThereAre() {
        val data = assertNotNull(weightChartData(listOf(point("2026-10-12", 80.0), point("2026-10-13", 80.0)), null))
        assertEquals(listOf(data.minX, data.maxX), dateTicks(data, maxLabels = 5))
    }

    @Test fun roomForOneLabelShowsTheNewestDay() {
        val data = assertNotNull(weightChartData(listOf(point("2026-10-12", 80.0), point("2026-10-20", 80.0)), null))
        assertEquals(listOf(data.maxX), dateTicks(data, maxLabels = 1))
        assertEquals(listOf(data.maxX), dateTicks(data, maxLabels = 0))
    }

    @Test fun aDateLabelIsDayAndShortMonth() {
        val x = assertNotNull(weightChartData(listOf(point("2026-10-12", 80.0)), null)).xs.single()
        val date = java.time.LocalDate.ofEpochDay(x.toLong())
        assertEquals("12 Oct", com.iron.breakfast.ui.dayMonthLabel(date))
        assertEquals("12 Oct 26", com.iron.breakfast.ui.dayMonthLabel(date, withYear = true))
    }
}
