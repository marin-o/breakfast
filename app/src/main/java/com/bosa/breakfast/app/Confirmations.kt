package com.bosa.breakfast.app

import com.bosa.breakfast.ui.components.ToastSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * "Logged!", the short confirmation after a food is logged to the meal and day the user just chose, so it does not
 * name them again. Daily, Meal, the Library and the resolver draw it as a toast near the bottom, and it goes by itself
 * [durationMs] after it was shown (a new one starts the time again), or by a swipe. Errors stay [Notices].
 *
 * TalkBack reads each confirmation once, a repeat too: every [logged] is said through
 * [announce] ([Announcements]).
 *
 * Its time is its [slot]'s, in [scope] (the view model's), waiting with [sleep].
 */
internal class Confirmations(
    scope: CoroutineScope,
    private val announce: (String) -> Unit,
    private val durationMs: Long = CONFIRMATION_MS,
    sleep: suspend (Long) -> Unit = { delay(it) },
) {
    val slot = ToastSlot<String>(scope, sleep)
    /** The confirmation showing, or null. */
    val message: String? get() = slot.shown

    /** A food was logged where the user chose. */
    fun logged() {
        slot.show(LOGGED, durationMs)
        announce(LOGGED)
    }
}

/** The text of [Confirmations.logged], and of the Undo offer of a logged batch. */
internal const val LOGGED = "Logged!"

/** How long a confirmation shows: a short toast's time. */
internal const val CONFIRMATION_MS = 4_000L
