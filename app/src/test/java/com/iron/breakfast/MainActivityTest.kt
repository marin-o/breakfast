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

    @Test
    fun customFoodEditPreservesAliasesAndAlignsDefaultServingToNewBasis() {
        val original = FoodRecord(
            id = "custom", name = "Old", aliases = listOf("legacy alias"), category = "Personal food",
            origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 2.0, 3.0)),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM
        )
        val edited = editedCustomFoodRecord(
            original, "New", "", "", "", NutritionValues(200.0, 2.0, 3.0, 4.0), 250.0, MeasureUnit.MILLILITER
        )
        assertEquals(listOf("legacy alias"), edited.aliases)
        assertEquals(250.0, edited.defaultAmount)
        assertEquals(MeasureUnit.MILLILITER, edited.defaultUnit)
        assertEquals(250.0, edited.nutrition.amount)
    }

    @Test
    fun productLabelUsesActualNutritionBasis() {
        val food = FoodRecord(
            id = "basis", name = "Soup", aliases = emptyList(), category = "Open Food Facts", origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(250.0, MeasureUnit.GRAM, NutritionValues(300.0, null, null, null))
        )
        assertEquals("Calories per 250 g", productNutritionBasisLabel(food))
    }
}
