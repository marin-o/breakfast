package com.iron.breakfast.today

import com.iron.breakfast.domain.MealEntry

/** A one-time Undo, shown as a snackbar until [UNDO_WINDOW_MS] after [offeredAtMs] or the next log, edit or delete. */
internal sealed interface UndoOffer {
    val offeredAtMs: Long
    data class Deleted(val entry: MealEntry, override val offeredAtMs: Long) : UndoOffer
    /** A logged batch; [entries] are the rows as saved, so Undo can leave any entry edited since. */
    data class Logged(val message: String, val entries: List<MealEntry>, override val offeredAtMs: Long) : UndoOffer
}
