package mk.bosa.breakfast.resolve

import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.TypedPart

/**
 * The "Not in your foods" queue while it is being resolved, as the screens opened from it (product review, the food
 * form) see it: the meal its parts go to (null: none is known yet), the parts left as typed (the first is the one being resolved), how many
 * there were, and the current part as the Log read it ([current]: "mleko200g" is mleko, 200 g). Built by
 * [ResolveFlow.queueView]; null when no queue is being resolved.
 */
internal data class ResolveQueueView(
    val meal: MealSlot?,
    val parts: List<String>,
    val total: Int,
    val current: TypedPart,
) {
    /** "n of m" for the part being resolved ([resolvePosition]). */
    fun position(): String = resolvePosition(total, parts.size).let { (n, m) -> "$n of $m" }
    /** The part after the one being resolved, if any. */
    val next: String? get() = parts.drop(1).firstOrNull()
}
