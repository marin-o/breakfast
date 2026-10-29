package com.iron.breakfast.app

import androidx.compose.runtime.snapshots.Snapshot
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiarySessionTest {
    private fun entry(id: String) =
        MealEntry(id, "f", "Egg", 1.0, MeasureUnit.PIECE, 50.0, NutritionValues(70.0, 6.0, 0.5, 5.0), "1 piece", false, 0L)

    /** A store whose rows the test sets; it counts reads and can report a stale snapshot once. */
    private class FakeSource : DiarySource {
        var rows: List<MealEntry> = emptyList()
        var openFailure: Exception? = null
        var staleOnce = false
        /** A damaged diary was set aside (D15): the notice waits. */
        var damaged = false
        var entryReads = 0
        val events = mutableListOf<String>()
        override suspend fun open() { openFailure?.let { throw it } }
        override fun foods(): List<FoodRecord> = emptyList()
        override fun entries(): List<MealEntry> { entryReads++; events += "refresh"; return rows }
        override suspend fun reloadIfStale(): Boolean = staleOnce.also { staleOnce = false }
        override fun damagedNoticePending() = damaged
    }

    private val uncaught = mutableListOf<Throwable>()
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob() + CoroutineExceptionHandler { _, e -> uncaught += e })
    private val source = FakeSource()
    private var refreshedCalls = 0
    private val openFailures = mutableListOf<Exception>()
    private val session = DiarySession(source, scope, onOpenFailed = { openFailures += it }, onRefreshed = { refreshedCalls++ })
    private val published = mutableListOf<DiaryChange>()

    private fun opened(): DiarySession {
        session.onChange { published += it; source.events += "publish" }
        var onOpened = false
        session.open { onOpened = true }
        assertTrue(onOpened)
        source.events.clear()
        source.entryReads = 0
        return session
    }

    @Test fun aCommittedWriteReloadsThenPublishesThenRunsItsHandler() {
        opened()
        source.rows = listOf(entry("a"))
        var seenAtPublish: List<MealEntry>? = null
        session.onChange { seenAtPublish = session.entries }
        session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = { source.events += "onSuccess" },
        ) { source.events += "run" }
        assertEquals(listOf("run", "refresh", "publish", "onSuccess"), source.events)
        assertEquals(listOf(entry("a")), seenAtPublish)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsLogged), published)
        assertFalse(session.busy)
    }

    @Test fun aNullChangePublishesNothingAndDoesNotReload() {
        opened()
        var handled = false
        session.write(change = { _: Unit -> null }, onSuccess = { handled = true }) {}
        assertTrue(handled)
        assertEquals(0, source.entryReads)
        assertTrue(published.isEmpty())
    }

    @Test fun aChangeWithoutRowsIsPublishedWithoutAReload() {
        opened()
        session.write(change = DiaryChange.MatchConfirmed) {}
        assertEquals(listOf<DiaryChange>(DiaryChange.MatchConfirmed), published)
        assertEquals(0, source.entryReads)
    }

    /** D15: a write that found the diary damaged fails, and the fresh diary storage opened instead is loaded. */
    @Test fun aFailureAfterTheDiaryWasSetAsideReloadsIt() {
        opened()
        var failure: Throwable? = null
        session.write(change = DiaryChange.FoodsLogged, onFailure = { failure = it }) {
            source.damaged = true
            source.staleOnce = true
            error("file is not a database")
        }
        assertEquals("file is not a database", failure?.message)
        assertTrue(published.isEmpty())
        assertEquals("read, then read again after the reload", 2, source.entryReads)
        assertFalse(session.busy)
    }

    @Test fun aFailureGoesToItsHandlerAndNothingIsPublished() {
        opened()
        var failure: Throwable? = null
        var succeeded = false
        session.write(
            change = DiaryChange.FoodsLogged,
            onFailure = { failure = it },
            onSuccess = { succeeded = true },
        ) { error("disk full") }
        assertEquals("disk full", failure?.message)
        assertFalse(succeeded)
        assertTrue(published.isEmpty())
        assertEquals(0, source.entryReads)
        assertFalse(session.busy)
    }

    @Test fun withoutAFailureHandlerTheFailurePropagatesAndTheGateIsReleased() {
        opened()
        session.write(change = DiaryChange.FoodsLogged) { error("unexpected") }
        assertEquals("unexpected", uncaught.single().message)
        assertTrue(published.isEmpty())
        assertFalse(session.busy)
    }

    @Test fun aCancelledWriteIsNotAFailureAndRunsNothingAfterIt() {
        opened()
        val gate = CompletableDeferred<Unit>()
        var failure: Throwable? = null
        var succeeded = false
        val job = session.write(
            change = DiaryChange.FoodsLogged,
            onFailure = { failure = it },
            onSuccess = { succeeded = true },
        ) { gate.await() }
        assertTrue(session.busy)
        job!!.cancel()
        assertNull(failure)
        assertFalse(succeeded)
        assertTrue(published.isEmpty())
        assertFalse(session.busy)
        assertTrue(uncaught.isEmpty())
    }

    @Test fun aNonCancellableWriteFinishesItsWorkFanOutAndHandlerWhenCancelled() {
        opened()
        val gate = CompletableDeferred<Unit>()
        var succeeded = false
        val job = session.write(
            change = DiaryChange.EntryEdited,
            nonCancellable = true,
            onSuccess = { succeeded = true },
        ) { gate.await() }
        job!!.cancel()
        assertTrue(session.busy)
        gate.complete(Unit)
        assertTrue(succeeded)
        assertEquals(listOf<DiaryChange>(DiaryChange.EntryEdited), published)
        assertFalse(session.busy)
    }

    @Test fun aSecondWriteIsRefusedWhileOneRuns() {
        opened()
        val gate = CompletableDeferred<Unit>()
        session.write(change = DiaryChange.FoodsLogged) { gate.await() }
        var secondRan = false
        val second = session.write(change = DiaryChange.FoodsLogged) { secondRan = true }
        assertNull(second)
        assertFalse(secondRan)
        gate.complete(Unit)
        assertFalse(session.busy)
        assertNotNull(session.write(change = { _: Unit -> null }) {})
    }

    @Test fun aWriteIsRefusedBeforeStorageIsOpen() {
        var ran = false
        assertNull(session.write(change = { _: Unit -> null }) { ran = true })
        assertFalse(ran)
        assertFalse(session.busy)
    }

    @Test fun aPickerResultWaitsForTheRunningWriteAndForStorage() = runBlocking {
        // Before storage is open: the picker result waits instead of being refused.
        var exported = false
        session.whenIdle { exported = true }
        assertFalse(exported)
        session.open {}
        Snapshot.sendApplyNotifications()
        assertTrue(exported)
        // While a write runs: it waits for it, then holds the gate itself.
        val gate = CompletableDeferred<Unit>()
        session.write(change = { _: Unit -> null }) { gate.await() }
        val second = CompletableDeferred<Unit>()
        var imported = false
        session.whenIdle { second.await(); imported = true }
        gate.complete(Unit)
        Snapshot.sendApplyNotifications()
        assertTrue(session.busy)
        assertFalse(imported)
        second.complete(Unit)
        assertTrue(imported)
        assertFalse(session.busy)
    }

    @Test fun aStaleSnapshotIsReloadedAndReadAgain() {
        opened()
        source.staleOnce = true
        val before = refreshedCalls
        session.refresh()
        assertEquals(2, source.entryReads)
        assertEquals(before + 2, refreshedCalls)
    }

    @Test fun storageThatCannotOpenIsReportedAndARetryOpensIt() {
        source.openFailure = IllegalStateException("locked")
        var opened = 0
        session.open { opened++ }
        assertTrue(session.storageUnavailable)
        assertFalse(session.ready)
        assertEquals("locked", openFailures.single().message)
        source.openFailure = null
        session.open { opened++ }
        assertFalse(session.storageUnavailable)
        assertTrue(session.ready)
        assertEquals(1, opened)
        // Open once: a later call does nothing.
        session.open { opened++ }
        assertEquals(1, opened)
    }

    @Test fun anEditThatCommittedAfterItsEditClosedReloadsTheDiaryAndKeepsTheUndo() {
        // B11 review: the quick-edit sheet was left while its save ran; the stored row must still reach the screen.
        opened()
        source.rows = listOf(entry("edited"))
        session.write(change = DiaryChange.EntryEditedAfterClose) {}
        assertEquals(1, source.entryReads)
        assertEquals(listOf(entry("edited")), session.entries)
        assertEquals(listOf<DiaryChange>(DiaryChange.EntryEditedAfterClose), published)
        val pending = com.iron.breakfast.today.UndoOffer.Deleted(entry("gone"), 1_000L)
        assertEquals(pending, undoAfterChange(pending, DiaryChange.EntryEditedAfterClose, nowMs = 2_000L))
        assertFalse(DiaryChange.EntryEditedAfterClose.refreshesLibrary)
    }

    @Test fun workThatOnlyNeedsTheDiaryStillHoldsTheGateAndPublishesNothing() {
        opened()
        val gate = CompletableDeferred<Unit>()
        var result: String? = null
        session.whenIdle(onSuccess = { value: String -> result = value }) { gate.await(); "read" }
        assertTrue(session.busy)
        assertNull(session.write(change = DiaryChange.FoodsLogged) {})
        gate.complete(Unit)
        assertEquals("read", result)
        assertEquals(0, source.entryReads)
        assertTrue(published.isEmpty())
        assertFalse(session.busy)
    }
}
