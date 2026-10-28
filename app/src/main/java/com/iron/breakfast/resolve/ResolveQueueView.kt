package com.iron.breakfast.resolve

import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.resolvePosition

/**
 * The "Not in your foods" queue while it is being resolved, as the screens opened from it (product review, the food
 * form) see it: the meal its parts go to, the parts left as typed (the first is the one being resolved), how many
 * there were, and the current part as the Log read it ([current]: "mleko200g" is mleko, 200 g). Built by
 * [ResolveFlow.queueView]; null when no queue is being resolved.
 */
internal data class ResolveQueueView(
    val meal: MealSlot?,
    val parts: List<String>,
    val total: Int,
    val current: TypedPart? = parts.firstOrNull()?.let(::plainReading),
) {
    /** "n of m" for the part being resolved ([resolvePosition]). */
    fun position(): String = resolvePosition(total, parts.size).let { (n, m) -> "$n of $m" }
    /** The part after the one being resolved, if any. */
    val next: String? get() = parts.drop(1).firstOrNull()
}

/** A part read by the parser alone, for a view built without the Log's planner. */
private fun plainReading(raw: String): TypedPart = ShorthandParser().parse(raw).let { TypedPart(it.foodPhrase, it.amount, it.unit) }
