package com.iron.breakfast

import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.dayKeyFor
import java.time.ZoneId

// Pure routing and screen decisions the view model makes. Kept out of the Activity file so each can be unit-tested.

internal fun productReviewOrigin(slot: MealSlot?, resolving: Boolean, queueNotEmpty: Boolean): ProductReviewOrigin = when {
    resolving && queueNotEmpty -> ProductReviewOrigin.QUEUE
    slot != null -> ProductReviewOrigin.MEAL
    else -> ProductReviewOrigin.LIBRARY
}

/**
 * The Log page's target meal. Precedence: a meal picked from the Log menu, then a single typed meal word
 * (it beats a preset), then the preset. Two different typed words give no target; the plan reports the error.
 */
internal fun logTargetMealOf(chosen: MealSlot?, typedMarkers: List<MealSlot>, preset: MealSlot?): MealSlot? =
    chosen ?: typedMarkers.singleOrNull() ?: preset

/** The picker label: the target meal's name, or "Meal" when nothing is chosen, typed or preset. */
internal fun logMealPickerLabel(target: MealSlot?): String = target?.label() ?: "Meal"

internal sealed interface LogSubmitPlan {
    /** Nothing to do: the app is not ready or a save is already running. */
    data object Ignore : LogSubmitPlan
    data class Error(val message: String) : LogSubmitPlan
    /** No meal is known: open the Log's meal menu; choosing a meal submits. */
    data object OpenMenu : LogSubmitPlan
    data class Save(val target: MealSlot) : LogSubmitPlan
}

/** Decides what Add does with the Log input. Pure, so the no-meal path and the busy guard can be unit-tested. */
internal fun logSubmitPlan(ready: Boolean, busy: Boolean, input: String, typedMarkers: List<MealSlot>, chosen: MealSlot?, preset: MealSlot?): LogSubmitPlan {
    if (!ready || busy) return LogSubmitPlan.Ignore
    if (input.isBlank()) return LogSubmitPlan.Error("Add at least one food.")
    if (typedMarkers.size > 1) return LogSubmitPlan.Error("Choose one meal marker, or remove the extra markers.")
    val target = logTargetMealOf(chosen, typedMarkers, preset) ?: return LogSubmitPlan.OpenMenu
    return LogSubmitPlan.Save(target)
}

/** Where a reviewed product is logged. While a resolve queue is active the meal is the queue's own and Add always advances it. */
internal data class ReviewLogPlan(val meal: MealSlot, val advancesQueue: Boolean)

internal fun contributionAuthCanDismiss(busy: Boolean): Boolean = !busy

internal fun reviewLogPlan(resolutionContinuation: Boolean, queueNotEmpty: Boolean, queueTarget: MealSlot?, chosen: MealSlot): ReviewLogPlan =
    if (resolutionContinuation && queueNotEmpty && queueTarget != null) ReviewLogPlan(queueTarget, true) else ReviewLogPlan(chosen, false)

/** The queue owns the meal while resolving, even when the Daily screen has no selected meal. */
internal fun resolverLogPlan(
    resolutionContinuation: Boolean,
    queueNotEmpty: Boolean,
    queueTarget: MealSlot?,
    currentMeal: MealSlot?,
): ReviewLogPlan? = when {
    resolutionContinuation && queueNotEmpty -> queueTarget?.let { ReviewLogPlan(it, true) }
    else -> currentMeal?.let { ReviewLogPlan(it, false) }
}

internal fun activeResolutionTarget(
    resolutionContinuation: Boolean,
    queue: List<String>,
    queueTarget: MealSlot?,
): MealSlot? = resolverLogPlan(resolutionContinuation, queue.isNotEmpty(), queueTarget, null)?.meal

internal fun canSaveProductToLibrary(slot: MealSlot?, resolutionContinuation: Boolean, queueNotEmpty: Boolean): Boolean =
    slot == null && !(resolutionContinuation && queueNotEmpty)

internal data class ScannerFailureTransition(
    val scannerOpen: Boolean,
    val unavailable: Boolean,
)

internal fun scannerUnavailableTransition(): ScannerFailureTransition =
    ScannerFailureTransition(scannerOpen = true, unavailable = true)

internal fun shouldApplyOffSearchResult(
    responseGeneration: Long,
    currentGeneration: Long,
    responseQuery: String,
    currentQuery: String,
    searchOpen: Boolean,
): Boolean = searchOpen && responseGeneration == currentGeneration && responseQuery == currentQuery

internal fun shouldApplyLibraryRefresh(
    responseGeneration: Long,
    currentGeneration: Long,
    responseQuery: String,
    currentQuery: String,
    responseArchivedOnly: Boolean,
    currentArchivedOnly: Boolean,
    libraryOpen: Boolean,
): Boolean = libraryOpen && responseGeneration == currentGeneration && responseQuery == currentQuery && responseArchivedOnly == currentArchivedOnly

/** The "No local match" screen shows only once a finished library search for the current query is empty and nothing sits over it. */
internal fun isLibraryNoMatch(ready: Boolean, libraryOpen: Boolean, query: String, resultsQuery: String?, noResults: Boolean, otherScreenOpen: Boolean): Boolean =
    ready && libraryOpen && query.isNotBlank() && resultsQuery == query && noResults && !otherScreenOpen

/** "n of m" for the resolve sticker; the total never reads lower than what is left. */
internal fun resolvePosition(total: Int, remaining: Int): Pair<Int, Int> {
    val shown = maxOf(total, remaining)
    return (shown - remaining + 1).coerceIn(1, maxOf(shown, 1)) to shown
}

/** A screen or sheet drawn over Daily, Meal or Library that handles Back itself. */
internal enum class Overlay { SCANNER, ENTRY_DETAIL, PRODUCT_REVIEW, RESOLVER, OFF_SEARCH, FOOD_FORM, SETTINGS, LIBRARY_FOOD, ENTRY_EDIT }

/**
 * What sits over the screen beneath, topmost first; null when nothing does. The one rule for "is something on top":
 * the Library and Meal Back handlers and the "No local match" screen all read it.
 */
internal fun topOverlay(
    scanner: Boolean,
    entryDetail: Boolean,
    productReview: Boolean,
    resolver: Boolean,
    offSearch: Boolean,
    foodForm: Boolean,
    libraryFood: Boolean,
    entryEdit: Boolean,
    settings: Boolean,
): Overlay? = when {
    scanner -> Overlay.SCANNER
    entryDetail -> Overlay.ENTRY_DETAIL
    productReview -> Overlay.PRODUCT_REVIEW
    resolver -> Overlay.RESOLVER
    offSearch -> Overlay.OFF_SEARCH
    foodForm -> Overlay.FOOD_FORM
    settings -> Overlay.SETTINGS
    libraryFood -> Overlay.LIBRARY_FOOD
    entryEdit -> Overlay.ENTRY_EDIT
    else -> null
}

/** Where leaving the Library goes. */
internal enum class LibraryExit { LOG, LIBRARY, MEAL, DAILY }

/**
 * The Library's arrow and system Back both go here: a part being resolved back to the Log, Archived foods to the
 * Library, a Library opened from a meal ([origin]) back to that meal, and the Library tab to Daily.
 */
internal fun exitLibraryTarget(resolving: Boolean, archivedOnly: Boolean, origin: MealSlot?): LibraryExit = when {
    resolving -> LibraryExit.LOG
    archivedOnly -> LibraryExit.LIBRARY
    origin != null -> LibraryExit.MEAL
    else -> LibraryExit.DAILY
}

/** What Back or the arrow does on New food or Edit food. */
internal enum class FoodFormBack { IGNORE, ASK, CLOSE }

/** While a save runs, Back waits for it; a changed form asks before discarding; an unchanged one closes. */
internal fun foodFormBack(saving: Boolean, dirty: Boolean): FoodFormBack = when {
    saving -> FoodFormBack.IGNORE
    dirty -> FoodFormBack.ASK
    else -> FoodFormBack.CLOSE
}

/**
 * The day the diary shows. While the app is on screen ([returning] false) the selection never moves: once the day
 * starts over, the old day simply stops being today. Back from the background, or reopened or restored after being
 * closed, a user who was [followingToday] lands on the new today; an older day they picked stays, and a day that is
 * no longer past (the clock or the day start moved) becomes today. Today is [dayKeyFor] at [nowMs].
 */
internal fun resolveSelectedDay(
    selected: String?,
    followingToday: Boolean,
    returning: Boolean,
    nowMs: Long,
    zone: ZoneId,
    dayStartHour: Int,
): String {
    val today = dayKeyFor(nowMs, zone, dayStartHour)
    return when {
        selected == null -> today
        !returning -> selected
        followingToday || selected > today -> today
        else -> selected
    }
}

/** How long an Undo offer stays: from the log or delete it undoes. */
internal const val UNDO_WINDOW_MS = 15_000L

/** True while an Undo offered at [offeredAtMs] can still be used. A clock set back before the offer closes it. */
internal fun undoOpen(offeredAtMs: Long, nowMs: Long, windowMs: Long = UNDO_WINDOW_MS): Boolean =
    nowMs >= offeredAtMs && nowMs - offeredAtMs < windowMs

/** What is left of an Undo offer's window at [nowMs]; 0 once it has closed. */
internal fun undoRemainingMs(offeredAtMs: Long, nowMs: Long, windowMs: Long = UNDO_WINDOW_MS): Long =
    if (undoOpen(offeredAtMs, nowMs, windowMs)) offeredAtMs + windowMs - nowMs else 0L
