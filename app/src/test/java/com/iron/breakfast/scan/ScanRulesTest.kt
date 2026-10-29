package com.iron.breakfast.scan

import com.iron.breakfast.domain.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Product review's and the scanner's rules (`ScanRules.kt`). */
class ScanRulesTest {
    @Test fun scannedProductReviewKeepsLibraryOriginSeparateFromMealAndQueue() {
        assertEquals(ProductReviewOrigin.LIBRARY, productReviewOrigin(null, queueActive = false))
        assertEquals(ProductReviewOrigin.MEAL, productReviewOrigin(MealSlot.DINNER, queueActive = false))
        assertEquals(ProductReviewOrigin.QUEUE, productReviewOrigin(null, queueActive = true))
    }

    @Test fun libraryOriginCanSaveWithoutCreatingDiaryLogPlan() {
        assertEquals(true, canSaveProductToLibrary(null, queueActive = false))
        assertEquals(false, canSaveProductToLibrary(null, queueActive = true))
    }

    @Test
    fun librarySaveIsHiddenDuringResolveQueue() {
        assertFalse(canSaveProductToLibrary(null, queueActive = true))
        assertTrue(canSaveProductToLibrary(null, queueActive = false))
        assertFalse(canSaveProductToLibrary(MealSlot.DINNER, queueActive = false))
    }

    @Test
    fun scannerFailureKeepsManualRouteOpen() {
        assertEquals(ScannerFailureTransition(true, true), scannerUnavailableTransition())
    }
}
