package com.bosa.breakfast.domain

import java.time.LocalDate

// What the Nutrition page's week holds: one stacked bar a day, the goal line and the averages.

/**
 * One day's bar, in kcal: the segments add up to [kcal]. An entry's kcal go to its macros by their 4/4/9 share, or to
 * [unknownKcal] when it has no macro data. [logged] false is an empty slot. [targetKcal] is that day's goal line.
 */
data class DayBar(
    val dayKey: String,
    val logged: Boolean,
    val kcal: Double,
    val proteinKcal: Double,
    val carbsKcal: Double,
    val fatKcal: Double,
    val unknownKcal: Double,
    val targetKcal: Double?,
)

/** [start] is its Monday; the averages are over [loggedDays] only, and null with none. */
data class NutritionWeek(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<DayBar>,
    val loggedDays: Int,
    val averageKcal: Double?,
    val averageProtein: Double?,
    val averageCarbs: Double?,
    val averageFat: Double?,
)

/** The Monday of [dayKey]'s week; null for a key that is no date. */
fun weekStartOf(dayKey: String): LocalDate? = dayOf(dayKey)?.let(::mondayOf)

/** The next arrow goes on: a later week is no later than the one holding [today]. */
fun hasNextWeek(start: LocalDate, today: String): Boolean = weekStartOf(today)?.let { start.isBefore(it) } == true

private fun dayBar(dayKey: String, entries: List<MealEntry>, history: List<TargetRow>): DayBar {
    var protein = 0.0
    var carbs = 0.0
    var fat = 0.0
    var unknown = 0.0
    for (entry in entries) {
        val n = entry.nutritionSnapshot
        val p = (n.protein ?: 0.0) * KCAL_PER_GRAM_PROTEIN
        val c = (n.carbohydrates ?: 0.0) * KCAL_PER_GRAM_CARBS
        val f = (n.fat ?: 0.0) * KCAL_PER_GRAM_FAT
        val total = p + c + f
        if (total <= 0.0) { unknown += n.calories; continue }
        protein += n.calories * p / total
        carbs += n.calories * c / total
        fat += n.calories * f / total
    }
    return DayBar(dayKey, entries.isNotEmpty(), nutritionTotalOf(entries).calories, protein, carbs, fat, unknown, targetOn(dayKey, history)?.kcal)
}

/** The week from Monday [start]. */
fun nutritionWeek(start: LocalDate, entries: List<MealEntry>, history: List<TargetRow>): NutritionWeek {
    val keys = (0L until 7L).map { start.plusDays(it).toString() }
    val byDay = entries.groupBy { it.dayKey }
    val logged = keys.mapNotNull { byDay[it] }
    fun average(of: (List<MealEntry>) -> Double): Double? = if (logged.isEmpty()) null else logged.sumOf(of) / logged.size
    fun macro(of: (NutritionValues) -> Double?): Double? = average { day -> day.sumOf { of(it.nutritionSnapshot) ?: 0.0 } }
    return NutritionWeek(
        start,
        start.plusDays(6),
        keys.map { dayBar(it, byDay[it].orEmpty(), history) },
        logged.size,
        average { nutritionTotalOf(it).calories },
        macro { it.protein },
        macro { it.carbohydrates },
        macro { it.fat },
    )
}
