package mk.bosa.breakfast.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// What the Weight page's chart draws, with x as [curveMinute] gives it.

enum class WeightRange { ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR, ALL }

/** 3M, or All with under 3 months of points. */
fun defaultWeightRange(points: List<WeightPoint>, today: String): WeightRange {
    val day = dayOf(today) ?: return WeightRange.ALL
    val oldest = points.mapNotNull { dayOf(it.dayKey) }.minOrNull() ?: return WeightRange.ALL
    return if (oldest.isAfter(day.minusMonths(3))) WeightRange.ALL else WeightRange.THREE_MONTHS
}

data class CurvePoint(val minute: Double, val kg: Double)

/** Faint [dots], and the bold [trend] as lines that break over gaps. 1Y and All hold weekly averages of both. */
data class WeightSeries(val dots: List<CurvePoint>, val trend: List<List<CurvePoint>>)

private const val TREND_ALPHA = 0.1
private const val LONGEST_GAP_DAYS = 14L

private class DayPoint(val day: LocalDate, val point: CurvePoint)

internal fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Exponential smoothing from the first point on, starting afresh after a gap of more than 14 days. */
private fun trendLines(points: List<DayPoint>): List<List<DayPoint>> {
    val lines = mutableListOf<MutableList<DayPoint>>()
    var previous: DayPoint? = null
    for (point in points) {
        val last = previous
        val kg = if (last == null || ChronoUnit.DAYS.between(last.day, point.day) > LONGEST_GAP_DAYS) {
            lines += mutableListOf<DayPoint>()
            point.point.kg
        } else {
            TREND_ALPHA * point.point.kg + (1 - TREND_ALPHA) * last.point.kg
        }
        val smoothed = DayPoint(point.day, CurvePoint(point.point.minute, kg))
        lines.last() += smoothed
        previous = smoothed
    }
    return lines
}

private fun weeklyAverages(points: List<DayPoint>): List<DayPoint> =
    points.groupBy { mondayOf(it.day) }.values.map { week ->
        DayPoint(week.first().day, CurvePoint(week.map { it.point.minute }.average(), week.map { it.point.kg }.average()))
    }

/** The chart for [range] on [today]. The trend is smoothed over the whole curve, so a range never starts it afresh. */
fun weightSeries(points: List<WeightPoint>, range: WeightRange, today: String, zone: ZoneId = ZoneId.systemDefault()): WeightSeries {
    val all = oldestFirst(points).mapNotNull { point ->
        val day = dayOf(point.dayKey) ?: return@mapNotNull null
        DayPoint(day, CurvePoint(curveMinute(point, zone) ?: return@mapNotNull null, point.kg))
    }
    val now = dayOf(today)
    val from = when (range) {
        WeightRange.ONE_MONTH -> now?.minusMonths(1)
        WeightRange.THREE_MONTHS -> now?.minusMonths(3)
        WeightRange.SIX_MONTHS -> now?.minusMonths(6)
        WeightRange.ONE_YEAR -> now?.minusYears(1)
        WeightRange.ALL -> null
    }
    val shown = { list: List<DayPoint> -> if (from == null) list else list.filter { !it.day.isBefore(from) } }
    val weekly = range == WeightRange.ONE_YEAR || range == WeightRange.ALL
    val drawn = { list: List<DayPoint> -> (if (weekly) weeklyAverages(list) else list).map { it.point } }
    return WeightSeries(
        dots = drawn(shown(all)),
        trend = trendLines(all).map(shown).filter { it.isNotEmpty() }.map(drawn),
    )
}
