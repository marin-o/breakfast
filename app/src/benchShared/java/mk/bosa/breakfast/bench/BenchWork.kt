package mk.bosa.breakfast.bench

import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.macroTotalsOf

/**
 * The work the Log page does off the main thread for one keystroke, as `BreakfastViewModel.logDraftView` does it:
 * one planner draft, then each choice's kcal from the same planner. Returns a count so the work is not dropped.
 */
fun logDraftWork(planner: LogPlanner, text: String): Int {
    val draft = planner.draft(text, typing = true)
    val choiceKcal = draft.choices.map { planner.calculationFor(draft.activeText, it.food) }
    return draft.rows.size + choiceKcal.size + draft.logged.size
}

/**
 * Types each of [inputs] one keystroke at a time, [passes] times, with the planner [plannerFor] gives each pass (a new
 * planner per pass, as after a write or a new draft). Returns one sample per keystroke, in nanoseconds.
 */
fun keystrokeSamples(inputs: List<String>, passes: Int, plannerFor: (pass: Int) -> LogPlanner): List<Long> {
    val samples = ArrayList<Long>()
    var sink = 0
    for (pass in 0 until passes) {
        val planner = plannerFor(pass)
        for (input in inputs) for (prefix in keystrokes(input)) {
            val start = System.nanoTime()
            sink += logDraftWork(planner, prefix)
            samples += System.nanoTime() - start
        }
    }
    check(sink >= 0)
    return samples
}

/**
 * What the Daily screen computes in composition for one day (`DailyScreen`): the day's entries, the foods by id,
 * the kcal fold, the macro totals and each meal's entries. Returns a count so the work is not dropped.
 */
fun dailyDataWork(entries: List<MealEntry>, foods: List<FoodRecord>, dayKey: String): Int {
    val selected = entries.filter { it.dayKey == dayKey }
    val byId = foods.associateBy { it.id }
    val total = selected.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    val macros = macroTotalsOf(selected.map { it.nutritionSnapshot })
    val perMeal = MealSlot.entries.map { slot -> selected.filter { it.mealSlot == slot } }
    return byId.size + perMeal.sumOf { it.size } + total.calories.toInt().coerceAtLeast(0) % 7 + macros.protein.known
}

/** Only the day-total fold of [dayEntries] (kcal and macros), as Daily shows it for the busiest day. */
fun dayTotalsWork(dayEntries: List<MealEntry>): Int {
    val total = dayEntries.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    val macros = macroTotalsOf(dayEntries.map { it.nutritionSnapshot })
    return total.calories.toInt().coerceAtLeast(0) % 7 + macros.fat.known
}
