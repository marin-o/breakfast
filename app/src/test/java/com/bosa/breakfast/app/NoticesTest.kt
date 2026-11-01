package com.bosa.breakfast.app

import com.bosa.breakfast.today.FakeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {
    private val time = FakeTime()
    private val said = mutableListOf<String>()
    private val timed = Notices(CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), time.sleep, announce = { said += it })

    @Test fun aNoticeClosesItselfAfterItsTime() {
        var seen = 0
        timed.show("Saved.", onDismiss = { seen++ })
        assertEquals(listOf(NOTICE_MS), time.sleeps)
        assertEquals("Saved.", timed.message)
        time.advance(NOTICE_MS)
        assertNull(timed.message)
        assertEquals(1, seen)
    }

    @Test fun aNoticeClosedByOkOrASwipeEndsItsTimer() {
        timed.show("Saved.")
        timed.dismiss()
        assertNull(timed.message)
        assertEquals(false, time.sleeping)
    }

    @Test fun aNewNoticeStartsTheTimeAgainAndEachIsSaidOnce() {
        timed.show("Saved.")
        timed.show("Saved.")
        assertEquals(listOf("Saved.", "Saved."), said)
        assertEquals(listOf(NOTICE_MS, NOTICE_MS), time.sleeps)
    }

    /** The damaged-diary notice: no time limit, it goes only when the user closes it. */
    @Test fun aNoticeWithNoDurationStaysUntilClosed() {
        timed.show("Read this.", durationMs = null)
        assertEquals(emptyList<Long>(), time.sleeps)
        assertEquals("Read this.", timed.message)
        timed.dismiss()
        assertNull(timed.message)
    }

    @Test fun whileTheAppIsAwayANoticesTimeStandsStill() {
        timed.show("Saved.")
        timed.stop()
        time.advance(NOTICE_MS)
        assertEquals("the cancelled wait ended nothing", "Saved.", timed.message)
        timed.start()
        assertEquals(listOf(NOTICE_MS, NOTICE_MS), time.sleeps)
        assertEquals("said once, on showing", listOf("Saved."), said)
        time.advance(NOTICE_MS)
        assertNull(timed.message)
    }

    @Test fun aNoticeThatCameWhileAwayIsSaidAndTimedOnReturn() {
        timed.stop()
        timed.show("Exported.")
        assertEquals(emptyList<String>(), said)
        assertEquals(emptyList<Long>(), time.sleeps)
        timed.start()
        assertEquals(listOf("Exported."), said)
        assertEquals(listOf(NOTICE_MS), time.sleeps)
    }

    @Test fun oneNoticeAtATimeTheLatestWins() {
        val notices = Notices()
        notices.show("Saved to your food library.")
        notices.show("Could not undo.")
        assertEquals("Could not undo.", notices.message)
        notices.dismiss()
        assertNull(notices.message)
    }

    /** D15: the damaged-diary notice clears its marker only once the user dismissed it. */
    @Test fun aNoticesDismissActionRunsWhenItIsDismissedNotWhenReplaced() {
        val notices = Notices()
        var seen = 0
        notices.show("The diary could not be read. Import an export to restore it.", onDismiss = { seen++ })
        notices.show("Could not undo.")
        notices.dismiss()
        assertEquals("replaced, never seen", 0, seen)
        notices.show("The diary could not be read. Import an export to restore it.", onDismiss = { seen++ })
        notices.dismiss()
        notices.dismiss()
        assertEquals(1, seen)
    }
}
