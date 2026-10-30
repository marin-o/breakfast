package com.iron.breakfast.scan

import com.iron.breakfast.domain.parseStrictAmountUnit
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.ParsedAmountUnit
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.MealSlot

// Product review's and the scanner's pure decisions, unit-tested.

/** Review amount field: "2 tbsp", or a bare number meaning the product's own basis unit. Null while it cannot be logged. */
internal fun parseProductAmount(text: String, basisUnit: MeasureUnit): ParsedAmountUnit? {
    val bare = parseDecimal(text)
    if (bare != null) return if (bare > 0.0) ParsedAmountUnit(bare, basisUnit) else null
    return parseStrictAmountUnit(text)
}

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
