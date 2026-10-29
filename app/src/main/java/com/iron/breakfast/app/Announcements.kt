package com.iron.breakfast.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * What TalkBack reads out for a snackbar that comes and goes ("Logged!", an Undo offer), each once, as it read the
 * dialogs those replaced. A node that appears is not reliably read, and a repeat of the same text changes nothing on
 * screen, so the owners [say] each new one here, and the root reads out what [take] gives every time [serial] moves.
 *
 * Kept with the view model: a recreated screen (rotation, dark mode) takes nothing it already read.
 */
internal class Announcements {
    /** Counts what was said; a repeat of the same text is a new one. */
    var serial by mutableIntStateOf(0); private set
    private val pending = ArrayDeque<String>()

    fun say(text: String) {
        pending.addLast(text)
        serial++
    }

    /** What to read out now, oldest first; each is given once. */
    fun take(): List<String> = pending.toList().also { pending.clear() }
}
