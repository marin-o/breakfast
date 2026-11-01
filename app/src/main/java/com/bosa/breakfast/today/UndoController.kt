package com.bosa.breakfast.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.DiarySession
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.app.undoAfterChange
import com.bosa.breakfast.domain.MealEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What Undo and Delete write. The view model adapts `BreakfastEngine`; tests fake it. */
internal interface UndoStore {
    suspend fun deleteEntry(id: String)
    /** Puts [entry] back with its own id and snapshot, so it keeps its history. */
    suspend fun restoreEntry(entry: MealEntry)
    /** Deletes [entries] in one transaction; an entry changed since it was logged stays. */
    suspend fun deleteEntries(entries: List<MealEntry>)
}

/**
 * The one pending Undo on Daily and Meal, and Delete, which offers it.
 *
 * - [offer] changes only here: by [onChange] after every committed diary write ([undoAfterChange]: a logged batch or
 *   a delete replaces it with its own, the next log, edit, delete, Undo or diary replace ends it, a "Not right?"
 *   pick, a confirmed match or a food change keep it), by its expiry once [windowMs] has passed since it was made,
 *   and by [windowChanged] when the Undo setting changes. A null window ("Until next action") never expires.
 * - [delete], [undoDelete] and [undoBatch] write through [session], so the gate, the reload and the published change
 *   are the session's; a failure says so through [notices].
 * - The entries of the last logged batch outlive its offer, for the resolve header ([lastBatchNames]): batch Undo and
 *   a diary replace clear them.
 * - Each new offer is said once through [announce] ([undoAnnouncement]), so TalkBack tells that Undo is there.
 *
 * The expiry job runs in [scope] (the view model's) and waits with [sleep]; [clock] stamps and checks offers.
 */
internal class UndoController(
    private val session: DiarySession,
    private val store: UndoStore,
    private val notices: Notices,
    /** The Undo setting's window, read each time it is needed; null for "Until next action". */
    private val windowMs: () -> Long?,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    /** Reads out a new offer (`app/Announcements`). */
    private val announce: (String) -> Unit,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    var offer by mutableStateOf<UndoOffer?>(null); private set
    /** The deleted entry the snackbar offers to put back. */
    val deletedEntry: MealEntry? get() = (offer as? UndoOffer.Deleted)?.entry
    /** The logged batch's snackbar text ("Logged!"). */
    val loggedMessage: String? get() = (offer as? UndoOffer.Logged)?.message
    private var expiry: Job? = null
    private var lastBatchIds: List<String> = emptyList()

    /** The session listener: every committed write moves the offer by [undoAfterChange]. */
    fun onChange(change: DiaryChange) {
        val next = undoAfterChange(offer, change, clock())
        if (next !== offer) {
            replace(next)
            next?.let { announce(undoAnnouncement(it)) }
        }
        when (change) {
            is DiaryChange.BatchLogged -> lastBatchIds = change.entries.map { it.id }
            DiaryChange.DiaryReplaced -> lastBatchIds = emptyList()
            else -> Unit
        }
    }

    /** The Undo setting changed: an offer showing now follows the new window from the moment it was made. */
    fun windowChanged() {
        val current = offer ?: return
        replace(current)
        liveOffer()
    }

    /** Deletes [entry]; the published change offers its Undo. */
    fun delete(entry: MealEntry) {
        session.write(DiaryChange.EntryDeleted(entry), onFailure = { notices.show(DELETE_FAILED) }) { store.deleteEntry(entry.id) }
    }

    /** Puts the deleted entry back with its own id and snapshot. An offer whose window ended is cleared instead. */
    fun undoDelete() {
        val deleted = liveOffer() as? UndoOffer.Deleted ?: return
        session.write(DiaryChange.UndoApplied, onFailure = { notices.show(UNDO_FAILED) }) { store.restoreEntry(deleted.entry) }
    }

    /** Deletes the logged batch in one transaction; an entry edited since it was logged stays. */
    fun undoBatch() {
        val logged = liveOffer() as? UndoOffer.Logged ?: return
        session.write(
            DiaryChange.UndoApplied,
            onSuccess = { lastBatchIds = emptyList() },
            onFailure = { notices.show(UNDO_FAILED) },
        ) { store.deleteEntries(logged.entries) }
    }

    /** Names of the foods the last logged batch saved that are still in the diary, for the resolve header. */
    fun lastBatchNames(): List<String> = lastBatchIds.mapNotNull { id -> session.entries.firstOrNull { it.id == id }?.foodName }

    /** Replaces the offer; null clears it. An offer clears itself when its window ends. */
    private fun replace(next: UndoOffer?) {
        expiry?.cancel()
        expiry = null
        offer = next
        if (next == null) return
        expiry = scope.launch {
            while (true) {
                // "Until next action": no timer, the next log, edit or delete replaces the offer.
                val window = windowMs() ?: return@launch
                val left = undoRemainingMs(next.offeredAtMs, clock(), window)
                if (left <= 0L) break
                sleep(left)
            }
            if (offer === next) offer = null
        }
    }

    /** The offer, while its window is open; an expired one is cleared. */
    private fun liveOffer(): UndoOffer? {
        val current = offer ?: return null
        if (undoOpen(current.offeredAtMs, clock(), windowMs())) return current
        replace(null)
        return null
    }
}

/** The notice when an Undo could not be applied. */
private const val UNDO_FAILED = "Could not undo."

/** The notice when a delete failed (the wording the app has always used). */
private const val DELETE_FAILED = "Could not edit this entry."
