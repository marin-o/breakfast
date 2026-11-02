package com.bosa.breakfast.ui.components

import com.bosa.breakfast.domain.CurvePoint
import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.WeightSeries
import com.bosa.breakfast.domain.curveMinute
import com.bosa.breakfast.domain.oldestFirst
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightChartTest {
    private fun point(day: String, kg: Double) = WeightPoint(day, kg, 0L)
    private val utc = ZoneOffset.UTC
    private fun at(day: String, time: String, kg: Double = 80.0, zone: ZoneId = utc) =
        WeightPoint(day, kg, LocalDateTime.parse("${day}T$time").atZone(zone).toInstant().toEpochMilli())

    /** The dots of [points], as the page's chart gets them from [com.bosa.breakfast.domain.weightSeries]. */
    private fun weightChartData(points: List<WeightPoint>, target: Double?, zone: ZoneId = ZoneId.systemDefault()) = weightChartData(
        WeightSeries(oldestFirst(points).mapNotNull { point -> curveMinute(point, zone)?.let { CurvePoint(it, point.kg) } }.sortedBy { it.minute }, emptyList()),
        target,
    )

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
        assertEquals(listOf(0.0, 1440.0, 15840.0), data.xs.map { it - data.xs.first() })
        assertEquals(listOf(81.0, 80.0, 79.0), data.ys)
        assertEquals(data.xs.first(), data.minX)
        assertEquals(data.xs.last(), data.maxX)
    }

    @Test fun onePointSitsInTheMiddleOfItsOwnDate() {
        val data = assertNotNull(weightChartData(listOf(point("2026-10-12", 80.0)), null))
        assertEquals(data.xs.single() - 1440, data.minX)
        assertEquals(data.xs.single() + 1440, data.maxX)
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
        assertTrue(ticks.all { it % 1440.0 == 0.0 })
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

    @Test fun onePointSitsOnItsDateWhateverItsTime() {
        val data = assertNotNull(weightChartData(listOf(at("2026-10-12", "23:30:00")), null, utc))
        assertEquals("2026-10-12", curveDate(data.xs.single()).toString())
        assertEquals(data.xs, dateTicks(data, maxLabels = 5))
    }

    @Test fun twoPointsOnADayAreApartByTheirRealTime() {
        val data = assertNotNull(weightChartData(listOf(at("2026-10-12", "19:00:00", 79.0), at("2026-10-12", "07:00:00", 80.0)), null, utc))
        assertEquals(listOf(80.0, 79.0), data.ys)
        assertEquals(720.0, data.xs[1] - data.xs[0])
        assertEquals(listOf(7.0 / 24, 19.0 / 24), data.xs.map { (it - data.minX) / (data.maxX - data.minX) })
        assertEquals(listOf(data.minX), dateTicks(data, maxLabels = 5))
        assertEquals("2026-10-12", curveDate(data.minX).toString())
    }

    /** The chart's x step is 1, and it draws every point at x over step, so x must be whole and no two alike. */
    @Test fun everyXIsAWholeMinuteAndNoTwoAreAlike() {
        val points = listOf(
            at("2026-10-12", "07:52:31", 80.0), at("2026-10-12", "07:52:59", 79.0), at("2026-10-12", "07:52:59", 78.0),
            point("2026-10-13", 77.0), point("2026-10-13", 76.0),
        )
        val data = assertNotNull(weightChartData(points, null, utc))
        assertTrue(data.xs.all { it % 1.0 == 0.0 }, "${data.xs}")
        assertEquals(data.xs.sorted().distinct(), data.xs)
        assertEquals(5, data.xs.size)
        assertEquals(listOf(80.0, 79.0, 78.0, 77.0, 76.0), data.ys)
    }

    @Test fun theSameHourOnTwoDaysIsADayApartAcrossAClockChange() {
        val skopje = ZoneId.of("Europe/Skopje")
        val data = assertNotNull(weightChartData(listOf(at("2026-10-24", "07:00:00", zone = skopje), at("2026-10-25", "07:00:00", zone = skopje)), null, skopje))
        assertEquals(1440.0, data.xs[1] - data.xs[0])
    }

    @Test fun theDaysLabelledAreWholeDaysFromTheFirstPointsDayToTheLast() {
        val data = assertNotNull(weightChartData(listOf(at("2026-10-12", "07:00:00"), at("2026-10-14", "20:00:00")), null, utc))
        assertEquals(listOf("2026-10-12", "2026-10-13", "2026-10-14"), dateTicks(data, maxLabels = 5).map { curveDate(it).toString() })
        assertTrue(data.minX <= data.xs.first() && data.xs.last() <= data.maxX)
        assertEquals(data.xs.last(), data.maxX)
    }

    @Test fun labelsNeverCrowdWhateverTheSpanAndWidth() {
        val first = LocalDate.parse("2026-01-01")
        for (days in 1L..400L) for (hour in listOf("00:00:00", "07:00:00", "23:59:00")) {
            val data = assertNotNull(weightChartData(listOf(at(first.toString(), hour), at(first.plusDays(days).toString(), hour)), null, utc))
            for (across in listOf(1f, 2f, 2.5f, 3f, 4f, 5f, 6f, 7.5f)) {
                val ticks = dateTicksThatFit(data, across)
                assertTrue(ticks.isNotEmpty() && ticks.size <= maxOf(1, across.toInt()))
                val width = data.maxX - data.minX
                ticks.zipWithNext().forEach { (a, b) -> assertTrue((b - a) / width * across >= 1f, "$days $hour $across $ticks") }
                assertEquals(data.xs.last() - data.xs.last().mod(1440.0), ticks.last())
                assertEquals(ticks.sorted().distinct(), ticks)
            }
        }
    }

    @Test fun theTrendIsDrawnOverTheDotsAndItsLinesAreWholeMinutes() {
        val series = WeightSeries(
            listOf(CurvePoint(14400.4, 80.0), CurvePoint(14400.6, 79.0), CurvePoint(30000.0, 78.0)),
            listOf(listOf(CurvePoint(14400.4, 80.0), CurvePoint(14400.6, 79.9)), listOf(CurvePoint(30000.0, 78.0))),
        )
        val data = assertNotNull(weightChartData(series, 70.0))
        assertEquals(listOf(14400.0, 14401.0, 30000.0), data.xs)
        assertEquals(2, data.trend.size)
        assertEquals(listOf(14400.0, 14401.0), data.trend[0].xs)
        assertAxisHolds(data.axis, 80.0, 78.0, 70.0)
    }

    @Test fun aTrendOutsideTheDotsWidensTheAxis() {
        val series = WeightSeries(listOf(CurvePoint(0.0, 80.0), CurvePoint(1440.0, 80.0)), listOf(listOf(CurvePoint(0.0, 84.5), CurvePoint(1440.0, 84.0))))
        assertAxisHolds(assertNotNull(weightChartData(series, null)).axis, 80.0, 84.5)
    }

    @Test fun monthTicksAreFirstsOfMonthsThinnedToTheRoom() {
        val minute = { day: String -> LocalDate.parse(day).toEpochDay() * 1440.0 }
        val all = monthTicks(minute("2026-08-10"), minute("2026-11-02"), 7f)
        assertEquals(listOf("2026-09-01", "2026-10-01", "2026-11-01"), all.map { curveDate(it).toString() })
        val thin = monthTicks(minute("2025-01-01"), minute("2026-12-31"), 4f)
        assertTrue(thin.size <= 4, "$thin")
        assertEquals("2025-01-01", curveDate(thin.first()).toString())
    }

    @Test fun aMonthLabelIsItsShortName() {
        assertEquals("Aug", com.bosa.breakfast.ui.monthLabel(LocalDate.parse("2026-08-01")))
        assertEquals("Aug 26", com.bosa.breakfast.ui.monthLabel(LocalDate.parse("2026-08-01"), withYear = true))
    }

    @Test fun aDateLabelIsDayAndShortMonth() {
        val x = assertNotNull(weightChartData(listOf(point("2026-10-12", 80.0)), null)).xs.single()
        val date = curveDate(x)
        assertEquals("12 Oct", com.bosa.breakfast.ui.dayMonthLabel(date))
        assertEquals("12 Oct 26", com.bosa.breakfast.ui.dayMonthLabel(date, withYear = true))
    }
}
