package com.bosa.breakfast.resolve

import com.bosa.breakfast.domain.MealSlot

/**
 * A "Not in your foods" part sent to the library. [draftText] is the draft as it was, [start] and [end] the part's span
 * in it, [part] its text; [preset], [chosen] and [slot] are where the Log was, so it comes back there.
 */
data class LogResolveRequest(
    val draftText: String,
    val start: Int,
    val end: Int,
    val part: String,
    val preset: MealSlot?,
    val chosen: MealSlot?,
    val slot: MealSlot?,
)
