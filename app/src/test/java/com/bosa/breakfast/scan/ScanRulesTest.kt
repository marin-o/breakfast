package com.bosa.breakfast.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Product review's and the scanner's rules (`ScanRules.kt`). */
class ScanRulesTest {
    @Test fun aReviewIsForWhereItStartedNotWhetherAMealIsOpen() {
        assertEquals(ProductReviewOrigin.LIBRARY, productReviewOrigin(libraryTab = true, queueActive = false))
        assertEquals(ProductReviewOrigin.MEAL, productReviewOrigin(libraryTab = false, queueActive = false))
        assertEquals(ProductReviewOrigin.QUEUE, productReviewOrigin(libraryTab = true, queueActive = true))
        assertEquals(ProductReviewOrigin.QUEUE, productReviewOrigin(libraryTab = false, queueActive = true))
    }

    @Test fun aScanFromTheLogIconIsAlwaysForItsDraft() {
        assertEquals(ProductReviewOrigin.LOG_PART, productReviewOrigin(libraryTab = false, queueActive = false, fromLogIcon = true))
        assertEquals(ProductReviewOrigin.LOG_PART, productReviewOrigin(libraryTab = false, queueActive = true, fromLogIcon = true))
    }

    @Test fun onlyALibraryReviewSavesToTheLibrary() {
        assertTrue(canSaveProductToLibrary(ProductReviewOrigin.LIBRARY, queueActive = false))
        assertFalse(canSaveProductToLibrary(ProductReviewOrigin.LIBRARY, queueActive = true))
        assertFalse(canSaveProductToLibrary(ProductReviewOrigin.MEAL, queueActive = false))
    }

    @Test
    fun scannerFailureKeepsManualRouteOpen() {
        assertEquals(ScannerFailureTransition(true, true), scannerUnavailableTransition())
    }
}
