package com.iron.breakfast.recipes

import com.iron.breakfast.domain.FoodCatalog
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
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))
    private val egg = FoodCatalog.fixtures.first { it.id == "egg" }

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
        assertTrue(recipeCanSave(whole.copy(portions = "", totalWeight = "300"), true))
        assertFalse(recipeCanSave(whole, ingredientsComplete = false))
        assertFalse(recipeCanSave(whole.copy(name = " "), true))
        assertFalse(recipeCanSave(whole.copy(portions = ""), true))
        assertFalse("a weight typed wrong", recipeCanSave(whole.copy(totalWeight = "0"), true))
        assertTrue(ingredientsComplete(planner.plan("2 eggs, 40 g rolled oats")))
        assertFalse(ingredientsComplete(planner.plan("2 eggs, ayranxyz")))
        assertFalse(ingredientsComplete(LogPlan(emptyList(), emptyList())))
    }

    @Test fun theSavedRecipeTakesTheFieldsAndEachPartAsResolved() {
        val plan = planner.plan("2 eggs")
        val recipe = recipeOf(null, RecipeFields(name = " Bowl ", portions = "2", totalWeight = "", aliases = "b, бол, b", ingredients = "2 eggs"), plan)
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
        val edit = recipeEdit(recipe, FoodCatalog.fixtures, planner)
        assertEquals("2 piece ${egg.name}, 500 g Old mince", edit.fields.ingredients)
        assertEquals(setOf(egg.id, "custom-gone"), edit.pins.values.map { it.id }.toSet())
        val plan = LogPlanner(FoodMatcher(FoodCatalog.fixtures), pins = edit.pins).plan(edit.fields.ingredients)
        assertTrue(ingredientsComplete(plan))
        val again = ingredientsOf(plan)
        assertEquals(gone.nutritionSnapshot, again[1].nutritionSnapshot)
        assertEquals(gone.foodName, again[1].foodName)
        assertEquals(egg.id, again[0].foodId)
        assertEquals("1600", edit.fields.totalWeight)
        assertEquals("4", edit.fields.portions)
    }

    @Test fun aPartAddedComesAfterTheLastWithAComma() {
        assertEquals("250 g Kefir", planner.withPartAdded("", "250 g Kefir"))
        assertEquals("2 eggs, 250 g Kefir", planner.withPartAdded("2 eggs ", "250 g Kefir"))
        assertEquals("2 eggs, 250 g Kefir", planner.withPartAdded("2 eggs,", "250 g Kefir"))
        assertEquals("1 portion Chili", planner.partText(chili.asFood(), 1.0, MeasureUnit.PORTION))
        assertEquals("40.5 g Oats", planner.partText(egg.copy(name = "Oats"), 40.5, MeasureUnit.GRAM))
    }
}
