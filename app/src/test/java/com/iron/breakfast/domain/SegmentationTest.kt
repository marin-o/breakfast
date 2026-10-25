package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentationTest {
    private val parser = ShorthandParser()
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))

    private val protein = FoodRecord(
        id = "custom_protein_bar", name = "Protein 2 bar", aliases = listOf("protein bar"), category = "Snack",
        origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(1.0, MeasureUnit.PIECE, NutritionValues(200.0, 20.0, 20.0, 5.0)),
        defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE,
    )
    private val vitamin = protein.copy(id = "custom_b12", name = "Vitamin B12 tablet", aliases = emptyList(), category = "Supplement")
    private val customMatcher = FoodMatcher(FoodCatalog.fixtures + protein + vitamin)
    private val customPlanner = LogPlanner(customMatcher)

    /** The same probe the planner gives the parser, for a planner over [matcher]. */
    private fun splitWith(p: LogPlanner, m: FoodMatcher, input: String): List<String> = ShorthandParser().split(input, object : FoodProbe {
        override fun resolves(text: String) = p.resolve(text) != null
        override fun covers(phrase: String) = m.covers(phrase)
        override fun isExactName(phrase: String) = m.isExactName(phrase)
        override fun isWholeFood(text: String) = p.resolve(text)?.let { !it.needsChoice && it.ranked.first().wholeMatch } ?: false
        override fun matchesWholeWord(word: String) = m.matchesWholeWord(word)
    })

    private fun split(input: String) = splitWith(planner, FoodMatcher(FoodCatalog.fixtures), input)

    @Test fun explicitSeparatorsSplitParts() {
        assertEquals(listOf("1 banana", "2 eggs"), parser.split("1 banana; 2 eggs"))
        assertEquals(listOf("1 banana", "2 eggs"), parser.split("1 banana + 2 eggs"))
        assertEquals(listOf("1 banana", "2 eggs"), parser.split("1 banana & 2 eggs"))
        assertEquals(listOf("1 banana", "2 eggs"), parser.split("1 banana and 2 eggs"))
        assertEquals(listOf("1 banana", "2 eggs"), parser.split("1 banana и 2 eggs"))
        assertEquals(listOf("1 banana", "2 eggs"), parser.split("1 banana\n2 eggs"))
    }

    @Test fun withAndSoAreFoodTextNotSeparators() {
        assertEquals(listOf("100g oats with milk"), parser.split("100g oats with milk"))
        assertEquals(listOf("Ѓеврек со сусам"), parser.split("Ѓеврек со сусам"))
    }

    @Test fun quantityAfterFoodStartsANewPartWithoutCommas() {
        assertEquals(listOf("2 egg", "1 banana"), split("2 egg 1 banana"))
        assertEquals(listOf("100g buckwheat", "1 tsp pb", "1 banana"), split("100g buckwheat 1 tsp pb 1 banana"))
        assertEquals(listOf("1 1/2 tbsp oats", "banana"), split("1 1/2 tbsp oats, banana"))
    }

    @Test fun aQuantityWithNoFoodAfterItStaysInItsPart() {
        assertEquals(listOf("2 egg 1"), split("2 egg 1"))
        assertEquals(listOf("peanut butter half a tbsp"), split("peanut butter half a tbsp"))
    }

    @Test fun percentNumbersAreNeverSplitPoints() {
        assertEquals(listOf("Кисело млеко 2.8%"), split("Кисело млеко 2.8%"))
        assertEquals(listOf("Greek yogurt 0% fat"), split("Greek yogurt 0% fat"))
        assertEquals(listOf("Greek yogurt 0 % fat"), split("Greek yogurt 0 % fat"))
        assertEquals(listOf("Milk 2.8%", "1 banana"), split("Milk 2.8% 1 banana"))
    }

    @Test fun ratioInsideAFoodNameIsNeverSplitPoint() {
        assertEquals(listOf("ground beef 90/10 raw 100 g"), split("ground beef 90/10 raw 100 g"))
        assertEquals(listOf("Ground beef 90/10 raw", "1 banana"), split("Ground beef 90/10 raw 1 banana"))
    }

    @Test fun aNumberedFoodNameStaysWhole() {
        assertEquals(listOf("1 protein 2 bar"), splitWith(customPlanner, customMatcher, "1 protein 2 bar"))
        assertEquals(listOf("Protein 2 bar", "1 banana"), splitWith(customPlanner, customMatcher, "Protein 2 bar 1 banana"))
        assertEquals(listOf("Vitamin B12 tablet", "1 banana"), splitWith(customPlanner, customMatcher, "Vitamin B12 tablet 1 banana"))
        assertEquals(listOf("vitamin b12 tablet"), splitWith(customPlanner, customMatcher, "vitamin b12 tablet"))
    }

    @Test fun mealWordBeforeAQuantityIsItsOwnPart() {
        assertEquals(listOf("lunch", "1 banana"), parser.split("lunch 1 banana"))
        assertEquals(listOf("breakfast cereal"), parser.split("breakfast cereal"))
        assertEquals(listOf("lunch", "2 eggs"), split("lunch, 2 eggs"))
    }

    @Test fun planSkipsMealWordsAndLogsEveryFood() {
        val plan = planner.plan("lunch 1 banana, dinner")
        assertEquals(listOf("banana"), plan.entries.map { it.food.id })
        assertTrue(plan.unresolved.isEmpty())
    }

    @Test fun unknownPartBecomesItsOwnUnresolvedRowWhenAnotherPartResolves() {
        val plan = planner.plan("100g buckwheat 1 tsp pb 1 banana")
        assertEquals(listOf("peanut_butter_smooth", "banana"), plan.entries.map { it.food.id })
        assertEquals(listOf("100g buckwheat"), plan.unresolved)
    }

    @Test fun wholeSegmentIsKeptWhenNothingInItResolves() {
        assertEquals(listOf("zzqx 2 wwpl"), split("zzqx 2 wwpl"))
    }

    @Test fun activePartIsTheLastSmartPart() {
        val parts = planner.segments("1 banana 2 egg")
        assertEquals(listOf("1 banana", "2 egg"), parts.map { it.text })
        assertEquals("2 egg", "1 banana 2 egg".substring(parts.last().start, parts.last().end))
    }

    @Test fun inputEndingInASeparatorHasNoActivePart() {
        assertTrue(planner.endsOpen("1 banana, "))
        assertTrue(planner.endsOpen("1 banana and"))
        assertTrue(planner.endsOpen("1 banana + "))
        assertFalse(planner.endsOpen("1 banana"))
        assertFalse(planner.endsOpen("1 banana, 2 eg"))
    }

    @Test fun everySeparatorEndsTheLastPart() {
        listOf(",", ";", "\n", "+", "&", " and", " AND", " и", " И").forEach { tail ->
            assertTrue("'$tail'", parser.endsOpen("1 banana$tail"))
        }
        assertTrue(parser.endsOpen("and"))
        listOf("1 band", "1 banana sandи", "сир ис", "mleko 2,8").forEach { assertFalse(it, parser.endsOpen(it)) }
    }

    @Test fun aCommaInsideAFoodNameKeepsTheNameWhole() {
        val pindzur = protein.copy(id = "pindzur", name = "Пинџур, домашен", aliases = emptyList(), category = "Spread")
        val named = LogPlanner(FoodMatcher(FoodCatalog.fixtures + pindzur))
        assertEquals(listOf("pindzur"), named.plan("Пинџур, домашен").entries.map { it.food.id })
        assertTrue(named.plan("Пинџур, домашен").unresolved.isEmpty())
        // "100 g" cannot be converted for a piece-counted food, so it is not logged: it stays a Not in your foods row.
        assertEquals(listOf("Пинџур, домашен 100 g"), named.plan("Пинџур, домашен 100 g").unresolved)
        assertEquals(listOf("pindzur"), named.plan("Пинџур, домашен 1 piece").entries.map { it.food.id })
        assertEquals(listOf("pindzur", "banana"), named.plan("Пинџур, домашен, 1 banana").entries.map { it.food.id })
    }

    @Test fun repeatedFoodsSeparatedByACommaStayTwoEntries() {
        assertEquals(listOf("banana", "banana"), planner.plan("banana, banana").entries.map { it.food.id })
    }

    @Test fun everyBundledNameWithADigitStaysOneFood() {
        val numbered = FoodCatalog.fixtures.filter { food -> food.name.any { it.isDigit() } }
        assertTrue(numbered.size >= 3)
        numbered.forEach { food ->
            // Each food is weighed in its own basis unit, so the amount is valid for that food.
            val amount = "${food.nutrition.amount.toInt()} ${food.nutrition.unit.label}"
            assertEquals("${food.name}: one food", listOf(food.id), planner.plan("${food.name} $amount").entries.map { it.food.id })
            assertEquals("${food.name}: beside a banana", listOf("banana", food.id), planner.plan("1 banana, ${food.name} $amount").entries.map { it.food.id })
            assertEquals("${food.name}: no amount typed", listOf(food.id), planner.plan(food.name).entries.map { it.food.id })
        }
    }
}

class SegmentationProbeRejoinTest {
    private val catalogue = FoodCatalog.fixtures + FoodRecord(
        id = "mac", name = "Macaroni and cheese", aliases = emptyList(), category = "Pasta", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(1.0, MeasureUnit.PIECE, NutritionValues(300.0, 10.0, 40.0, 12.0)),
        defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE,
    )

    @Test fun wordSeparatorInsideAFoodNameIsRejoinedThroughTheProbe() {
        val matcher = FoodMatcher(catalogue)
        val planner = LogPlanner(matcher)
        val probe = object : FoodProbe {
            override fun resolves(text: String) = planner.resolve(text)?.needsChoice == false
            override fun covers(phrase: String) = matcher.covers(phrase)
            override fun isExactName(phrase: String) = matcher.isExactName(phrase)
            override fun isWholeFood(text: String) = planner.resolve(text)?.let { !it.needsChoice && it.ranked.first().wholeMatch } ?: false
            override fun matchesWholeWord(word: String) = matcher.matchesWholeWord(word)
        }
        assertEquals(listOf("Mac and cheese", "egg"), ShorthandParser().split("Mac and cheese and egg", probe))
        assertEquals(listOf("Mac and cheese"), ShorthandParser().split("Mac and cheese", probe))
    }

    @Test fun joinedNameIsOneEntryThroughThePlanner() {
        val planner = LogPlanner(FoodMatcher(catalogue))
        assertEquals(listOf("mac"), planner.plan("Mac and cheese").entries.map { it.food.id })
    }
}
