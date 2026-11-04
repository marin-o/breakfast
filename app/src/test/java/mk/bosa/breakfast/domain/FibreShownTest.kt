package mk.bosa.breakfast.domain

import mk.bosa.breakfast.ui.screens.dailyMacroNote
import mk.bosa.breakfast.ui.screens.macroStats
import mk.bosa.breakfast.ui.theme.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fibre is shown with "Track fibre" on; then it stands beside the macros, never in the kcal split. */
class FibreShownTest {
    private val oats = NutritionValues(389.0, 16.9, 66.3, 6.9, fibre = 10.6)
    private val plain = NutritionValues(100.0, 1.0, 2.0, 3.0)

    @Test fun onlyTrackFibreShowsFibre() {
        assertFalse(fibreShown(ProfileValues()))
        assertFalse("a stored target alone shows nothing", fibreShown(ProfileValues(fibreTarget = 30.0)))
        assertTrue(fibreShown(ProfileValues(trackFibre = true)))
        assertTrue(fibreShown(ProfileValues(fibreTarget = 30.0, trackFibre = true)))
    }

    @Test fun underTrackingOnlyTrackFibreStillDecides() {
        assertFalse(fibreShown(ProfileValues(fibreTarget = 30.0, trackingOnly = true)))
        val tracking = ProfileValues(fibreTarget = 30.0, trackingOnly = true, trackFibre = true)
        assertTrue(fibreShown(tracking))
        // The target is hidden, so the column is the total only.
        val fibre = macroStats(489.0, macroTotalsOf(listOf(oats)), tracking.targetsShown(), macroTotalOf(listOf(10.6))).last()
        assertEquals(Mark.Fibre, fibre.kind)
        assertNull(fibre.target)
    }

    @Test fun shownWithNoTargetFibreIsItsTotalOnly() {
        val stats = macroStats(489.0, macroTotalsOf(listOf(oats, plain)), ProfileValues(), macroTotalOf(listOf(10.6, null)), showFibre = true)
        assertEquals(4, stats.size)
        val fibre = stats.last()
        assertEquals(Mark.Fibre, fibre.kind)
        assertEquals("10.6" to "g ?", fibre.value to fibre.unit)
        assertNull("no target line", fibre.target)
        assertEquals("no share of the kcal", 0f, fibre.share, 0f)
        // The macros' kcal shares are as they were without fibre.
        assertEquals(macroStats(489.0, macroTotalsOf(listOf(oats, plain))), stats.dropLast(1))
    }

    @Test fun notShownFibreIsNoColumnEvenWithItsTotal() {
        assertEquals(3, macroStats(489.0, macroTotalsOf(listOf(oats)), ProfileValues(), macroTotalOf(listOf(10.6)), showFibre = false).size)
    }

    @Test fun shownWithATargetFibreFillsTowardIt() {
        val fibre = macroStats(489.0, macroTotalsOf(listOf(oats)), ProfileValues(fibreTarget = 21.2), macroTotalOf(listOf(10.6)), showFibre = true).last()
        assertEquals("/ 21.2 g", fibre.target)
        assertEquals(0.5f, fibre.share, 1e-6f)
    }

    @Test fun theNoteCountsFibreOnlyWhenShown() {
        val known = macroTotalsOf(listOf(plain))
        // Daily passes fibre's total only while fibre is shown.
        assertEquals("? some foods have no value", dailyMacroNote(known, fibre = macroTotalOf(listOf(1.0, null))))
        assertNull(dailyMacroNote(known, fibre = null))
    }

    @Test fun aRecipeKeepsFibresKnownAndUnknownParts() {
        fun ingredient(values: NutritionValues) = RecipeIngredient("f", "Food", 100.0, MeasureUnit.GRAM, 100.0, MeasureUnit.GRAM, values)
        val recipe = Recipe("recipe-porridge", "Porridge", portions = 2.0, ingredients = listOf(ingredient(oats), ingredient(plain)))
        val portion = recipe.nutritionFor(1.0, MeasureUnit.PORTION)!!
        assertEquals(5.3, portion.fibre.sum, 1e-9)
        assertTrue(portion.fibre.partial)
    }

    @Test fun theWeekAveragesFibre() {
        val day = "2026-11-02"
        val entry = MealEntry(
            id = "e", foodId = "oats", foodName = "Oats", enteredAmount = 100.0, enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 100.0, nutritionSnapshot = oats, conversionProvenance = "test", approximateConversion = false,
            savedAtEpochMs = 0L, dayKey = day,
        )
        val week = nutritionWeek(java.time.LocalDate.parse(day), listOf(entry), emptyList())
        assertEquals(10.6, week.averageFibre!!, 1e-9)
    }
}
