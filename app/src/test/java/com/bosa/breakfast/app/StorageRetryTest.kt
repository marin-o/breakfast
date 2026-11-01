package com.bosa.breakfast.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MOB-019: a storage open that fails is reported, not thrown, so Try again can run the same open once more. */
class StorageRetryTest {
    /** Its first initialize throws, as a locked or unreadable database would; the next one opens. */
    private class FlakyStorage {
        var calls = 0
        var ready = false
        suspend fun initialize() {
            calls++
            if (calls == 1) throw IllegalStateException("disk I/O error")
            ready = true
        }
    }

    @Test fun aFailedOpenIsReportedAndTryAgainOpens() = runBlocking {
        val storage = FlakyStorage()
        val logged = mutableListOf<Exception>()

        assertFalse(openStorage(storage::initialize) { logged += it })
        assertFalse(storage.ready)
        assertEquals("disk I/O error", logged.single().message)

        assertTrue(openStorage(storage::initialize) { logged += it })
        assertTrue(storage.ready)
        assertEquals(2, storage.calls)
        assertEquals(1, logged.size)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotTakenForAFailure() = runBlocking<Unit> {
        openStorage({ throw CancellationException("left") }) { throw AssertionError("not a failure") }
    }
}
