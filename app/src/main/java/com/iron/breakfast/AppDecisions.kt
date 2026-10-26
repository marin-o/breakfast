package com.iron.breakfast

import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.NutritionInput
import com.iron.breakfast.domain.ResolvedNutrition
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.dayKeyFor
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import com.iron.breakfast.scan.ProductReviewOrigin
import com.iron.breakfast.ui.label
import com.iron.breakfast.today.UndoOffer

// Pure routing and screen decisions the view model makes. Kept out of the Activity file so each can be unit-tested.

/** A personal-food form field that a refused save points at. */
internal enum class FoodFormField { NAME, BARCODE, BASIS, KCAL, PROTEIN, CARBS, FAT }

/** Why a personal-food save is refused: the notice, and the field to move focus to. */
internal data class FoodSaveRefusal(val message: String, val field: FoodFormField)

/**
 * The personal-food save checks, in order: a structured number that is not a number, name and kcal, the barcode,
 * the local bounds (kcal 0 to 10000, macros 0 to 1000 per basis), then a positive basis. Null when the food may save.
 */
internal fun foodSaveRefusal(name: String, input: NutritionInput, resolved: ResolvedNutrition, barcodeError: String?): FoodSaveRefusal? {
    val invalidNumbers = "Nutrition values must be finite and nonnegative."
    if (resolved.invalid) {
        val field = listOf(
            FoodFormField.KCAL to input.calories, FoodFormField.PROTEIN to input.protein, FoodFormField.CARBS to input.carbs,
            FoodFormField.FAT to input.fat, FoodFormField.BASIS to input.basisAmount,
        ).firstOrNull { (_, text) -> text.isNotBlank() && parseDecimal(text) == null }?.first ?: FoodFormField.KCAL
        return FoodSaveRefusal(invalidNumbers, field)
    }
    if (name.isBlank()) return FoodSaveRefusal("Name and calories are required.", FoodFormField.NAME)
    val kcal = resolved.calories ?: return FoodSaveRefusal("Name and calories are required.", FoodFormField.KCAL)
    if (barcodeError != null) return FoodSaveRefusal(barcodeError, FoodFormField.BARCODE)
    if (!kcal.isFinite() || kcal < 0.0 || kcal > 10000.0) return FoodSaveRefusal(invalidNumbers, FoodFormField.KCAL)
    listOf(FoodFormField.PROTEIN to resolved.protein, FoodFormField.CARBS to resolved.carbohydrates, FoodFormField.FAT to resolved.fat)
        .firstOrNull { (_, value) -> value != null && (!value.isFinite() || value < 0.0 || value > 1000.0) }
        ?.let { return FoodSaveRefusal(invalidNumbers, it.first) }
    if (!resolved.basisAmount.isFinite() || resolved.basisAmount <= 0.0) return FoodSaveRefusal("Enter a positive nutrition basis.", FoodFormField.BASIS)
    return null
}

internal fun productReviewOrigin(slot: MealSlot?, resolving: Boolean, queueNotEmpty: Boolean): ProductReviewOrigin = when {
    resolving && queueNotEmpty -> ProductReviewOrigin.QUEUE
    slot != null -> ProductReviewOrigin.MEAL
    else -> ProductReviewOrigin.LIBRARY
}

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
    /** Nothing to do: the app is not ready or a save is already running. */
    data object Ignore : LogSubmitPlan
    data class Error(val message: String) : LogSubmitPlan
    /** No meal is known: open the Log's meal menu; choosing a meal submits. */
    data object OpenMenu : LogSubmitPlan
    data class Save(val target: MealSlot) : LogSubmitPlan
}

/** Decides what Add does with the Log input. Pure, so the no-meal path and the busy guard can be unit-tested. */
internal fun logSubmitPlan(
    ready: Boolean, busy: Boolean, input: String, typedMarkers: List<MealSlot>, chosen: MealSlot?, preset: MealSlot?,
    automatic: MealSlot? = null,
): LogSubmitPlan {
    if (!ready || busy) return LogSubmitPlan.Ignore
    if (input.isBlank()) return LogSubmitPlan.Error("Add at least one food.")
    if (typedMarkers.size > 1) return LogSubmitPlan.Error("Choose one meal marker, or remove the extra markers.")
    val target = logTargetMealOf(chosen, typedMarkers, preset, automatic) ?: return LogSubmitPlan.OpenMenu
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

/**
 * The kind of a diary change, for [undoEndsAt]. [FOOD_EDIT] is a food saved, edited, archived or restored without a log;
 * [UNDO] an Undo applied; [REPLACE] the whole diary replaced (import) or deleted; [EDIT_AFTER_CLOSE] an entry edit
 * that committed after its edit had closed.
 */
internal enum class DiaryChangeKind { LOG, EDIT, EDIT_AFTER_CLOSE, DELETE, NOT_RIGHT_PICK, CONFIRM_MATCH, FOOD_EDIT, UNDO, REPLACE }

/**
 * Whether [change] ends a pending Undo offer, in every Undo mode ("Until next action" included): the next log, edit
 * or delete does. A "Not right?" pick or a confirmed match keeps it (B4: the batch Undo survives a correction; the
 * corrected entry is then left by Undo, which skips entries changed since), so an "Until next action" offer still ends
 * at the next log, edit or delete and never stays for good. An edit that committed after its edit closed keeps it
 * too: the user had already left that edit, and its save has never ended Undo.
 */
internal fun undoEndsAt(change: DiaryChangeKind): Boolean = when (change) {
    DiaryChangeKind.LOG, DiaryChangeKind.EDIT, DiaryChangeKind.DELETE, DiaryChangeKind.UNDO, DiaryChangeKind.REPLACE -> true
    DiaryChangeKind.NOT_RIGHT_PICK, DiaryChangeKind.CONFIRM_MATCH, DiaryChangeKind.FOOD_EDIT, DiaryChangeKind.EDIT_AFTER_CLOSE -> false
}

/** A logged batch replaces the offer; a batch that saved nothing ([logged] null) keeps [current]. */
internal fun undoAfterLog(current: UndoOffer?, logged: UndoOffer.Logged?): UndoOffer? = logged ?: current

/** An activity stop counts as leaving the app unless it is a recreation for a configuration change. */
internal fun stopMeansLeaving(changingConfigurations: Boolean): Boolean = !changingConfigurations

/** How long an Undo offer stays by default: from the log or delete it undoes. */
internal const val UNDO_WINDOW_MS = 15_000L

/**
 * True while an Undo offered at [offeredAtMs] can still be used. A clock set back before the offer closes it. A null
 * [windowMs] ("Until next action") never runs out: only the next log, edit or delete replaces the offer.
 */
internal fun undoOpen(offeredAtMs: Long, nowMs: Long, windowMs: Long? = UNDO_WINDOW_MS): Boolean =
    nowMs >= offeredAtMs && (windowMs == null || nowMs - offeredAtMs < windowMs)

/** What is left of an Undo offer's window at [nowMs]; 0 once it has closed. */
internal fun undoRemainingMs(offeredAtMs: Long, nowMs: Long, windowMs: Long = UNDO_WINDOW_MS): Long =
    if (undoOpen(offeredAtMs, nowMs, windowMs)) offeredAtMs + windowMs - nowMs else 0L

/**
 * One attempt to open storage: true when [open] completed. A failure goes to [onFailure] (the log) and is not thrown,
 * so the caller can offer Try again; cancellation is thrown as usual.
 */
internal suspend fun openStorage(open: suspend () -> Unit, onFailure: (Exception) -> Unit): Boolean =
    try {
        open()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        false
    }

/**
 * A "Not in your foods" row still names its text in [draft] (MOB-025): the Log draws rows from a draft computed off
 * the main thread, so after a keystroke a tapped row's span may hold other text, and that tap must do nothing.
 */
internal fun rowStillInDraft(draft: String, row: DraftRow.Unresolved): Boolean =
    row.start >= 0 && row.start < row.end && row.end <= draft.length && draft.substring(row.start, row.end) == row.text
