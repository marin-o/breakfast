package mk.bosa.breakfast.food

import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
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

    @Test fun anUntouchedFieldKeepsTheStoredValueAndATypedOneIsAsTyped() {
        val food = FoodRecord(
            id = "third", name = "Third", aliases = emptyList(), category = "Test", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0 / 3, 1.0 / 3, null, null)),
            density = 1.0 / 3,
        )
        val draft = FoodDraft().apply { load(food) }
        assertEquals("0.333", draft.protein)
        draft.brand = "Dukat"
        assertEquals((1.0 / 3).toString(), draft.nutritionInput().protein)
        assertEquals(1.0 / 3, draft.densityValue()!!, 0.0)
        draft.protein = "0.5"
        assertEquals("0.5", draft.nutritionInput().protein)
    }

    @Test fun aTypedAliasKeepsItsDecimalComma() {
        val draft = FoodDraft().apply { aliasInput = "mleko 2,8%, млеко" }
        assertEquals(listOf("mleko 2,8%", "млеко"), draft.allAliases())
    }
}
