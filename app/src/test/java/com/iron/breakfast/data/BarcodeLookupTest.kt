package com.iron.breakfast.data

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.barcodeForms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store's barcode lookup: the SQL IN over [barcodeForms], then [pickBarcodeMatch]. */
class BarcodeLookupTest {
    private fun food(id: String, barcode: String) = FoodRecord(
        id = id, name = id, aliases = emptyList(), category = "Personal food", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null)),
        barcode = barcode,
    ).toEntity()

    private fun lookup(rows: List<FoodEntity>, scanned: String): FoodEntity? {
        val forms = barcodeForms(scanned)
        return pickBarcodeMatch(rows.filter { it.barcode in forms }, scanned)
    }

    @Test fun aFoodStoredInEitherFormIsFoundByTheOther() {
        assertEquals("upc", lookup(listOf(food("upc", "036000291452")), "0036000291452")?.id)
        assertEquals("ean", lookup(listOf(food("ean", "0036000291452")), "036000291452")?.id)
        assertEquals("gtin", lookup(listOf(food("gtin", "00036000291452")), "036000291452")?.id)
    }

    @Test fun theExactSpellingWins() {
        val rows = listOf(food("upc", "036000291452"), food("ean", "0036000291452"))
        assertEquals("ean", lookup(rows, "0036000291452")?.id)
        assertEquals("upc", lookup(rows, "036000291452")?.id)
        assertNull(lookup(listOf(food("other", "3017620422003")), "0036000291452"))
    }
}

class BarcodeReviewFixTest {
    private fun food(id: String, barcode: String) = FoodRecord(
        id = id, name = id, aliases = emptyList(), category = "Personal food", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null)), barcode = barcode,
    ).toEntity()

    @Test fun severalLiveMatchesPickExactThenCanonicalThenId() {
        val rows = listOf(food("b-upc", "036000291452"), food("a-gtin", "00036000291452"), food("c-ean", "0036000291452"), food("a-ean", "0036000291452"))
        assertEquals("b-upc", pickBarcodeMatch(rows, "036000291452")?.id)
        assertEquals("a-ean", pickBarcodeMatch(rows.filterNot { it.barcode == "036000291452" }, "036000291452")?.id)
        assertEquals("a-gtin", pickBarcodeMatch(listOf(food("z", "00036000291452"), food("a-gtin", "00036000291452")), "036000291452")?.id)
        assertEquals("a-ean", pickBarcodeMatch(rows.reversed().filterNot { it.barcode == "036000291452" }, "036000291452")?.id)
    }

    @Test fun anOldEightDigitUpcERowIsFoundByItsExpandedScan() {
        val forms = barcodeForms("012345000065")
        val rows = listOf(food("old-upce", "01234565"))
        assertEquals("old-upce", pickBarcodeMatch(rows.filter { it.barcode in forms }, "012345000065")?.id)
    }

    @Test fun librarySearchFindsAFullCodeInAnySpellingAndKeepsPartialMatches() {
        assertTrue(barcodeMatchesQuery("036000291452", "0036000291452"))
        assertTrue(barcodeMatchesQuery("0036000291452", "036000291452"))
        assertTrue(barcodeMatchesQuery("01234565", "012345000065"))
        assertTrue(barcodeMatchesQuery("0036000291452", "60002"))
        assertFalse(barcodeMatchesQuery("0036000291452", "milk"))
        assertFalse(barcodeMatchesQuery(null, "0036000291452"))
    }
}
