package mk.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A recipe's nutrition: the sum of its ingredients, per portion and per gram, and the recipe as a food the Log logs. */
class RecipeTest {
    private fun ingredient(foodId: String, kcal: Double, protein: Double?, carbs: Double?, fat: Double?, grams: Double = 100.0) =
        RecipeIngredient(foodId, foodId, grams, MeasureUnit.GRAM, grams, MeasureUnit.GRAM, NutritionValues(kcal, protein, carbs, fat))

    private val chili = Recipe(
        id = "recipe-chili", name = "Chili", aliases = listOf("чили"), portions = 4.0, totalWeight = 1600.0,
        ingredients = listOf(
            ingredient("mince", 1250.0, 100.0, 0.0, 90.0, grams = 500.0),
            ingredient("beans", 300.0, 20.0, 50.0, null, grams = 240.0),
            ingredient("rice", 730.0, 14.0, 160.0, 1.0, grams = 200.0),
        ),
    )

    @Test fun theTotalIsTheSumAndAMissingMacroIsKeptAsTheDayTotalKeepsIt() {
        val total = chili.total()
        assertEquals(2280.0, total.values.calories, 1e-9)
        assertEquals(134.0, total.values.protein!!, 1e-9)
        // Fat: one ingredient has no value. The sum is of the known ones, and it says it is partial ("91 g ?").
        assertEquals(91.0, total.values.fat!!, 1e-9)
        assertTrue(total.macros.fat.partial)
        assertFalse(total.macros.protein.partial)
        assertEquals(total.macros, macroTotalsOf(chili.ingredients.map { it.nutritionSnapshot }))
        // No ingredient knows it: unknown, never zero.
        val noCarbs = chili.copy(ingredients = chili.ingredients.map { it.copy(nutritionSnapshot = it.nutritionSnapshot.copy(carbohydrates = null)) })
        assertTrue(noCarbs.total().macros.carbohydrates.allUnknown)
        assertNull(noCarbs.total().values.carbohydrates)
    }

    @Test fun perPortionIsTheTotalOverThePortionsAndPerGramOverTheTotalWeight() {
        val portion = chili.nutritionFor(1.0, MeasureUnit.PORTION)!!
        assertEquals(570.0, portion.values.calories, 1e-9)
        assertEquals(33.5, portion.values.protein!!, 1e-9)
        assertTrue("still partial", portion.macros.fat.partial)
        assertEquals(855.0, chili.nutritionFor(1.5, MeasureUnit.PORTION)!!.values.calories, 1e-9)
        assertEquals(427.5, chili.nutritionFor(300.0, MeasureUnit.GRAM)!!.values.calories, 1e-9)
        assertNull(chili.copy(totalWeight = null).nutritionFor(300.0, MeasureUnit.GRAM))
        assertNull(chili.copy(portions = null).nutritionFor(1.0, MeasureUnit.PORTION))
        assertNull(chili.nutritionFor(1.0, MeasureUnit.TABLESPOON))
    }

    @Test fun theListShowsAPortionOrElseOneHundredGrams() {
        assertEquals(1.0 to MeasureUnit.PORTION, chili.shownServing())
        assertEquals(100.0 to MeasureUnit.GRAM, chili.copy(portions = null).shownServing())
        assertEquals(listOf(MeasureUnit.PORTION, MeasureUnit.GRAM), chili.yields().map { it.first })
        assertEquals(listOf(MeasureUnit.PORTION), chili.copy(totalWeight = null).yields().map { it.first })
        assertEquals(listOf(MeasureUnit.GRAM), chili.copy(portions = null).yields().map { it.first })
    }

    /** As a food, the calculator gives exactly what [nutritionFor] gives, in every unit the recipe has and no other. */
    @Test fun asAFoodItLogsByPortionByWeightOrBoth() {
        val calculator = NutritionCalculator()
        for (recipe in listOf(chili, chili.copy(totalWeight = null), chili.copy(portions = null))) {
            val food = recipe.asFood()
            assertEquals(FoodOrigin.CUSTOM, food.origin)
            assertEquals(recipe.id, food.id)
            assertEquals(recipe.aliases, food.aliases)
            assertEquals(recipe.shownServing(), food.defaultAmount to food.defaultUnit)
            for (unit in listOf(MeasureUnit.PORTION, MeasureUnit.GRAM)) {
                val calculation = calculator.calculate(food, 1.5, unit)
                val expected = recipe.nutritionFor(1.5, unit)
                assertEquals("$unit for ${recipe.yields().map { it.first }}", expected != null, calculation.supported)
                if (expected != null) {
                    assertEquals(expected.values.calories, calculation.nutrition.calories, 1e-9)
                    assertEquals(expected.values.protein!!, calculation.nutrition.protein!!, 1e-9)
                    assertFalse(calculation.approximate)
                }
            }
        }
        // With a total weight a portion resolves to its grams: 1 portion of 1600 g in 4.
        assertEquals(400.0, calculator.calculate(chili.asFood(), 1.0, MeasureUnit.PORTION).resolvedBaseAmount, 1e-9)
        assertEquals(MeasureUnit.GRAM, chili.asFood().nutrition.unit)
        assertEquals(MeasureUnit.PORTION, chili.copy(totalWeight = null).asFood().nutrition.unit)
    }

    /** A total volume: per ml is the total over it, ml is offered only with it, and without portions or a weight a bare count is 100 ml. */
    @Test fun aRecipeWithATotalVolumeLogsByTheMl() {
        val soup = chili.copy(portions = null, totalWeight = null, totalVolume = 1500.0)
        assertEquals(456.0, soup.nutritionFor(300.0, MeasureUnit.MILLILITER)!!.values.calories, 1e-9)
        assertNull(soup.nutritionFor(300.0, MeasureUnit.GRAM))
        assertNull(chili.nutritionFor(300.0, MeasureUnit.MILLILITER))
        assertEquals(listOf(MeasureUnit.MILLILITER), soup.yields().map { it.first })
        assertEquals(100.0 to MeasureUnit.MILLILITER, soup.shownServing())
        // A weight wins the basis over a volume; portions win the serving over both.
        assertEquals(100.0 to MeasureUnit.GRAM, soup.copy(totalWeight = 1600.0).shownServing())
        assertEquals(1.0 to MeasureUnit.PORTION, soup.copy(portions = 4.0).shownServing())
        val all = chili.copy(totalVolume = 1500.0)
        assertEquals(listOf(MeasureUnit.PORTION, MeasureUnit.GRAM, MeasureUnit.MILLILITER), all.yields().map { it.first })
        val calculator = NutritionCalculator()
        for (recipe in listOf(soup, all, soup.copy(portions = 4.0), soup.copy(totalWeight = 1600.0))) {
            val food = recipe.asFood()
            for (unit in listOf(MeasureUnit.PORTION, MeasureUnit.GRAM, MeasureUnit.MILLILITER)) {
                val calculation = calculator.calculate(food, 300.0, unit)
                val expected = recipe.nutritionFor(300.0, unit)
                assertEquals("$unit for ${recipe.yields().map { it.first }}", expected != null, calculation.supported)
                if (expected != null) assertEquals(expected.values.calories, calculation.nutrition.calories, 1e-9)
            }
        }
        assertEquals(MeasureUnit.MILLILITER, soup.asFood().nutrition.unit)
        assertEquals(MeasureUnit.GRAM, all.asFood().nutrition.unit)
    }

    @Test fun theIndexFollowsFoodsAndIsBuiltOncePerPairOfLists() {
        val foods = listOf(FoodRecord("mince", "Lean mince", emptyList(), "Personal food", FoodOrigin.CUSTOM, NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(150.0, 22.0, 0.0, 5.0))))
        val recipes = listOf(chili)
        val index = RecipeIndex()
        val current = index.recipes(foods, recipes)
        assertEquals(750.0, current.single().ingredients[0].nutritionSnapshot.calories, 1e-9)
        assertEquals("Lean mince", current.single().ingredients[0].foodName)
        // The deleted foods' ingredients keep their own numbers.
        assertEquals(chili.ingredients.drop(1), current.single().ingredients.drop(1))
        val matchable = index.matchable(foods, recipes)
        assertEquals(listOf("mince", "recipe-chili"), matchable.map { it.id })
        assertSame(matchable, index.matchable(foods, recipes))
        assertSame(current, index.recipes(foods, recipes))
        // No recipes: the foods themselves, so the Log and the ingredients share one matcher.
        assertSame(foods, index.matchable(foods, emptyList()))
    }

    @Test fun recipesAreOrderedByTheirLastLogThenByName() {
        val soup = chili.copy(id = "recipe-soup", name = "soup")
        val ajvar = chili.copy(id = "recipe-ajvar", name = "Ajvar")
        val entries = listOf(MealEntry("e", soup.id, "soup", 1.0, MeasureUnit.PORTION, 1.0, NutritionValues.ZERO, "", false, 5L))
        assertEquals(listOf("recipe-soup", "recipe-ajvar", "recipe-chili"), recipeRecencyOrder(listOf(chili, soup, ajvar), entries).map { it.id })
    }

    @Test fun aRecipeIdIsToldFromAFoodId() {
        assertTrue(isRecipeId("recipe-1"))
        assertFalse(isRecipeId("custom-1"))
        assertFalse(isRecipeId("egg"))
    }
}
