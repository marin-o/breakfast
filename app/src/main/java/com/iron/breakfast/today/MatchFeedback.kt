package com.iron.breakfast.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.suspendRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What "Not right?" reads and writes. The view model adapts `BreakfastEngine`; tests fake it. */
internal interface MatchStore {
    /** Other foods [entry]'s typed text could have meant, best first. */
    fun alternativesFor(entry: MealEntry): List<RankedFood>
    suspend fun replaceEntry(entryId: String, foodId: String): ReplacementResult
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
) {
    /** The entries whose strip is open. */
    var ids by mutableStateOf<Set<String>>(emptySet()); private set
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
            DiaryChange.DiaryReplaced -> clear()
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
     * the strip. The pending Undo stays ([com.iron.breakfast.undoEndsAt]).
     */
    fun chooseAlternative(entry: MealEntry, food: FoodRecord) {
        if (!isOpen(entry)) return
        session.write(
            change = { pick: Pick -> DiaryChange.MatchCorrected.takeIf { pick.result is ReplacementResult.Success } },
            onSuccess = { pick ->
                when (val result = pick.result) {
                    is ReplacementResult.Success -> if (pick.learned) ids = ids - entry.id
                    is ReplacementResult.Failure -> notices.show(result.reason)
                }
            },
            onFailure = {},
        ) {
            val result = store.replaceEntry(entry.id, food.id)
            val learned = result is ReplacementResult.Success && suspendRunCatching { store.reinforce(food.id) }.isSuccess
            Pick(result, learned)
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
