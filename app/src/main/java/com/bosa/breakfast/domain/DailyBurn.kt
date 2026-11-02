package com.bosa.breakfast.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

// Daily burn: Mifflin-St Jeor times an activity factor, then what logging and weighing say. See docs/profile-pages-spec.md.

enum class Sex { FEMALE, MALE, NOT_SAYING }

enum class Activity { INACTIVE, LOW_ACTIVE, ACTIVE, VERY_ACTIVE }

/** What the equation needs. A null [sex] counts as Not saying. */
data class BurnBody(val age: Int, val heightCm: Double, val weightKg: Double, val sex: Sex?, val activity: Activity)

/** kcal stored in a kg of body weight (rho). */
const val KCAL_PER_KG = 8840.0

/** The last days a slope or an intake looks at, today included. */
const val LEARNING_WINDOW_DAYS = 28L

private const val MIN_WEIGH_INS = 14
private const val MIN_SPAN_DAYS = 21L
private const val MIN_ELIGIBLE_DAYS = 21
private const val ELIGIBLE_SHARE_OF_MEDIAN = 0.5
private const val ADAPTIVE_FLOOR_SIGMA = 100.0
private const val PLAUSIBLE_LOW = 0.75
private const val PLAUSIBLE_HIGH = 1.3

/** Resting energy by the Mifflin-St Jeor equation, before the sex term. */
private fun restingBeforeSex(body: BurnBody) = 10 * body.weightKg + 6.25 * body.heightCm - 5 * body.age

private fun sexTerm(sex: Sex?) = when (sex) {
    Sex.MALE -> 5.0
    Sex.FEMALE -> -161.0
    Sex.NOT_SAYING, null -> -78.0
}

/** Set low on purpose: too high a burn means a target that gains weight, and logging corrects a low one. */
private fun activityFactor(activity: Activity) = when (activity) {
    Activity.INACTIVE -> 1.4
    Activity.LOW_ACTIVE -> 1.55
    Activity.ACTIVE -> 1.7
    Activity.VERY_ACTIVE -> 1.9
}

/** The equation alone, kcal a day, never below zero; Not saying is the average of both. */
fun formulaBurn(body: BurnBody): Double =
    ((restingBeforeSex(body) + sexTerm(body.sex)) * activityFactor(body.activity)).coerceAtLeast(0.0)

/** How far the equation may be off (sigma). */
private fun priorSigma(sex: Sex?): Double = when (sex) {
    Sex.MALE -> 339.0
    Sex.FEMALE -> 246.0
    Sex.NOT_SAYING, null -> 300.0
}

/** A weight's change by linear regression: kg a day, and its standard error. */
data class WeightSlope(val kgPerDay: Double, val standardError: Double)

/** The window's first day key. Day keys sort as dates, so years of rows are left out without parsing them. */
private fun learningWindowStart(today: LocalDate): String = today.minusDays(LEARNING_WINDOW_DAYS - 1).toString()

/** [points] in the last [LEARNING_WINDOW_DAYS] days to [today]. */
internal fun inLearningWindow(points: List<WeightPoint>, today: LocalDate): List<WeightPoint> {
    val first = learningWindowStart(today)
    val last = today.toString()
    return points.filter { it.dayKey >= first && it.dayKey <= last && dayOf(it.dayKey) != null }
}

internal fun dayOf(dayKey: String): LocalDate? = runCatching { LocalDate.parse(dayKey) }.getOrNull()

/** Regression over raw weigh-ins, placed by [curveMinute]. Null under 3 points, or when they all sit at one moment (no variance). */
fun weightSlope(points: List<WeightPoint>, zone: ZoneId = ZoneId.systemDefault()): WeightSlope? {
    val xy = points.mapNotNull { point -> curveMinute(point, zone)?.let { it / MINUTES_PER_DAY to point.kg } }
    if (xy.size < 3) return null
    val meanX = xy.sumOf { it.first } / xy.size
    val meanY = xy.sumOf { it.second } / xy.size
    val sxx = xy.sumOf { (x, _) -> (x - meanX) * (x - meanX) }
    if (sxx == 0.0) return null
    val slope = xy.sumOf { (x, y) -> (x - meanX) * (y - meanY) } / sxx
    val residuals = xy.sumOf { (x, y) ->
        val off = y - (meanY + slope * (x - meanX))
        off * off
    }
    return WeightSlope(slope, sqrt(residuals / (xy.size - 2) / sxx))
}

internal fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

/** The mean intake of the window's eligible days, and how many there are. Today is still being logged, so it does not count. */
private fun eligibleIntake(entries: List<MealEntry>, today: LocalDate): Pair<Double, Int>? {
    val first = learningWindowStart(today)
    val end = today.toString()
    val days = entries.filter { it.dayKey >= first && it.dayKey < end }.groupBy { it.dayKey }
        .filterKeys { dayOf(it) != null }
        .values.map { nutritionTotalOf(it).calories }
    val floor = (median(days) ?: return null) * ELIGIBLE_SHARE_OF_MEDIAN
    val eligible = days.filter { it >= floor }
    return if (eligible.isEmpty()) null else eligible.average() to eligible.size
}

/**
 * [kcal], the burn to use; [formula], the equation alone; [learned], how much logging moved it (w, 0 to 1). With
 * [loggingMismatch] the logging and the weight don't fit together, so they count for nothing.
 */
data class DailyBurn(val kcal: Double, val formula: Double, val learned: Double, val loggingMismatch: Boolean)

/** The burn for [body] on [today], learning from the weigh-ins and the logged days when there are enough. */
fun dailyBurn(body: BurnBody, points: List<WeightPoint>, entries: List<MealEntry>, today: String, zone: ZoneId = ZoneId.systemDefault()): DailyBurn {
    val formula = formulaBurn(body)
    val alone = DailyBurn(formula, formula, 0.0, false)
    val day = dayOf(today) ?: return alone
    val window = inLearningWindow(points, day)
    if (window.size < MIN_WEIGH_INS) return alone
    val days = window.mapNotNull { dayOf(it.dayKey) }
    if (ChronoUnit.DAYS.between(days.min(), days.max()) < MIN_SPAN_DAYS) return alone
    val (intake, eligibleDays) = eligibleIntake(entries, day) ?: return alone
    if (eligibleDays < MIN_ELIGIBLE_DAYS) return alone
    val slope = weightSlope(window, zone) ?: return alone

    val adaptive = intake - KCAL_PER_KG * slope.kgPerDay
    if (adaptive < PLAUSIBLE_LOW * formula || adaptive > PLAUSIBLE_HIGH * formula) return alone.copy(loggingMismatch = true)
    val spread = KCAL_PER_KG * slope.standardError
    val adaptiveVariance = spread * spread + ADAPTIVE_FLOOR_SIGMA * ADAPTIVE_FLOOR_SIGMA
    val sigma = priorSigma(body.sex)
    val learned = sigma * sigma / (sigma * sigma + adaptiveVariance)
    return DailyBurn(formula + learned * (adaptive - formula), formula, learned, false)
}
