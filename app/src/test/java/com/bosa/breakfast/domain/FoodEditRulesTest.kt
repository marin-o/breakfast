package com.bosa.breakfast.domain

import com.bosa.breakfast.testFood
import com.bosa.breakfast.data.toConversionEntities
import com.bosa.breakfast.data.toDomain
import com.bosa.breakfast.food.FoodDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Editing a food (personal or cached), the barcode rule, and the Library's order. */
class FoodEditRulesTest {
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
    fun cachedOffFoodEditKeepsSourceIdentityForLaterRecache() {
        val tablespoon = ServingConversion(MeasureUnit.TABLESPOON, 15.0, "package serving")
        val original = FoodRecord(
            id = "off-123", name = "Imported name", aliases = emptyList(), category = "Open Food Facts",
            origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 2.0, 3.0)),
            conversions = listOf(tablespoon), defaultAmount = 1.0, defaultUnit = MeasureUnit.TABLESPOON,
            sourceId = "source-123", sourceName = "Open Food Facts", barcode = "123"
        )
        val edited = editedCustomFoodRecord(original, "My name", "Brand", "123", "alias", NutritionValues(200.0, 2.0, 3.0, 4.0), 100.0, MeasureUnit.GRAM)
        assertEquals(FoodOrigin.GENERIC, edited.origin)
        assertEquals("Open Food Facts", edited.sourceName)
        assertEquals("source-123", edited.sourceId)
        assertEquals("123", edited.barcode)
        assertEquals(listOf(tablespoon), edited.conversions)
        assertEquals(1.0, edited.defaultAmount)
        assertEquals(MeasureUnit.TABLESPOON, edited.defaultUnit)
        assertTrue(edited.personalOverride)
    }

    private fun food(id: String, name: String, origin: FoodOrigin = FoodOrigin.GENERIC, barcode: String? = null, sourceId: String? = null) =
        testFood(id, name, origin = origin, values = NutritionValues(100.0, 1.0, 2.0, 3.0), barcode = barcode, sourceId = sourceId)

    @Test
    fun barcodeRuleAcceptsOnlyAsciiDigitsOfProductLengths() {
        listOf(8, 12, 13, 14).forEach { length -> assertEquals("1".repeat(length), normalizeBarcode("1".repeat(length))) }
        listOf(7, 9, 10, 11, 15).forEach { length -> assertNull(normalizeBarcode("1".repeat(length))) }
        assertEquals("12345678", normalizeBarcode("  12345678 \n"))
        assertNull(normalizeBarcode("١٢٣٤٥٦٧٨"))
        assertNull(normalizeBarcode("１２３４５６７８"))
        assertNull(normalizeBarcode("https://example.com/12345678"))
        assertNull(normalizeBarcode("1234 5678"))
        assertEquals("12", "1٢a2".asciiDigits())
        assertEquals("1.5", "1.٣5".decimalInput())
        assertEquals("15", "1.٣5".asciiDigits())
    }

    @Test
    fun barcodeValidationBlocksOtherFoodsAndNamesThem() {
        val other = food("other-oats", "Old Oats", barcode = "1234567890123")
        val self = food("self", "Mine", barcode = "4006381333931")
        val foods = listOf(other, self)
        assertEquals("That barcode already belongs to Old Oats.", validateBarcodeInput("1234567890123", foods, editingId = "self"))
        assertEquals("That barcode already belongs to Mine.", validateBarcodeInput(" 4006381333931 ", foods, editingId = "other"))
        assertNull(validateBarcodeInput("4006381333931", foods, editingId = "self"))
        assertNull(validateBarcodeInput("", foods, editingId = "self"))
        assertNull(validateBarcodeInput("   ", foods))
        assertEquals("Enter an 8, 12, 13 or 14 digit barcode or clear the field.", validateBarcodeInput("123", foods))
        assertEquals("Enter an 8, 12, 13 or 14 digit barcode or clear the field.", validateBarcodeInput("12345678901", foods))
    }

    @Test
    fun customBarcodeEditMirrorsSourceIdAndClearRemovesBoth() {
        val original = food("custom", "Mine", origin = FoodOrigin.CUSTOM, barcode = "4006381333931", sourceId = "4006381333931")
        val values = NutritionValues(100.0, 1.0, 2.0, 3.0)
        val changed = editedCustomFoodRecord(original, "Mine", "", "5449000000996", "", values, 100.0, MeasureUnit.GRAM)
        assertEquals("5449000000996", changed.barcode)
        assertEquals("5449000000996", changed.sourceId)
        val cleared = editedCustomFoodRecord(changed, "Mine", "", "", "", values, 100.0, MeasureUnit.GRAM)
        assertNull(cleared.barcode)
        assertNull(cleared.sourceId)
    }

    @Test
    fun offFoodBarcodeEditKeepsItsSourceCodeAndClearOnlyRemovesTheBarcode() {
        val original = food("off-1", "Coconut water", barcode = "5449000000996", sourceId = "5449000000996")
        val values = NutritionValues(100.0, 1.0, 2.0, 3.0)
        val changed = editedCustomFoodRecord(original, "Coconut water", "", "4006381333931", "", values, 100.0, MeasureUnit.GRAM)
        assertEquals("4006381333931", changed.barcode)
        assertEquals("5449000000996", changed.sourceId)
        val cleared = editedCustomFoodRecord(changed, "Coconut water", "", "", "", values, 100.0, MeasureUnit.GRAM)
        assertNull(cleared.barcode)
        assertEquals("5449000000996", cleared.sourceId)
    }

    @Test
    fun editedAliasAndServingListsReplaceStoredOnesOutright() {
        val carton = ServingConversion(MeasureUnit.PIECE, 330.0, "carton")
        val original = food("custom", "Mine", origin = FoodOrigin.CUSTOM).copy(aliases = listOf("a", "b"), conversions = listOf(ServingConversion(MeasureUnit.GRAM, 100.0, "custom basis"), carton))
        val values = NutritionValues(100.0, 1.0, 2.0, 3.0)
        val kept = editedCustomFoodRecord(original, "Mine", "", "", "", values, 100.0, MeasureUnit.GRAM, aliases = emptyList(), servings = listOf(carton))
        assertEquals(emptyList(), kept.aliases)
        assertEquals(listOf("custom basis", "carton"), kept.conversions.map { it.provenance })
        val removed = editedCustomFoodRecord(original, "Mine", "", "", "", values, 100.0, MeasureUnit.GRAM, servings = emptyList())
        assertEquals(listOf("custom basis"), removed.conversions.map { it.provenance })
        assertEquals(emptyList(), original.copy(conversions = removed.conversions).householdServings())
    }

    @Test
    fun namedSameBasisServingsSurviveEditAndRoomRoundTrip() {
        val bottle = ServingConversion(MeasureUnit.MILLILITER, 330.0, "personal serving", label = "bottle")
        val glass = ServingConversion(MeasureUnit.MILLILITER, 250.0, "personal serving", approximate = true, label = "glass")
        val original = food("custom", "Juice", origin = FoodOrigin.CUSTOM).copy(
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(40.0, 0.0, 10.0, 0.0)),
            conversions = listOf(ServingConversion(MeasureUnit.MILLILITER, 100.0, "custom basis"), bottle, glass),
        )
        val edited = editedCustomFoodRecord(
            original, "Juice", "", "", "", NutritionValues(40.0, 0.0, 10.0, 0.0), 100.0, MeasureUnit.MILLILITER,
            servings = listOf(bottle, glass),
        )
        val readBack = edited.toConversionEntities().map { it.toDomain() }
        assertEquals(listOf(null, "bottle", "glass"), readBack.map { it.label })
        assertEquals(listOf(330.0, 250.0), readBack.drop(1).map { it.baseUnitsPerServing })
    }

    @Test
    fun draftKeepsMultipleNamedServingsWithTheSameUnit() {
        val draft = FoodDraft()
        draft.servings = listOf(ServingConversion(MeasureUnit.MILLILITER, 330.0, "personal", label = "bottle"))
        draft.basisUnit = "ml"
        draft.servingUnit = MeasureUnit.MILLILITER
        draft.servingAmount = "250"
        draft.servingLabel = "glass"
        assertTrue(draft.commitServing())
        assertEquals(listOf("bottle", "glass"), draft.servings.map { it.label })
    }

    @Test
    fun barcodeEditDoesNotChangeExistingDiaryEntrySnapshots() {
        val original = food("custom", "Mine", origin = FoodOrigin.CUSTOM, barcode = "4006381333931")
        val calc = NutritionCalculator().calculate(original, 50.0, MeasureUnit.GRAM)
        val entry = MealEntry("e1", original.id, original.name, 50.0, MeasureUnit.GRAM, calc.resolvedBaseAmount, calc.nutrition, calc.provenance, calc.approximate, 1L, original.name, MealSlot.LUNCH, "2026-10-14", MeasureUnit.GRAM)
        val before = entry.copy()
        val edited = editedCustomFoodRecord(original, "Mine", "", "5449000000996", "", NutritionValues(100.0, 1.0, 2.0, 3.0), 100.0, MeasureUnit.GRAM)
        assertEquals("5449000000996", edited.barcode)
        assertEquals(before, entry)
        assertEquals(calc.nutrition, entry.nutritionSnapshot)
    }

    @Test
    fun libraryOrdersMostRecentFirstThenTiesByNameAndNeverLoggedLast() {
        val oats = food("oats", "Rolled oats")
        val banana = food("banana", "Banana")
        val apple = food("apple", "Apple")
        val cherry = food("cherry", "cherry")
        val zucchini = food("zucchini", "Zucchini")
        fun entry(id: String, foodId: String, time: Long) = MealEntry(id, foodId, foodId, 100.0, MeasureUnit.GRAM, 100.0, NutritionValues(100.0, null, null, null), "basis", false, time, foodId, MealSlot.BREAKFAST, "2026-10-14", MeasureUnit.GRAM)
        val ordered = libraryRecencyOrder(
            listOf(zucchini, apple, oats, cherry, banana),
            listOf(entry("1", "oats", 10), entry("2", "banana", 20), entry("3", "apple", 20), entry("4", "oats", 5)),
        )
        // apple and banana tie at 20, so name decides; oats' latest is 10; cherry and zucchini were never logged.
        assertEquals(listOf("apple", "banana", "oats", "cherry", "zucchini"), ordered.map { it.id })
    }
}
