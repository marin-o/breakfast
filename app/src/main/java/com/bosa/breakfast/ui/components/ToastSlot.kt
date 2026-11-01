package com.bosa.breakfast.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What a [Toast]'s gestures reach. Each call names the show it belongs to ([showId]), so a gesture begun on one toast
 * can never close or pause a newer one that took its place.
 */
internal interface ToastOwner {
    val showId: Long
    fun close(showId: Long)
    /** The finger is on the toast ([held]) or has left it. */
    fun hold(showId: Long, held: Boolean)
}

/** A toast with no time of its own, which only [close] ends. */
internal fun closingToast(close: () -> Unit): ToastOwner = object : ToastOwner {
    override val showId = 0L
    override fun close(showId: Long) = close()
    override fun hold(showId: Long, held: Boolean) = Unit
}

/**
 * One toast and how long it stays: its timer runs in [scope] (the view model's) and waits with [sleep]. A new show
 * replaces the one showing, gets a new [showId] and starts its own time, also with the same text.
 *
 * Time stands still while the app is away ([stop] to [start]) and while a finger holds the toast ([hold]). When either
 * ends the time starts over: the whole duration of a [show], or what [showUntil]'s reading says is left.
 */
internal class ToastSlot<T : Any>(private val scope: CoroutineScope, private val sleep: suspend (Long) -> Unit) : ToastOwner {
    /** What shows, or null. */
    var shown by mutableStateOf<T?>(null); private set
    override var showId by mutableLongStateOf(0L); private set
    var away = false; private set

    private var held = false
    private var onClose: (() -> Unit)? = null
    /** How long is left, given whether a wait is already over; null stays without an end. */
    private var time: (Boolean) -> Long? = { null }
    private var timer: Job? = null
    /** Moves with every timer started or stopped, so one that wakes late ends nothing. */
    private var timerRun = 0L

    /** Shows [value] for [durationMs]; null stays until closed. [onClose] runs when this one closes, not when another replaces it. */
    fun show(value: T, durationMs: Long?, onClose: (() -> Unit)? = null) = place(value, onClose) { waited -> if (waited) 0L else durationMs }

    /** Shows [value] until [remainingMs], read after each wait, is used up; a null reading stays without an end. */
    fun showUntil(value: T, remainingMs: () -> Long?) = place(value, null) { remainingMs() }

    private fun place(value: T, onClose: (() -> Unit)?, time: (Boolean) -> Long?) {
        showId++
        shown = value
        held = false
        this.onClose = onClose
        this.time = time
        restartTime()
    }

    /** Closes the toast of [showId]; a newer one stays. */
    override fun close(showId: Long) {
        if (showId == this.showId) close()
    }

    /** Closes what shows. */
    fun close() {
        stopTime()
        val closed = onClose
        onClose = null
        held = false
        shown = null
        closed?.invoke()
    }

    override fun hold(showId: Long, held: Boolean) {
        if (showId != this.showId || shown == null) return
        this.held = held
        restartTime()
    }

    /** The app left the screen. */
    fun stop() {
        away = true
        stopTime()
    }

    /** The app is on screen again. */
    fun start() {
        if (!away) return
        away = false
        restartTime()
    }

    /** Starts the time of what shows again, under the same [showId]. */
    fun restartTime() {
        stopTime()
        if (shown == null || away || held) return
        val run = timerRun
        val time = time
        timer = scope.launch {
            var waited = false
            while (true) {
                val left = time(waited) ?: return@launch
                if (left <= 0L) break
                sleep(left)
                waited = true
            }
            if (run == timerRun) close()
        }
    }

    private fun stopTime() {
        timerRun++
        timer?.cancel()
        timer = null
    }
}
