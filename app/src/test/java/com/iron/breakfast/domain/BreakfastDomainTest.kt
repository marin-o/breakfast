package com.iron.breakfast.domain

import com.iron.breakfast.data.StorageCodec
import com.iron.breakfast.data.StorageMigrations
import com.iron.breakfast.data.StorageModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.system.measureNanoTime

class BreakfastDomainTest {
    private val parser = ShorthandParser()

    @Test
    fun archivedFoodsAreExcludedFromLoggingCatalog() {
        val active = FoodCatalog.fixtures.first()
        val archived = active.copy(id = "archived", archived = true)
        assertEquals(listOf(active), foodsAvailableForLogging(listOf(active, archived)))
    }

    @Test
    fun archivedPersonalOverrideCannotBecomeEffectiveDuringRecache() {
        val candidate = FoodCatalog.fixtures.first().copy(id = "off-123", barcode = "123")
        val archivedOverride = candidate.copy(name = "Archived local edit", archived = true, personalOverride = true)
        assertEquals(candidate, cachedFoodForLogging(candidate, archivedOverride, null))
        val activeOverride = archivedOverride.copy(archived = false)
        assertEquals(activeOverride, cachedFoodForLogging(candidate, activeOverride, null))
    }

    @Test
    fun suggestionHotPathStaysBoundedWithExpandedCatalog() {
        val matcher = FoodMatcher(FoodCatalog.fixtures)
        val parsed = parser.parse("ground beef 90/10 raw 100 g")
        val groundBeef = matcher.suggestions(parsed.foodPhrase)
        val partialName = matcher.suggestions("ground beef")
        assertTrue(groundBeef.any { it.food.name == "Ground beef 90/10 raw" })
        assertTrue(partialName.isNotEmpty())
        repeat(20) { matcher.suggestions(parsed.foodPhrase) }
        val elapsed = measureNanoTime {
            repeat(1_000) { matcher.suggestions(parsed.foodPhrase) }
        }
        println("suggestion-hot-path-ms=" + elapsed / 1_000_000.0)
    }

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
    fun quickEditAmountUnitParsingIsStrict() {
        assertEquals(ParsedAmountUnit(100.0, MeasureUnit.GRAM), parseStrictAmountUnit("100 g"))
        assertEquals(ParsedAmountUnit(0.5, MeasureUnit.TABLESPOON), parseStrictAmountUnit("1/2 tbsp"))
        assertNull(parseStrictAmountUnit("100"))
        assertNull(parseStrictAmountUnit("100 g oats"))
        assertNull(parseStrictAmountUnit("0 g"))
        assertNull(parseStrictAmountUnit("100 bananas"))
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
    fun keepsCompositionRatiosInFoodPhraseAndUsesLaterExplicitQuantity() {
        val parsed = parser.parse("ground beef 90/10 raw 100 g")
        assertEquals("ground beef 90/10 raw", parsed.foodPhrase)
        assertEquals(100.0, parsed.amount)
        assertEquals(MeasureUnit.GRAM, parsed.unit)
        assertEquals("Ground beef 90/10 raw", FoodMatcher(FoodCatalog.fixtures).best(parsed.foodPhrase)?.food?.name)

        assertEquals(0.5, parser.parse("1/2 banana").amount)
        assertEquals(null, parser.parse("1/2 banana").unit)
        assertEquals(0.5, parser.parse("banana 1/2").amount)
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
    fun expandedFoodsKeepSourceIdentityAndRawMeatDefaults() {
        val chicken = FoodCatalog.fixtures.first { it.id == "usda_2646170_chicken_breast_raw" }
        assertEquals("2646170", chicken.sourceId)
        assertEquals("USDA FoodData Central Foundation Foods", chicken.sourceName)
        assertEquals("2026-04-30", chicken.sourceRelease)
        assertEquals("raw", chicken.preparation)
        assertEquals(MeasureUnit.GRAM, chicken.defaultUnit)

        val matcher = FoodMatcher(FoodCatalog.fixtures)
        assertEquals("Chicken breast raw", matcher.best("chicken")?.food?.name)
        assertEquals("Red bell pepper raw", matcher.best("red pepper")?.food?.name)
        assertEquals("Chicken breast raw", matcher.best("skinless chicken")?.food?.name)
        assertEquals("Tuna canned drained", matcher.best("light tuna")?.food?.name)
        assertEquals("Ketchup", matcher.best("restaurant ketchup")?.food?.name)
        assertEquals(null, matcher.best("boneless salmon"))
        assertEquals(null, matcher.best("cooked chicken"))

        val calculation = NutritionCalculator().calculate(chicken, 100.0, MeasureUnit.GRAM)
        assertTrue(calculation.supported)
        assertEquals(106.0, calculation.nutrition.calories, 0.01)
        assertEquals(22.5, calculation.nutrition.protein ?: -1.0, 0.01)
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

        val mustard = FoodCatalog.fixtures.first { it.id == "usda_326698_mustard" }
        val mustardTeaspoon = NutritionCalculator().calculate(mustard, 1.0, MeasureUnit.TEASPOON)
        assertTrue(mustardTeaspoon.supported)
        assertEquals(6.0, mustardTeaspoon.resolvedBaseAmount, 0.01)
    }

    @Test
    fun unsupportedTablespoonsUseBasisAwareApproximateFallback() {
        val pestoMl = FoodRecord(
            id = "off-pesto-ml",
            name = "Branded pesto",
            aliases = listOf("pesto"),
            category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(400.0, 5.0, 8.0, 38.0)),
            defaultAmount = 100.0,
            defaultUnit = MeasureUnit.MILLILITER
        )
        val calculator = NutritionCalculator()

        val tablespoon = calculator.calculate(pestoMl, 1.0, MeasureUnit.TABLESPOON)
        assertTrue(tablespoon.supported)
        assertTrue(tablespoon.approximate)
        assertEquals(15.0, tablespoon.resolvedBaseAmount, 0.01)
        assertEquals(60.0, tablespoon.nutrition.calories, 0.01)
        assertTrue(tablespoon.provenance.contains("15 ml"))
        assertTrue(tablespoon.provenance.contains("Approximate"))

        val teaspoon = calculator.calculate(pestoMl, 1.0, MeasureUnit.TEASPOON)
        assertEquals(5.0, teaspoon.resolvedBaseAmount, 0.01)
        assertTrue(teaspoon.approximate)

        val unsupportedPiece = calculator.calculate(pestoMl, 1.0, MeasureUnit.PIECE)
        assertTrue(!unsupportedPiece.supported)
    }

    @Test
    fun explicitProductConversionWinsOverFallbackAndGramBasisGetsRoughEstimate() {
        val brandedPesto = FoodRecord(
            id = "off-pesto-g",
            name = "Branded pesto",
            aliases = listOf("pesto"),
            category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(500.0, 4.0, 10.0, 48.0)),
            conversions = listOf(ServingConversion(MeasureUnit.TABLESPOON, 18.0, "Product label: 1 tbsp = 18 g")),
            defaultAmount = 100.0,
            defaultUnit = MeasureUnit.GRAM
        )
        val calculator = NutritionCalculator()
        val explicit = calculator.calculate(brandedPesto, 1.0, MeasureUnit.TABLESPOON)
        assertEquals(18.0, explicit.resolvedBaseAmount, 0.01)
        assertTrue(!explicit.approximate)
        assertEquals("Product label: 1 tbsp = 18 g", explicit.provenance)

        val noProductConversion = brandedPesto.copy(conversions = emptyList())
        val fallback = calculator.calculate(noProductConversion, 1.0, MeasureUnit.TABLESPOON)
        assertEquals(15.0, fallback.resolvedBaseAmount, 0.01)
        assertTrue(fallback.approximate)
        assertTrue(fallback.provenance.contains("density varies"))

        val nonHundredBasis = noProductConversion.copy(
            nutrition = noProductConversion.nutrition.copy(amount = 30.0)
        )
        val nonHundredFallback = calculator.calculate(nonHundredBasis, 1.0, MeasureUnit.TABLESPOON)
        assertTrue(nonHundredFallback.provenance.contains("per-30 g nutrition basis"))
        assertTrue(!nonHundredFallback.provenance.contains("per-100 g nutrition basis"))
    }

    @Test
    fun srLegacySaucesKeepSourceMetadataAndPortions() {
        val mayonnaise = FoodCatalog.fixtures.first { it.id == "usda_171009_mayonnaise" }
        val soySauce = FoodCatalog.fixtures.first { it.id == "usda_174277_soy_sauce" }
        val barbecue = FoodCatalog.fixtures.first { it.id == "usda_174523_barbecue_sauce" }

        assertEquals("USDA FoodData Central SR Legacy", mayonnaise.sourceName)
        assertEquals("2018-04", mayonnaise.sourceRelease)
        assertEquals(13.8, NutritionCalculator().calculate(mayonnaise, 1.0, MeasureUnit.TABLESPOON).resolvedBaseAmount, 0.01)
        assertEquals(5.3, NutritionCalculator().calculate(soySauce, 1.0, MeasureUnit.TEASPOON).resolvedBaseAmount, 0.01)
        assertEquals(17.0, NutritionCalculator().calculate(barbecue, 1.0, MeasureUnit.TABLESPOON).resolvedBaseAmount, 0.01)
    }

    @Test
    fun storageCodecRoundTripsEntriesPreferencesAndFoods() {
        val food = FoodCatalog.fixtures.first { it.id == "usda_2646170_chicken_breast_raw" }
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
        assertEquals("2646170", restored.foods.first().sourceId)
        assertEquals("2026-04-30", restored.foods.first().sourceRelease)
        assertEquals("Chicken, breast, boneless, skinless, raw", restored.foods.first().sourceDescription)
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
        assertEquals("my pb", migratedFixture.aliases.first())
        assertTrue("peanut butter" in migratedFixture.aliases)
        assertTrue(migratedFixture.conversions.any { it.unit == MeasureUnit.TEASPOON })
        assertEquals(userFood, migrated.foods.first { it.id == "user_pb" })
        assertEquals(original.preferences, migrated.preferences)
        assertEquals(original.entries, migrated.entries)
    }

    @Test
    fun fixtureMigrationAppendsNewFoodsWithoutReplacingStoredFoods() {
        val storedFixture = FoodCatalog.fixtures.first { it.id == "milk_28" }
            .copy(aliases = listOf("my milk"))
        val legacyChicken = FoodCatalog.fixtures.first { it.id == "usda_2646170_chicken_breast_raw" }
            .copy(aliases = listOf("my chicken"), sourceDescription = null)
        val userFood = storedFixture.copy(id = "user_milk", name = "My milk")
        val original = StorageModel(
            foods = listOf(storedFixture, userFood, legacyChicken),
            entries = emptyList(),
            preferences = listOf(Preference("user_milk", explicitCount = 2, lastReinforcedEpochMs = 7L))
        )

        val migrated = StorageMigrations.addFixtureConversions(original)

        assertTrue(migrated.foods.any { it.id == "usda_2646170_chicken_breast_raw" })
        assertTrue(migrated.foods.any { it.id == "usda_171009_mayonnaise" })
        assertTrue(migrated.foods.any { it.id == "usda_174277_soy_sauce" })
        assertTrue(migrated.foods.any { it.id == "usda_174523_barbecue_sauce" })
        assertTrue(migrated.foods.none { it.id == "custom_grandmas_ajvar" })
        val migratedMilk = migrated.foods.first { it.id == "milk_28" }
        assertEquals("my milk", migratedMilk.aliases.first())
        assertTrue("milk" in migratedMilk.aliases)
        val migratedChicken = migrated.foods.first { it.id == legacyChicken.id }
        assertTrue("my chicken" in migratedChicken.aliases)
        assertTrue("skinless chicken" in migratedChicken.aliases)
        assertEquals("Chicken, breast, boneless, skinless, raw", migratedChicken.sourceDescription)
        assertEquals(userFood, migrated.foods.first { it.id == "user_milk" })
        assertEquals(original.preferences, migrated.preferences)
        assertEquals(original.entries, migrated.entries)
    }
}
