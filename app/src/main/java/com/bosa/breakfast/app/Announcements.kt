package com.bosa.breakfast.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * What TalkBack reads out for a toast that comes and goes ("Logged!", an Undo offer, a notice), each once, as it read
 * the dialogs those replaced. A node that appears is not reliably read, and a repeat of the same text changes nothing on
 * screen, so the owners [say] each new one here, and the root reads out what [take] gives every time [serial] moves.
 *
 * Kept with the view model: a recreated screen (rotation, dark mode) takes nothing it already read. While the app is
 * away ([stop] to [start]) nothing is kept: on return only what happens from then on is read, not a burst of old ones.
 */
internal class Announcements {
    /** Counts what was said; a repeat of the same text is a new one. */
    var serial by mutableIntStateOf(0); private set
    private val pending = ArrayDeque<String>()

    private var away = false

    fun say(text: String) {
        if (away) return
        pending.addLast(text)
        serial++
    }

    /** The app left the screen: what is waiting is dropped, and nothing is kept until [start]. */
    fun stop() {
        away = true
        pending.clear()
    }

    /** The app is on screen again. */
    fun start() { away = false }

    /** What to read out now, oldest first; each is given once. */
    fun take(): List<String> = pending.toList().also { pending.clear() }
}
