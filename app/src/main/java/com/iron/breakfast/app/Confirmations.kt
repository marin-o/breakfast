package com.iron.breakfast.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Logged!", the short confirmation after a food is logged to the meal and day the user just chose, so it does not
 * name them again. Unlike [Notices] it is not a dialog: Daily, Meal, the Library and the resolver draw it as a
 * snackbar near the bottom, and it goes by itself [durationMs] after it was shown (a new one starts the time again).
 * Errors stay [Notices].
 *
 * TalkBack reads each confirmation once, as it read the dialog: every [logged] is a new [serial], also while "Logged!"
 * still shows, and the root reads out what [takeAnnouncement] gives for it. That is kept here, with the view model,
 * so a recreated screen (rotation, dark mode) does not read it again.
 *
 * The timer runs in [scope] (the view model's) and waits with [sleep].
 */
internal class Confirmations(
    private val scope: CoroutineScope,
    private val durationMs: Long = CONFIRMATION_MS,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** The confirmation showing, or null. */
    var message by mutableStateOf<String?>(null); private set
    /** Counts the confirmations made; a repeat of the same text is a new one. */
    var serial by mutableIntStateOf(0); private set
    private var announced = 0
    private var timer: Job? = null

    /** A food was logged where the user chose. */
    fun logged() {
        timer?.cancel()
        message = LOGGED
        serial++
        timer = scope.launch {
            sleep(durationMs)
            message = null
        }
    }

    /** What to read out for the newest confirmation, once; null when it was read already or has gone. */
    fun takeAnnouncement(): String? {
        if (serial == announced) return null
        announced = serial
        return message
    }
}

/** The text of [Confirmations.logged]. */
internal const val LOGGED = "Logged!"

/** How long a confirmation shows: a short snackbar's time. */
internal const val CONFIRMATION_MS = 4_000L
