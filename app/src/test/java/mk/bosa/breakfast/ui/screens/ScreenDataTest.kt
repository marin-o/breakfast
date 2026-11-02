package mk.bosa.breakfast.ui.screens

import mk.bosa.breakfast.domain.editableNumber
import mk.bosa.breakfast.ui.shortDateLabel
import mk.bosa.breakfast.ui.countLabel
import mk.bosa.breakfast.contribution.contributionAvailable
import mk.bosa.breakfast.domain.sourceLetter
import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.TestFoods
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionCalculator
import mk.bosa.breakfast.domain.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import kotlin.math.round

class ScreenDataTest {
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

    private fun assertPrintedProductRounds(display: QuantityDisplay, calories: Double) {
        val parenthetical = Regex("\\(([0-9.]+) [a-z]+\\) .* ([0-9.]+)(?: kcal/[a-z]+)?$").find(display.line)
        val direct = Regex("^([0-9.]+) [a-z]+ .* ([0-9.]+)(?: kcal/[a-z]+)?$").find(display.line)
        val match = parenthetical ?: direct
        requireNotNull(match) { "Could not parse printed quantity: ${display.line}" }
        val amount = match.groupValues[1].toDouble()
        val rate = match.groupValues[2].toDouble()
        assertEquals(round(calories), round(amount * rate), 0.0)
    }

    @Test
    fun sourceMarkerMapsUsdaPersonalAndOpenFoodFacts() {
        assertEquals("B", sourceLetter(generic))
        assertEquals("P", sourceLetter(generic.copy(origin = FoodOrigin.CUSTOM)))
        assertEquals("P", sourceLetter(generic.copy(personalOverride = true)))
        assertEquals("P", sourceLetter(generic.copy(personalOverride = true, sourceName = "Open Food Facts")))
        assertEquals("O", sourceLetter(generic.copy(sourceName = "Open Food Facts")))
        assertEquals("B", sourceLetter(generic.copy(id = "local-generic", sourceName = "Local import", sourceId = "local-1")))
        assertEquals("B", sourceLetter(generic.copy(id = "never-a-fixture", sourceName = null, sourceId = null)))
        assertEquals("?", sourceLetter(null))
        // A food an older version bundled stays stored, and stays Bundled.
        assertEquals("B", sourceLetter(TestFoods.fixtures.first { it.id == "banana" }))
        assertEquals("Bundled", sourceLabel("B"))
        assertEquals("Unknown source", sourceLabel("?"))
        val usda = generic.copy(id = "fndds_2705975", sourceName = "USDA FoodData Central")
        assertEquals("U", sourceLetter(usda))
        assertEquals("USDA FoodData Central", sourceLabel("U"))
        assertEquals("P", sourceLetter(usda.copy(personalOverride = true)))
        val off = generic.copy(id = "off-local", sourceName = "Open Food Facts", sourceId = "4006381333931")
        assertEquals("O", sourceLetter(off))
        assertEquals("P", sourceLetter(off.copy(personalOverride = true)))
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
        assertEquals("330 ml \u00D7 0.19 kcal/ml", display.line)
        assertPrintedProductRounds(display, liquid.nutritionSnapshot.calories)
    }

    @Test
    fun exactServingStillShowsResolvedBaseAndDirectAmountStaysEntered() {
        val exactServing = entry.copy(approximateConversion = false, resolvedBaseUnit = MeasureUnit.GRAM)
        assertEquals("1 tsp (5.3 g) \u00D7 5.88 kcal/g", quantityRateDisplay(exactServing, null).line)
        val direct = entry.copy(
            enteredAmount = 100.0,
            enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 100.0,
            resolvedBaseUnit = MeasureUnit.GRAM,
            nutritionSnapshot = NutritionValues(120.0, null, null, null),
            approximateConversion = false,
        )
        val display = quantityRateDisplay(direct, null)
        assertEquals("100 g \u00D7 1.20 kcal/g", display.line)
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
        assertEquals("1 tsp (3 g) \u00D7 0.33 kcal/g", display.line)
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

    @Test
    fun basisLabelUsesTheRealBasisAmountAndUnit() {
        assertEquals("Per 100 ml", basisLabel(100.0, MeasureUnit.MILLILITER))
        assertEquals("Per 100 g", basisLabel(100.0, MeasureUnit.GRAM))
        assertEquals("Per 250 ml", basisLabel(250.0, MeasureUnit.MILLILITER))
        assertEquals("Per 30.5 g", basisLabel(30.5, MeasureUnit.GRAM))
    }

    @Test
    fun contributionIsHiddenOnceAnOffFoodBarcodeNoLongerMatchesItsSource() {
        val off = generic.copy(sourceName = "Open Food Facts", sourceId = "5449000000996", barcode = "5449000000996")
        assertEquals(true, contributionAvailable(off))
        assertEquals(true, contributionAvailable(off.copy(personalOverride = true)))
        assertEquals(false, contributionAvailable(off.copy(barcode = "4006381333931")))
        assertEquals(false, contributionAvailable(off.copy(barcode = null)))
        assertEquals(true, contributionAvailable(generic.copy(barcode = "4006381333931")))
    }

    @Test
    fun contributionDisplayRequiresANormalizedBarcode() {
        val off = generic.copy(id = "off-local", sourceName = "Open Food Facts", sourceId = "4006381333931")
        assertEquals(false, contributionAvailable(off.copy(barcode = "1234567")))
        assertEquals(false, contributionAvailable(off.copy(barcode = "１２３４５６７８")))
        assertEquals(false, contributionAvailable(off.copy(barcode = "١٢٣٤٥٦٧٨")))
        assertEquals(true, contributionAvailable(off.copy(barcode = " 4006381333931 ")))
    }

    @Test
    fun contributionIsHiddenForAFoodWhoseNutritionCouldNotBeSent() {
        val food = generic.copy(barcode = "4006381333931")
        assertEquals(true, contributionAvailable(food))
        val heavy = food.copy(nutrition = food.nutrition.copy(amount = 100.0, values = NutritionValues(400.0, 500.0, null, null)))
        assertEquals(false, contributionAvailable(heavy))
    }

    @Test
    fun foodCountPluralises() {
        assertEquals("0 foods", countLabel(0, "food", "foods"))
        assertEquals("1 food", countLabel(1, "food", "foods"))
        assertEquals("13 foods", countLabel(13, "food", "foods"))
        assertEquals("1 result", countLabel(1, "result", "results"))
    }

    @Test
    fun rateUnitIsDroppedWhenApproximateOrTooLong() {
        assertEquals("60 g × 3.89 kcal/g", withRateUnit("60 g × 3.89", "g", false))
        assertEquals("(16 g) × 5.88", withRateUnit("(16 g) × 5.88", "g", true))
        val long = "1 tablespoon (15.25 g) × 5.8812"
        assertEquals(long, withRateUnit(long, "g", false))
    }

    @Test
    fun aMealsRowsShowTheAmountWithoutTheRate() {
        val grams = entry.copy(enteredAmount = 8.0, enteredUnit = MeasureUnit.GRAM, enteredUnitLabel = null, approximateConversion = false)
        assertEquals("8 g", entryQuantityLine(grams))
        val spoon = entry.copy(enteredAmount = 1.0, enteredUnit = MeasureUnit.TABLESPOON, enteredUnitLabel = null, approximateConversion = true)
        assertEquals("≈1 tbsp", entryQuantityLine(spoon))
    }

    @Test
    fun editableNumbersDropTrailingZero() {
        assertEquals("19", editableNumber(19.0))
        assertEquals("389.5", editableNumber(389.5))
        assertEquals("0.25", editableNumber(0.25))
        assertEquals("100", editableNumber(100.0))
        assertEquals("", editableNumber(null))
    }

    @Test
    fun entryDateLineHasNoComma() {
        assertEquals("Wed 14 Oct", shortDateLabel("2026-10-14"))
    }

    @Test
    fun kcalAlwaysWhole() {
        assertEquals("148", formatKcal(148.1))
        assertEquals("389", formatKcal(388.6))
    }

    @Test fun aChoiceThatCannotBeCountedShowsNoKcal() {
        val oats = TestFoods.fixtures.first { it.nutrition.unit == MeasureUnit.GRAM && it.conversions.none { c -> c.unit == MeasureUnit.PIECE } }
        assertNull(choiceKcalDetail(NutritionCalculator().calculate(oats, 2.0, MeasureUnit.PIECE)))
        assertEquals("${formatKcal(oats.nutrition.values.calories)} kcal", choiceKcalDetail(NutritionCalculator().calculate(oats, oats.nutrition.amount, oats.nutrition.unit)))
    }
}
