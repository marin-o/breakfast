package com.iron.breakfast.resolve

import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.resolvePosition

/**
 * The "Not in your foods" queue while it is being resolved, as the screens opened from it (product review, the food
 * form) see it: the meal its parts go to, the parts left as typed (the first is the one being resolved), and how many
 * there were. Built by the resolve flow's owner; null when no queue is being resolved.
 */
internal data class ResolveQueueView(val meal: MealSlot?, val parts: List<String>, val total: Int) {
    /** "n of m" for the part being resolved ([resolvePosition]). */
    fun position(): String = resolvePosition(total, parts.size).let { (n, m) -> "$n of $m" }
    /** The part after the one being resolved, if any. */
    val next: String? get() = parts.drop(1).firstOrNull()
}
