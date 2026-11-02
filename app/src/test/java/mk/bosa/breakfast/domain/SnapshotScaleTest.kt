package mk.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An entry whose food was deleted changes its amount by its own snapshot. */
class SnapshotScaleTest {
    private val jam = MealEntry(
        "e", "deleted-jam", "Jam", 20.0, MeasureUnit.GRAM, 20.0, NutritionValues(50.0, 0.1, 12.0, null), "20 g", false, 0L,
        mealSlot = MealSlot.BREAKFAST, dayKey = "2026-10-28", resolvedBaseUnit = MeasureUnit.GRAM,
    )

    @Test fun theAmountScalesKcalAndEveryMacro() {
        val scaled = snapshotScaled(jam, 30.0, MeasureUnit.GRAM)!!
        assertEquals(75.0, scaled.nutrition.calories, 1e-9)
        assertEquals(0.15, scaled.nutrition.protein!!, 1e-9)
        assertEquals(18.0, scaled.nutrition.carbohydrates!!, 1e-9)
        assertNull("an unknown macro stays unknown", scaled.nutrition.fat)
        assertEquals(30.0, scaled.resolvedBaseAmount, 1e-9)
        assertEquals("20 g", scaled.provenance)
        assertTrue(scaled.supported)
    }

    @Test fun anotherUnitOrABadAmountCannotScale() {
        assertNull(snapshotScaled(jam, 1.0, MeasureUnit.TABLESPOON))
        assertNull(snapshotScaled(jam, 0.0, MeasureUnit.GRAM))
        assertNull(snapshotScaled(jam, Double.NaN, MeasureUnit.GRAM))
        val zero = jam.copy(enteredAmount = 0.0)
        assertFalse(snapshotScalable(zero))
        assertNull(snapshotScaled(zero, 10.0, MeasureUnit.GRAM))
        assertTrue(snapshotScalable(jam))
    }
}
