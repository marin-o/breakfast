package com.iron.breakfast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoWindowTest {
    private val offered = 1_000_000L

    @Test fun anOfferLastsFifteenSeconds() {
        assertEquals(15_000L, UNDO_WINDOW_MS)
        assertTrue(undoOpen(offered, offered))
        assertTrue(undoOpen(offered, offered + 14_999L))
        assertFalse(undoOpen(offered, offered + 15_000L))
        assertFalse(undoOpen(offered, offered + 60_000L))
    }

    @Test fun aClockSetBackClosesTheOffer() {
        assertFalse(undoOpen(offered, offered - 1L))
        assertEquals(0L, undoRemainingMs(offered, offered - 1L))
    }

    @Test fun remainingTimeCountsDownToZero() {
        assertEquals(15_000L, undoRemainingMs(offered, offered))
        assertEquals(5_000L, undoRemainingMs(offered, offered + 10_000L))
        assertEquals(0L, undoRemainingMs(offered, offered + 15_000L))
        assertEquals(0L, undoRemainingMs(offered, offered + 99_000L))
    }
}
