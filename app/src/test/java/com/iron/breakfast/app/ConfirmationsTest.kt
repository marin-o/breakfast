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

    @Test fun aNewOneStartsTheTimeAgain() {
        confirmations.logged()
        confirmations.logged()
        assertEquals("the first wait was cancelled", listOf(CONFIRMATION_MS, CONFIRMATION_MS), time.sleeps)
        time.advance(CONFIRMATION_MS)
        assertNull(confirmations.message)
    }
}
