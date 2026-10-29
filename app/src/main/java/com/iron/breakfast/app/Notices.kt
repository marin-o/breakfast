package com.iron.breakfast.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app's one modal message: the result of a diary write ("Saved to your food library.", "Could not undo."), an
 * Open Food Facts or scanner outcome ("No connection. Add the product manually."), or a refused input ("Choose one
 * meal marker."). A food logged where the user chose says so in [Confirmations] instead, which is not a dialog.
 *
 * An app-level channel, not part of the diary: most messages are not about a diary write, so it is not owned by
 * [DiarySession]. The view model builds one and hands it to the features that speak through it.
 *
 * It is state, not a one-time event (guide §6.5): [message] stays until [dismiss], so a recreation draws it again
 * and nothing is lost while no screen is drawn. There is one at a time; a new message replaces the one showing.
 */
internal class Notices {
    var message by mutableStateOf<String?>(null); private set

    private var onDismiss: (() -> Unit)? = null

    /**
     * Shows [message], replacing the one showing. [onDismiss] runs when the user dismisses this one, not when another
     * replaces it (the damaged-diary notice clears its marker only once it was seen).
     */
    fun show(message: String, onDismiss: (() -> Unit)? = null) {
        this.message = message
        this.onDismiss = onDismiss
    }

    fun dismiss() {
        val seen = onDismiss
        message = null
        onDismiss = null
        seen?.invoke()
    }
}
