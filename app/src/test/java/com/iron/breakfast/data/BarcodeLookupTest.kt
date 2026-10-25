package com.iron.breakfast.data

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.barcodeForms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The store's barcode lookup: the SQL IN over [barcodeForms], then [pickBarcodeMatch]. */
class BarcodeLookupTest {
    private fun food(id: String, barcode: String, archived: Boolean = false) = FoodRecord(
        id = id, name = id, aliases = emptyList(), category = "Personal food", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, null, null, null)),
        barcode = barcode, archived = archived,
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

    @Test fun theExactSpellingWinsAndArchivedRowsAreSkipped() {
        val rows = listOf(food("upc", "036000291452"), food("ean", "0036000291452"))
        assertEquals("ean", lookup(rows, "0036000291452")?.id)
        assertEquals("upc", lookup(rows, "036000291452")?.id)
        assertEquals("ean", lookup(listOf(food("upc", "036000291452", archived = true), food("ean", "0036000291452")), "036000291452")?.id)
        assertNull(lookup(listOf(food("upc", "036000291452", archived = true)), "0036000291452"))
        assertNull(lookup(listOf(food("other", "3017620422003")), "0036000291452"))
    }
}
