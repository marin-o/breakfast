package mk.bosa.breakfast.app

import mk.bosa.breakfast.today.FakeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationsTest {
    private val time = FakeTime()
    /** What was said for TalkBack ([Announcements.say]). */
    private val said = mutableListOf<String>()
    private val confirmations = Confirmations(CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), announce = { said += it }, sleep = time.sleep)

    @Test fun loggedShowsLoggedAndGoesByItself() {
        assertNull(confirmations.message)
        confirmations.logged()
        assertEquals("Logged!", confirmations.message)
        assertEquals(listOf(CONFIRMATION_MS), time.sleeps)
        time.advance(CONFIRMATION_MS)
        assertNull(confirmations.message)
    }

    @Test fun eachConfirmationIsSaidOnceARepeatToo() {
        confirmations.logged()
        assertEquals(listOf("Logged!"), said)
        // A second log while "Logged!" still shows: the same text, a new confirmation.
        confirmations.logged()
        assertEquals(listOf("Logged!", "Logged!"), said)
        time.advance(CONFIRMATION_MS)
        assertEquals("going says nothing", 2, said.size)
    }

    @Test fun aSwipeEndsItAndItsTimer() {
        confirmations.logged()
        confirmations.slot.close(confirmations.slot.showId)
        assertNull(confirmations.message)
        confirmations.logged()
        assertEquals("Logged!", confirmations.message)
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
