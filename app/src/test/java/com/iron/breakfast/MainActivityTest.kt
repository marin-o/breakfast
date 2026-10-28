package com.iron.breakfast

import com.iron.breakfast.data.toConversionEntities
import com.iron.breakfast.data.toDomain
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.domain.decimalInput
import com.iron.breakfast.domain.normalizeBarcode
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.domain.NutritionCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.iron.breakfast.domain.libraryRecencyOrder
import com.iron.breakfast.domain.editedCustomFoodRecord
import com.iron.breakfast.scan.productPrefill
import com.iron.breakfast.scan.parseProductUnit
import com.iron.breakfast.contribution.contributionDraftAfterLocalSave
import com.iron.breakfast.scan.productNutritionBasisLabel
import com.iron.breakfast.food.FoodDraft

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

        // A queued part prefills from the planner's reading, as the resolve flow passes it; a glued part keeps its amount.
        val planner = com.iron.breakfast.domain.LogPlanner(com.iron.breakfast.domain.FoodMatcher(com.iron.breakfast.domain.FoodCatalog.fixtures))
        val resolver = productPrefill(pesto, planner.typed("1 tbsp pesto"))
        assertEquals(1.0, resolver.amount)
        assertEquals(MeasureUnit.TABLESPOON, resolver.unit)

        val glued = productPrefill(pesto, planner.typed("млеко200г"))
        assertEquals(200.0, glued.amount)
        assertEquals(MeasureUnit.GRAM, glued.unit)

        val directSearch = productPrefill(pesto, planner.typed("pesto"))
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
            sourceName = "Open Food Facts", barcode = "4006381333931"
        )
        assertNull(contributionDraftAfterLocalSave(scanned, fromScan = false))
        assertEquals("4006381333931", contributionDraftAfterLocalSave(scanned, fromScan = true)?.barcode)
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

    @Test
    fun productLabelUsesActualNutritionBasis() {
        val food = FoodRecord(
            id = "basis", name = "Soup", aliases = emptyList(), category = "Open Food Facts", origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(250.0, MeasureUnit.GRAM, NutritionValues(300.0, null, null, null))
        )
        assertEquals("Calories per 250 g", productNutritionBasisLabel(food))
    }

    private fun food(id: String, name: String, origin: FoodOrigin = FoodOrigin.GENERIC, barcode: String? = null, archived: Boolean = false, sourceId: String? = null) = FoodRecord(
        id = id, name = name, aliases = emptyList(), category = "Test", origin = origin,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(100.0, 1.0, 2.0, 3.0)),
        barcode = barcode, archived = archived, sourceId = sourceId,
    )

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
    fun barcodeValidationBlocksOtherFoodsIncludingArchivedAndNamesThem() {
        val archived = food("archived", "Old Oats", barcode = "1234567890123", archived = true)
        val self = food("self", "Mine", barcode = "4006381333931")
        val foods = listOf(archived, self)
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
