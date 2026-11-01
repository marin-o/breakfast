package com.bosa.breakfast.today

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.ui.components.Reveal
import com.bosa.breakfast.ui.components.Snackbar
import com.bosa.breakfast.ui.components.rememberTick

/** The Undo offers, where they show: the logged batch and the deleted entry. The bottom area and the Log both draw these. */
@Composable
internal fun UndoSnackbars(undo: UndoController, shown: Boolean = true) {
    val tick = rememberTick()
    Reveal(undo.loggedMessage.takeIf { shown }) { Snackbar(it, UNDO, { tick(); undo.undoBatch() }, Modifier.padding(bottom = 8.dp)) }
    Reveal(undo.deletedEntry.takeIf { shown }) { Snackbar(deletedText(it), UNDO, { tick(); undo.undoDelete() }, Modifier.padding(bottom = 8.dp)) }
}
