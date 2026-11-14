package mk.bosa.breakfast.recipes

import mk.bosa.breakfast.domain.DraftPins
import mk.bosa.breakfast.domain.FoodMatcher
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.Recipe
import mk.bosa.breakfast.domain.RecipeIngredient
import mk.bosa.breakfast.domain.ShorthandParser
import mk.bosa.breakfast.testFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ingredients box takes a part after a comma or on a line of its own, and an opened recipe types back one per line. */
class RecipeIngredientLinesTest {
    private val cheese = testFood("cheese", "Cheese")
    private val peppers = testFood("peppers", "Peppers, raw, NFS")
    private val oil = testFood("oil", "Olive oil")
    private val foods = listOf(cheese, peppers, oil)
    private val parser = ShorthandParser()
    private fun planner(pins: Map<String, FoodRecord> = emptyMap()) = LogPlanner(FoodMatcher(foods), pins = pins)
    private val three = listOf("75 g cheese", "50 g peppers", "40 g olive oil")

    @Test fun aLineBreakSplitsPartsLikeAComma() {
        assertEquals(three, parser.split("75 g cheese\n50 g peppers\n40 g olive oil"))
        assertEquals(parser.split("75 g cheese, 50 g peppers, 40 g olive oil"), parser.split("75 g cheese\n50 g peppers\n40 g olive oil"))
    }

    @Test fun commasAndLinesMix() {
        assertEquals(three, parser.split("75 g cheese, 50 g peppers\n40 g olive oil"))
        assertEquals(three, parser.split("75 g cheese\n50 g peppers, 40 g olive oil"))
    }

    @Test fun blankLinesAndSpacesAreIgnored() {
        assertEquals(three, parser.split("\n\n  75 g cheese  \n\n\n 50 g peppers\t\n   \n40 g olive oil \n\n"))
    }

    @Test fun windowsLineEndingsSplitToo() {
        assertEquals(three, parser.split("75 g cheese\r\n50 g peppers\r\n40 g olive oil\r\n"))
    }

    @Test fun theBoxResolvesEachLineAsAnIngredient() {
        val plan = planner().plan("75 g cheese\n50 g olive oil\n")
        assertTrue(ingredientsComplete(plan))
        assertEquals(listOf("cheese" to 75.0, "oil" to 50.0), plan.entries.map { it.food.id to it.amount })
    }

    @Test fun aTypedLineEndsItsPartSoTheNextIsNew() {
        assertTrue(parser.endsOpen("75 g cheese\n"))
        assertFalse(parser.endsOpen("75 g cheese"))
    }

    @Test fun theLogBarReadsTheSameWithoutLineBreaks() {
        assertEquals(three, parser.split("75 g cheese, 50 g peppers, 40 g olive oil"))
        assertEquals(listOf("75 g cheese"), parser.split(" 75 g cheese "))
    }

    private fun ingredient(food: FoodRecord, amount: Double) =
        RecipeIngredient(food.id, food.name, amount, MeasureUnit.GRAM, amount, MeasureUnit.GRAM, NutritionValues(amount, 1.0, 1.0, 1.0))

    @Test fun anOpenedRecipeIsOneIngredientPerLineAndReadsBackAsItself() {
        val recipe = Recipe("r", "Pizza", portions = 2.0, ingredients = listOf(ingredient(cheese, 75.0), ingredient(peppers, 50.0), ingredient(oil, 40.0)))
        val edit = recipeEdit(recipe, foods, planner())
        assertEquals("75 g Cheese\n50 g Peppers, raw, NFS\n40 g Olive oil", edit.fields.ingredients)
        // The edit's pins are put back on the text as the form does, and the planner reads that.
        val pins = DraftPins().apply { restore(edit.fields.ingredients, edit.pins) }
        val plan = planner(pins.keys(edit.fields.ingredients, ::planner)).plan(edit.fields.ingredients)
        assertTrue(ingredientsComplete(plan))
        assertEquals(
            recipe.ingredients.map { Triple(it.foodId, it.enteredAmount, it.enteredUnit) },
            ingredientsOf(plan).map { Triple(it.foodId, it.enteredAmount, it.enteredUnit) },
        )
    }

    @Test fun aCommaNameKeepsItsCommasOnItsLine() {
        val text = "50 g Peppers, raw, NFS\n75 g Cheese"
        val pins = DraftPins().apply { restore(text, mapOf("50 g Peppers, raw, NFS" to peppers, "75 g Cheese" to cheese)) }
        val plan = planner(pins.keys(text, ::planner)).plan(text)
        assertEquals(listOf("peppers", "cheese"), plan.entries.map { it.food.id })
        assertTrue(plan.unresolved.isEmpty())
    }
}
