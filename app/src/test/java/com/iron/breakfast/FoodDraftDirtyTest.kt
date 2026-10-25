package com.iron.breakfast

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ServingConversion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Back asks before discarding a food form only when something typed differs from the form as it opened. */
class FoodDraftDirtyTest {
    private val ajvar = FoodRecord(
        id = "ajvar", name = "Ajvar", aliases = listOf("ајвар"), category = "Spread", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(120.0, 2.0, 8.0, 9.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
    )

    private fun opened(food: FoodRecord? = null): Pair<FoodDraft, FoodDraftFields> {
        val draft = FoodDraft().apply { load(food) }
        return draft to draft.fields()
    }

    @Test fun aFormAsItOpenedIsClean() {
        val (blank, blankBaseline) = opened()
        assertFalse(blank.isDirty(blankBaseline))
        val (edit, editBaseline) = opened(ajvar)
        assertFalse(edit.isDirty(editBaseline))
    }

    @Test fun aPrefilledNewFoodIsCleanUntilTypedInto() {
        // The no-match screen and a scan open New food with a name or barcode already in it.
        val draft = FoodDraft().apply { reset(); name = "Ajvar"; barcode = "5310000000000"; barcodeFromScan = true }
        val baseline = draft.fields()
        assertFalse(draft.isDirty(baseline))
        draft.brand = "Podravka"
        assertTrue(draft.isDirty(baseline))
    }

    @Test fun everyTypedFieldMakesTheFormDirty() {
        val edits: List<(FoodDraft) -> Unit> = listOf(
            { it.name = "Ajvar hot" },
            { it.brand = "Podravka" },
            { it.barcode = "5310000000000" },
            { it.shorthand = "per 100g: 120 kcal" },
            { it.calories = "130" },
            { it.protein = "2,5" },
            { it.carbs = "9" },
            { it.fat = "10" },
            { it.basisAmount = "50" },
            { it.basisUnit = "ml" },
            { it.aliases = it.aliases + "pindjur" },
            { it.aliasInput = "pindjur" },
            { it.servings = it.servings + ServingConversion(MeasureUnit.TABLESPOON, 15.0, "personal serving") },
            { it.servingUnit = MeasureUnit.TABLESPOON },
            { it.servingAmount = "15" },
            { it.servingLabel = "jar" },
        )
        edits.forEachIndexed { index, edit ->
            val (draft, baseline) = opened(ajvar)
            edit(draft)
            assertTrue("edit $index", draft.isDirty(baseline))
        }
    }

    @Test fun openingAFieldWithoutTypingIsNotAChange() {
        val (draft, baseline) = opened(ajvar)
        draft.aliasFieldOpen = true
        draft.servingFormOpen = true
        assertFalse(draft.isDirty(baseline))
    }

    @Test fun typingBackTheOriginalValueIsClean() {
        val (draft, baseline) = opened(ajvar)
        draft.calories = "999"
        draft.calories = baseline.calories
        assertFalse(draft.isDirty(baseline))
    }
}
