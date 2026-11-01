package com.bosa.breakfast.app

import com.bosa.breakfast.ui.components.ToastSlot
import kotlinx.coroutines.CoroutineScope

/**
 * The app's one notice: the result of a diary write ("Saved to your food library.", "Could not undo."), an Open Food
 * Facts or scanner outcome ("No connection. Add the product manually."), or a refused input ("Choose one meal marker.").
 * It asks nothing: a toast with OK over every screen (`ui/components/NoticeToastHost`), gone after its time, by OK or by
 * a swipe. A food logged where the user chose says so in [Confirmations] instead.
 *
 * An app-level channel, not part of the diary: most messages are not about a diary write, so it is not owned by
 * [DiarySession]. The view model builds one and hands it to the features that speak through it.
 *
 * It is state, not a one-time event (guide §6.5): a recreation draws [message] again. There is one at a time; a new
 * message replaces the one showing, and is said once through [announce], once the app is back if it came while away.
 * Its time is its [slot]'s, in [scope] (the view model's), waiting with [sleep].
 */
internal class Notices(
    scope: CoroutineScope,
    sleep: suspend (Long) -> Unit,
    private val announce: (String) -> Unit = {},
) {
    val slot = ToastSlot<String>(scope, sleep)
    val message: String? get() = slot.shown

    private var unsaid = false

    /**
     * Shows [message], replacing the one showing, for [durationMs]; null stays until the user closes it. [onDismiss] runs
     * when this one closes, not when another replaces it (the damaged-diary notice clears its marker only once it was seen).
     */
    fun show(message: String, onDismiss: (() -> Unit)? = null, durationMs: Long? = NOTICE_MS) {
        unsaid = slot.away
        if (!unsaid) announce(message)
        slot.show(message, durationMs, onDismiss)
    }

    fun dismiss() = slot.close()

    /** The app left the screen. */
    fun stop() = slot.stop()

    /** The app is on screen again: a notice showing gets its whole time, and one that came meanwhile is said now. */
    fun start() {
        slot.start()
        if (unsaid) slot.shown?.let(announce)
        unsaid = false
    }
}

/** A notice's action. */
internal const val NOTICE_ACTION = "OK"

/** How long a notice shows: longer than "Logged!", as it has more to read. */
internal const val NOTICE_MS = 6_000L
