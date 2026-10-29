package com.iron.breakfast.scan

import com.iron.breakfast.domain.MealSlot

// Product review's and the scanner's pure decisions, unit-tested.

/** Where a product review opens from: the resolve queue while one is being resolved ([queueActive]), else a meal or the Library. */
internal fun productReviewOrigin(slot: MealSlot?, queueActive: Boolean): ProductReviewOrigin = when {
    queueActive -> ProductReviewOrigin.QUEUE
    slot != null -> ProductReviewOrigin.MEAL
    else -> ProductReviewOrigin.LIBRARY
}

internal fun canSaveProductToLibrary(slot: MealSlot?, queueActive: Boolean): Boolean = slot == null && !queueActive

internal data class ScannerFailureTransition(
    val scannerOpen: Boolean,
    val unavailable: Boolean,
)

internal fun scannerUnavailableTransition(): ScannerFailureTransition =
    ScannerFailureTransition(scannerOpen = true, unavailable = true)
