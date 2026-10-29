package com.iron.breakfast.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Logged!", the short confirmation after a food is logged to the meal and day the user just chose, so it does not
 * name them again. Unlike [Notices] it is not a dialog: Daily, Meal and the Library draw it as a snackbar over their
 * composer, and it goes by itself [durationMs] after it was shown (a new one starts the time again). Errors stay
 * [Notices].
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
    private var timer: Job? = null

    /** A food was logged where the user chose. */
    fun logged() {
        timer?.cancel()
        message = LOGGED
        timer = scope.launch {
            sleep(durationMs)
            message = null
        }
    }
}

/** The text of [Confirmations.logged]. */
internal const val LOGGED = "Logged!"

/** How long a confirmation shows: a short snackbar's time. */
internal const val CONFIRMATION_MS = 4_000L
