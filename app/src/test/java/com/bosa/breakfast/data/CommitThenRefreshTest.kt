package com.bosa.breakfast.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** MOB-025 (A1-06): a write that committed is reported as saved even when the reload after it fails. */
class CommitThenRefreshTest {
    @Test fun aRefreshFailureAfterTheCommitIsSavedAndReloadsLater() = runBlocking {
        val failures = mutableListOf<Throwable>()
        var committed = 0
        val result = commitThenRefresh(
            commit = { committed++; "saved" },
            refresh = { throw IllegalStateException("cursor window full") },
        ) { failures += it }
        assertEquals("saved", result)
        assertEquals(1, committed)
        assertEquals("cursor window full", failures.single().message)
    }

    /** An Error from the reload (not just an Exception) still leaves the committed write saved. */
    @Test fun anErrorFromTheRefreshIsStillReloadLater() = runBlocking {
        val failures = mutableListOf<Throwable>()
        assertEquals("saved", commitThenRefresh(commit = { "saved" }, refresh = { throw StackOverflowError("deep") }) { failures += it })
        assertTrue(failures.single() is StackOverflowError)
    }

    @Test fun aFailedCommitIsAFailureAndRefreshesNothing() = runBlocking {
        var refreshed = false
        try {
            commitThenRefresh<Unit>(commit = { error("disk full") }, refresh = { refreshed = true }) { fail("not a refresh failure") }
            fail("a failed commit must throw")
        } catch (expected: IllegalStateException) {
            assertEquals("disk full", expected.message)
        }
        assertTrue(!refreshed)
    }

    @Test fun aSuccessfulRefreshReportsNothing() = runBlocking {
        var refreshed = false
        assertEquals(3, commitThenRefresh(commit = { 3 }, refresh = { refreshed = true }) { fail("no failure") })
        assertTrue(refreshed)
    }
}
