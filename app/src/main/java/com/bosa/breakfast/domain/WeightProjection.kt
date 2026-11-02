package com.bosa.breakfast.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToLong

/** What the Weight page says below its chart. */
sealed interface WeightProjection {
    /** No target, or it is reached: nothing shows. */
    data object Nothing : WeightProjection

    /** "Not enough data yet. Come back in [comeBackInDays] days." */
    data class NotEnoughData(val comeBackInDays: Int) : WeightProjection

    /** "No date yet. Your weight isn't moving toward your target." */
    data object NoDate : WeightProjection

    /**
     * The target's dates. With [comeBackInDays] there are under 14 weigh-in days, and the "Not enough data yet" line
     * follows.
     */
    data class Dated(val kgPerWeek: Double, val targetKg: Double, val earliest: LocalDate, val latest: LocalDate, val comeBackInDays: Int?) : WeightProjection
}

private const val TAU_DAYS = 343.0
private const val Z_95 = 1.96
private const val ENOUGH_WEIGH_IN_DAYS = 14
private const val FEWEST_WEIGH_IN_DAYS = 3
private const val SLOWEST_KG_A_WEEK = 0.05
private const val LONGEST_YEARS = 2L

/** The weigh-in nearest now has reached [target], by the [goal]'s direction; with no direction, only when it stands on it. */
private fun reached(latestKg: Double, target: Double, goal: Goal?): Boolean = when (goal) {
    Goal.LOSE -> latestKg <= target
    Goal.GAIN -> latestKg >= target
    Goal.MAINTAIN, null -> latestKg == target
}

/** The projection toward [targetKg] from the raw weigh-ins in [points]; distance is from the latest of them. */
fun weightProjection(points: List<WeightPoint>, targetKg: Double?, goal: Goal?, today: String, zone: ZoneId = ZoneId.systemDefault()): WeightProjection {
    val target = targetKg ?: return WeightProjection.Nothing
    val day = dayOf(today) ?: return WeightProjection.Nothing
    val latest = newestFirst(points).firstOrNull()
    if (latest != null && reached(latest.kg, target, goal)) return WeightProjection.Nothing

    val window = inLearningWindow(points, day)
    val weighInDays = window.map { it.dayKey }.distinct().size
    val comeBack = (ENOUGH_WEIGH_IN_DAYS - weighInDays).coerceAtLeast(1)
    if (latest == null || weighInDays < FEWEST_WEIGH_IN_DAYS) return WeightProjection.NotEnoughData(comeBack)
    val slope = weightSlope(window, zone) ?: return WeightProjection.NotEnoughData(comeBack)
    val enoughDays = weighInDays >= ENOUGH_WEIGH_IN_DAYS

    val left = abs(latest.kg - target)
    val speed = abs(slope.kgPerDay)
    val slowest = speed - Z_95 * slope.standardError
    val latestDays = if (slowest > 0 && left < TAU_DAYS * slowest) TAU_DAYS * ln(TAU_DAYS * slowest / (TAU_DAYS * slowest - left)) else null
    val latestDate = latestDays?.let { day.plusDays(it.roundToLong()) }
    if (latestDate == null || latestDate.isAfter(day.plusYears(LONGEST_YEARS))) {
        return if (enoughDays) WeightProjection.NoDate else WeightProjection.NotEnoughData(comeBack)
    }

    val toward = (target - latest.kg) * slope.kgPerDay > 0
    if (!toward || speed * 7 < SLOWEST_KG_A_WEEK) return WeightProjection.NoDate
    val earliest = day.plusDays((left / (speed + Z_95 * slope.standardError)).roundToLong())
    return WeightProjection.Dated(speed * 7, target, earliest, latestDate, comeBack.takeUnless { enoughDays })
}
