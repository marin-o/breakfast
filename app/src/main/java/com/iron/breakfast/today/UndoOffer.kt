package com.iron.breakfast.today

import com.iron.breakfast.domain.MealEntry

/** A one-time Undo, shown as a snackbar until [UNDO_WINDOW_MS] after [offeredAtMs] or the next log, edit or delete. */
internal sealed interface UndoOffer {
    val offeredAtMs: Long
    /** The snackbar's text. */
    val text: String
    data class Deleted(val entry: MealEntry, override val offeredAtMs: Long) : UndoOffer {
        override val text: String get() = deletedText(entry)
    }
    /** A logged batch; [entries] are the rows as saved, so Undo can leave any entry edited since. */
    data class Logged(val message: String, val entries: List<MealEntry>, override val offeredAtMs: Long) : UndoOffer {
        override val text: String get() = message
    }
}

/** The delete snackbar's text ("Egg deleted"). */
internal fun deletedText(entry: MealEntry): String = "${entry.foodName} deleted"

/** The snackbars' action. */
internal const val UNDO = "Undo"

/** What TalkBack reads for a new offer: its text, then the action ("Logged! Undo", "Egg deleted. Undo"). */
internal fun undoAnnouncement(offer: UndoOffer): String =
    offer.text + (if (offer.text.lastOrNull() in listOf('.', '!', '?')) " " else ". ") + UNDO
