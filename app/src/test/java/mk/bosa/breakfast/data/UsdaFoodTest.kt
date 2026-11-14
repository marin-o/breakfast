package mk.bosa.breakfast.data

import mk.bosa.breakfast.domain.TestFoods
import mk.bosa.breakfast.domain.FoodMatcher
import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.LogPlanner
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionCalculator
import mk.bosa.breakfast.domain.isOpenFoodFactsSource
import mk.bosa.breakfast.domain.measures
import mk.bosa.breakfast.domain.servingNamed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsdaFoodTest {
    private val row = UsdaFoodRow(
        fdcId = 2705975,
        foodCode = "24127202",
        description = "Chicken breast, fried, coated, prepared skinless, coating eaten, from raw",
        additional = "sauteed with coating; NS as to skin / coating eaten",
        kcal = 219.0,
        protein = 24.6,
        fat = 9.99,
        carbs = 6.01,
        portions = listOf(
            UsdaPortionRow("1 cup, cooked, diced", 135.0),
            UsdaPortionRow("1 small breast", 140.0),
            UsdaPortionRow("1/2 bagel", 50.0),
        ),
    )

    @Test fun `a row becomes a food with its source and the original description as name`() {
        val food = row.toFoodRecord()!!
        assertEquals("fndds_2705975", food.id)
        assertEquals(row.description, food.name)
        assertEquals(FoodOrigin.GENERIC, food.origin)
        assertEquals("2705975", food.sourceId)
        assertEquals("USDA FoodData Central", food.sourceName)
        assertEquals("FNDDS 2021-2023", food.sourceRelease)
        assertEquals("FNDDS 24127202: sauteed with coating; NS as to skin / coating eaten", food.sourceDescription)
        assertNull(food.barcode)
        assertEquals(100.0, food.defaultAmount, 0.0)
        assertEquals(MeasureUnit.GRAM, food.defaultUnit)
    }

    @Test fun `an SR Legacy row says so, and keeps the id of the foods users already stored`() {
        val flour = UsdaFoodRow(
            fdcId = 168894, foodCode = "20081", description = "Wheat flour, white, all-purpose, enriched, bleached", additional = null,
            kcal = 364.0, protein = 10.33, fat = 0.98, carbs = 76.31, portions = listOf(UsdaPortionRow("1 cup", 125.0)),
            release = "SR Legacy 2018-04",
        ).toFoodRecord()!!
        assertEquals("fndds_168894", flour.id)
        assertEquals("USDA FoodData Central", flour.sourceName)
        assertEquals("SR Legacy 2018-04", flour.sourceRelease)
        assertEquals("SR Legacy 20081", flour.sourceDescription)
        assertEquals(125.0, flour.grams(MeasureUnit.CUP).resolvedBaseAmount, 0.0)
        assertEquals("USDA SR Legacy 2018-04 portion", flour.conversions.single { it.unit == MeasureUnit.CUP }.provenance)
    }

    @Test fun `its values are per 100 g, each in its own place`() {
        val nutrition = row.toFoodRecord()!!.nutrition
        assertEquals(100.0, nutrition.amount, 0.0)
        assertEquals(MeasureUnit.GRAM, nutrition.unit)
        assertEquals(219.0, nutrition.values.calories, 0.0)
        assertEquals(24.6, nutrition.values.protein!!, 0.0)
        assertEquals(9.99, nutrition.values.fat!!, 0.0)
        assertEquals(6.01, nutrition.values.carbohydrates!!, 0.0)
    }

    @Test fun `a missing macro stays unknown`() {
        val values = row.copy(protein = null, fat = null, carbs = null).toFoodRecord()!!.nutrition.values
        assertNull(values.protein)
        assertNull(values.fat)
        assertNull(values.carbohydrates)
    }

    @Test fun `a macro of 0 stays 0`() {
        assertEquals(0.0, row.copy(fat = 0.0).toFoodRecord()!!.nutrition.values.fat!!, 0.0)
    }

    @Test fun `no description becomes a sourceDescription of the code alone`() {
        assertEquals("FNDDS 24127202", row.copy(additional = null).toFoodRecord()!!.sourceDescription)
    }

    @Test fun `portions are named approximate servings in grams`() {
        val food = row.toFoodRecord()!!
        val serving = food.servingNamed("small breast")!!
        assertEquals(MeasureUnit.GRAM, serving.unit)
        assertEquals(140.0, serving.baseUnitsPerServing, 0.0)
        assertTrue(serving.approximate)
        assertEquals(
            listOf(
                Measure(MeasureUnit.GRAM),
                Measure(MeasureUnit.CUP),
                Measure(MeasureUnit.SERVING, "cup, cooked, diced"),
                Measure(MeasureUnit.SERVING, "small breast"),
                Measure(MeasureUnit.SERVING, "1/2 bagel"),
            ),
            food.measures(),
        )
        assertEquals(4, food.householdServings().size)
    }

    @Test fun `a label repeated in another case is one serving, the first`() {
        val food = row.copy(portions = listOf(UsdaPortionRow("1 Bowl", 200.0), UsdaPortionRow("1 bowl", 240.0))).toFoodRecord()!!
        assertEquals(200.0, food.servingNamed("bowl")!!.baseUnitsPerServing, 0.0)
        assertEquals(1, food.householdServings().size)
    }

    private val calculator = NutritionCalculator()

    private fun FoodRecord.grams(unit: MeasureUnit) = calculator.calculate(this, 1.0, unit)

    @Test fun `1 cup of 2705975 is its cup portion`() {
        val food = row.toFoodRecord()!!
        val cup = food.grams(MeasureUnit.CUP)
        assertEquals(135.0, cup.resolvedBaseAmount, 0.0)
        assertEquals(295.65, cup.nutrition.calories, 1e-9)
        assertTrue(cup.approximate)
        val named = LogPlanner(FoodMatcher(listOf(food.copy(name = "Coated chicken")))).plan("1 cup coated chicken").entries.single()
        assertEquals(135.0, named.calculation.resolvedBaseAmount, 0.0)
    }

    @Test fun `a spoon portion is what the spoon weighs`() {
        val food = row.copy(portions = listOf(UsdaPortionRow("1 tablespoon", 14.0), UsdaPortionRow("1 teaspoon, dry", 4.0))).toFoodRecord()!!
        assertEquals(14.0, food.grams(MeasureUnit.TABLESPOON).resolvedBaseAmount, 0.0)
        assertEquals(4.0, food.grams(MeasureUnit.TEASPOON).resolvedBaseAmount, 0.0)
        assertEquals(14.0, food.servingNamed("tbsp")!!.baseUnitsPerServing, 0.0)
        val typed = LogPlanner(FoodMatcher(listOf(food.copy(name = "Coated chicken")))).plan("1 tbsp coated chicken").entries.single()
        assertEquals(14.0, typed.calculation.resolvedBaseAmount, 0.0)
    }

    @Test fun `the first cup portion is the cup, and a plain 1 cup isn't offered twice`() {
        val food = row.copy(portions = listOf(UsdaPortionRow("1 cup", 200.0), UsdaPortionRow("1 cup, diced", 140.0))).toFoodRecord()!!
        assertEquals(200.0, food.grams(MeasureUnit.CUP).resolvedBaseAmount, 0.0)
        assertEquals(
            listOf(Measure(MeasureUnit.GRAM), Measure(MeasureUnit.CUP), Measure(MeasureUnit.SERVING, "cup, diced")),
            food.measures(),
        )
    }

    @Test fun `only one whole cup or spoon is that unit`() {
        val food = row.copy(portions = listOf(UsdaPortionRow("1 cupcake, any size", 60.0), UsdaPortionRow("1/2 cup", 80.0), UsdaPortionRow("2 tablespoons", 30.0))).toFoodRecord()!!
        assertEquals(setOf(MeasureUnit.GRAM), food.conversions.map { it.unit }.toSet())
    }

    @Test fun `a food without a cup portion gets no invented row, and the cup has no value`() {
        val food = row.copy(portions = listOf(UsdaPortionRow("1 small breast", 140.0))).toFoodRecord()!!
        assertTrue(food.conversions.none { it.unit == MeasureUnit.CUP })
        assertFalse(Measure(MeasureUnit.CUP) in food.measures())
        assertFalse(food.grams(MeasureUnit.CUP).supported)
        assertFalse(food.grams(MeasureUnit.TABLESPOON).supported)
        assertFalse(food.grams(MeasureUnit.TEASPOON).supported)
    }

    @Test fun `a row past the plausible bounds is no food`() {
        assertNull(row.copy(kcal = 1200.0).toFoodRecord())
        assertNull(row.copy(protein = -1.0).toFoodRecord())
    }

    @Test fun `it isn't taken for an Open Food Facts food`() {
        assertFalse(isOpenFoodFactsSource(row.toFoodRecord()!!))
    }
}
