package com.iron.breakfast

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainActivityTest {
    @Test
    fun resolverProductPrefillPreservesEnteredServingAndDirectSearchKeepsBasisDefault() {
        val pesto = FoodRecord(
            id = "off-pesto",
            name = "Branded pesto",
            aliases = emptyList(),
            category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(500.0, 4.0, 10.0, 48.0)),
            defaultAmount = 100.0,
            defaultUnit = MeasureUnit.GRAM
        )

        val resolver = productPrefill("1 tbsp pesto", pesto)
        assertEquals(1.0, resolver.amount)
        assertEquals(MeasureUnit.TABLESPOON, resolver.unit)

        val directSearch = productPrefill("pesto", pesto)
        assertEquals(100.0, directSearch.amount)
        assertEquals(MeasureUnit.GRAM, directSearch.unit)

        assertEquals(MeasureUnit.MILLILITER, parseProductUnit(" ml "))
        assertEquals(MeasureUnit.TABLESPOON, parseProductUnit(" tbsp "))
        assertNull(parseProductUnit(" cups "))
    }

    @Test
    fun contributionRequiresExplicitScanProvenanceAfterLocalSave() {
        val scanned = FoodRecord(
            id = "off-scan", name = "Scanned drink", aliases = emptyList(), category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(42.0, null, null, null)),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.MILLILITER,
            sourceName = "Open Food Facts", barcode = "123"
        )
        assertNull(contributionDraftAfterLocalSave(scanned, fromScan = false))
        assertEquals("123", contributionDraftAfterLocalSave(scanned, fromScan = true)?.barcode)
    }
}
