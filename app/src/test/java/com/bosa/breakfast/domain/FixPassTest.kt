package com.bosa.breakfast.domain

import com.bosa.breakfast.testFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review fixes: word separators, bare articles, number words inside names, quick-add numbers, typos, script rules. */
class FixPassTest {
    private fun food(
        id: String, name: String, aliases: List<String> = emptyList(), category: String = "Test",
        amount: Double = 1.0, unit: MeasureUnit = MeasureUnit.PIECE,
    ) = testFood(id, name, aliases, category, basisAmount = amount, unit = unit, values = NutritionValues(100.0, 5.0, 10.0, 2.0), defaultAmount = amount, defaultUnit = unit)

    private val fixtures = LogPlanner(FoodMatcher(TestFoods.fixtures))

    // Item 2: word separators split only when both sides are foods on their own.

    @Test fun macAndCheeseStaysOneUnresolvedPartWithOnlyMacadamiaAndCheeseFoods() {
        val planner = LogPlanner(FoodMatcher(listOf(food("macadamia", "Macadamia"), food("cheese", "Cheese"))))
        assertEquals(listOf("Mac and cheese"), planner.segments("Mac and cheese").map { it.text })
        val plan = planner.plan("Mac and cheese")
        assertTrue(plan.entries.isEmpty())
        assertEquals(listOf("Mac and cheese"), plan.unresolved)
    }

    @Test fun macAndCheeseIsOneEntryWhenAFoodCoversIt() {
        val planner = LogPlanner(FoodMatcher(listOf(food("mac", "Macaroni and cheese"))))
        assertEquals(listOf("mac"), planner.plan("Mac and cheese").entries.map { it.food.id })
        assertEquals(1, planner.segments("Mac and cheese").size)
    }

    @Test fun ampersandInsideANameStaysOneSegment() {
        val planner = LogPlanner(FoodMatcher(TestFoods.fixtures))
        assertEquals(listOf("M&M"), planner.segments("M&M").map { it.text })
        assertEquals(listOf("Ben & Jerry's"), planner.segments("Ben & Jerry's").map { it.text })
        assertEquals(listOf("Ben & Jerry's"), planner.plan("Ben & Jerry's").unresolved)
    }

    @Test fun eggAndBananaSplitsIntoTwoFoods() {
        assertEquals(listOf("egg", "banana"), fixtures.plan("egg and banana").entries.map { it.food.id })
        assertEquals(listOf("1 egg", "1 banana"), fixtures.segments("1 egg and 1 banana").map { it.text })
    }

    @Test fun semicolonAndNewlineNeverRejoin() {
        val named = LogPlanner(FoodMatcher(listOf(food("pindzur", "Пинџур, домашен", category = "Spread"))))
        assertEquals(2, named.segments("Пинџур; домашен").size)
        assertEquals(2, named.segments("Пинџур\nдомашен").size)
        // A comma does rejoin, because that join is exactly the food's name.
        assertEquals(1, named.segments("Пинџур, домашен").size)
    }

    // Item 1: save and the rows use the same segmentation, with meal words removed by span.

    @Test fun mealWordsAreRemovedByTheirSpanKeepingTheOtherText() {
        assertEquals("2 eggs, 1 banana", removeAllMealMarkerTokens("lunch, 2 eggs, dinner, 1 banana"))
        assertEquals("Mac and cheese", removeAllMealMarkerTokens("lunch, Mac and cheese"))
    }

    @Test fun planSkipsMealWordsSoSaveAndRowsAgree() {
        val planner = LogPlanner(FoodMatcher(listOf(food("mac", "Macaroni and cheese"))))
        val withMeal = planner.plan("lunch, Mac and cheese")
        assertEquals(listOf("mac"), withMeal.entries.map { it.food.id })
        assertEquals(withMeal.entries.map { it.food.id }, planner.plan("Mac and cheese").entries.map { it.food.id })
        assertEquals(withMeal.entries.map { it.amount }, planner.plan("Mac and cheese").entries.map { it.amount })
    }

    // Item 3: a bare article is one only for a piece-counted food.

    @Test fun aBareArticleIsOneOnlyForAPieceFood() {
        val milk = food("milk", "Milk", category = "Dairy", amount = 100.0, unit = MeasureUnit.MILLILITER)
        val planner = LogPlanner(FoodMatcher(listOf(milk, food("egg", "Egg", aliases = listOf("eggs")))))
        val milkResolution = requireNotNull(planner.resolve("a milk"))
        assertEquals(100.0, milkResolution.amount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, milkResolution.unit)
        assertEquals(1.0, requireNotNull(planner.resolve("an egg")).amount, 0.0)
        assertEquals(1.0, requireNotNull(fixtures.resolve("a banana")).amount, 0.0)
    }

    @Test fun aArticleWithAUnitStillReadsTheUnit() {
        assertEquals(1.0, requireNotNull(fixtures.resolve("a tbsp peanut butter")).amount, 0.0)
        assertEquals(MeasureUnit.TABLESPOON, requireNotNull(fixtures.resolve("a tbsp peanut butter")).unit)
        val bread = FoodMatcher(listOf(food("bread", "Bread", aliases = listOf("bread"), amount = 1.0, unit = MeasureUnit.PIECE)))
        assertEquals(1.0, requireNotNull(LogPlanner(bread).resolve("a slice of bread")).amount, 0.0)
    }

    @Test fun aCoupleAndAHalfAreStillAmounts() {
        assertEquals(2.0, requireNotNull(fixtures.resolve("a couple of eggs")).amount, 0.0)
        assertEquals(0.5, requireNotNull(fixtures.resolve("a half banana")).amount, 0.0)
    }

    // Item 4: a number word inside a food's own name is part of the name.

    @Test fun numberWordsInsideCatalogueNamesResolveAsTheFoodWithItsDefaultAmount() {
        val names = listOf("Ten grain bread", "Quarter Pounder", "Two-bite brownies")
        val planner = LogPlanner(FoodMatcher(names.mapIndexed { i, name -> food("f$i", name, amount = 1.0) }))
        names.forEachIndexed { i, name ->
            val resolution = requireNotNull(planner.resolve(name))
            assertEquals("f$i", resolution.food.id)
            assertEquals(1.0, resolution.amount, 0.0)
            assertEquals(MeasureUnit.PIECE, resolution.unit)
            assertEquals(listOf("f$i"), planner.plan(name).entries.map { it.food.id })
        }
        assertEquals(listOf("f0", "f2"), planner.plan("Ten grain bread, 1 Two-bite brownies").entries.map { it.food.id })
    }

    @Test fun numberWordsInsideNamesStayAmountsWhenNoFoodCoversThem() {
        assertEquals(2.0, requireNotNull(fixtures.resolve("two eggs")).amount, 0.0)
    }

    // Item 5: the chip's kcal, the chosen text and the logged entry agree.

    @Test fun quickAddChoiceKcalTextAndLoggedEntryAgree() {
        val bar = food("bar", "Protein 2 bar", amount = 1.0, unit = MeasureUnit.PIECE)
        val planner = LogPlanner(FoodMatcher(listOf(bar)))
        val shownKcal = planner.calculationFor("Protein 2 bar", bar).nutrition.calories
        val text = planner.chosenText("Protein 2 bar", bar)
        assertEquals("1 piece Protein 2 bar", text)
        val logged = requireNotNull(planner.plan(text).entries.singleOrNull())
        assertEquals("bar", logged.food.id)
        assertEquals(1.0, logged.amount, 0.0)
        assertEquals(MeasureUnit.PIECE, logged.unit)
        assertEquals(shownKcal, logged.calculation.nutrition.calories, 1e-9)
    }

    // Item 6: typos. One edit from six letters, two from nine; a weak match is only a choice.

    @Test fun typoAllowancesStartAtSixLettersAndTwoEditsAtNine() {
        val banana = fixtures
        assertTrue(banana.resolve("bananna") != null)
        assertTrue(FoodMatcher(listOf(food("maple", "Maple syrup"))).suggestions("apple").isEmpty())
        assertTrue(FoodMatcher(listOf(food("maple", "Maple syrup"))).suggestions("maplee").isNotEmpty())
    }

    @Test fun appleWithOnlyMapleSyrupIsNotResolved() {
        val planner = LogPlanner(FoodMatcher(listOf(food("maple", "Maple syrup"))))
        assertNull(planner.resolve("apple"))
        assertEquals(listOf("apple"), planner.plan("apple").unresolved)
    }

    @Test fun aTypoWithOneCandidateAndNoFirstLetterEditResolves() {
        val buckwheat = LogPlanner(FoodMatcher(TestFoods.fixtures + food("buckwheat", "Buckwheat", category = "Grain")))
        assertEquals("buckwheat", buckwheat.resolve("buckweat")?.food?.id)
        assertEquals("banana", fixtures.resolve("bananna")?.food?.id)
        assertEquals("banana", fixtures.resolve("бананна")?.food?.id) // a Cyrillic typo matches the Cyrillic alias
    }

    @Test fun aTypoWithAnEditAtTheFirstLetterIsOnlyAChoice() {
        val planner = LogPlanner(FoodMatcher(listOf(food("banana", "Banana"))))
        val resolution = requireNotNull(planner.resolve("vanana"))
        assertTrue(resolution.needsChoice)
        assertEquals("banana", resolution.food.id)
        assertEquals(listOf("vanana"), planner.plan("vanana").unresolved)
        assertTrue(planner.plan("vanana").entries.isEmpty())
    }

    @Test fun aTypoWithSeveralCandidatesIsOnlyAChoice() {
        val planner = LogPlanner(FoodMatcher(listOf(food("banana", "Banana"), food("banann", "Banann"))))
        assertTrue(requireNotNull(planner.resolve("bananna")).needsChoice)
        assertTrue(planner.plan("bananna").entries.isEmpty())
    }

    // Scripts never cross: a Latin word never finds a Cyrillic name, and a Cyrillic word never finds a Latin one.

    @Test fun latinSpellingsDoNotFindCyrillicNames() {
        val chevap = food("chevap", "Чевап", category = "Meat")
        val shopska = food("shopska", "Шопска", category = "Salad")
        val zelka = food("zelka", "Зелка", category = "Vegetable")
        val pindzur = food("pindzur", "Пинџур", category = "Spread")
        val planner = LogPlanner(FoodMatcher(listOf(chevap, shopska, zelka, pindzur)))
        assertEquals(null, planner.resolve("cevap")?.food?.id)
        assertEquals(null, planner.resolve("sopska")?.food?.id)
        assertEquals(null, planner.resolve("zelka")?.food?.id)
        assertEquals(null, planner.resolve("pindzur")?.food?.id)
        assertEquals(null, planner.resolve("chevap")?.food?.id)
    }

    @Test fun cyrillicNamesMatchTheirOwnSpelling() {
        val chevap = food("chevap", "Чевап", category = "Meat")
        val shopska = food("shopska", "Шопска", category = "Salad")
        val planner = LogPlanner(FoodMatcher(listOf(chevap, shopska)))
        assertEquals("chevap", planner.resolve("чевап")?.food?.id)
        assertEquals("shopska", planner.resolve("шопска")?.food?.id)
    }

    @Test fun exactLatinNameRanksAboveACyrillicOneOfTheSameSound() {
        val matcher = FoodMatcher(listOf(food("latin", "Cevap"), food("cyrillic", "Чевап")))
        assertEquals("latin", matcher.best("cevap")?.food?.id)
        assertEquals("cyrillic", matcher.best("чевап")?.food?.id)
    }

    // Item 8: a lenient performance bound on a 500-food catalogue and a 10-part input.

    @Test fun tenPartInputAgainstFiveHundredFoodsStaysFast() {
        val words = listOf("alpha", "bravo", "cedar", "delta", "ember", "fjord", "garnet", "harbor", "iris", "jasper")
        val catalogue = (0 until 500).map { i ->
            food("f$i", "${words[i % 10]} ${words[(i / 10) % 10]} food$i", aliases = listOf("item$i"), category = "Synthetic")
        }
        val input = (0 until 10).joinToString(", ") { i -> "${i + 1} ${words[i]} ${words[(i + 3) % 10]} food${i * 37 % 500}" }
        val planner = LogPlanner(FoodMatcher(catalogue))
        LogPlanner(FoodMatcher(catalogue)).plan(input)
        val best = (1..10).minOf { _ ->
            val fresh = LogPlanner(FoodMatcher(catalogue))
            val start = System.nanoTime()
            fresh.plan(input)
            (System.nanoTime() - start) / 1_000_000
        }
        println("plan-500-food-ms=$best")
        assertTrue("plan took $best ms", best < 50)
        val bestDraft = (1..10).minOf { _ ->
            val fresh = LogPlanner(FoodMatcher(catalogue))
            val start = System.nanoTime()
            fresh.draft(input)
            (System.nanoTime() - start) / 1_000_000
        }
        println("draft-500-food-ms=$bestDraft")
        assertTrue("draft took $bestDraft ms", bestDraft < 50)
        assertFalse(planner.plan(input).entries.isEmpty())
    }
}

class FixPassWordTest {
    private val fixtures = LogPlanner(FoodMatcher(TestFoods.fixtures))

    @Test fun shortAndApostropheWordsNeverFindAFoodBySubstring() {
        // "M", "s" and "Jerry's" used to match "Grandma's ajvar"; now a short word must be a whole word.
        assertEquals(listOf("M&M"), fixtures.plan("M&M").unresolved)
        assertEquals(listOf("Ben & Jerry's"), fixtures.plan("Ben & Jerry's").unresolved)
        assertEquals(listOf("Jerry's"), fixtures.plan("Jerry's").unresolved)
        assertTrue(fixtures.plan("M&M").entries.isEmpty())
    }

    @Test fun aPartialNumberWordNameResolvesToTheFoodWithItsDefault() {
        val grain = LogPlanner(FoodMatcher(TestFoods.fixtures + FoodRecord(
            id = "tenbread", name = "Ten grain bread", aliases = emptyList(), category = "Bread", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(1.0, MeasureUnit.PIECE, NutritionValues(100.0, 5.0, 10.0, 2.0)),
            defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE,
        )))
        // "ten" used to read as 10 pieces; the food's own name carries it, so it is one piece.
        assertEquals(1.0, requireNotNull(grain.resolve("ten grain")).amount, 0.0)
    }

    @Test fun aWarmMilkIsTheDefaultMilkNotOneMillilitre() {
        val milk = FoodRecord(
            id = "milk", name = "Milk", aliases = emptyList(), category = "Dairy", origin = FoodOrigin.GENERIC,
            nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(60.0, 3.0, 5.0, 3.0)),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.MILLILITER,
        )
        val planner = LogPlanner(FoodMatcher(listOf(milk)))
        assertEquals(100.0, requireNotNull(planner.resolve("a warm milk")).amount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, requireNotNull(planner.resolve("a warm milk")).unit)
    }
}
