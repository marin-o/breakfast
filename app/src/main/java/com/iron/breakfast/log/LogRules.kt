package com.iron.breakfast.log

import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.label

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

/**
 * A "Not in your foods" row still names its text in [draft] (MOB-025): the Log draws rows from a draft computed off
 * the main thread, so after a keystroke a tapped row's span may hold other text, and that tap must do nothing.
 */
internal fun rowStillInDraft(draft: String, row: DraftRow.Unresolved): Boolean =
    row.start >= 0 && row.start < row.end && row.end <= draft.length && draft.substring(row.start, row.end) == row.text
