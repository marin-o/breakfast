package com.iron.breakfast.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.openStorage
import com.iron.breakfast.suspendRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What [DiarySession] reads from storage. The view model adapts `BreakfastEngine`; tests fake it. */
internal interface DiarySource {
    /** Opens storage (seeding and syncing the catalogue); throws when it cannot. */
    suspend fun open()
    fun foods(): List<FoodRecord>
    fun entries(): List<MealEntry>
    /** Reloads the snapshot a committed write could not reload; true when it did. */
    suspend fun reloadIfStale(): Boolean
}

/** How a write takes the gate (see [DiarySession.write]). */
internal enum class GateEntry {
    /**
     * A user action: refused (nothing runs, null is returned) unless storage is open and no write runs. The gate is
     * held from the call itself, before the launch, so a second tap in the same frame is refused.
     */
    NOW,
    /**
     * A document-picker result (export, import), which can arrive before storage is open or while a write runs, for
     * example after a recreation: it waits until storage is open and no write runs, then holds the gate.
     */
    WHEN_IDLE,
}

/**
 * The diary as the app shows it, and the one way to write it.
 *
 * - The read model: [foods] and [entries], reloaded by [refresh]; [ready] once storage opened, [storageUnavailable]
 *   while it could not be.
 * - The write gate: [busy] is "one diary write at a time", a UX rule over the store's own lock. Every write goes
 *   through [write], which holds the gate and, after a committed write, reloads the read model and publishes a
 *   [DiaryChange] to the listeners registered with [onChange] (Undo, the Library list).
 * - The notice channel: [notice] is the one modal message ("Added to Lunch", a failure); a new one replaces it.
 *
 * Coroutines run in [scope] (the view model's, on Main); [source] does its own dispatching.
 */
internal class DiarySession(
    private val source: DiarySource,
    private val scope: CoroutineScope,
    /** Storage could not be opened; the cause goes to the log only. */
    private val onOpenFailed: (Exception) -> Unit,
    /** Runs after every reload, for state derived from [entries] (the open entry detail). */
    private val onRefreshed: () -> Unit = {},
) {
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    /** Storage failed to open; the dialog stays until a retry ([open] again) opens it. */
    var storageUnavailable by mutableStateOf(false); private set
    var foods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var entries by mutableStateOf<List<MealEntry>>(emptyList()); private set
    /** The single modal message; [dismissNotice] clears it. */
    var notice by mutableStateOf<String?>(null); private set

    private val listeners = mutableListOf<(DiaryChange) -> Unit>()
    private var opening = false

    fun show(message: String) { notice = message }
    fun dismissNotice() { notice = null }

    /** [listener] runs after each published change, in registration order, after the read model was reloaded. */
    fun onChange(listener: (DiaryChange) -> Unit) { listeners += listener }

    /** Opens storage once; [onOpened] runs when it opened. On failure [storageUnavailable] is set and a later call retries. */
    fun open(onOpened: () -> Unit) {
        if (ready || opening) return
        opening = true
        scope.launch {
            val opened = openStorage({ source.open(); refresh() }, onOpenFailed)
            opening = false
            storageUnavailable = !opened
            if (opened) {
                ready = true
                onOpened()
            }
        }
    }

    /** Reloads [foods] and [entries]. A write that committed but could not reload the diary is reloaded now, then read again. */
    fun refresh() {
        foods = source.foods()
        entries = source.entries()
        onRefreshed()
        scope.launch { if (suspendRunCatching { source.reloadIfStale() }.getOrDefault(false)) refresh() }
    }

    /**
     * Runs [run] as the one diary write, holding [busy] until it ends (cleared in finally, so a throwing handler cannot
     * leave the app stuck busy). When [run] returns, [change] says what changed: a non-null change reloads the read
     * model (when [DiaryChange.rowsChanged]) and is published to the listeners, then [onSuccess] runs. A null change
     * (nothing committed, or the result is no longer wanted) publishes nothing.
     *
     * - [entry]: [GateEntry.NOW] for user actions, [GateEntry.WHEN_IDLE] for picker results.
     * - [nonCancellable]: the whole write, the fan-out and both handlers run to the end even if the returned job is
     *   cancelled (the full entry edit, which owns its save until it ends). Otherwise a cancel stops at the next
     *   suspension and runs nothing after it; a [run] that must finish wraps its own work in NonCancellable.
     * - [onFailure]: a failure of [run] (never a [kotlinx.coroutines.CancellationException], which is rethrown) goes
     *   here. Null lets it propagate, for runs that catch everything they expect themselves.
     *
     * Returns the write's job, or null when the gate refused it.
     */
    fun <T> write(
        change: (T) -> DiaryChange?,
        entry: GateEntry = GateEntry.NOW,
        nonCancellable: Boolean = false,
        onFailure: ((Throwable) -> Unit)? = null,
        onSuccess: (T) -> Unit = {},
        run: suspend () -> T,
    ): Job? {
        if (entry == GateEntry.NOW) {
            if (!ready || busy) return null
            busy = true
        }
        return scope.launch {
            if (entry == GateEntry.WHEN_IDLE) {
                awaitIdle()
                busy = true
            }
            try {
                if (nonCancellable) withContext(NonCancellable) { complete(run, change, onFailure, onSuccess) }
                else complete(run, change, onFailure, onSuccess)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun <T> complete(
        run: suspend () -> T,
        change: (T) -> DiaryChange?,
        onFailure: ((Throwable) -> Unit)?,
        onSuccess: (T) -> Unit,
    ) {
        val result = if (onFailure == null) run() else suspendRunCatching { run() }.getOrElse { onFailure(it); return }
        change(result)?.let(::publish)
        onSuccess(result)
    }

    private fun publish(change: DiaryChange) {
        if (change.rowsChanged) refresh()
        listeners.forEach { it(change) }
    }

    /** Waits until storage is open and no write is running. */
    private suspend fun awaitIdle() {
        while (!ready || busy) snapshotFlow { ready && !busy }.first { it }
    }
}
