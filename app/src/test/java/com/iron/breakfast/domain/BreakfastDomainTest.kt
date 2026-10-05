package com.iron.breakfast.domain

import com.iron.breakfast.data.StorageCodec
import com.iron.breakfast.data.StorageModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreakfastDomainTest {
    private val parser = ShorthandParser()

    @Test
    fun parsesQuantityFirstAndFractionAfterFood() {
        val first = parser.parse("2 tbsp oats")
        assertEquals("oats", first.foodPhrase)
        assertEquals(2.0, first.amount)
        assertEquals(MeasureUnit.TABLESPOON, first.unit)

        val second = parser.parse("peanut butter half a tbsp")
        assertEquals("peanut butter", second.foodPhrase)
        assertEquals(0.5, second.amount)
        assertEquals(MeasureUnit.TABLESPOON, second.unit)
    }

    @Test
    fun parsesCommaSegmentsAndMixedFraction() {
        assertEquals(listOf("1 1/2 tbsp oats", "banana"), parser.split("1 1/2 tbsp oats, banana"))
        assertEquals(1.5, parser.parse("1 1/2 tbsp oats").amount)
    }

    @Test
    fun explicitModifiersConstrainCandidates() {
        val matcher = FoodMatcher(FoodCatalog.fixtures)
        assertEquals("Oat milk", matcher.best("oat milk")?.food?.name)
        assertTrue(matcher.suggestions("crunchy peanut butter").all { it.food.name.contains("crunchy", true) })
        assertEquals("Домашно млеко", matcher.best("домашно млеко")?.food?.name)
    }

    @Test
    fun calculationKeepsServingProvenanceAndSnapshotsNutrition() {
        val milk = FoodCatalog.fixtures.first { it.id == "milk_28" }
        val result = NutritionCalculator().calculate(milk, 2.0, MeasureUnit.TABLESPOON)
        assertEquals(30.0, result.resolvedBaseAmount)
        assertEquals(16.2, result.nutrition.calories, 0.01)
        assertTrue(result.provenance.contains("15 ml"))
    }

    @Test
    fun storageCodecRoundTripsEntriesPreferencesAndFoods() {
        val food = FoodCatalog.fixtures.first()
        val entry = MealEntry(
            id = "entry-1",
            foodId = food.id,
            foodName = food.name,
            enteredAmount = 2.0,
            enteredUnit = MeasureUnit.TABLESPOON,
            resolvedBaseAmount = 30.0,
            nutritionSnapshot = NutritionValues(16.2, 0.96, 1.44, 0.84),
            conversionProvenance = "15 ml household tablespoon",
            approximateConversion = false,
            savedAtEpochMs = 123L
        )
        val original = StorageModel(listOf(food), listOf(entry), listOf(Preference(food.id, 2, 123L)))
        val restored = StorageCodec.decode(StorageCodec.encode(original))
        assertEquals(original, restored)
    }
}
