package com.iron.breakfast.domain

import com.iron.breakfast.ui.screens.countedQuantity
import com.iron.breakfast.ui.screens.quantityRateDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Log bar resolves a recipe the way it does a personal food: by name or alias, with the script rule, in the units
 * the recipe has ("portion" only with portions, "g" only with a total weight), over the matcher the engine builds from
 * the foods and the recipes ([RecipeIndex.matchable]).
 */
class RecipeLogBarTest {
    private val ingredient = RecipeIngredient("mince", "Beef mince", 500.0, MeasureUnit.GRAM, 500.0, MeasureUnit.GRAM, NutritionValues(2000.0, 160.0, 0.0, 144.0))
    private val chili = Recipe("recipe-chili", "Chili", aliases = listOf("чили"), portions = 4.0, totalWeight = 1600.0, ingredients = listOf(ingredient))
    private val stew = Recipe("recipe-stew", "Манџа", portions = 5.0, ingredients = listOf(ingredient))
    private val soup = Recipe("recipe-soup", "Lentil soup", totalWeight = 1000.0, ingredients = listOf(ingredient))

    private fun planner(recipes: List<Recipe> = listOf(chili, stew, soup), foods: List<FoodRecord> = FoodCatalog.fixtures) =
        LogPlanner(FoodMatcher(RecipeIndex().matchable(foods, recipes)))

    private fun resolved(text: String, planner: LogPlanner = planner()): SegmentResolution =
        planner.plan(text).entries.single()

    @Test fun aRecipeLogsByPortionByDefault() {
        val one = resolved("1 chili")
        assertEquals("recipe-chili", one.food.id)
        assertEquals(1.0 to MeasureUnit.PORTION, one.amount to one.unit)
        assertEquals(500.0, one.calculation.nutrition.calories, 1e-9)
        assertEquals(750.0, resolved("1.5 chili").calculation.nutrition.calories, 1e-9)
        assertEquals(500.0, resolved("chili").calculation.nutrition.calories, 1e-9)
        val two = resolved("2 portions chili")
        assertEquals(2.0 to MeasureUnit.PORTION, two.amount to two.unit)
        assertEquals(1000.0, two.calculation.nutrition.calories, 1e-9)
        assertEquals(MeasureUnit.PORTION, resolved("1 portion chili").unit)
    }

    @Test fun aRecipeWithATotalWeightLogsByTheGram() {
        val weighed = resolved("300 g chili")
        assertEquals(300.0 to MeasureUnit.GRAM, weighed.amount to weighed.unit)
        assertEquals(375.0, weighed.calculation.nutrition.calories, 1e-9)
        assertEquals(300.0, weighed.calculation.resolvedBaseAmount, 1e-9)
        // Only portions: g is not its unit, so the part is not counted (it shows as not in your foods).
        assertTrue(planner().plan("300 g манџа").entries.isEmpty())
        assertEquals(400.0, resolved("1 манџа").calculation.nutrition.calories, 1e-9)
        // Only a weight: grams, and no portion; a bare number is grams, as for a personal food on a gram basis.
        assertEquals(600.0, resolved("300 g lentil soup").calculation.nutrition.calories, 1e-9)
        assertTrue(planner().plan("2 portions lentil soup").entries.isEmpty())
        assertEquals(100.0 to MeasureUnit.GRAM, resolved("lentil soup").let { it.amount to it.unit })
    }

    @Test fun aRecipeIsFoundByItsAliasAndOnlyInItsOwnScript() {
        // The Cyrillic alias finds it; a Latin name never matches Cyrillic text and the reverse.
        assertEquals("recipe-chili", resolved("1 чили").food.id)
        assertEquals("recipe-stew", resolved("манџа").food.id)
        assertTrue(planner().plan("mandza").entries.isEmpty())
        // A word mixing scripts (a Cyrillic и in Latin "chili") matches nothing.
        assertTrue(planner().plan("1 chиli").entries.isEmpty())
        assertTrue(planner().draft("chи").suggestions.isEmpty())
        assertEquals(listOf("recipe-stew"), planner().draft("ман").suggestions.map { it.food.id })
    }

    @Test fun aRecipeRanksAsAPersonalFoodDoes() {
        // The same name as a bundled food: the recipe wins, as a personal food would, by the same score.
        val bundled = FoodCatalog.fixtures.first { it.id == "banana" }
        val bananaBread = Recipe("recipe-banana", bundled.name, portions = 1.0, ingredients = listOf(ingredient))
        val withRecipe = FoodMatcher(RecipeIndex().matchable(FoodCatalog.fixtures, listOf(bananaBread))).suggestions(bundled.name)
        val withPersonal = FoodMatcher(FoodCatalog.fixtures + bananaBread.asFood().copy(id = "custom-banana")).suggestions(bundled.name)
        assertEquals("recipe-banana", withRecipe.first().food.id)
        assertEquals(withPersonal.map { it.score }, withRecipe.map { it.score })
    }

    @Test fun aChoiceOfARecipeReadsBackAsThatRecipe() {
        val planner = planner()
        val chosen = planner.choose("2 eggs, chi", chili.asFood())
        assertEquals("2 eggs, 1 portion Chili", chosen.input)
        assertEquals("1 portion Chili", chosen.part)
        assertEquals(listOf("egg", "recipe-chili"), planner.plan(chosen.input).entries.map { it.food.id })
    }

    @Test fun portionsReadAsTheyAreCounted() {
        assertEquals("1 portion", countedQuantity(1.0, MeasureUnit.PORTION))
        assertEquals("1.5 portions", countedQuantity(1.5, MeasureUnit.PORTION))
        assertEquals("2 pieces", countedQuantity(2.0, MeasureUnit.PIECE))
        assertNull(countedQuantity(300.0, MeasureUnit.GRAM))
        val entry = MealEntry("e", chili.id, chili.name, 2.0, MeasureUnit.PORTION, 800.0, NutritionValues(1000.0, 80.0, 0.0, 72.0), "", false, 0L, resolvedBaseUnit = MeasureUnit.GRAM)
        assertEquals("2 portions", quantityRateDisplay(entry, null).line)
        assertFalse(quantityRateDisplay(entry.copy(enteredAmount = 300.0, enteredUnit = MeasureUnit.GRAM), null).line.contains("portion"))
    }
}
