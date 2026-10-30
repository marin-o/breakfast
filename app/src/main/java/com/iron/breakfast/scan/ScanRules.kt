package com.iron.breakfast.scan

import com.iron.breakfast.domain.parseStrictAmountUnit
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.ParsedAmountUnit
import com.iron.breakfast.domain.MeasureUnit

// Product review's and the scanner's pure decisions, unit-tested.

/** Review amount field: "2 tbsp", or a bare number meaning the product's own basis unit. Null while it cannot be logged. */
internal fun parseProductAmount(text: String, basisUnit: MeasureUnit): ParsedAmountUnit? {
    val bare = parseDecimal(text)
    if (bare != null) return if (bare > 0.0) ParsedAmountUnit(bare, basisUnit) else null
    return parseStrictAmountUnit(text)
}

/**
 * What a product review is for, by where it started: the resolve queue's part while one is being resolved ([queueActive]),
 * Save to Library from the Library tab ([libraryTab]), and a log from anywhere else: Daily, a meal, a meal's Library.
 */
internal fun productReviewOrigin(libraryTab: Boolean, queueActive: Boolean): ProductReviewOrigin = when {
    queueActive -> ProductReviewOrigin.QUEUE
    libraryTab -> ProductReviewOrigin.LIBRARY
    else -> ProductReviewOrigin.MEAL
}

internal fun canSaveProductToLibrary(origin: ProductReviewOrigin, queueActive: Boolean): Boolean =
    origin == ProductReviewOrigin.LIBRARY && !queueActive

internal data class ScannerFailureTransition(
    val scannerOpen: Boolean,
    val unavailable: Boolean,
)

internal fun scannerUnavailableTransition(): ScannerFailureTransition =
    ScannerFailureTransition(scannerOpen = true, unavailable = true)
