package com.bosa.breakfast.ui.screens

import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A counted piece reads as the piece the user typed; its gram conversion is not shown as a multiplier. */
class PieceQuantityLabelTest {
    private fun egg(amount: Double) = MealEntry(
        id = "e", foodId = "egg", foodName = "Egg",
        enteredAmount = amount, enteredUnit = MeasureUnit.PIECE,
        resolvedBaseAmount = 50.0 * amount, resolvedBaseUnit = MeasureUnit.GRAM,
        nutritionSnapshot = NutritionValues(71.5 * amount, 6.3 * amount, 0.35 * amount, 4.75 * amount),
        conversionProvenance = "large egg reference", approximateConversion = true,
        savedAtEpochMs = 0L, mealSlot = MealSlot.BREAKFAST,
    )

    @Test fun oneSavedEggReadsOnePieceWithNoApproximationOrMultiplier() {
        val display = quantityRateDisplay(egg(1.0), null)
        assertEquals("1 piece", display.line)
        assertNull(display.approximateLabel)
        assertEquals("1 piece", entryQuantityLine(egg(1.0)))
    }

    @Test fun twoEggsReadTwoPieces() {
        assertEquals("2 pieces", quantityRateDisplay(egg(2.0), null).line)
    }
}
