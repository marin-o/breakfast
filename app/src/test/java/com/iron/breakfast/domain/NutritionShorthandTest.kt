package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionShorthandTest {
    @Test
    fun recognisesLabelledNumbersAndTheirBoldRanges() {
        val text = "per 100g: 120 kcal, 2g protein, 8g fat"
        val parsed = parseNutritionShorthand(text)
        assertEquals(120.0, parsed.calories!!.value, 0.0)
        assertEquals("120", text.substring(parsed.calories!!.range))
        assertEquals("2g", text.substring(parsed.protein!!.range))
        assertEquals("8g", text.substring(parsed.fat!!.range))
        assertEquals("100g", text.substring(parsed.basisAmount!!.range))
        assertNull(parsed.carbohydrates)
        assertEquals(MeasureUnit.GRAM, parsed.basisUnit)
    }

    @Test
    fun keepsTheOriginalLabelledNumberRule() {
        val parsed = parseNutritionShorthand("450 kcal, 12g protein, 20 g carbs, 1,5 g fat")
        assertEquals(450.0, parsed.calories!!.value, 0.0)
        assertEquals(12.0, parsed.protein!!.value, 0.0)
        assertEquals(20.0, parsed.carbohydrates!!.value, 0.0)
        assertEquals(1.5, parsed.fat!!.value, 0.0)
        assertNull(parsed.basisAmount)
    }

    @Test
    fun basisDefaultsToStructuredFieldsAndShorthandPerWins() {
        assertEquals(ResolvedNutrition(120.0, null, null, null, 100.0, MeasureUnit.GRAM), resolveNutrition(NutritionInput(shorthand = "120 kcal")))
        val ml = resolveNutrition(NutritionInput(shorthand = "per 250 ml: 90 kcal", basisAmount = "100", basisUnit = "g"))
        assertEquals(250.0, ml.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, ml.basisUnit)
        val structured = resolveNutrition(NutritionInput(calories = "50", basisAmount = "30", basisUnit = "ml"))
        assertEquals(30.0, structured.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, structured.basisUnit)
    }

    @Test
    fun basisAcceptsGramWordsDecimalsAndAnyCase() {
        val gram = resolveNutrition(NutritionInput(shorthand = "per 100 gram: 120 kcal"))
        assertEquals(100.0, gram.basisAmount, 0.0)
        assertEquals(MeasureUnit.GRAM, gram.basisUnit)
        val comma = resolveNutrition(NutritionInput(shorthand = "per 1,5 ml: 10 kcal"))
        assertEquals(1.5, comma.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, comma.basisUnit)
        val dot = resolveNutrition(NutritionInput(shorthand = "per 1.5 ml: 10 kcal"))
        assertEquals(1.5, dot.basisAmount, 0.0)
        val mixed = resolveNutrition(NutritionInput(shorthand = "PER 250 Ml: 10 KCAL"))
        assertEquals(250.0, mixed.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, mixed.basisUnit)
        assertEquals(10.0, mixed.calories!!, 0.0)
    }

    @Test
    fun changedStructuredBasisBeatsShorthandPer() {
        val amountChanged = resolveNutrition(NutritionInput(shorthand = "per 250 ml: 90 kcal", basisAmount = "50", basisUnit = "g"))
        assertEquals(50.0, amountChanged.basisAmount, 0.0)
        assertEquals(MeasureUnit.GRAM, amountChanged.basisUnit)
        val unitChanged = resolveNutrition(NutritionInput(shorthand = "per 250 g: 90 kcal", basisAmount = "100", basisUnit = "ml"))
        assertEquals(100.0, unitChanged.basisAmount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, unitChanged.basisUnit)
    }

    @Test
    fun noRecognisedNumbersLeavesCaloriesMissing() {
        assertNull(resolveNutrition(NutritionInput(shorthand = "tasty stuff")).calories)
        assertEquals(emptyList<IntRange>(), parseNutritionShorthand("tasty stuff").recognisedRanges)
    }
}
