package com.bosa.breakfast.ui.components

import com.bosa.breakfast.today.FakeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ToastSlotTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    private val time = FakeTime()
    private val slot = ToastSlot<String>(scope, time.sleep)

    /** Sleepers a cancel does not reach, so each can be woken late: what [FakeTime] cannot do. */
    private val late = mutableListOf<Continuation<Unit>>()
    private val lateSlot = ToastSlot<String>(scope) { suspendCoroutine { late += it } }

    @Test fun aLateTimerCannotCloseANewerToast() {
        lateSlot.show("old", 1_000L)
        lateSlot.show("new", 1_000L)
        late[0].resume(Unit)
        assertEquals("new", lateSlot.shown)
        late[1].resume(Unit)
        assertNull(lateSlot.shown)
    }

    @Test fun aLateTimerCannotCloseWhatAFingerHolds() {
        lateSlot.show("held", 1_000L)
        lateSlot.hold(lateSlot.showId, true)
        late[0].resume(Unit)
        assertEquals("held", lateSlot.shown)
    }

    @Test fun aLateTimerCannotCloseWhileTheAppIsAway() {
        lateSlot.show("away", 1_000L)
        lateSlot.stop()
        late[0].resume(Unit)
        assertEquals("away", lateSlot.shown)
    }

    @Test fun aNullDurationStaysUntilClosed() {
        slot.show("stays", null)
        assertFalse(time.sleeping)
        slot.close()
        assertNull(slot.shown)
    }

    /** The same text during a swipe's way out: the swipe's close names the old show and ends nothing. */
    @Test fun aSameTextShowOutlivesTheCloseOfTheOneBefore() {
        slot.show("Saved.", 1_000L)
        val swiped = slot.showId
        slot.show("Saved.", 1_000L)
        assertNotEquals(swiped, slot.showId)
        slot.close(swiped)
        assertEquals("Saved.", slot.shown)
        assertTrue(time.sleeping)
        slot.close(slot.showId)
        assertNull(slot.shown)
    }

    @Test fun aFingerStopsTheTimeAndAReleaseStartsItOverInFull() {
        slot.show("x", 1_000L)
        slot.hold(slot.showId, true)
        assertFalse(time.sleeping)
        time.advance(5_000L)
        assertEquals("x", slot.shown)
        slot.hold(slot.showId, false)
        assertEquals(listOf(1_000L, 1_000L), time.sleeps)
        time.advance(1_000L)
        assertNull(slot.shown)
    }

    /** A toast replacing the held one has its own time, which the old gesture's end does not touch. */
    @Test fun aNewShowIsNotHeldByTheOldFinger() {
        slot.show("old", 1_000L)
        val old = slot.showId
        slot.hold(old, true)
        slot.show("new", 2_000L)
        assertTrue(time.sleeping)
        slot.hold(old, false)
        assertEquals("not restarted", listOf(1_000L, 2_000L), time.sleeps)
        slot.hold(old, true)
        assertTrue("not held", time.sleeping)
    }

    @Test fun awayStopsTheTimeAndAShowWhileAwayWaitsForTheReturn() {
        slot.show("x", 1_000L)
        slot.stop()
        assertFalse(time.sleeping)
        slot.show("y", 2_000L)
        assertFalse(time.sleeping)
        slot.start()
        assertEquals(listOf(1_000L, 2_000L), time.sleeps)
    }

    @Test fun onCloseRunsOnceWhenClosedAndNotWhenReplaced() {
        var closed = 0
        slot.show("x", 1_000L, onClose = { closed++ })
        slot.show("y", 1_000L)
        slot.close()
        assertEquals(0, closed)
        slot.show("z", 1_000L, onClose = { closed++ })
        time.advance(1_000L)
        slot.close()
        assertEquals(1, closed)
    }

    @Test fun eachShowStartsItsOwnSwipeAndItIsKeptWhileItFades() {
        val showing = ToastShowing(1L)
        val first = showing.swipeFor("Saved.", 1L)
        assertSame(first, showing.swipeFor("Saved.", 1L))
        val second = showing.swipeFor("Saved.", 2L)
        assertNotSame("same text, new show", first, second)
        assertSame("fading out in place", second, showing.swipeFor(null, 2L))
        assertNotSame("the same toast shown again", second, showing.swipeFor("Saved.", 2L))
    }
}
