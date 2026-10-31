package com.iron.breakfast.domain

/**
 * A food's or recipe's meal when its page or sheet opens: the meal it was opened from, else the Meal setting's [automatic] meal,
 * else Lunch (today's preselection; Ask). The page's meal menu still changes it.
 */
internal fun presetMeal(openedFrom: MealSlot?, automatic: MealSlot?): MealSlot = openedFrom ?: automatic ?: MealSlot.LUNCH
