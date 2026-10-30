package com.iron.breakfast.entry

import com.iron.breakfast.domain.Measure
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.editableNumber
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryPreFillTest {
    private val third = MealEntry("e", "f", "Milk", 1.0 / 3, MeasureUnit.CUP, 80.0, NutritionValues(50.0, null, null, null), "1/3 cup", false, 0L)

    @Test fun aRoundedAmountLeftAsItWasIsNoChange() {
        assertFalse(entryDraftNutritionChanged(third, editableNumber(third.enteredAmount), Measure(MeasureUnit.CUP), "f"))
        assertTrue(entryDraftNutritionChanged(third, "0.5", Measure(MeasureUnit.CUP), "f"))
    }
}
