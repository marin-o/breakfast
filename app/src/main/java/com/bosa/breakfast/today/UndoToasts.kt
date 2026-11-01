package com.bosa.breakfast.today

import androidx.compose.runtime.Composable
import com.bosa.breakfast.ui.components.ToastReveal
import com.bosa.breakfast.ui.components.rememberTick

/**
 * The Undo offers, where they show: the logged batch and the deleted entry. The bottom area and the Log both draw these.
 * A swipe ends the offer it was made on; what was logged or deleted stays so.
 */
@Composable
internal fun UndoToasts(undo: UndoController, shown: Boolean = true) {
    val tick = rememberTick()
    ToastReveal(undo.loggedMessage.takeIf { shown }, undo.slot, UNDO, { tick(); undo.undoBatch() })
    ToastReveal(undo.deletedEntry?.takeIf { shown }?.let(::deletedText), undo.slot, UNDO, { tick(); undo.undoDelete() })
}
