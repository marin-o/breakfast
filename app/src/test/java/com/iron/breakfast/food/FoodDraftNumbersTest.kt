package com.iron.breakfast.food

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Test

/** The food form shows stored numbers as written and reads typed aliases by the shared comma rule. */
class FoodDraftNumbersTest {
    @Test fun aTinyStoredValueLoadsWithoutAnExponent() {
        val food = FoodRecord(
            id = "salt", name = "Salt", aliases = emptyList(), category = "Spice", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(0.0, 0.0005, 0.0, 12.5)),
            defaultAmount = 1.0, defaultUnit = MeasureUnit.GRAM,
        )
        val draft = FoodDraft().apply { load(food) }
        assertEquals("0.0005", draft.protein)
        assertEquals("12.5", draft.fat)
        assertEquals("100", draft.basisAmount)
    }

    @Test fun aTypedAliasKeepsItsDecimalComma() {
        val draft = FoodDraft().apply { aliasInput = "mleko 2,8%, млеко" }
        assertEquals(listOf("mleko 2,8%", "млеко"), draft.allAliases())
    }
}
