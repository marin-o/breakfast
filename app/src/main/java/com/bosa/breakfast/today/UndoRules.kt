package com.bosa.breakfast.today

import com.bosa.breakfast.app.DiaryChangeKind

// Undo's pure decisions, unit-tested: what ends an offer, and how long one stays.

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
