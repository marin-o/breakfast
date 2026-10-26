package com.iron.breakfast.library

import com.iron.breakfast.domain.MealSlot

/**
 * The Library food page's meal when it opens: the meal it was opened from, else the Meal setting's [automatic] meal,
 * else Lunch (today's preselection; Ask). The page's meal menu still changes it.
 */
internal fun libraryPresetMeal(openedFrom: MealSlot?, automatic: MealSlot?): MealSlot = openedFrom ?: automatic ?: MealSlot.LUNCH
