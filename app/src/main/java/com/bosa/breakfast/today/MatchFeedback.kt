package com.bosa.breakfast.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.DiarySession
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.Measure
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.RankedFood
import com.bosa.breakfast.domain.ReplacementResult
import com.bosa.breakfast.suspendRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What "Not right?" reads and writes. The view model adapts `BreakfastEngine`; tests fake it. */
internal interface MatchStore {
    /** Other foods [entry]'s typed text could have meant, best first. */
    fun alternativesFor(entry: MealEntry): List<RankedFood>
    suspend fun replaceEntry(entryId: String, foodId: String): ReplacementResult
    /** The entry becomes [amount] of [unit] of [foodId], in the same meal and day. */
    suspend fun replaceEntryWith(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, servingName: String?, meal: MealSlot, dayKey: String): ReplacementResult
    /** Learns that [foodId] was the right food (a no-op while Learn from my picks is off). */
    suspend fun reinforce(foodId: String)
}

/**
 * "Not right?" feedback: after a logged batch, each entry it saved by an approximate conversion shows a correction
 * strip (confirm the food, or pick another) for [WINDOW_MS].
 *
 * - [onChange] (the session listener) opens it for a batch ([DiaryChange.BatchLogged]) and clears it when the diary
 *   is replaced. Nothing else writes [ids].
 * - The window is held while the resolver is open ([resolverOpen], an input from the resolve flow), so feedback
 *   started before a resolve is still there after it; otherwise it expires. The timer checks once a [TICK_MS].
 * - [confirm] and [chooseAlternative] write through [session]. A pick keeps the batch Undo (B4: it ends only at the
 *   next log, edit or delete; batch Undo then leaves the corrected entry).
 * - [search] leaves the strip for a food found elsewhere: Open Food Facts, a scan or a new food, all as
 *   `ProductReviewOrigin.CORRECTION`, which only this opens, so [correctingId] is read only on that way. Whatever it
 *   finds comes back through [correct] and replaces the entry; the window has no say by then. [correctingId] comes back
 *   after process death with the form that may be open on it.
 *
 * The timer runs in [scope] (the view model's) and waits with [sleep]; [clock] times the window.
 */
internal class MatchFeedback(
    private val session: DiarySession,
    private val store: MatchStore,
    private val notices: Notices,
    /** True while the resolver is open; read on each tick. */
    private val resolverOpen: () -> Boolean,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    /** The search a correction goes to, on the entry's food name. */
    private val openSearch: (String) -> Unit = {},
    initialCorrectingId: String? = null,
) {
    /** The entries whose strip is open. */
    var ids by mutableStateOf<Set<String>>(emptySet()); private set
    /** The entry the last [search] was for. */
    var correctingId by mutableStateOf(initialCorrectingId); private set
    /** When the window ends; 0 when no feedback runs. */
    private var until = 0L
    private var timer: Job? = null

    fun isOpen(entry: MealEntry): Boolean = entry.id in ids && clock() < until

    /** The strip's other foods; none once its feedback ended. */
    fun alternatives(entry: MealEntry): List<RankedFood> = if (isOpen(entry)) store.alternativesFor(entry) else emptyList()

    /** The session listener. */
    fun onChange(change: DiaryChange) {
        when (change) {
            is DiaryChange.BatchLogged -> show(change.entries.filter { it.approximateConversion }.mapTo(mutableSetOf()) { it.id })
            // Nothing from the old diary may be confirmed into the new one.
            DiaryChange.DiaryReplaced -> { clear(); correctingId = null }
            else -> Unit
        }
    }

    /** "It was right": the food is learned and the strip closes. A failure is silent and leaves the strip. */
    fun confirm(entry: MealEntry) {
        if (!isOpen(entry)) return
        session.write(DiaryChange.MatchConfirmed, onSuccess = { ids = ids - entry.id }, onFailure = {}) { store.reinforce(entry.foodId) }
    }

    /**
     * A "Not right?" pick: the entry becomes [food], which is learned, and the strip closes. A refused replacement says
     * why. A replaced entry is reloaded even when learning the pick then failed; that failure is silent and leaves
     * the strip. The pending Undo stays ([com.bosa.breakfast.undoEndsAt]).
     */
    fun chooseAlternative(entry: MealEntry, food: FoodRecord) {
        if (!isOpen(entry)) return
        session.write(
            change = { pick: Pick -> DiaryChange.MatchCorrected.takeIf { pick.result is ReplacementResult.Success } },
            onSuccess = { pick -> settle(entry.id, pick) },
            onFailure = {},
        ) { learnedPick(food.id, store.replaceEntry(entry.id, food.id)) }
    }

    /** Search on the strip: what is found from here replaces [entry]. */
    fun search(entry: MealEntry) {
        if (!isOpen(entry)) return
        correctingId = entry.id
        openSearch(entry.foodName)
    }

    /** The entry the last [search] was for, while the diary has it. */
    fun correctingEntry(): MealEntry? = session.entries.firstOrNull { it.id == correctingId }

    /**
     * A food found from [search], reviewed: [store] stores it, and in the same write the entry becomes [amount] of
     * [measure] of it. Stored, it goes to [onStored] whatever the replacement said; a refused one says why. Reported as a
     * food change: the Library lists the food, a pin follows it, and the pending Undo stays as for any pick. With no entry
     * left to correct (the diary was replaced under it), nothing is written: it says so and [onGone] runs.
     */
    fun correct(store: suspend () -> FoodRecord, amount: Double, measure: Measure, onGone: () -> Unit, onStored: (FoodRecord) -> Unit) {
        val id = correctingId ?: run {
            notices.show("That saved entry is no longer available.")
            onGone()
            return
        }
        // The entry gone, the plain replacement is what says so.
        val entry = correctingEntry()
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { (food, pick) ->
                if (pick.result is ReplacementResult.Success) correctingId = null
                settle(id, pick)
                onStored(food)
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) {
            val food = store()
            val result = if (entry == null) this.store.replaceEntry(id, food.id)
            else this.store.replaceEntryWith(id, food.id, amount, measure.unit, measure.name, entry.mealSlot, entry.dayKey)
            food to learnedPick(food.id, result)
        }
    }

    /** [result], learned when it replaced the entry. */
    private suspend fun learnedPick(foodId: String, result: ReplacementResult): Pick =
        Pick(result, result is ReplacementResult.Success && suspendRunCatching { store.reinforce(foodId) }.isSuccess)

    private fun settle(entryId: String, pick: Pick) {
        when (val result = pick.result) {
            is ReplacementResult.Success -> if (pick.learned) ids = ids - entryId
            is ReplacementResult.Failure -> notices.show(result.reason)
        }
    }

    /** Opens feedback for [entryIds] for a fresh window; none ends it. */
    private fun show(entryIds: Set<String>) {
        ids = entryIds
        until = if (entryIds.isEmpty()) 0L else clock() + WINDOW_MS
        timer?.cancel()
        timer = if (until == 0L) null else scope.launch {
            while (until > 0L) {
                sleep(TICK_MS)
                if (resolverOpen()) until += TICK_MS
                else if (clock() >= until) { expire(); break }
            }
        }
    }

    private fun clear() {
        timer?.cancel()
        timer = null
        expire()
    }

    private fun expire() {
        until = 0L
        ids = emptySet()
    }

    /** A pick as saved: the replacement, and whether the pick was then learned. */
    private data class Pick(val result: ReplacementResult, val learned: Boolean)

    companion object {
        /** How long feedback stays after a batch, not counting time with the resolver open. */
        const val WINDOW_MS = 30_000L
        const val TICK_MS = 1_000L
    }
}
