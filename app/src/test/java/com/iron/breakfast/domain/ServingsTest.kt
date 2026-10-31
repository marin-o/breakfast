package com.iron.breakfast.domain

import com.iron.breakfast.testFood
import com.iron.breakfast.ui.screens.previewQuantityLine
import com.iron.breakfast.ui.screens.countedQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Measurements (docs/measurements-spec.md): named servings, the weight and volume units, density, and how they read. */
class ServingsTest {
    private val calculator = NutritionCalculator()

    private fun food(
        id: String, name: String, unit: MeasureUnit = MeasureUnit.GRAM, kcal: Double = 100.0,
        servings: List<ServingConversion> = emptyList(), density: Double? = null, defaultUnit: MeasureUnit = unit,
    ) = testFood(
        id, name, category = "Personal food", unit = unit, values = NutritionValues(kcal, 1.0, 1.0, 1.0),
        conversions = listOf(ServingConversion(unit, 100.0, "custom basis")) + servings,
        defaultAmount = 100.0, defaultUnit = defaultUnit, density = density, personalOverride = true,
    )

    private val bread = food("custom-bread", "Bread", kcal = 250.0, servings = listOf(ServingConversion(MeasureUnit.GRAM, 30.0, "personal serving", label = "slice")))
    private val coke = food("custom-coke", "Coke", MeasureUnit.MILLILITER, kcal = 42.0, servings = listOf(ServingConversion(MeasureUnit.MILLILITER, 330.0, "personal serving", label = "can")))
    private val iceCream = food("custom-ice", "Ice cream", kcal = 200.0, density = 55.0)
    private val rice = food("custom-rice", "Rice", kcal = 130.0)
    private val cupNoodles = food("custom-cup-noodles", "Cup noodles", kcal = 450.0, defaultUnit = MeasureUnit.PIECE,
        servings = listOf(ServingConversion(MeasureUnit.PIECE, 65.0, "personal serving")))
    private val banana = FoodCatalog.fixtures.first { it.id == "banana" }

    private fun planner(vararg extra: FoodRecord) = LogPlanner(FoodMatcher(FoodCatalog.fixtures + extra))

    @Test fun aServingNameMatchesInAnyCaseSingularOrPluralWithinItsScript() {
        assertTrue(servingNameMatches("slices", "slice"))
        assertTrue(servingNameMatches("SLICE", "Slice"))
        assertTrue(servingNameMatches("berries", "berry"))
        assertTrue(servingNameMatches("glasses", "glass"))
        assertTrue(servingNameMatches("кутија", "Кутија"))
        assertFalse(servingNameMatches("can", "кан"))
        assertFalse("a word mixing scripts matches nothing", servingNameMatches("cаn", "can"))
        assertFalse(servingNameMatches("cup", "can"))
        assertEquals("2 slices", "2 ${servingNameFor("slice", 2.0)}")
        assertEquals("slice", servingNameFor("slice", 1.0))
        assertEquals("patties", servingNameFor("patty", 2.0))
        assertEquals("boxes", servingNameFor("box", 3.0))
        assertEquals("кутија", servingNameFor("кутија", 2.0))
    }

    @Test fun onlyTheServingItselfTurnsPluralNotWhatDescribesIt() {
        assertEquals("cups, cooked, diced", servingNameFor("cup, cooked, diced", 2.0))
        assertEquals("cup, cooked, diced", servingNameFor("cup, cooked, diced", 1.0))
        assertEquals("bottles (8 fl oz)", servingNameFor("bottle (8 fl oz)", 2.0))
        assertEquals("small breasts", servingNameFor("small breast", 2.0))
        assertEquals("large pizzas (13-15\" diameter)", servingNameFor("large pizza (13-15\" diameter)", 2.0))
        assertEquals("patties, cooked", servingNameFor("patty, cooked", 2.0))
        assertEquals("10 sticks, thin", servingNameFor("10 sticks, thin", 2.0))
    }

    /** The USDA catalogue is read, never written back: a stored copy stays what it was when it was added. */
    @Test fun aRepickedCatalogueFoodIsTheStoredOne() {
        val picked = testFood("fndds_2705975", "Chicken breast", values = NutritionValues(219.0, 24.6, 6.01, 9.99))
        val stored = picked.copy(nutrition = picked.nutrition.copy(values = NutritionValues(200.0, 20.0, 5.0, 9.0)))
        assertEquals(stored, cachedFoodForLogging(picked, stored, null))
        assertEquals(picked, cachedFoodForLogging(picked, null, null))
    }

    @Test fun aServingIsNamedByItsLabelOrByItsUnit() {
        assertEquals("piece", ServingConversion(MeasureUnit.PIECE, 118.0, "USDA").name)
        assertEquals("tbsp", ServingConversion(MeasureUnit.TABLESPOON, 16.0, "standard").name)
        assertEquals("bottle", ServingConversion(MeasureUnit.MILLILITER, 500.0, "personal", label = "bottle").name)
        assertNull("the basis is no serving", ServingConversion(MeasureUnit.GRAM, 100.0, "custom basis").name)
    }

    @Test fun theCalculatorCountsNamedServingsUnitsAndDensity() {
        assertEquals(150.0, calculator.calculate(bread, 2.0, MeasureUnit.SERVING, "slices").nutrition.calories, 1e-9)
        assertFalse("a name it lacks counts nothing", calculator.calculate(bread, 2.0, MeasureUnit.SERVING, "can").supported)
        // A named serving in g never stands for "g" on a food on ml.
        val gramServingOnMl = coke.copy(conversions = coke.conversions + ServingConversion(MeasureUnit.GRAM, 50.0, "personal serving", label = "scoop"))
        assertFalse(calculator.calculate(gramServingOnMl, 100.0, MeasureUnit.GRAM).supported)
        // Volume on ml: fixed sizes, exact.
        val cup = calculator.calculate(coke, 1.0, MeasureUnit.CUP)
        assertEquals(240.0, cup.resolvedBaseAmount, 1e-9)
        assertFalse(cup.approximate)
        // On g without a density: spoons and cups as 1 ml ≈ 1 g, marked; plain ml does not count.
        val spoon = calculator.calculate(rice, 2.0, MeasureUnit.TABLESPOON)
        assertEquals(30.0, spoon.resolvedBaseAmount, 1e-9)
        assertTrue(spoon.approximate)
        assertTrue(calculator.calculate(rice, 1.0, MeasureUnit.CUP).let { it.supported && it.approximate && it.resolvedBaseAmount == 240.0 })
        assertFalse(calculator.calculate(rice, 100.0, MeasureUnit.MILLILITER).supported)
        assertFalse("g on ml needs a density", calculator.calculate(coke, 100.0, MeasureUnit.GRAM).supported)
        // With a density, both families: 1 cup of ice cream at 55 g per 100 ml is 132 g; 110 g of it is 200 ml.
        val iceCup = calculator.calculate(iceCream, 1.0, MeasureUnit.CUP)
        assertEquals(132.0, iceCup.resolvedBaseAmount, 1e-9)
        assertFalse(iceCup.approximate)
        assertEquals(110.0, calculator.calculate(iceCream, 200.0, MeasureUnit.MILLILITER).resolvedBaseAmount, 1e-9)
        val milkByWeight = coke.copy(density = 104.0)
        assertEquals(100.0, calculator.calculate(milkByWeight, 104.0, MeasureUnit.GRAM).resolvedBaseAmount, 1e-9)
    }

    /** The Log bar reads a serving's name where a unit goes, in front of the food or after its amount, never over a name. */
    @Test fun theLogBarReadsServingNames() {
        val planner = planner(bread, coke, rice, cupNoodles)
        fun logged(text: String) = planner.plan(text).entries.single()
        logged("2 slices bread").let {
            assertEquals(MeasureUnit.SERVING to "slice", it.unit to it.label)
            assertEquals(150.0, it.calculation.nutrition.calories, 1e-9)
        }
        assertEquals(150.0, logged("bread 2 slices").calculation.nutrition.calories, 1e-9)
        for (text in listOf("1 can coke", "coke 1 can", "a can of coke", "1 CAN Coke")) {
            val part = logged(text)
            assertEquals(text, "custom-coke", part.food.id)
            assertEquals(text, MeasureUnit.SERVING to "can", part.unit to part.label)
            assertEquals(text, 138.6, part.calculation.nutrition.calories, 1e-9)
        }
        assertEquals(277.2, logged("2 cans coke").calculation.nutrition.calories, 1e-9)
        // A name the food lacks is not counted, as an unsupported unit.
        assertTrue(planner.plan("2 cans bread").entries.isEmpty())
        // "slice" without a slice is a piece, as before.
        assertEquals(MeasureUnit.PIECE, logged("2 slices banana").unit)
        assertEquals(planner(bread).plan("2 pcs banana").entries.single().calculation, logged("2 pieces banana").calculation)
        // A food's name always wins: "Cup noodles" logs as before cups were a unit.
        logged("1 cup noodles").let {
            assertEquals("custom-cup-noodles", it.food.id)
            assertEquals(1.0 to MeasureUnit.PIECE, it.amount to it.unit)
        }
    }

    /**
     * Fix pass, 1: a serving keeps "N word" with the food before it only when that food has it. A serving of another
     * food (an Open Food Facts product's "cookie") never joins two foods typed without a comma.
     */
    @Test fun aServingOfAnotherFoodNeverJoinsTwoFoods() {
        val coffee = food("custom-coffee", "Coffee", MeasureUnit.MILLILITER, kcal = 2.0)
        val biscuits = food("off-biscuits", "Butter biscuits", servings = listOf(ServingConversion(MeasureUnit.GRAM, 12.5, "Open Food Facts serving size", label = "cookie")))
        val cookies = food("custom-cookies", "Cookies", kcal = 480.0)
        val before = LogPlanner(FoodMatcher(FoodCatalog.fixtures + coffee + cookies))
        val after = LogPlanner(FoodMatcher(FoodCatalog.fixtures + coffee + cookies + biscuits))
        for (text in listOf("coffee 2 cookies", "200 ml coffee 2 cookies", "coffee 2 cookies 1 banana")) {
            assertEquals(text, before.segments(text).map { it.text }, after.segments(text).map { it.text })
        }
        assertEquals(listOf("coffee", "2 cookies"), after.segments("coffee 2 cookies").map { it.text })
        // The head food's own serving still goes with its amount.
        assertEquals(listOf("coke 1 can"), planner(coke).segments("coke 1 can").map { it.text })
    }

    /** Fix pass, 3: a unit word that begins a food's name ("cup noodles") does not end the food before it. */
    @Test fun aUnitWordThatBeginsAFoodsNameDoesNotEndTheFoodBeforeIt() {
        val planner = planner(cupNoodles)
        assertEquals(listOf("banana", "1 cup noodles"), planner.segments("banana 1 cup noodles").map { it.text })
        assertEquals(listOf("banana", "custom-cup-noodles"), planner.plan("banana 1 cup noodles").entries.map { it.food.id })
        assertEquals(listOf("2 cups milk"), planner.segments("2 cups milk").map { it.text })
    }

    /** Fix pass, 4: an unnamed row in the other of g and ml never stands for that unit; only the density crosses. */
    @Test fun anUnnamedRowInTheOtherFamilyNeverCountsTheUnit() {
        val milk = coke.copy(conversions = coke.conversions + ServingConversion(MeasureUnit.GRAM, 30.0, "personal serving"), density = 104.0)
        assertEquals(100.0 * 100.0 / 104.0, calculator.calculate(milk, 100.0, MeasureUnit.GRAM).resolvedBaseAmount, 1e-9)
        assertFalse(calculator.calculate(milk.copy(density = null), 100.0, MeasureUnit.GRAM).supported)
        val flour = rice.copy(conversions = rice.conversions + ServingConversion(MeasureUnit.MILLILITER, 30.0, "personal serving"))
        assertFalse(calculator.calculate(flour, 100.0, MeasureUnit.MILLILITER).supported)
        assertEquals(listOf(Measure(MeasureUnit.GRAM)), flour.measures())
    }

    /** Fix pass, 5: a part's serving of the food picked for it (resolver, Library bar) is that serving; else as typed. */
    @Test fun aPickedFoodTakesTheServingTyped() {
        val planner = planner(coke, rice)
        assertEquals(2.0 to Measure(MeasureUnit.SERVING, "can"), planner.quantityOf("2 cans xyzcoke", coke))
        assertEquals(2.0 to Measure(MeasureUnit.SERVING, "can"), planner.quantityOf("xyzcoke 2 cans", coke))
        assertEquals(2.0 to Measure(MeasureUnit.GRAM), planner.quantityOf("2 cans xyzrice", rice))
        assertEquals(200.0 to Measure(MeasureUnit.GRAM), planner.quantityOf("200 g xyzrice", rice))
    }

    /** Fix pass (search servings), 1: a re-picked result without servings keeps those its stored copy has. */
    @Test fun aRepickedResultKeepsTheServingsItsStoredCopyHas() {
        val searched = coke.copy(id = "off-coke", conversions = listOf(ServingConversion(MeasureUnit.MILLILITER, 1.0, "Open Food Facts per 100 ml")), personalOverride = false)
        val stored = searched.copy(conversions = searched.conversions + ServingConversion(MeasureUnit.MILLILITER, 330.0, "Open Food Facts serving size", label = "can"))
        val logged = cachedFoodForLogging(searched, stored, null)
        assertEquals(listOf("can"), logged.conversions.mapNotNull { it.name })
        assertEquals(searched.nutrition, logged.nutrition)
        // Its own serving by that name wins; a stored personal edit is kept whole, as before.
        val own = searched.copy(conversions = searched.conversions + ServingConversion(MeasureUnit.MILLILITER, 250.0, "Open Food Facts serving size", label = "Can"))
        assertEquals(listOf(250.0), cachedFoodForLogging(own, stored, null).conversions.filter { it.name != null }.map { it.baseUnitsPerServing })
        val edited = stored.copy(name = "My coke", personalOverride = true)
        assertEquals(edited, cachedFoodForLogging(searched, edited, null))
        assertEquals(searched, cachedFoodForLogging(searched, null, null))
    }

    @Test fun weightAndVolumeUnitsWorkWhenTypedAndReadAsTyped() {
        val planner = planner(rice, coke, iceCream)
        fun logged(text: String) = planner.plan(text).entries.single()
        logged("1.5 kg rice").let {
            assertEquals(1500.0 to MeasureUnit.GRAM, it.amount to it.unit)
            assertEquals("kg", it.label)
            assertEquals("1.5 kg", previewQuantityLine(it.amount, it.unit, it.calculation.approximate, it.label))
        }
        assertEquals(226.8, logged("8 oz rice").amount, 1e-9)
        assertEquals(453.6, logged("1 lb rice").amount, 1e-9)
        assertEquals(59.14, logged("2 fl oz coke").amount, 1e-9)
        assertEquals(500.0, logged("5 dl coke").amount, 1e-9)
        assertEquals("2 cups", previewQuantityLine(2.0, MeasureUnit.CUP, false))
        assertEquals("≈1 cup", previewQuantityLine(1.0, MeasureUnit.CUP, true))
        assertEquals(132.0, logged("1 cup ice cream").calculation.resolvedBaseAmount, 1e-9)
        assertTrue("plain ml on g with no density", planner.plan("200 ml rice").entries.isEmpty())
        assertTrue(planner.plan("1 fl oz rice").entries.isEmpty())
    }

    @Test fun theAmountSheetOffersWhatTheFoodHas() {
        // Nothing extra: as before.
        assertEquals(listOf(Measure(MeasureUnit.PIECE), Measure(MeasureUnit.GRAM)), banana.measures())
        assertEquals(listOf(Measure(MeasureUnit.GRAM)), rice.measures())
        // A density adds the other of g and ml; named servings follow.
        assertEquals(listOf(Measure(MeasureUnit.GRAM), Measure(MeasureUnit.MILLILITER)), iceCream.measures())
        assertEquals(listOf(Measure(MeasureUnit.GRAM), Measure(MeasureUnit.SERVING, "slice")), bread.measures())
        assertEquals("slice", bread.measures().last().label)
        assertEquals(Measure(MeasureUnit.SERVING, "slice"), bread.typedMeasure(MeasureUnit.PIECE, "slices"))
        assertEquals(Measure(MeasureUnit.PIECE), banana.typedMeasure(MeasureUnit.PIECE, "pcs"))
        assertEquals(Measure(MeasureUnit.GRAM, "kg"), rice.typedMeasure(MeasureUnit.GRAM, "kg", "kg"))
    }

    @Test fun anEntryReadsInWhatItWasLoggedIn() {
        assertEquals("2 slices", countedQuantity(2.0, MeasureUnit.SERVING, "slice"))
        assertEquals("1 can", countedQuantity(1.0, MeasureUnit.SERVING, "can"))
        assertEquals("1.5 kg", countedQuantity(1500.0, MeasureUnit.GRAM, "kg"))
        assertEquals("8 oz", countedQuantity(226.8, MeasureUnit.GRAM, "oz"))
        assertNull(countedQuantity(150.0, MeasureUnit.GRAM, null))
    }

    @Test fun theQuickEditReadsAServingName() {
        assertEquals(ParsedAmountUnit(2.0, MeasureUnit.PIECE, "slice"), parseEditAmount("2 slice"))
        assertEquals(ParsedAmountUnit(1.0, MeasureUnit.SERVING, "can"), parseEditAmount("1 can"))
        assertEquals(ParsedAmountUnit(1.5, MeasureUnit.CUP), parseEditAmount("1.5 cup"))
        assertEquals(ParsedAmountUnit(1500.0, MeasureUnit.GRAM, "kg", "kg"), parseEditAmount("1.5 kg"))
        assertNull(parseEditAmount("1 can coke"))
        // The strict reading (the product review's) keeps refusing a word that is no unit.
        assertNull(parseStrictAmountUnit("1 can"))
    }

    /** Fix pass, 2: a printed plural's singular is chosen deliberately, never invented. */
    @Test fun aPrintedPluralIsMadeSingularDeliberately() {
        val expected = mapOf(
            "slices" to "slice", "pieces" to "piece", "cookies" to "cookie", "biscuits" to "biscuit", "peaches" to "peach",
            "glasses" to "glass", "bars" to "bar", "berries" to "berry", "boxes" to "box", "dishes" to "dish",
            "tomatoes" to "tomatoes", "candies" to "candies", "кутии" to "кутии",
        )
        expected.forEach { (plural, singular) -> assertEquals(plural, singular, printedSingular(plural)) }
        assertEquals("slice" to 12.5, servingFromLabel("2 slices (25 g)", MeasureUnit.GRAM, "serving")?.let { it.name to it.baseUnitsPerServing })
    }

    /** A product's printed serving size, only when it reads clearly and in its basis. */
    @Test fun aPrintedServingSizeBecomesANamedServing() {
        fun serving(text: String?, basis: MeasureUnit) = servingFromLabel(text, basis, "serving")?.let { it.name to it.baseUnitsPerServing }
        assertEquals("can" to 330.0, serving("1 can (330 ml)", MeasureUnit.MILLILITER))
        assertEquals("biscuit" to 12.5, serving("2 biscuits (25 g)", MeasureUnit.GRAM))
        assertEquals("serving" to 30.0, serving("30 g", MeasureUnit.GRAM))
        assertEquals("serving" to 250.0, serving("250ml", MeasureUnit.MILLILITER))
        assertEquals("slice" to 12.5, serving("1 slice (12,5 g)", MeasureUnit.GRAM))
        assertNull("the unit is not its basis", serving("1 can (330 ml)", MeasureUnit.GRAM))
        assertNull(serving("one portion", MeasureUnit.GRAM))
        assertNull(serving("1 portion (100 g) or 2 cups", MeasureUnit.GRAM))
        assertNull(serving("2 oz", MeasureUnit.GRAM))
        assertNull(serving(null, MeasureUnit.GRAM))
    }
}
