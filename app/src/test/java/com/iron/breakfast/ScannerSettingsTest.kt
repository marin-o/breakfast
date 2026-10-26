package com.iron.breakfast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B9 Scanner group: Look up unknown barcodes and Contribute (Flashlight at start is the scanner's first torch state). */
class ScannerSettingsTest {
    @Test fun lookUpOnAsksOpenFoodFactsAsToday() {
        assertEquals(UnknownBarcodeStep.LOOK_UP, unknownBarcodeStep(lookUp = true))
    }

    @Test fun lookUpOffGoesStraightToTheManualForm() {
        assertEquals(UnknownBarcodeStep.MANUAL_FORM, unknownBarcodeStep(lookUp = false))
    }

    @Test fun contributeHideHidesTheActionEverywhere() {
        assertTrue(contributionShown(show = true, available = true))
        assertFalse(contributionShown(show = true, available = false))
        assertFalse(contributionShown(show = false, available = true))
        assertFalse(contributionShown(show = false, available = false))
    }
}
