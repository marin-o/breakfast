package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalBarcodeTest {
    private val stored = FoodRecord(
        id = "custom-cola", name = "Cola", aliases = emptyList(), category = "Personal food", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(42.0, null, null, null)),
    )

    @Test fun upcAAndItsEan13AreOneCode() {
        assertEquals("0036000291452", canonicalBarcode("036000291452"))
        assertEquals("0036000291452", canonicalBarcode("0036000291452"))
        assertEquals("0036000291452", canonicalBarcode(" 036000291452 "))
        assertTrue(sameBarcode("036000291452", "0036000291452"))
        assertTrue(sameBarcode("0036000291452", "036000291452"))
    }

    @Test fun aGtin14WithALeadingZeroIsTheSameCodeAndOtherCodesStay() {
        assertEquals("0036000291452", canonicalBarcode("00036000291452"))
        assertEquals("3017620422003", canonicalBarcode("03017620422003"))
        assertEquals("13017620422000", canonicalBarcode("13017620422000"))
        assertEquals("3017620422003", canonicalBarcode("3017620422003"))
        assertEquals("96385074", canonicalBarcode("96385074"))
        assertNull(canonicalBarcode("WIFI:S:x;;"))
        assertFalse(sameBarcode("96385074", "0096385074"))
        assertFalse(sameBarcode(null, "3017620422003"))
        assertFalse(sameBarcode("3017620422003", "3017620422010"))
    }

    @Test fun barcodeFormsListEveryStoredSpellingOwnFormFirst() {
        assertEquals(listOf("036000291452", "0036000291452", "00036000291452"), barcodeForms("036000291452"))
        assertEquals(listOf("0036000291452", "036000291452", "00036000291452"), barcodeForms("0036000291452"))
        assertEquals(listOf("3017620422003", "03017620422003"), barcodeForms("3017620422003"))
        assertEquals(listOf("96385074"), barcodeForms("96385074"))
        assertEquals(emptyList<String>(), barcodeForms("WIFI:S:x;;"))
    }

    @Test fun theDuplicateCheckCatchesBothForms() {
        assertEquals("That barcode already belongs to Cola.", validateBarcodeInput("0036000291452", listOf(stored.copy(barcode = "036000291452"))))
        assertEquals("That barcode already belongs to Cola.", validateBarcodeInput("036000291452", listOf(stored.copy(barcode = "0036000291452"))))
        assertNull(validateBarcodeInput("036000291452", listOf(stored.copy(barcode = "0036000291452")), editingId = "custom-cola"))
    }

    @Test fun anExpandedUpcEFeedsTheSameCanonicalCode() {
        val scanned = requireNotNull(scannedProductBarcode("04252614", upcE = true))
        assertEquals("0042100005264", canonicalBarcode(scanned))
        assertTrue(sameBarcode(scanned, "0042100005264"))
    }
}
