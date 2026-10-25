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

class UndoAfterLogTest {
    private val entry = com.iron.breakfast.domain.MealEntry(
        "e", "f", "Egg", 1.0, com.iron.breakfast.domain.MeasureUnit.PIECE, 50.0,
        com.iron.breakfast.domain.NutritionValues(70.0, 6.0, 0.5, 5.0), "1 piece", false, 0L,
    )
    private val deleted = UndoOffer.Deleted(entry, 1_000L)

    @Test fun aBatchThatSavedNothingKeepsThePendingUndo() {
        assertEquals(deleted, undoAfterLog(deleted, null))
        assertEquals(null, undoAfterLog(null, null))
    }

    @Test fun aLoggedBatchReplacesThePendingUndo() {
        val logged = UndoOffer.Logged("1 food added to Breakfast", listOf(entry), 2_000L)
        assertEquals(logged, undoAfterLog(deleted, logged))
    }

    @Test fun onlyARealStopCountsAsLeaving() {
        assertTrue(stopMeansLeaving(changingConfigurations = false))
        assertFalse(stopMeansLeaving(changingConfigurations = true))
    }
}
