package mk.bosa.breakfast.domain

import mk.bosa.breakfast.testFood
import org.junit.Assert.assertEquals
import org.junit.Test

/** What an amount becomes when its unit chip changes: a count starts at 1, a weight or volume keeps the same food. */
class AmountSwitchTest {
    private fun food(
        unit: MeasureUnit = MeasureUnit.GRAM, servings: List<ServingConversion> = emptyList(), density: Double? = null,
        defaultUnit: MeasureUnit = unit, defaultAmount: Double = 100.0, id: String = "custom-x",
    ) = testFood(
        id, "Food", category = "Personal food", unit = unit, values = NutritionValues(100.0, 1.0, 1.0, 1.0),
        conversions = listOf(ServingConversion(unit, 100.0, "custom basis")) + servings,
        defaultAmount = defaultAmount, defaultUnit = defaultUnit, density = density, personalOverride = true,
    )

    private val bar = Measure(MeasureUnit.SERVING, "bar")
    private val g = Measure(MeasureUnit.GRAM)
    private val ml = Measure(MeasureUnit.MILLILITER)
    private val barFood = food(servings = listOf(ServingConversion(MeasureUnit.GRAM, 50.0, "personal serving", label = "bar")))

    @Test fun aNamedServingStartsAtOne() {
        assertEquals(1.0, barFood.amountAfterSwitch(100.0, g, bar), 0.0)
        assertEquals(1.0, barFood.amountAfterSwitch(250.0, g, bar), 0.0)
    }

    @Test fun aServingConvertsToGramsThroughItsWeight() {
        assertEquals(50.0, barFood.amountAfterSwitch(1.0, bar, g), 0.0)
        assertEquals(150.0, barFood.amountAfterSwitch(3.0, bar, g), 0.0)
    }

    @Test fun aPieceStartsAtOneAndConvertsBack() {
        val noodles = food(defaultUnit = MeasureUnit.PIECE, defaultAmount = 1.0, servings = listOf(ServingConversion(MeasureUnit.PIECE, 65.0, "personal serving")))
        val piece = Measure(MeasureUnit.PIECE)
        assertEquals(1.0, noodles.amountAfterSwitch(130.0, g, piece), 0.0)
        assertEquals(130.0, noodles.amountAfterSwitch(2.0, piece, g), 0.0)
    }

    @Test fun gramsAndMlConvertOnlyWithADensity() {
        val milk = food(density = 50.0)
        assertEquals(200.0, milk.amountAfterSwitch(100.0, g, ml), 0.0)
        assertEquals(50.0, milk.amountAfterSwitch(100.0, ml, g), 0.0)
        // No density: nothing converts, so the unit starts at 100.
        assertEquals(100.0, food().amountAfterSwitch(250.0, g, ml), 0.0)
        assertEquals(100.0, food(MeasureUnit.MILLILITER).amountAfterSwitch(30.0, ml, g), 0.0)
    }

    @Test fun aSpoonKeepsTheSameQuantity() {
        val oil = food(MeasureUnit.MILLILITER)
        assertEquals(2.0, oil.amountAfterSwitch(30.0, ml, Measure(MeasureUnit.TABLESPOON)), 0.0)
        assertEquals(30.0, oil.amountAfterSwitch(2.0, Measure(MeasureUnit.TABLESPOON), ml), 0.0)
        assertEquals(1.0, oil.amountAfterSwitch(240.0, ml, Measure(MeasureUnit.CUP)), 0.0)
        // The spoon of a food on g without a density is the rough 1 ml to 1 g.
        assertEquals(6.0, food().amountAfterSwitch(30.0, g, Measure(MeasureUnit.TEASPOON)), 0.0)
    }

    @Test fun theResultIsRoundedAsAFieldShowsIt() {
        val thirds = food(servings = listOf(ServingConversion(MeasureUnit.GRAM, 100.0 / 3, "personal serving", label = "third")))
        assertEquals(33.333, thirds.amountAfterSwitch(1.0, Measure(MeasureUnit.SERVING, "third"), g), 0.0)
    }

    @Test fun withNoAmountOrNoConversionItFallsBack() {
        assertEquals(100.0, barFood.amountAfterSwitch(null, bar, g), 0.0)
        assertEquals(1.0, barFood.amountAfterSwitch(null, g, bar), 0.0)
        assertEquals(100.0, barFood.amountAfterSwitch(Double.NaN, bar, g), 0.0)
        assertEquals(100.0, barFood.amountAfterSwitch(-2.0, bar, g), 0.0)
        // A serving the food has no more.
        assertEquals(100.0, barFood.amountAfterSwitch(2.0, Measure(MeasureUnit.SERVING, "slice"), g), 0.0)
        // The food's own default, when it is in that unit.
        val spoonDefault = food(defaultUnit = MeasureUnit.TABLESPOON, defaultAmount = 2.0, id = "usda-1")
        assertEquals(2.0, spoonDefault.amountAfterSwitch(null, g, Measure(MeasureUnit.TABLESPOON)), 0.0)
    }

    @Test fun theMeasureAlreadyShownKeepsItsAmount() {
        assertEquals(37.5, barFood.amountAfterSwitch(37.5, g, g), 0.0)
        assertEquals(2.0, barFood.amountAfterSwitch(2.0, bar, bar), 0.0)
    }

    @Test fun aRecipesPortionAndGramsConvertThroughItsYield() {
        val chili = Recipe(
            id = "recipe-chili", name = "Chili", portions = 4.0, totalWeight = 1600.0,
            ingredients = listOf(RecipeIngredient("mince", "mince", 500.0, MeasureUnit.GRAM, 500.0, MeasureUnit.GRAM, NutritionValues(1250.0, 100.0, 0.0, 90.0))),
        ).asFood()
        val portion = Measure(MeasureUnit.PORTION)
        assertEquals(1.0, chili.amountAfterSwitch(400.0, g, portion), 0.0)
        assertEquals(800.0, chili.amountAfterSwitch(2.0, portion, g), 0.0)
        // A recipe with portions only has no grams to convert to.
        val portionsOnly = Recipe(id = "recipe-a", name = "A", portions = 2.0, ingredients = emptyList()).asFood()
        assertEquals(100.0, portionsOnly.amountAfterSwitch(1.0, portion, g), 0.0)
    }

    @Test fun theFieldTextFollowsTheSwitch() {
        assertEquals("50", barFood.amountTextAfterSwitch("1", null, bar, g))
        assertEquals("1", barFood.amountTextAfterSwitch("100", 100.0, g, bar))
        // Not a number: the unit's own start.
        assertEquals("100", barFood.amountTextAfterSwitch("abc", null, bar, g))
        // The food is gone: the text stays.
        assertEquals("7", (null as FoodRecord?).amountTextAfterSwitch("7", null, bar, g))
    }
}
