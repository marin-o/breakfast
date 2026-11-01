package com.bosa.breakfast.log

import com.bosa.breakfast.domain.DraftRow
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.ui.label

// The Log's pure decisions, unit-tested: its target meal, what Add does, and whether a tapped row still applies.

/**
 * The Log page's target meal. Precedence: a meal picked from the Log menu, then a single typed meal word
 * (it beats a preset), then the preset, then the "Meal" setting's [automatic] meal (null for Ask, see
 * [automaticMeal]). Two different typed words give no target; the plan reports the error.
 */
internal fun logTargetMealOf(chosen: MealSlot?, typedMarkers: List<MealSlot>, preset: MealSlot?, automatic: MealSlot? = null): MealSlot? =
    chosen ?: typedMarkers.singleOrNull() ?: preset ?: automatic

/** The meal of the most recent log: the entry saved last. Null when nothing is logged. */
internal fun lastUsedMeal(entries: List<MealEntry>): MealSlot? = entries.maxByOrNull { it.savedAtEpochMs }?.mealSlot

/** The picker label: the target meal's name, or "Meal" when nothing is chosen, typed or preset. */
internal fun logMealPickerLabel(target: MealSlot?): String = target?.label() ?: "Meal"

internal sealed interface LogSubmitPlan {
    /** Nothing to do: the session cannot start a write (storage closed or a save running, `DiarySession.canWrite`). */
    data object Ignore : LogSubmitPlan
    data class Error(val message: String) : LogSubmitPlan
    /** No meal is known: open the Log's meal menu; choosing a meal submits. */
    data object OpenMenu : LogSubmitPlan
    data class Save(val target: MealSlot) : LogSubmitPlan
}

/** Decides what Add does with the Log input. Pure, so the no-meal path and the busy guard can be unit-tested. */
internal fun logSubmitPlan(
    canWrite: Boolean, input: String, typedMarkers: List<MealSlot>, chosen: MealSlot?, preset: MealSlot?, automatic: MealSlot? = null,
): LogSubmitPlan {
    if (!canWrite) return LogSubmitPlan.Ignore
    if (input.isBlank()) return LogSubmitPlan.Error("Add at least one food.")
    if (typedMarkers.size > 1) return LogSubmitPlan.Error("Choose one meal marker, or remove the extra markers.")
    val target = logTargetMealOf(chosen, typedMarkers, preset, automatic) ?: return LogSubmitPlan.OpenMenu
    return LogSubmitPlan.Save(target)
}

/** What the Log's bar offers. */
internal enum class LogBar {
    /** "Add to <meal>" with the sum. */
    ADD,
    /** The same, disabled: all that is left has no value to log. */
    ADD_DISABLED,
    /** "Find matches" across the bar, no sum: nothing would log, and a tap looks for what needs a match. */
    FIND_MATCHES,
    /** No bar: the foods left are already being matched under their rows. */
    HIDDEN,
}

/**
 * The Log's bar. Something that would log always gets the normal bar. Otherwise a part that needs a match and is not
 * queued gets "Find matches", a running queue needs no bar, and what cannot log leaves Add with nothing to do.
 */
internal fun logBarOf(addLogs: Boolean, needsMatch: Boolean, hasNoValue: Boolean, queueActive: Boolean, unqueuedNeedsMatch: Boolean = false): LogBar = when {
    addLogs -> LogBar.ADD
    queueActive && !unqueuedNeedsMatch -> LogBar.HIDDEN
    needsMatch -> LogBar.FIND_MATCHES
    hasNoValue -> LogBar.ADD_DISABLED
    else -> LogBar.ADD
}
