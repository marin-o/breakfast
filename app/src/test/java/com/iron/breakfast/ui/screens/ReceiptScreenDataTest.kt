package com.iron.breakfast.ui.screens

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import kotlin.math.round

class ReceiptScreenDataTest {
    private val generic = FoodRecord(
        id = "milk_28", name = "Chicken", aliases = emptyList(), category = "Meat",
        origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(120.0, null, null, null)),
        sourceId = "123", sourceName = "USDA FoodData Central Foundation Foods",
    )

    private val entry = MealEntry(
        id = "entry", foodId = "pb", foodName = "Peanut butter", enteredAmount = 1.0,
        enteredUnit = MeasureUnit.TEASPOON, resolvedBaseAmount = 5.3,
        nutritionSnapshot = NutritionValues(31.164, null, null, null), conversionProvenance = "portion",
        approximateConversion = true, savedAtEpochMs = 0L, resolvedBaseUnit = MeasureUnit.GRAM,
    )

    private fun assertPrintedProductRounds(display: ReceiptQuantityDisplay, calories: Double) {
        val parenthetical = Regex("\\(([0-9.]+) [a-z]+\\) .* ([0-9.]+)$").find(display.line)
        val direct = Regex("^([0-9.]+) [a-z]+ .* ([0-9.]+)$").find(display.line)
        val match = parenthetical ?: direct
        requireNotNull(match) { "Could not parse printed quantity: ${display.line}" }
        val amount = match.groupValues[1].toDouble()
        val rate = match.groupValues[2].toDouble()
        assertEquals(round(calories), round(amount * rate), 0.0)
    }

    @Test
    fun sourceMarkerMapsBundledPersonalAndOpenFoodFacts() {
        assertEquals("B", sourceLetter(generic))
        assertEquals("P", sourceLetter(generic.copy(origin = FoodOrigin.CUSTOM)))
        assertEquals("P", sourceLetter(generic.copy(personalOverride = true)))
        assertEquals("O", sourceLetter(generic.copy(sourceName = "Open Food Facts")))
        assertEquals("?", sourceLetter(generic.copy(id = "local-generic", sourceName = "Local import", sourceId = "local-1")))
        assertEquals("B", sourceLetter(FoodCatalog.fixtures.first { it.id == "banana" }))
        assertEquals("B", sourceLetter(FoodCatalog.fixtures.first { it.id == "oat_milk" }))
    }

    @Test
    fun quantityRateUsesSnapshotAndResolvedBaseAmount() {
        val display = quantityRateDisplay(entry, generic.copy(id = "pb", nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(588.0, null, null, null))))
        assertEquals("(5.3 g) \u00D7 5.88", display.line)
        assertEquals("1 tsp", display.approximateLabel)
        assertPrintedProductRounds(display, entry.nutritionSnapshot.calories)
    }

    @Test
    fun liquidRateUsesMillilitersAndSnapshot() {
        val liquid = entry.copy(
            enteredAmount = 330.0,
            enteredUnit = MeasureUnit.MILLILITER,
            resolvedBaseAmount = 330.0,
            resolvedBaseUnit = MeasureUnit.MILLILITER,
            nutritionSnapshot = NutritionValues(62.7, null, null, null),
            approximateConversion = false,
        )
        val food = generic.copy(id = "water", nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(19.0, null, null, null)))
        val display = quantityRateDisplay(liquid, food)
        assertEquals("330 ml \u00D7 0.19", display.line)
        assertPrintedProductRounds(display, liquid.nutritionSnapshot.calories)
    }

    @Test
    fun exactServingStillShowsResolvedBaseAndDirectAmountStaysEntered() {
        val exactServing = entry.copy(approximateConversion = false, resolvedBaseUnit = MeasureUnit.GRAM)
        assertEquals("1 tsp (5.3 g) \u00D7 5.88", quantityRateDisplay(exactServing, null).line)
        val direct = entry.copy(
            enteredAmount = 100.0,
            enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 100.0,
            resolvedBaseUnit = MeasureUnit.GRAM,
            nutritionSnapshot = NutritionValues(120.0, null, null, null),
            approximateConversion = false,
        )
        val display = quantityRateDisplay(direct, null)
        assertEquals("100 g \u00D7 1.20", display.line)
        assertPrintedProductRounds(display, direct.nutritionSnapshot.calories)
    }

    @Test
    fun ratePrecisionReconcilesDisplayedProduct() {
        val adversarial = entry.copy(
            resolvedBaseAmount = 3.0,
            resolvedBaseUnit = MeasureUnit.GRAM,
            nutritionSnapshot = NutritionValues(1.0, null, null, null),
            approximateConversion = false,
        )
        val display = quantityRateDisplay(adversarial, null)
        assertEquals("1 tsp (3 g) \u00D7 0.33", display.line)
        assertPrintedProductRounds(display, adversarial.nutritionSnapshot.calories)
    }

    @Test
    fun displayedAmountAndRateReconcileWhenBothNeedRounding() {
        val direct = entry.copy(
            enteredAmount = 2.25,
            enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 2.25,
            resolvedBaseUnit = MeasureUnit.GRAM,
            nutritionSnapshot = NutritionValues(13.23, null, null, null),
            approximateConversion = false,
        )
        val exactServing = entry.copy(
            enteredAmount = 1.0,
            enteredUnit = MeasureUnit.TEASPOON,
            resolvedBaseAmount = 5.34,
            resolvedBaseUnit = MeasureUnit.GRAM,
            nutritionSnapshot = NutritionValues(31.4, null, null, null),
            approximateConversion = false,
        )
        val liquid = entry.copy(
            enteredAmount = 330.0,
            enteredUnit = MeasureUnit.MILLILITER,
            resolvedBaseAmount = 329.6,
            resolvedBaseUnit = MeasureUnit.MILLILITER,
            nutritionSnapshot = NutritionValues(62.7, null, null, null),
            approximateConversion = true,
        )
        assertPrintedProductRounds(quantityRateDisplay(direct, null), direct.nutritionSnapshot.calories)
        assertPrintedProductRounds(quantityRateDisplay(exactServing, null), exactServing.nutritionSnapshot.calories)
        assertPrintedProductRounds(quantityRateDisplay(liquid, null), liquid.nutritionSnapshot.calories)
    }

    @Test
    fun earliestTimeUsesLocalZoneAndOldestEntry() {
        val later = entry.copy(savedAtEpochMs = 60 * 60 * 1000L)
        assertEquals("01:00", earliestMealTime(listOf(later, entry), ZoneOffset.ofHours(1)))
    }
}
