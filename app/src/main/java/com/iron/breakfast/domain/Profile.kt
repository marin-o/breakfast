package com.iron.breakfast.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

// The user's profile: pure values and rules. Everything is optional, and an empty profile changes nothing in the app.

/** What the profile holds beside the weight curve; null is not set. */
data class ProfileValues(
    val name: String = "",
    val proteinTarget: Double? = null,
    val carbsTarget: Double? = null,
    val fatTarget: Double? = null,
    val currentWeight: Double? = null,
    val targetWeight: Double? = null,
)

/** One point of the weight curve: one per diary day. */
data class WeightPoint(val dayKey: String, val kg: Double, val savedAtEpochMs: Long)

/** The profile's fields as typed. */
@Serializable
data class ProfileDraft(
    val name: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val current: String = "",
    val target: String = "",
) {
    companion object {
        fun of(values: ProfileValues) = ProfileDraft(
            values.name,
            editableNumber(values.proteinTarget),
            editableNumber(values.carbsTarget),
            editableNumber(values.fatTarget),
            editableNumber(values.currentWeight),
            editableNumber(values.targetWeight),
        )
    }
}

private val WEIGHT_KG = 1.0..500.0
private val MACRO_TARGET_G = 1.0..5000.0

const val WEIGHT_RANGE_MESSAGE = "Enter 1 to 500 kg."
const val MACRO_TARGET_RANGE_MESSAGE = "Enter 1 to 5000 g."

/** A weight the app keeps, in kg. */
fun weightValid(kg: Double): Boolean = kg in WEIGHT_KG

/** A macro target the app keeps, in g. */
fun macroTargetValid(grams: Double): Boolean = grams in MACRO_TARGET_G

/** A weight as typed: empty is not set, otherwise in range with either decimal mark. */
fun weightTextValid(text: String): Boolean = text.isBlank() || positiveAmountOf(text)?.let(::weightValid) == true

/** A macro target as typed: empty is not set, otherwise in range with either decimal mark. */
fun macroTargetTextValid(text: String): Boolean = text.isBlank() || positiveAmountOf(text)?.let(::macroTargetValid) == true

/** [draft] as values, a field left as it was keeping its exact number in [saved]; null while a number is not valid. */
fun profileOf(draft: ProfileDraft, saved: ProfileValues): ProfileValues? {
    if (!listOf(draft.protein, draft.carbs, draft.fat).all(::macroTargetTextValid)) return null
    if (!listOf(draft.current, draft.target).all(::weightTextValid)) return null
    return ProfileValues(
        draft.name.trim(),
        fieldNumber(draft.protein, saved.proteinTarget),
        fieldNumber(draft.carbs, saved.carbsTarget),
        fieldNumber(draft.fat, saved.fatTarget),
        fieldNumber(draft.current, saved.currentWeight),
        fieldNumber(draft.target, saved.targetWeight),
    )
}

/** "Add to curve" shows: a weight is typed, and it is another one or today has no point yet, so a steady weight can be recorded. */
fun curveOffered(draft: ProfileDraft, saved: ProfileValues, hasPointToday: Boolean): Boolean =
    draft.current.isNotBlank() && (!hasPointToday || draft.current != ProfileDraft.of(saved).current)

/** The weight a save puts on the curve today: with the tick on, a changed weight, or any weight on a day without a point; else none. */
fun curveWeightOf(before: ProfileValues, after: ProfileValues, addToCurve: Boolean, hasPointToday: Boolean): Double? {
    val kg = after.currentWeight ?: return null
    return if (addToCurve && (kg != before.currentWeight || !hasPointToday)) kg else null
}

/** The curve as a list reads it: the newest day first. */
fun newestFirst(points: List<WeightPoint>): List<WeightPoint> = points.sortedByDescending { it.dayKey }

/** A diary day as a position along the curve's x axis; null for a key that is no date. */
fun curveX(dayKey: String): Double? = runCatching { LocalDate.parse(dayKey).toEpochDay().toDouble() }.getOrNull()
