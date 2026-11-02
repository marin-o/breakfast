package com.bosa.breakfast.app

import com.bosa.breakfast.domain.TargetRow
import com.bosa.breakfast.domain.WeightPoint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.Recipe
import com.bosa.breakfast.suspendRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** What [DiarySession] reads from storage. The view model adapts `BreakfastEngine`; tests fake it. */
internal interface DiarySource {
    /** Opens storage (seeding and syncing the catalogue); throws when it cannot. */
    suspend fun open()
    fun foods(): List<FoodRecord>
    fun entries(): List<MealEntry>
    /** The recipes, each ingredient following its food while it exists. */
    fun recipes(): List<Recipe> = emptyList()
    /** The profile's weight curve. */
    fun weightPoints(): List<WeightPoint> = emptyList()
    /** The targets by the day they took effect. */
    fun targetHistory(): List<TargetRow> = emptyList()
    /** Reloads the snapshot a committed write could not reload; true when it did. */
    suspend fun reloadIfStale(): Boolean
    /** A damaged diary was set aside (D15) and the user has not seen the notice yet. */
    fun damagedNoticePending(): Boolean = false
    /** The user saw that notice. */
    fun damagedNoticeSeen() {}
    /** True once after a reload that went on with a fresh diary after damage: the session resets as after an import. */
    fun takeDiaryReplaced(): Boolean = false
}

/**
 * The diary as the app shows it, and the one way to write it.
 *
 * - The read model: [foods], [entries] and [recipes], reloaded by [refresh]; [ready] once storage opened, [storageUnavailable]
 *   while it could not be.
 * - The write gate: [busy] is "one diary write at a time", a UX rule over the store's own lock. Every write goes
 *   through [write], which holds the gate and, after a committed write, reloads the read model and publishes a
 *   [DiaryChange] to the listeners registered with [onChange] (Undo, match feedback, the Library list). Work that
 *   only needs the diary to stand still (export, reading an import file) runs through [whenIdle].
 *
 * Messages are not the diary's: they go through the app's [Notices].
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

    /**
     * A user write could start now: storage is open and no write runs. The one rule of the gate: [write] refuses by it,
     * and an owner asks it only to refuse earlier, before it validates, says something or changes its own state.
     */
    val canWrite: Boolean get() = ready && !busy
    var foods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var entries by mutableStateOf<List<MealEntry>>(emptyList()); private set
    var recipes by mutableStateOf<List<Recipe>>(emptyList()); private set
    var weightPoints by mutableStateOf<List<WeightPoint>>(emptyList()); private set
    var targetHistory by mutableStateOf<List<TargetRow>>(emptyList()); private set

    private val listeners = mutableListOf<(DiaryChange) -> Unit>()
    private var opening = false

    /** A damaged diary was set aside and the user has not seen the notice yet (see [DiarySource.damagedNoticePending]). */
    fun damagedNoticePending(): Boolean = source.damagedNoticePending()

    /** The user saw that notice. */
    fun damagedNoticeSeen() = source.damagedNoticeSeen()

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

    /** A [DiarySource.reloadIfStale] is running ([checkStale]). */
    private var checking = false
    /** [refresh] was called while one ran: one more check follows it, however many calls came. */
    private var checkAgain = false

    /** Reloads [foods], [entries] and [recipes]. A write that committed but could not reload the diary is reloaded now, then read again. */
    fun refresh() {
        foods = source.foods()
        entries = source.entries()
        recipes = source.recipes()
        weightPoints = source.weightPoints()
        targetHistory = source.targetHistory()
        onRefreshed()
        checkStale()
    }

    /**
     * Asks the source to reload what a committed write could not ([DiarySource.reloadIfStale]): at most one check runs
     * at a time, and the calls made meanwhile become one more check after it. A check that reloaded reads the diary
     * again, which asks once more, as each refresh did.
     */
    private fun checkStale() {
        if (checking) { checkAgain = true; return }
        checking = true
        scope.launch {
            try {
                do {
                    checkAgain = false
                    if (suspendRunCatching { source.reloadIfStale() }.getOrDefault(false)) {
                        // A fresh diary after damage: everything that referred to the old one goes, as after an import.
                        if (source.takeDiaryReplaced()) publish(DiaryChange.DiaryReplaced) else refresh()
                    }
                } while (checkAgain)
            } finally {
                checking = false
            }
        }
    }

    /**
     * Runs [run] as the one diary write, a user action: refused (nothing runs, null is returned) unless storage is open
     * and no write runs. The gate is held from the call itself, before the launch, so a second tap in the same frame is
     * refused, and released in finally, so a throwing handler cannot leave the app stuck busy.
     *
     * When [run] returns, [change] names what it committed: a change with [DiaryChange.rowsChanged] reloads the read
     * model, then the change is published to the listeners, then [onSuccess] runs. Null means nothing was committed
     * (a refused edit), so nothing is reloaded or published. A write that committed rows always names a change, even
     * when no screen wants its result any more (see [DiaryChange.EntryEditedAfterClose]): the diary on screen must
     * match the store.
     *
     * - [nonCancellable]: the whole write, the fan-out and both handlers run to the end even if the returned job is
     *   cancelled (the full entry edit, which owns its save until it ends). Otherwise a cancel stops at the next
     *   suspension and runs nothing after it; a [run] that must finish wraps its own work in NonCancellable.
     * - [onFailure]: a failure of [run] (never a [kotlinx.coroutines.CancellationException], which is rethrown) goes
     *   here. Null lets it propagate, for runs that catch everything they expect themselves.
     */
    fun <T> write(
        change: (T) -> DiaryChange?,
        nonCancellable: Boolean = false,
        onFailure: ((Throwable) -> Unit)? = null,
        onSuccess: (T) -> Unit = {},
        run: suspend () -> T,
    ): Job? {
        if (!canWrite) return null
        busy = true
        return launchHolding(waitForIdle = false, nonCancellable, onFailure, change, onSuccess, run)
    }

    /** A [write] whose committed change does not depend on what [run] returns. */
    fun <T> write(
        change: DiaryChange,
        nonCancellable: Boolean = false,
        onFailure: ((Throwable) -> Unit)? = null,
        onSuccess: (T) -> Unit = {},
        run: suspend () -> T,
    ): Job? = write({ _: T -> change }, nonCancellable, onFailure, onSuccess, run)

    /**
     * Runs [run] holding the gate, so no write changes the diary under it, once storage is open and no write runs. For
     * a document-picker result (export, reading an import file), which can arrive before storage is open or while a
     * write runs, for example after a recreation: it waits instead of being refused. It commits nothing to the diary,
     * so nothing is reloaded or published; [onSuccess] gets its result. A failure propagates (these runs catch what
     * they expect); a cancel runs nothing after it.
     */
    fun <T> whenIdle(onSuccess: (T) -> Unit = {}, run: suspend () -> T): Job =
        launchHolding(waitForIdle = true, nonCancellable = false, onFailure = null, change = { null }, onSuccess, run)

    private fun <T> launchHolding(
        waitForIdle: Boolean,
        nonCancellable: Boolean,
        onFailure: ((Throwable) -> Unit)?,
        change: (T) -> DiaryChange?,
        onSuccess: (T) -> Unit,
        run: suspend () -> T,
    ): Job = scope.launch {
        if (waitForIdle) {
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

    private suspend fun <T> complete(
        run: suspend () -> T,
        change: (T) -> DiaryChange?,
        onFailure: ((Throwable) -> Unit)?,
        onSuccess: (T) -> Unit,
    ) {
        val result = if (onFailure == null) run() else suspendRunCatching { run() }.getOrElse {
            onFailure(it)
            // The write found the diary damaged and storage set it aside: the fresh one is loaded now.
            if (source.damagedNoticePending()) refresh()
            return
        }
        change(result)?.let(::publish)
        onSuccess(result)
    }

    /**
     * A write that is not the user's ([run]: a background merge into a stored food): it never holds or waits for the
     * user's gate, so no tap is refused for it, and the store's own lock orders it with the user's writes. When [run]
     * reports a commit, the diary reloads and [change] is published, as after a user write, so open screens follow.
     * A failure is dropped: nothing the user asked for failed.
     */
    fun background(change: DiaryChange, run: suspend () -> Boolean): Job = scope.launch {
        if (suspendRunCatching { run() }.getOrDefault(false)) publish(change)
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

/**
 * One attempt to open storage: true when [open] completed. A failure goes to [onFailure] (the log) and is not thrown,
 * so the caller can offer Try again; cancellation is thrown as usual.
 */
internal suspend fun openStorage(open: suspend () -> Unit, onFailure: (Exception) -> Unit): Boolean =
    try {
        open()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        false
    }
