package mk.bosa.breakfast.domain

import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// A goal as daily kcal, its lowest limits and warnings, the daily adjustment and the targets by day.

enum class Goal { LOSE, MAINTAIN, GAIN }

/** The targets from [dayKey] on; null is not set. */
data class TargetRow(val dayKey: String, val kcal: Double?, val protein: Double?, val carbs: Double?, val fat: Double?) {
    val hasAny: Boolean get() = listOf(kcal, protein, carbs, fat).any { it != null }
}

/** [values]' targets as the row of [dayKey]. */
fun targetsOf(values: ProfileValues, dayKey: String) =
    TargetRow(dayKey, values.kcalTarget, values.proteinTarget, values.carbsTarget, values.fatTarget)

/**
 * The targets in effect on [dayKey]: the latest row at or before it; a day before the first row uses the first. A caller
 * asking about the past of a day passes only the rows up to that day, so a row dated later never stands in for it.
 */
fun targetOn(dayKey: String, history: List<TargetRow>): TargetRow? {
    val sorted = history.sortedBy { it.dayKey }
    return sorted.lastOrNull { it.dayKey <= dayKey } ?: sorted.firstOrNull()
}

/** The lowest Daily kcal a goal gives; a hand-typed target may go below it. */
fun lowestKcal(sex: Sex?): Int = if (sex == Sex.FEMALE) 1200 else 1500

/**
 * What a goal gives: [kcal], and, when the lowest limit decided it, the limit in [lowest]. [paceAtLowest] is the real
 * loss at it, kg a week, only when Lose still loses there; Maintain, Gain or a burn at or under the limit claim no pace.
 */
data class GoalKcal(val kcal: Int, val lowest: Int? = null, val paceAtLowest: Double? = null)

/** [burn] moved by [paceKgPerWeek] for [goal], whole kcal. Null for Lose or Gain without a pace. */
fun goalKcal(burn: Double, goal: Goal, paceKgPerWeek: Double?, sex: Sex?): GoalKcal? {
    val change = when (goal) {
        Goal.MAINTAIN -> 0.0
        Goal.LOSE -> -(paceKgPerWeek ?: return null) * KCAL_PER_KG / 7
        Goal.GAIN -> (paceKgPerWeek ?: return null) * KCAL_PER_KG / 7
    }
    val wanted = (burn + change).roundToInt()
    val lowest = lowestKcal(sex)
    if (wanted >= lowest) return GoalKcal(wanted)
    val pace = if (goal == Goal.LOSE && burn > lowest) (burn - lowest) * 7 / KCAL_PER_KG else null
    return GoalKcal(lowest, lowest, pace)
}

/** A pace above 1% of the weight a week; Maintain has no pace. */
fun paceTooFast(goal: Goal?, paceKgPerWeek: Double?, weightKg: Double?): Boolean =
    goal != null && goal != Goal.MAINTAIN && paceKgPerWeek != null && weightKg != null && paceKgPerWeek > weightKg * 0.01

enum class LoseWarning { UNDERWEIGHT, UNDER_18 }

private const val LOWEST_HEALTHY_BMI = 18.5

fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / ((heightCm / 100) * (heightCm / 100))

/** What Lose warns about; nothing is blocked. A value not set says nothing. */
fun loseWarnings(goal: Goal?, heightCm: Double?, age: Int?, currentKg: Double?, targetKg: Double?): Set<LoseWarning> {
    if (goal != Goal.LOSE) return emptySet()
    val underweight = heightCm != null && heightCm > 0 && listOfNotNull(currentKg, targetKg).any { bmi(it, heightCm) < LOWEST_HEALTHY_BMI }
    return buildSet {
        if (underweight) add(LoseWarning.UNDERWEIGHT)
        if (age != null && age < 18) add(LoseWarning.UNDER_18)
    }
}

private const val MOST_KCAL_IN_A_WEEK = 100.0
private const val WEEK_DAYS_BEFORE_TODAY = 6L

/**
 * [wanted] as far as [current] may move on [today]: over any 7 days the target stays within 100 kcal. Every value in
 * effect in the last 7 days counts, so a bigger step the user saved holds the target until it has left the window. It
 * only moves toward [wanted], never past the band. Rows after [today] say nothing. With no row holding a kcal yet the value
 * before a move would go unrecorded, so nothing moves until a starting row is a day old ([needsStartingRow]).
 */
fun limitedKcal(wanted: Int, current: Double, history: List<TargetRow>, today: String): Double {
    val windowStart = dayOf(today)?.minusDays(WEEK_DAYS_BEFORE_TODAY)?.toString() ?: return current
    val past = history.filter { it.dayKey <= today }
    if (past.all { it.kcal == null }) return current
    val inWindow = past.filter { it.dayKey > windowStart }.map { it.kcal } + targetOn(windowStart, past)?.kcal + current
    val seen = inWindow.filterNotNull()
    val low = ceil(seen.max() - MOST_KCAL_IN_A_WEEK)
    val high = floor(seen.min() + MOST_KCAL_IN_A_WEEK)
    if (low > high) return current
    val aim = wanted.toDouble().coerceIn(KCAL_TARGET)
    return when {
        aim < current -> maxOf(aim, low).coerceAtMost(current)
        aim > current -> minOf(aim, high).coerceAtLeast(current)
        else -> current
    }
}

/** Targets with nothing behind them up to [today]: saved before the history existed, or from an older file. A row today starts it. */
fun needsStartingRow(values: ProfileValues, history: List<TargetRow>, today: String): Boolean {
    val past = history.filter { it.dayKey <= today }
    return if (past.isEmpty()) targetsOf(values, today).hasAny else values.kcalTarget != null && past.all { it.kcal == null }
}

/** These values with the targets [row] stored. */
fun ProfileValues.withTargetsOf(row: TargetRow): ProfileValues =
    copy(kcalTarget = row.kcal, proteinTarget = row.protein, carbsTarget = row.carbs, fatTarget = row.fat)

/**
 * What the daily adjustment makes of [values] on [today], macros following; null when nothing changes: no saved goal
 * with a Daily kcal, tracking only, a body not complete, a target already set today, no history yet, or no move to make.
 */
fun dailyAdjusted(
    values: ProfileValues,
    points: List<WeightPoint>,
    entries: List<MealEntry>,
    history: List<TargetRow>,
    today: String,
    zone: ZoneId = ZoneId.systemDefault(),
): ProfileValues? {
    val goal = values.goal ?: return null
    val current = values.kcalTarget ?: return null
    if (values.trackingOnly || history.any { it.dayKey == today }) return null
    val body = values.burnBody() ?: return null
    val wanted = goalKcal(dailyBurn(body, points, entries, today, zone).kcal, goal, values.paceKgPerWeek, values.sex) ?: return null
    val next = limitedKcal(wanted.kcal, current, history, today)
    return if (next == current) null else values.withKcalFollowing(next)
}

/** The burn's inputs from what is saved; null until height, age, current weight and activity are there. */
fun ProfileValues.burnBody(): BurnBody? {
    return BurnBody(age ?: return null, heightCm ?: return null, currentWeight ?: return null, sex, activity ?: return null)
}

/** These values as Meals reads them: tracking only hides the targets, and keeps them. */
fun ProfileValues.targetsShown(): ProfileValues =
    if (trackingOnly) copy(kcalTarget = null, proteinTarget = null, carbsTarget = null, fatTarget = null) else this

/** "1500 kcal is the lowest here. That's about 0.4 kg a week."; null when the lowest did not apply or claims no pace. */
fun lowestLine(result: GoalKcal): String? {
    val lowest = result.lowest ?: return null
    val pace = result.paceAtLowest ?: return null
    return "$lowest kcal is the lowest here. That's about ${kgAWeekText(pace)} kg a week."
}

/** Height as typed, null until valid. */
val ProfileDraft.validHeightCm: Double? get() = positiveAmountOf(height)?.takeIf(::heightValid)

/** Current weight as typed, null until valid. */
val ProfileDraft.validCurrentKg: Double? get() = positiveAmountOf(current)?.takeIf(::weightValid)

/** Target weight as typed, null until valid. */
val ProfileDraft.validTargetKg: Double? get() = positiveAmountOf(target)?.takeIf(::weightValid)

/** The burn's inputs as typed; null until height, age, current weight and activity are valid. */
fun ProfileDraft.burnBody(): BurnBody? =
    BurnBody(ageOf(age) ?: return null, validHeightCm ?: return null, validCurrentKg ?: return null, sex, activity ?: return null)

/** [goal] chosen with the burn the draft has; Daily kcal fills in when the pace allows it. */
fun ProfileDraft.withGoal(goal: Goal, burn: Double?): ProfileDraft = copy(goal = goal).filledFromGoal(burn)

/** Kg a week chosen ([custom] for the Custom choice); Daily kcal fills in by the goal. */
fun ProfileDraft.withPace(text: String, custom: Boolean, burn: Double?): ProfileDraft = copy(pace = text, paceCustom = custom).filledFromGoal(burn)

/**
 * This draft, just edited from [before], with Daily kcal refilled when the edit moved the burn ([burnOf]) under a chosen goal,
 * macros following. No goal, tracking only (targets are kept there) or a body not valid leaves Daily kcal as it is.
 */
fun ProfileDraft.followingBurn(before: ProfileDraft, burnOf: (BurnBody) -> Double): ProfileDraft {
    if (goal == null || trackingOnly) return this
    val body = burnBody() ?: return this
    val old = before.burnBody()
    if (body == old) return this
    val burn = burnOf(body)
    return if (old != null && burnOf(old) == burn) this else filledFromGoal(burn)
}

private fun ProfileDraft.filledFromGoal(burn: Double?): ProfileDraft {
    val chosen = goal ?: return this
    val kcal = goalKcal(burn ?: return this, chosen, positiveAmountOf(pace), sex)?.kcal ?: return this
    return withDailyKcal(kcal.toString())
}
