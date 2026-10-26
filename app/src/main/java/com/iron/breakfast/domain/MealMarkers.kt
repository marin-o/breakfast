package com.iron.breakfast.domain

/** The meals that meal words in [value] name, in typed order ("lunch, 1 banana" names Lunch). */
internal fun mealMarkerSlots(value: String): List<MealSlot> = ShorthandParser().split(value).mapNotNull(ShorthandLexicon::mealSlotOf)

internal fun removeMealMarkerToken(value: String, target: MealSlot): String =
    ShorthandParser().removingParts(value) { ShorthandLexicon.mealSlotOf(it.text) == target }

/** Drops every meal-word part; the Log page's chosen meal then wins over a typed one. */
internal fun removeAllMealMarkerTokens(value: String): String =
    ShorthandParser().removingParts(value) { ShorthandLexicon.mealSlotOf(it.text) != null }
