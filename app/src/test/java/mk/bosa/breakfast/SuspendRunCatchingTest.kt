package mk.bosa.breakfast

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MOB-027: a cancelled save is not a failed save, so it sets no failure notice. */
class SuspendRunCatchingTest {
    @Test fun aCancelledScopeSetsNoFailureNotice() = runBlocking {
        var notice: String? = null
        var after = false
        val started = CompletableDeferred<Unit>()
        val job = launch {
            suspendRunCatching { started.complete(Unit); awaitCancellation() }
                .onFailure { notice = "Could not save this meal." }
            after = true
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertNull(notice)
        assertTrue("nothing after the call runs on a cancel", !after)
    }

    @Test fun plainRunCatchingWouldReportTheCancelAsAFailure() = runBlocking {
        // The behaviour the helper replaces: runCatching swallows the cancellation and the failure branch runs.
        var notice: String? = null
        val started = CompletableDeferred<Unit>()
        val job = launch {
            runCatching { started.complete(Unit); awaitCancellation() }.onFailure { notice = "Could not save this meal." }
        }
        started.await()
        job.cancelAndJoin()
        assertEquals("Could not save this meal.", notice)
    }

    @Test fun anOrdinaryFailureIsAResult() = runBlocking {
        val result = suspendRunCatching<Int> { error("disk full") }
        assertEquals("disk full", result.exceptionOrNull()?.message)
        assertEquals(3, suspendRunCatching { 1 + 2 }.getOrNull())
    }
}
