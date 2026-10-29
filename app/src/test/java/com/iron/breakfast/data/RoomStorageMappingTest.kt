package com.iron.breakfast.data

import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ServingConversion
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomStorageMappingTest {
    @Test
    fun fixtureSeedIsFreshAndIdempotentByStableId() {
        val all = fixtureRowsMissingFrom(emptySet())
        assertEquals(FoodCatalog.fixtures.map { it.id }.toSet(), all.map { it.id }.toSet())
        assertTrue(fixtureRowsMissingFrom(all.map { it.id }.toSet()).isEmpty())
        val existing = setOf(FoodCatalog.fixtures.first().id)
        assertTrue(fixtureRowsMissingFrom(existing).none { it.id in existing })
    }

    @Test
    fun mealEntryRoundTripPreservesSnapshotAndFutureMealFields() {
        val original = MealEntry(
            id = "entry-1",
            foodId = "food-1",
            foodName = "Example",
            enteredAmount = 2.0,
            enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 2.0,
            nutritionSnapshot = NutritionValues(10.0, 0.0, 1.5, 0.5),
            conversionProvenance = "2 g",
            approximateConversion = false,
            savedAtEpochMs = 0L,
            sourcePhrase = "2 g example"
        )
        assertEquals(original, original.toEntity().toDomain())
        assertEquals(FoodOrigin.GENERIC, FoodCatalog.fixtures.first().origin)
    }

    @Test
    fun mealEntryMappingPreservesImmutableResolvedBasisUnit() {
        val bananaServing = MealEntry(
            id = "banana-serving", foodId = "banana", foodName = "Banana",
            enteredAmount = 1.0, enteredUnit = MeasureUnit.PIECE,
            resolvedBaseAmount = 118.0, resolvedBaseUnit = MeasureUnit.GRAM,
            nutritionSnapshot = NutritionValues(105.0, null, null, null),
            conversionProvenance = "medium banana reference", approximateConversion = true,
            savedAtEpochMs = 1L,
        )
        assertEquals(MeasureUnit.GRAM, bananaServing.toEntity().toDomain().resolvedBaseUnit)
    }

    @Test
    fun nullableMacroColumnsKeepMissingDistinctFromZero() {
        val missing = MealEntryEntity(
            id = "missing", foodId = "food", foodName = "food", enteredAmount = 1.0,
            enteredUnit = MeasureUnit.PIECE.name, resolvedBaseAmount = 1.0, snapshotCalories = 10.0,
            snapshotProtein = null, snapshotCarbohydrates = null, snapshotFat = null,
            conversionProvenance = "piece", approximateConversion = false, savedAtEpochMs = 1L,
            sourcePhrase = "food", mealSlot = "BREAKFAST", dayKey = "1970-01-01"
        )
        val zero = missing.copy(snapshotProtein = 0.0, snapshotCarbohydrates = 0.0, snapshotFat = 0.0)
        assertEquals(null, missing.snapshotProtein)
        assertNotEquals(missing.snapshotProtein, zero.snapshotProtein)
        assertEquals(0.0, zero.snapshotProtein)
        assertNull(missing.toDomain().nutritionSnapshot.protein)
        assertEquals(0.0, zero.toDomain().nutritionSnapshot.protein)
    }

    @Test
    fun nullableMacrosScaleAsMissingButAggregateAccordingToSuppliedValues() {
        val missingEntity = MealEntryEntity(
            id = "missing", foodId = "food", foodName = "food", enteredAmount = 1.0,
            enteredUnit = MeasureUnit.PIECE.name, resolvedBaseAmount = 1.0, snapshotCalories = 10.0,
            snapshotProtein = null, snapshotCarbohydrates = null, snapshotFat = null,
            conversionProvenance = "piece", approximateConversion = false, savedAtEpochMs = 1L,
            sourcePhrase = "food", mealSlot = "BREAKFAST", dayKey = "1970-01-01"
        )
        val missing = missingEntity.toDomain().nutritionSnapshot
        assertNull((missing * 2.0).protein)

        val unknown = NutritionValues(20.0, null, null, null)
        val aggregateWithOnlyUnknown = NutritionValues.ZERO + unknown
        assertEquals(0.0, aggregateWithOnlyUnknown.protein)

        val known = NutritionValues(30.0, 4.0, null, null)
        val knownSubtotal = NutritionValues.ZERO + known + unknown
        assertEquals(4.0, knownSubtotal.protein)

        val trueZero = NutritionValues(30.0, 0.0, null, null)
        assertEquals(0.0, trueZero.protein)
        assertNotEquals(null, trueZero.protein)
    }

    @Test
    fun foodEntityRetainsSourceAndNullableNutrientColumns() {
        val food = FoodCatalog.fixtures.first()
        val entity = food.toEntity()
        assertEquals(food.id, entity.id)
        assertEquals(food.sourceId, entity.sourceId)
        assertEquals(food.sourceRelease, entity.sourceRelease)
        assertEquals(food.nutrition.values.protein, entity.protein)
        assertEquals(food.nutrition.values.fat, entity.fat)
    }

    @Test
    fun personalOverrideRoundTripsAlongsideOffSourceIdentity() {
        val edited = FoodCatalog.fixtures.first().copy(
            sourceName = "Open Food Facts", sourceId = "123", barcode = "123",
            personalOverride = true, name = "My local name"
        )
        val restored = edited.toEntity().toDomain(edited.aliases, edited.conversions)
        assertEquals("123", restored.sourceId)
        assertEquals("123", restored.barcode)
        assertTrue(restored.personalOverride)
        assertEquals("My local name", restored.name)
    }

    @Test
    fun aCustomFoodRoundTripsAndIsNeverStoredArchived() {
        val archived = FoodCatalog.fixtures.first().copy(
            id = "custom-food", origin = FoodOrigin.CUSTOM,
            aliases = listOf("legacy alias")
        )
        assertEquals(archived, archived.toEntity().toDomain(archived.aliases, archived.conversions))
        assertEquals(false, archived.toEntity().archived)
        val entry = MealEntry(
            id = "historical", foodId = archived.id, foodName = archived.name,
            enteredAmount = 100.0, enteredUnit = MeasureUnit.GRAM, resolvedBaseAmount = 100.0,
            nutritionSnapshot = NutritionValues(archived.nutrition.values.calories, 1.0, 2.0, 3.0),
            conversionProvenance = "historical snapshot", approximateConversion = false,
            savedAtEpochMs = 1L, dayKey = "1970-01-01"
        )
        assertEquals(archived.nutrition.values.calories, entry.nutritionSnapshot.calories)
        assertEquals("historical snapshot", entry.conversionProvenance)
    }

    @Test
    fun personalServingLabelRoundTripsThroughRoomMapping() {
        val food = FoodCatalog.fixtures.first().copy(
            conversions = listOf(ServingConversion(MeasureUnit.MILLILITER, 330.0, "personal serving", label = "bottle"))
        )
        val persisted = food.toConversionEntities().single()
        val restored = food.toEntity().toDomain(food.aliases, listOf(persisted.toDomain()))
        assertEquals("bottle", restored.conversions.single().label)
    }
}
