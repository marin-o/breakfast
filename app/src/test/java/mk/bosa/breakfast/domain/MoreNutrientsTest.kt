package mk.bosa.breakfast.domain

import mk.bosa.breakfast.ui.screens.dailyMacroNote
import mk.bosa.breakfast.ui.screens.macroStats
import mk.bosa.breakfast.ui.theme.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saturated fat, sugars, fibre and salt go where the macros go, and stay unknown when nothing knows them. */
class MoreNutrientsTest {
    private val oats = NutritionValues(389.0, 16.9, 66.3, 6.9, saturatedFat = 1.2, sugars = 1.0, fibre = 10.6, salt = 0.0)
    private val plain = NutritionValues(100.0, 1.0, 2.0, 3.0)

    @Test fun scalingCarriesTheFour() {
        val half = oats * 0.5
        assertEquals(0.6, half.saturatedFat!!, 1e-9)
        assertEquals(0.5, half.sugars!!, 1e-9)
        assertEquals(5.3, half.fibre!!, 1e-9)
        assertEquals(0.0, half.salt!!, 0.0)
        val unknown = plain * 2.0
        assertNull(unknown.saturatedFat); assertNull(unknown.sugars); assertNull(unknown.fibre); assertNull(unknown.salt)
    }

    @Test fun aSumKeepsWhatIsKnownAndNeverMakesAnUnknownZero() {
        val sum = oats + plain
        assertEquals(10.6, sum.fibre!!, 1e-9)
        assertEquals(0.0, sum.salt!!, 0.0)
        val none = plain + plain
        assertNull(none.fibre); assertNull(none.salt); assertNull(none.sugars); assertNull(none.saturatedFat)
        assertNull(NutritionValues.ZERO.fibre)
    }

    @Test fun aRecipeTotalCarriesTheFour() {
        fun ingredient(values: NutritionValues) = RecipeIngredient("f", "Food", 100.0, MeasureUnit.GRAM, 100.0, MeasureUnit.GRAM, values)
        val recipe = Recipe("recipe-porridge", "Porridge", portions = 2.0, ingredients = listOf(ingredient(oats), ingredient(plain)))
        val total = recipe.total().values
        assertEquals(10.6, total.fibre!!, 1e-9)
        assertEquals(5.3, recipe.nutritionFor(1.0, MeasureUnit.PORTION)!!.values.fibre!!, 1e-9)
        assertEquals(5.3, recipe.asFood().let { NutritionCalculator().calculate(it, 1.0, MeasureUnit.PORTION) }.nutrition.fibre!!, 1e-9)
        val unknown = Recipe("recipe-tea", "Tea", portions = 1.0, ingredients = listOf(ingredient(plain)))
        assertNull(unknown.total().values.fibre)
    }

    @Test fun anEntryLoggedFromAFoodKeepsTheFour() {
        val food = FoodRecord("oats", "Oats", emptyList(), "test", FoodOrigin.CUSTOM, NutritionBasis(100.0, MeasureUnit.GRAM, oats))
        val calculation = NutritionCalculator().calculate(food, 40.0, MeasureUnit.GRAM)
        assertEquals(4.24, calculation.nutrition.fibre!!, 1e-9)
        assertEquals(0.4, calculation.nutrition.sugars!!, 1e-9)
    }

    private fun fibreOf(vararg values: Double?) = macroTotalOf(values.toList())

    @Test fun withoutTrackFibreMealsShowsTheThreeMacros() {
        val stats = macroStats(489.0, macroTotalsOf(listOf(oats, plain)), ProfileValues(), fibreOf(10.6, null))
        assertEquals(3, stats.size)
        assertEquals(3, macroStats(489.0, macroTotalsOf(listOf(oats)), ProfileValues(fibreTarget = 30.0), fibreOf(10.6)).size)
        assertEquals(3, macroStats(489.0, macroTotalsOf(listOf(oats)), ProfileValues(fibreTarget = 30.0, trackFibre = true)).size)
    }

    @Test fun withAFibreTargetFibreIsTheFourthColumnTowardItsTarget() {
        val stats = macroStats(489.0, macroTotalsOf(listOf(oats)), ProfileValues(fibreTarget = 30.0, trackFibre = true), fibreOf(10.6, 4.4))
        val fibre = stats.last()
        assertEquals(Mark.Fibre, fibre.kind)
        assertEquals("Fibre", fibre.label)
        assertEquals("15" to "g", fibre.value to fibre.unit)
        assertEquals("/ 30 g", fibre.target)
        assertEquals(0.5f, fibre.share, 1e-6f)
        // Over the target the bar is full, and nothing else changes.
        assertEquals(1f, macroStats(489.0, macroTotalsOf(listOf(oats)), ProfileValues(fibreTarget = 5.0, trackFibre = true), fibreOf(10.6)).last().share, 0f)
    }

    @Test fun fibreSumsAsAMacroDoes() {
        val target = ProfileValues(fibreTarget = 30.0, trackFibre = true)
        val partly = macroStats(0.0, macroTotalsOf(emptyList()), target, fibreOf(10.6, null)).last()
        assertEquals("10.6" to "g ?", partly.value to partly.unit)
        val none = macroStats(0.0, macroTotalsOf(emptyList()), target, fibreOf(null)).last()
        assertEquals("—", none.value)
        // An empty day is zero, not unknown.
        val empty = macroStats(0.0, macroTotalsOf(emptyList()), target, fibreOf()).last()
        assertEquals("0" to "g", empty.value to empty.unit)
    }

    @Test fun aPartlyKnownFibreBringsTheNote() {
        val known = macroTotalsOf(listOf(plain))
        assertNull(dailyMacroNote(known))
        assertEquals("? some foods have no value", dailyMacroNote(known, fibre = fibreOf(1.0, null)))
        assertNull(dailyMacroNote(known, fibre = fibreOf(1.0, 2.0)))
        assertNull(dailyMacroNote(known, showNote = false, fibre = fibreOf(1.0, null)))
    }

    @Test fun theFibreTargetIsAnOptionalProfileField() {
        val saved = ProfileValues(kcalTarget = 2000.0, trackFibre = true)
        assertEquals(30.0, profileOf(ProfileDraft.of(saved).copy(fibre = "30"), saved)!!.fibreTarget)
        assertNull(profileOf(ProfileDraft.of(saved), saved)!!.fibreTarget)
        assertEquals("30", ProfileDraft.of(saved.copy(fibreTarget = 30.0)).fibre)
        // Out of range stops the save, as a macro target does.
        assertNull(profileOf(ProfileDraft.of(saved).copy(fibre = "0"), saved))
        assertNull(profileOf(ProfileDraft.of(saved).copy(fibre = "6000"), saved))
    }

    @Test fun trackFibreIsOffByDefaultAndShowsTheTargetField() {
        val saved = ProfileValues(kcalTarget = 2000.0)
        assertFalse(ProfileDraft.of(saved).trackFibre)
        assertFalse(ProfileDraft.of(saved).fibreTargetShown)
        assertTrue(ProfileDraft.of(saved).copy(trackFibre = true).fibreTargetShown)
        assertFalse(ProfileDraft.of(saved).copy(trackFibre = true, trackingOnly = true).fibreTargetShown)
        assertTrue(profileOf(ProfileDraft.of(saved).copy(trackFibre = true), saved)!!.trackFibre)
    }

    @Test fun untickingTrackFibreKeepsTheTarget() {
        val saved = ProfileValues(kcalTarget = 2000.0, fibreTarget = 30.0, trackFibre = true)
        val off = profileOf(ProfileDraft.of(saved).copy(trackFibre = false), saved)!!
        assertFalse(off.trackFibre)
        assertEquals(30.0, off.fibreTarget)
        assertEquals("30", ProfileDraft.of(off).copy(trackFibre = true).fibre)
        // A hidden fibre field that is not valid keeps the saved target and stops nothing.
        assertEquals(30.0, profileOf(ProfileDraft.of(saved).copy(trackFibre = false, fibre = "0"), saved)!!.fibreTarget)
    }

    @Test fun fibreIsNoPartOfTheKcalSplit() {
        val saved = ProfileValues(kcalTarget = 2000.0, fibreTarget = 30.0)
        val draft = ProfileDraft.of(saved).withDailyKcal("2400")
        assertEquals("30", draft.fibre)
        assertEquals(30.0, profileOf(draft, saved)!!.fibreTarget)
    }

    @Test fun trackingOnlyHidesTheFibreTargetAndKeepsIt() {
        val values = ProfileValues(fibreTarget = 30.0, trackingOnly = true)
        assertNull(values.targetsShown().fibreTarget)
        assertEquals(30.0, ProfileValues(fibreTarget = 30.0).targetsShown().fibreTarget)
        val saved = ProfileValues(fibreTarget = 30.0)
        val kept = profileOf(ProfileDraft.of(saved).copy(trackingOnly = true, fibre = "nonsense"), saved)!!
        assertEquals(30.0, kept.fibreTarget)
    }
}
