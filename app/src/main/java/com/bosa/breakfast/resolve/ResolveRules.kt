package com.bosa.breakfast.resolve

import com.bosa.breakfast.domain.MealSlot

// The resolve queue's pure decisions, unit-tested: where a food logs while it runs, and the "n of m" it shows.

/** Where a food is logged ([queueLogPlan]), and whether that advances the resolve queue. */
internal data class ReviewLogPlan(val meal: MealSlot, val advancesQueue: Boolean)

/**
 * Where a food logs from a screen the resolver can open (product review's Add, the food form's Save). While a resolve
 * queue is being resolved ([queueActive]: it is continued and has parts left) the queue owns the meal, even when the
 * screen beneath has none, and the log advances the queue; otherwise it logs to [meal], the one chosen or open
 * beneath. Null: nowhere to log, which is also a queue the Log's Find matches started with no meal yet; product review
 * and the food form do not log for such a queue, they send what is picked back to the Log's draft.
 */
internal fun queueLogPlan(queueActive: Boolean, queueMeal: MealSlot?, meal: MealSlot?): ReviewLogPlan? = when {
    queueActive -> queueMeal?.let { ReviewLogPlan(it, true) }
    else -> meal?.let { ReviewLogPlan(it, false) }
}

/** "n of m" for the resolve sticker; the total never reads lower than what is left. */
internal fun resolvePosition(total: Int, remaining: Int): Pair<Int, Int> {
    val shown = maxOf(total, remaining)
    return (shown - remaining + 1).coerceIn(1, maxOf(shown, 1)) to shown
}
