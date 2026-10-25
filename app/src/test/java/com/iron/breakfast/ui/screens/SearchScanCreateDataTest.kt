package com.iron.breakfast.ui.screens

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.NutritionInput
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ParsedAmountUnit
import com.iron.breakfast.domain.ServingConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchScanCreateDataTest {
    private val ajvar = FoodRecord(
        id = "ajvar", name = "Ajvar, mild", aliases = emptyList(), category = "Sauce", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(120.0, 2.0, 9.0, 8.0)),
        conversions = listOf(ServingConversion(MeasureUnit.TABLESPOON, 15.0, "estimate", approximate = true)),
    )

    @Test
    fun basisCaptionFollowsTheProductsOwnUnit() {
        assertEquals("/100 g", basisCaption(100.0, MeasureUnit.GRAM))
        assertEquals("/100 ml", basisCaption(100.0, MeasureUnit.MILLILITER))
        assertEquals("/250 ml", basisCaption(250.0, MeasureUnit.MILLILITER))
    }

    @Test
    fun unknownDisplayKeepsTheTypedQuantity() {
        assertEquals(UnknownDisplay("2 tbsp", "ajvar lovec"), unknownDisplay("2 tbsp ajvar lovec"))
        assertEquals(UnknownDisplay(null, "ajvar lovec"), unknownDisplay("ajvar lovec"))
        assertEquals(UnknownDisplay("1.5 g", "salt"), unknownDisplay("1.5 g salt"))
    }

    @Test
    fun matchDisplayShowsApproximateHouseholdMeasureWithBaseAmount() {
        val match = matchDisplay(ajvar, "2 tbsp ajvar lovec")
        assertEquals("2 tbsp", match.approximateLabel)
        assertEquals("(30 g)", match.detail)
        assertEquals("36", match.kcal)
    }

    @Test
    fun matchDisplayLeavesExactAmountsUnmarked() {
        val grams = matchDisplay(ajvar, "50 g ajvar")
        assertNull(grams.approximateLabel)
        assertEquals("50 g", grams.detail)
        assertEquals("60", grams.kcal)
        val unsupported = matchDisplay(ajvar, "1 piece ajvar")
        assertEquals("—", unsupported.kcal)
        assertNull(unsupported.approximateLabel)
    }

    @Test
    fun matchDisplayUsesDefaultAmountWhenNoneWasTyped() {
        val match = matchDisplay(ajvar.copy(defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM), "ajvar")
        assertEquals("100 g", match.detail)
        assertEquals("120", match.kcal)
    }

    @Test
    fun reviewAmountAcceptsUnitsAndBareNumbers() {
        assertEquals(ParsedAmountUnit(2.0, MeasureUnit.TABLESPOON), parseProductAmount("2 tbsp", MeasureUnit.GRAM))
        assertEquals(ParsedAmountUnit(250.0, MeasureUnit.MILLILITER), parseProductAmount("250", MeasureUnit.MILLILITER))
        assertEquals(ParsedAmountUnit(1.5, MeasureUnit.GRAM), parseProductAmount("1,5", MeasureUnit.GRAM))
        assertNull(parseProductAmount("", MeasureUnit.GRAM))
        assertNull(parseProductAmount("0", MeasureUnit.GRAM))
        assertNull(parseProductAmount("2 tbsp oats", MeasureUnit.GRAM))
    }

    @Test
    fun reviewApproximationChipOnlyForGuessedConversions() {
        val calculator = NutritionCalculator()
        val tbsp = calculator.calculate(ajvar, 2.0, MeasureUnit.TABLESPOON)
        assertEquals("30 g", productApproxLabel(tbsp, MeasureUnit.TABLESPOON, MeasureUnit.GRAM))
        val grams = calculator.calculate(ajvar, 30.0, MeasureUnit.GRAM)
        assertNull(productApproxLabel(grams, MeasureUnit.GRAM, MeasureUnit.GRAM))
        assertNull(productApproxLabel(null, null, MeasureUnit.GRAM))
    }

    @Test
    fun shorthandFillsParsedValuesAndLeavesAbsentOnesEmpty() {
        val state = createFoodState(NutritionInput(shorthand = "per 100g: 120 kcal, 2g protein, 8g fat"), "Ajvar", null)
        assertEquals(100.0, state.nutrition.basisAmount, 0.0)
        assertEquals(MeasureUnit.GRAM, state.nutrition.basisUnit)
        assertEquals(120.0, state.nutrition.calories!!, 0.0)
        assertEquals(2.0, state.nutrition.protein!!, 0.0)
        assertNull(state.nutrition.carbohydrates)
        assertEquals(8.0, state.nutrition.fat!!, 0.0)
        assertTrue(state.canSave)
        assertFalse(state.showRequired)
    }

    @Test
    fun shorthandBasisUsesMillilitresWhenItSaysSo() {
        val nutrition = createFoodState(NutritionInput(shorthand = "per 250ml: 90 kcal"), "Juice", null).nutrition
        assertEquals(250.0, nutrition.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, nutrition.basisUnit)
    }

    @Test
    fun missingCaloriesBlockSaveAndShowRequiredOnceStarted() {
        val untouched = createFoodState(NutritionInput(), "Ajvar", null)
        assertTrue(untouched.caloriesMissing)
        assertFalse(untouched.canSave)
        assertFalse(untouched.showRequired)
        val started = createFoodState(NutritionInput(shorthand = "per 100g: 2g protein"), "Ajvar", null)
        assertTrue(started.showRequired)
        assertFalse(started.canSave)
        assertEquals(2.0, started.nutrition.protein!!, 0.0)
        assertNull(started.nutrition.calories)
    }

    @Test
    fun saveNeedsNameAndAValidBarcode() {
        val input = NutritionInput(calories = "120")
        assertTrue(createFoodState(input, "Ajvar", null).canSave)
        assertFalse(createFoodState(input, "  ", null).canSave)
        assertFalse(createFoodState(input, "Ajvar", "That barcode already belongs to Milk.").canSave)
    }

    @Test
    fun structuredFieldsWinOverShorthandPerField() {
        val resolved = createFoodState(NutritionInput(shorthand = "100 kcal, 5g protein", calories = "130"), "Ajvar", null).nutrition
        assertEquals(130.0, resolved.calories!!, 0.0)
        assertEquals(5.0, resolved.protein!!, 0.0)
    }
}
