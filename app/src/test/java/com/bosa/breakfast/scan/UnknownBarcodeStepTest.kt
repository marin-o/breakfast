package com.bosa.breakfast.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings, Scanner, "Look up unknown barcodes": what an unknown barcode leads to. */
class UnknownBarcodeStepTest {
    @Test fun lookUpOnAsksOpenFoodFactsAsToday() {
        assertEquals(UnknownBarcodeStep.LOOK_UP, unknownBarcodeStep(lookUp = true))
    }

    @Test fun lookUpOffGoesStraightToTheManualForm() {
        assertEquals(UnknownBarcodeStep.MANUAL_FORM, unknownBarcodeStep(lookUp = false))
    }
}
