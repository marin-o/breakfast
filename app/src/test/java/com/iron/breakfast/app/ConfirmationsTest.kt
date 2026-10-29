package com.iron.breakfast.app

import com.iron.breakfast.today.FakeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationsTest {
    private val time = FakeTime()
    private val confirmations = Confirmations(CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), sleep = time.sleep)

    @Test fun loggedShowsLoggedAndGoesByItself() {
        assertNull(confirmations.message)
        confirmations.logged()
        assertEquals("Logged!", confirmations.message)
        assertEquals(listOf(CONFIRMATION_MS), time.sleeps)
        time.advance(CONFIRMATION_MS)
        assertNull(confirmations.message)
    }

    @Test fun eachConfirmationIsReadOutOnceARepeatToo() {
        assertNull("nothing to read at start", confirmations.takeAnnouncement())
        confirmations.logged()
        assertEquals("Logged!", confirmations.takeAnnouncement())
        assertNull("a recreated screen asks again and reads nothing", confirmations.takeAnnouncement())
        // A second log while "Logged!" still shows: the same text, a new confirmation.
        confirmations.logged()
        assertEquals("Logged!", confirmations.message)
        assertEquals(2, confirmations.serial)
        assertEquals("Logged!", confirmations.takeAnnouncement())
        assertNull(confirmations.takeAnnouncement())
    }

    @Test fun oneThatHasGoneIsNotReadLate() {
        confirmations.logged()
        time.advance(CONFIRMATION_MS)
        assertNull(confirmations.takeAnnouncement())
    }

    @Test fun aNewOneStartsTheTimeAgain() {
        confirmations.logged()
        confirmations.logged()
        assertEquals("the first wait was cancelled", listOf(CONFIRMATION_MS, CONFIRMATION_MS), time.sleeps)
        time.advance(CONFIRMATION_MS)
        assertNull(confirmations.message)
    }
}
