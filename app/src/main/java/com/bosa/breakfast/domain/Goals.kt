package com.bosa.breakfast.domain

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

/** The targets in effect on [dayKey]: the latest row at or before it; a day before the first row uses the first. */
fun targetOn(dayKey: String, history: List<TargetRow>): TargetRow? {
    val sorted = history.sortedBy { it.dayKey }
    return sorted.lastOrNull { it.dayKey <= dayKey } ?: sorted.firstOrNull()
}

/** The lowest Daily kcal a goal gives; a hand-typed target may go below it. */
fun lowestKcal(sex: Sex?): Int = if (sex == Sex.FEMALE) 1200 else 1500

/**
 * What a goal gives: [kcal], and, when the lowest limit decided it, the limit in [lowest] with the real pace at it
 * ([paceAtLowest], kg a week, never below zero).
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
    return GoalKcal(lowest, lowest, ((burn - lowest) * 7 / KCAL_PER_KG).coerceAtLeast(0.0))
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
 * effect in the last 7 days counts, so a bigger step the user saved holds the target until it has left the window.
 */
fun limitedKcal(wanted: Int, current: Double, history: List<TargetRow>, today: String): Double {
    val windowStart = dayOf(today)?.minusDays(WEEK_DAYS_BEFORE_TODAY)?.toString() ?: return current
    val inWindow = history.filter { it.dayKey > windowStart && it.dayKey <= today }.map { it.kcal } + targetOn(windowStart, history)?.kcal + current
    val seen = inWindow.filterNotNull()
    val low = ceil(seen.max() - MOST_KCAL_IN_A_WEEK)
    val high = floor(seen.min() + MOST_KCAL_IN_A_WEEK)
    return if (low > high) current else wanted.toDouble().coerceIn(low, high)
}

/**
 * What the daily adjustment makes of [values] on [today], macros following; null when nothing changes: no saved goal
 * with a Daily kcal, tracking only, a body not complete, a target already set today, or no move to make.
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

/** The burn's inputs as typed; null until height, age, current weight and activity are valid. */
fun ProfileDraft.burnBody(): BurnBody? {
    val cm = positiveAmountOf(height)?.takeIf(::heightValid) ?: return null
    val kg = positiveAmountOf(current)?.takeIf(::weightValid) ?: return null
    return BurnBody(ageOf(age) ?: return null, cm, kg, sex, activity ?: return null)
}

/** [goal] chosen with the burn the draft has; Daily kcal fills in when the pace allows it. */
fun ProfileDraft.withGoal(goal: Goal, burn: Double?): ProfileDraft = copy(goal = goal).filledFromGoal(burn)

/** Kg a week chosen ([custom] for the Custom choice); Daily kcal fills in by the goal. */
fun ProfileDraft.withPace(text: String, custom: Boolean, burn: Double?): ProfileDraft = copy(pace = text, paceCustom = custom).filledFromGoal(burn)

private fun ProfileDraft.filledFromGoal(burn: Double?): ProfileDraft {
    val chosen = goal ?: return this
    val kcal = goalKcal(burn ?: return this, chosen, positiveAmountOf(pace), sex)?.kcal ?: return this
    return withDailyKcal(kcal.toString())
}
