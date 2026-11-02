package mk.bosa.breakfast.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    /** Moving toward the target, too slowly to give a date: "No date yet. At this pace, your weight may level off before your target." */
    data object MayLevelOff : WeightProjection

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

/**
 * The weigh-in nearest now has reached [target], by the [goal]'s direction. With no direction, the earliest weigh-in in
 * the window ([startKg]) gives it: the target is reached once crossed, or stood on when the weight started there.
 */
private fun reached(latestKg: Double, startKg: Double?, target: Double, goal: Goal?): Boolean = when (goal) {
    Goal.LOSE -> latestKg <= target
    Goal.GAIN -> latestKg >= target
    Goal.MAINTAIN, null -> {
        val start = startKg ?: latestKg
        when {
            start > target -> latestKg <= target
            start < target -> latestKg >= target
            else -> latestKg == target
        }
    }
}

/** The projection toward [targetKg] from the raw weigh-ins in [points]; distance is from the latest of them. */
fun weightProjection(points: List<WeightPoint>, targetKg: Double?, goal: Goal?, today: String, zone: ZoneId = ZoneId.systemDefault()): WeightProjection {
    val target = targetKg ?: return WeightProjection.Nothing
    val day = dayOf(today) ?: return WeightProjection.Nothing
    val latest = newestFirst(points).firstOrNull()
    val window = inLearningWindow(points, day)
    if (latest != null && reached(latest.kg, oldestFirst(window).firstOrNull()?.kg, target, goal)) return WeightProjection.Nothing

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
    val moving = (target - latest.kg) * slope.kgPerDay > 0 && speed * 7 >= SLOWEST_KG_A_WEEK
    if (latestDate == null || latestDate.isAfter(day.plusYears(LONGEST_YEARS))) {
        return when {
            !enoughDays -> WeightProjection.NotEnoughData(comeBack)
            moving -> WeightProjection.MayLevelOff
            else -> WeightProjection.NoDate
        }
    }

    if (!moving) return WeightProjection.NoDate
    val earliest = day.plusDays((left / (speed + Z_95 * slope.standardError)).roundToLong())
    return WeightProjection.Dated(speed * 7, target, earliest, latestDate, comeBack.takeUnless { enoughDays })
}

/** A pace in words: one decimal, two under 0.1, no trailing zero ("0.4", "1", "0.06"). */
fun kgAWeekText(kgPerWeek: Double): String {
    val text = String.format(Locale.ROOT, if (kgPerWeek < 0.095) "%.2f" else "%.1f", kgPerWeek)
    return text.trimEnd('0').trimEnd('.')
}

private fun comeBackLine(days: Int) = "Not enough data yet. Come back in ${if (days == 1) "1 day" else "$days days"}."

private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)

/** "March", with its year only when it is not within the next 12 months of [today]. */
private fun monthText(date: LocalDate, today: LocalDate): String {
    val name = date.format(MONTH)
    return if (YearMonth.from(date).isBefore(YearMonth.from(today).plusMonths(12))) name else "$name ${date.year}"
}

/** The lines the Weight page shows for [projection] on [today], in order; none for [WeightProjection.Nothing]. */
fun projectionLines(projection: WeightProjection, today: LocalDate): List<String> = when (projection) {
    WeightProjection.Nothing -> emptyList()
    WeightProjection.NoDate -> listOf("No date yet. Your weight isn't moving toward your target.")
    WeightProjection.MayLevelOff -> listOf("No date yet. At this pace, your weight may level off before your target.")
    is WeightProjection.NotEnoughData -> listOf(comeBackLine(projection.comeBackInDays))
    is WeightProjection.Dated -> {
        val to = monthText(projection.latest, today)
        val time = if (YearMonth.from(projection.earliest) == YearMonth.from(projection.latest)) "in $to" else "between ${monthText(projection.earliest, today)} and $to"
        listOfNotNull(
            "At about ${kgAWeekText(projection.kgPerWeek)} kg a week, you reach ${editableNumber(projection.targetKg)} kg $time.",
            projection.comeBackInDays?.let(::comeBackLine),
        )
    }
}
