package com.iron.breakfast.domain

import com.iron.breakfast.data.StorageCodec
import com.iron.breakfast.data.StorageMigrations
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
        assertEquals(0.5, parser.parse("1/2 tbsp peanut butter").amount)
        assertEquals("milk 3.2%", parser.parse("milk 3.2%").foodPhrase)
        assertEquals("milk 3.2%", parser.parse("milk 3.2% 100 ml").foodPhrase)
        assertEquals(100.0, parser.parse("milk 3.2% 100 ml").amount)
    }

    @Test
    fun explicitModifiersConstrainCandidates() {
        val matcher = FoodMatcher(FoodCatalog.fixtures)
        assertEquals("Oat milk", matcher.best("oat milk")?.food?.name)
        assertTrue(matcher.suggestions("crunchy peanut butter").all { it.food.name.contains("crunchy", true) })
        assertEquals("Домашно млеко", matcher.best("домашно млеко")?.food?.name)
        assertEquals("Milk 3.2%", matcher.best("milk 3.2%")?.food?.name)

        val generic = FoodCatalog.fixtures.first { it.id == "custom_grandmas_ajvar" }
            .copy(id = "generic_spread", name = "Spread", aliases = listOf("spread"), origin = FoodOrigin.GENERIC)
        val custom = generic.copy(id = "custom_spread", name = "Home spread", origin = FoodOrigin.CUSTOM)
        assertEquals("Home spread", FoodMatcher(listOf(generic, custom)).best("spread")?.food?.name)
    }

    @Test
    fun calculationKeepsServingProvenanceAndSnapshotsNutrition() {
        val milk = FoodCatalog.fixtures.first { it.id == "milk_28" }
        val result = NutritionCalculator().calculate(milk, 2.0, MeasureUnit.TABLESPOON)
        assertEquals(30.0, result.resolvedBaseAmount)
        assertEquals(16.2, result.nutrition.calories, 0.01)
        assertTrue(result.provenance.contains("15 ml"))

        val peanutButter = FoodCatalog.fixtures.first { it.id == "peanut_butter_smooth" }
        val teaspoon = NutritionCalculator().calculate(peanutButter, 1.0, MeasureUnit.TEASPOON)
        assertTrue(teaspoon.supported)
        assertEquals(5.3, teaspoon.resolvedBaseAmount, 0.01)

        val unsupported = NutritionCalculator().calculate(milk, 1.0, MeasureUnit.PIECE)
        assertTrue(!unsupported.supported)
        assertEquals(0.0, unsupported.nutrition.calories)
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
            savedAtEpochMs = 123L,
            sourcePhrase = "1 tbsp smooth peanut butter"
        )
        val original = StorageModel(listOf(food), listOf(entry), listOf(Preference(food.id, 2, 123L)))
        val restored = StorageCodec.decode(StorageCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun fixtureMigrationAddsConversionsWithoutOverwritingUserData() {
        val peanutButter = FoodCatalog.fixtures.first { it.id == "peanut_butter_smooth" }
        val oldFixture = peanutButter.copy(
            aliases = listOf("my pb"),
            conversions = peanutButter.conversions.filter { it.unit == MeasureUnit.TABLESPOON }
        )
        val userFood = peanutButter.copy(id = "user_pb", name = "My peanut butter", aliases = listOf("my jar"))
        val original = StorageModel(
            foods = listOf(oldFixture, userFood),
            entries = emptyList(),
            preferences = listOf(Preference("user_pb", explicitCount = 3, lastReinforcedEpochMs = 99L))
        )

        val migrated = StorageMigrations.addFixtureConversions(original)
        val migratedFixture = migrated.foods.first { it.id == peanutButter.id }
        assertEquals(listOf("my pb"), migratedFixture.aliases)
        assertTrue(migratedFixture.conversions.any { it.unit == MeasureUnit.TEASPOON })
        assertEquals(userFood, migrated.foods.first { it.id == "user_pb" })
        assertEquals(original.preferences, migrated.preferences)
        assertEquals(original.entries, migrated.entries)
    }
}
