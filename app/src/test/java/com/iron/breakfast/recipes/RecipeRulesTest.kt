package com.iron.breakfast.recipes

import com.iron.breakfast.domain.TestFoods
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.LogPlan
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.asFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recipe form's pure rules: its yields, when it may save, and a recipe typed back into it. */
class RecipeRulesTest {
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures))
    private val egg = TestFoods.fixtures.first { it.id == "egg" }

    @Test fun aYieldIsAPositiveNumberOrNothing() {
        assertEquals(4.0, yieldOf("4")!!, 0.0)
        assertEquals(1.5, yieldOf("1,5")!!, 0.0)
        assertNull(yieldOf(""))
        assertNull(yieldOf("0"))
        assertNull(yieldOf("x"))
        assertFalse(yieldError(""))
        assertTrue(yieldError("0"))
        assertTrue(yieldError("-"))
        assertFalse(yieldError("1200"))
    }

    @Test fun saveNeedsANameAYieldAndIngredientsThatAllResolve() {
        val whole = RecipeFields(name = "Bowl", portions = "2", ingredients = "2 eggs")
        assertTrue(recipeCanSave(whole, ingredientsComplete = true))
        assertTrue(recipeCanSave(whole.copy(portions = "", total = "300"), true))
        assertTrue("a volume alone", recipeCanSave(whole.copy(portions = "", total = "1500", totalUnit = MeasureUnit.MILLILITER), true))
        assertTrue("any weight unit typed", recipeCanSave(whole.copy(portions = "", total = "1.6 kg"), true))
        assertFalse("a total typed wrong", recipeCanSave(whole.copy(total = "0"), true))
        assertFalse(recipeCanSave(whole.copy(portions = "", total = "x"), true))
        assertFalse("no weight or volume", recipeCanSave(whole.copy(portions = "", total = "2 pieces"), true))
        assertFalse(recipeCanSave(whole, ingredientsComplete = false))
        assertFalse(recipeCanSave(whole.copy(name = " "), true))
        assertFalse(recipeCanSave(whole.copy(portions = ""), true))
        assertFalse("a weight typed wrong", recipeCanSave(whole.copy(total = "0 g"), true))
        assertTrue(ingredientsComplete(planner.plan("2 eggs, 40 g rolled oats")))
        assertFalse(ingredientsComplete(planner.plan("2 eggs, ayranxyz")))
        assertFalse(ingredientsComplete(LogPlan(emptyList(), emptyList())))
    }

    @Test fun theSavedRecipeTakesTheFieldsAndEachPartAsResolved() {
        val plan = planner.plan("2 eggs")
        val recipe = recipeOf(null, RecipeFields(name = " Bowl ", portions = "2", total = "", aliases = "b, бол, b", ingredients = "2 eggs"), plan)
        assertEquals("", recipe.id)
        assertEquals("Bowl", recipe.name)
        assertEquals(listOf("b", "бол"), recipe.aliases)
        assertEquals(2.0, recipe.portions!!, 0.0)
        assertNull(recipe.totalWeight)
        val ingredient = recipe.ingredients.single()
        val calculation = NutritionCalculator().calculate(egg, 2.0, MeasureUnit.PIECE)
        assertEquals(egg.id, ingredient.foodId)
        assertEquals(2.0 to MeasureUnit.PIECE, ingredient.enteredAmount to ingredient.enteredUnit)
        assertEquals(calculation.resolvedBaseAmount, ingredient.resolvedBaseAmount, 0.0)
        assertEquals(egg.nutrition.unit, ingredient.resolvedBaseUnit)
        assertEquals(calculation.nutrition, ingredient.nutritionSnapshot)
        assertEquals("recipe-1", recipeOf("recipe-1", RecipeFields(name = "Bowl", portions = "2"), plan).id)
    }

    /** Typed back, a recipe resolves to exactly its ingredients again, an ingredient whose food is gone included. */
    @Test fun aRecipeTypedBackIntoTheFormReadsAsItsIngredients() {
        val gone = mince.copy(foodId = "custom-gone", foodName = "Old mince")
        val recipe = chili.copy(ingredients = listOf(mince.copy(foodId = egg.id, foodName = egg.name, enteredAmount = 2.0, enteredUnit = MeasureUnit.PIECE), gone))
        val edit = recipeEdit(recipe, TestFoods.fixtures, planner)
        assertEquals("2 piece ${egg.name}, 500 g Old mince", edit.fields.ingredients)
        assertEquals(setOf(egg.id, "custom-gone"), edit.pins.values.map { it.id }.toSet())
        val plan = LogPlanner(FoodMatcher(TestFoods.fixtures), pins = edit.pins).plan(edit.fields.ingredients)
        assertTrue(ingredientsComplete(plan))
        val again = ingredientsOf(plan)
        assertEquals(gone.nutritionSnapshot, again[1].nutritionSnapshot)
        assertEquals(gone.foodName, again[1].foodName)
        assertEquals(egg.id, again[0].foodId)
        assertEquals("1600" to MeasureUnit.GRAM, edit.fields.total to edit.fields.totalUnit)
        // One total: the weight, else the volume.
        assertEquals("1600", recipeEdit(recipe.copy(totalVolume = 1500.0), TestFoods.fixtures, planner).fields.total)
        assertEquals("1500" to MeasureUnit.MILLILITER, recipeEdit(recipe.copy(totalWeight = null, totalVolume = 1500.0), TestFoods.fixtures, planner).fields.let { it.total to it.totalUnit })
        assertEquals("4", edit.fields.portions)
        val soup = recipeOf(null, RecipeFields(name = "Soup", total = "750", totalUnit = MeasureUnit.MILLILITER, ingredients = "2 eggs"), planner.plan("2 eggs"))
        assertEquals(750.0, soup.totalVolume!!, 0.0)
        assertNull(soup.totalWeight)
    }

    @Test fun theTotalTakesAnyWeightOrVolumeUnit() {
        assertEquals(MeasureUnit.GRAM to 1600.0, totalOf("1600", MeasureUnit.GRAM))
        assertEquals(MeasureUnit.MILLILITER to 1600.0, totalOf("1600", MeasureUnit.MILLILITER))
        assertEquals(MeasureUnit.GRAM to 1600.0, totalOf("1.6 kg", MeasureUnit.MILLILITER))
        assertEquals(MeasureUnit.MILLILITER to 1500.0, totalOf("1.5 l", MeasureUnit.GRAM))
        assertEquals(MeasureUnit.MILLILITER to 480.0, totalOf("2 cups", MeasureUnit.GRAM))
        assertEquals(MeasureUnit.GRAM to 453.6, totalOf("1 lb", MeasureUnit.GRAM))
        assertNull(totalOf("2 pieces", MeasureUnit.GRAM))
        assertNull(totalOf("", MeasureUnit.GRAM))
    }

    @Test fun aPartAddedComesAfterTheLastWithAComma() {
        assertEquals("250 g Kefir", planner.withPartAdded("", "250 g Kefir"))
        assertEquals("2 eggs, 250 g Kefir", planner.withPartAdded("2 eggs ", "250 g Kefir"))
        assertEquals("2 eggs, 250 g Kefir", planner.withPartAdded("2 eggs,", "250 g Kefir"))
        assertEquals("1 portion Chili", planner.partText(chili.asFood(), 1.0, MeasureUnit.PORTION))
        assertEquals("40.5 g Oats", planner.partText(egg.copy(name = "Oats"), 40.5, MeasureUnit.GRAM))
    }
}
