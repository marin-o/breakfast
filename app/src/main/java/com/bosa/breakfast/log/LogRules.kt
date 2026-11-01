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
    /** No meal is known: the Log's meal menu opens and choosing a meal submits, unless the draft only finds matches. */
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

/** What the Log page does with its text field when the queue or the meal menu changes. */
internal enum class LogFieldFocus {
    /** No focus and no keyboard: it would cover the rows of the foods being matched. */
    KEYBOARD_DOWN,
    /** The field takes focus, and the keyboard comes with it. */
    FOCUS,
    /** As it is: focus now would dismiss the menu or bring the keyboard up over it, or over a write that may close the page. */
    LEAVE,
}

/**
 * The Log field's focus: down while foods are [matching] or since "Find more matches" put the keyboard away until a tap on
 * the text ([keptDown]); otherwise it waits for an open menu ([menuOpen]) and a save ([writing]).
 */
internal fun logFieldFocusOf(matching: Boolean, menuOpen: Boolean, writing: Boolean, keptDown: Boolean = false): LogFieldFocus = when {
    matching || keptDown -> LogFieldFocus.KEYBOARD_DOWN
    menuOpen || writing -> LogFieldFocus.LEAVE
    else -> LogFieldFocus.FOCUS
}

/**
 * Where the Log's list scrolls to show a row at [top] of the list's content, [height] tall, in a [viewport] scrolled to
 * [scrolled]: nowhere if all of it shows, else as little as it takes; one too tall to fit starts at the top.
 */
internal fun logScrollToShow(top: Int, height: Int, viewport: Int, scrolled: Int): Int = when {
    top < scrolled || height >= viewport -> top
    top + height > scrolled + viewport -> top + height - viewport
    else -> scrolled
}

/**
 * The draft was edited since the Log composed with [openedWith], which [edited] remembers once true. Only an edit arms the
 * Open Food Facts pause: opening the Log sends nothing, nor does a restored draft whose rows come once the diary is ready.
 */
internal fun logDraftEdited(edited: Boolean, text: String, openedWith: String): Boolean = edited || text != openedWith

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

/** "Find more matches" beside Add: a part is being typed ([catalogueQuery]) and Add would log. Find matches already searches. */
internal fun logOffersMoreMatches(bar: LogBar, catalogueQuery: String): Boolean = bar == LogBar.ADD && catalogueQuery.isNotBlank()

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
