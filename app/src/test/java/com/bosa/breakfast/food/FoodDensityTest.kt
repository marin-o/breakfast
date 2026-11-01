package com.bosa.breakfast.food

import com.bosa.breakfast.domain.FoodOrigin
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionBasis
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.editedCustomFoodRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The food form's "100 ml =" … "g": the density it reads, keeps and saves, and a serving typed in the other family. */
class FoodDensityTest {
    private val iceCream = FoodRecord(
        "custom-ice", "Ice cream", emptyList(), "Personal food", FoodOrigin.CUSTOM,
        NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(200.0, 3.0, 24.0, 11.0)), density = 55.0, personalOverride = true,
    )

    @Test fun theFormLoadsAndChecksTheDensity() {
        val draft = FoodDraft().apply { load(iceCream) }
        assertEquals("55", draft.density)
        assertEquals(55.0, draft.densityValue()!!, 0.0)
        assertFalse(draft.densityError())
        draft.density = ""
        assertNull(draft.densityValue())
        assertFalse("blank is no density, not an error", draft.densityError())
        draft.density = "0"
        assertTrue(draft.densityError())
        // It is part of what the form holds, so a change to it asks before Back discards it.
        val baseline = FoodDraft().apply { load(iceCream) }.fields()
        assertTrue(draft.isDirty(baseline))
        val restored = FoodDraft().apply { restore(draft.state()) }
        assertEquals("0", restored.density)
    }

    @Test fun aServingInTheOtherFamilyIsKeptInTheBasisThroughTheDensity() {
        assertEquals(66.0, servingInBasis(120.0, MeasureUnit.MILLILITER, MeasureUnit.GRAM, 55.0), 1e-9)
        assertEquals(200.0, servingInBasis(110.0, MeasureUnit.GRAM, MeasureUnit.MILLILITER, 55.0), 1e-9)
        assertEquals(30.0, servingInBasis(30.0, MeasureUnit.GRAM, MeasureUnit.GRAM, 55.0), 0.0)
        assertEquals("no density: as typed", 120.0, servingInBasis(120.0, MeasureUnit.MILLILITER, MeasureUnit.GRAM, null), 0.0)
        val draft = FoodDraft().apply { load(iceCream) }
        draft.openServing(MeasureUnit.MILLILITER)
        draft.servingLabel = "scoop"
        draft.servingAmount = "120"
        assertTrue(draft.commitServing())
        assertEquals(66.0, draft.servings.single { it.label == "scoop" }.baseUnitsPerServing, 1e-9)
    }

    @Test fun anEditKeepsOrChangesTheDensity() {
        fun edited(density: Double?) = editedCustomFoodRecord(
            iceCream, "Ice cream", "", "", "", iceCream.nutrition.values, 100.0, MeasureUnit.GRAM, density = density,
        )
        assertEquals(60.0, edited(60.0).density!!, 0.0)
        assertNull(edited(null).density)
        assertEquals(55.0, editedCustomFoodRecord(iceCream, "Ice cream", "", "", "", iceCream.nutrition.values, 100.0, MeasureUnit.GRAM).density!!, 0.0)
    }
}
