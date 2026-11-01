package com.bosa.breakfast.scan

import com.bosa.breakfast.domain.parseStrictAmountUnit
import com.bosa.breakfast.domain.parseDecimal
import com.bosa.breakfast.domain.ParsedAmountUnit
import com.bosa.breakfast.domain.MeasureUnit

// Product review's and the scanner's pure decisions, unit-tested.

/** Review amount field: "2 tbsp", or a bare number meaning the product's own basis unit. Null while it cannot be logged. */
internal fun parseProductAmount(text: String, basisUnit: MeasureUnit): ParsedAmountUnit? {
    val bare = parseDecimal(text)
    if (bare != null) return if (bare > 0.0) ParsedAmountUnit(bare, basisUnit) else null
    return parseStrictAmountUnit(text)
}

/**
 * What a product review is for, by where it started: a new part of the draft from the Log's scan icon ([fromLogIcon],
 * even with a queue under it), the resolve queue's part while one is being resolved ([queueActive]),
 * Save to Library from the Library tab ([libraryTab]), and a log from anywhere else: Daily, a meal, a meal's Library.
 * A scan on the way from "Not right?" ([correcting]) is for that entry, wherever it started.
 */
internal fun productReviewOrigin(libraryTab: Boolean, queueActive: Boolean, fromLogIcon: Boolean = false, correcting: Boolean = false): ProductReviewOrigin = when {
    correcting -> ProductReviewOrigin.CORRECTION
    fromLogIcon -> ProductReviewOrigin.LOG_PART
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
