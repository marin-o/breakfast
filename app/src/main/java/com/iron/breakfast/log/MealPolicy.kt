package com.iron.breakfast.log

import com.iron.breakfast.settings.MealBounds
import com.iron.breakfast.settings.BeforeDayStart
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.settings.MealMode

/**
 * The meal By time gives the wall-clock [hour] (0 to 23). From [dayStartHour] on: before [MealBounds.breakfastUntil]
 * Breakfast, then Lunch, then Dinner, then Snacks. Before [dayStartHour] (the previous diary day's late hours): the
 * [before] choice, null for Ask (the meal menu opens). With the day starting at 00:00 no hour is before it.
 */
internal fun mealByTime(hour: Int, bounds: MealBounds, dayStartHour: Int = 0, before: BeforeDayStart = BeforeDayStart.SNACKS): MealSlot? = when {
    hour < dayStartHour -> when (before) {
        BeforeDayStart.SNACKS -> MealSlot.SNACKS
        BeforeDayStart.DINNER -> MealSlot.DINNER
        BeforeDayStart.ASK -> null
    }
    hour < bounds.breakfastUntil -> MealSlot.BREAKFAST
    hour < bounds.lunchUntil -> MealSlot.LUNCH
    hour < bounds.dinnerUntil -> MealSlot.DINNER
    else -> MealSlot.SNACKS
}

/**
 * The meal a log goes to when none is chosen, typed or preset: null for Ask (the meal menu opens), [mealByTime] for
 * By time, and [lastUsed] (the meal of the latest logged entry, null when there is none) for Last used.
 */
internal fun automaticMeal(
    mode: MealMode, hour: Int, bounds: MealBounds, lastUsed: MealSlot?,
    dayStartHour: Int = 0, before: BeforeDayStart = BeforeDayStart.SNACKS,
): MealSlot? = when (mode) {
    MealMode.ASK -> null
    MealMode.BY_TIME -> mealByTime(hour, bounds, dayStartHour, before)
    MealMode.LAST_USED -> lastUsed
}
