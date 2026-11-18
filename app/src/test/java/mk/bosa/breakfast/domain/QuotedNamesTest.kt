package mk.bosa.breakfast.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mk.bosa.breakfast.recipes.ingredientsOf
import mk.bosa.breakfast.search.UsdaCatalogue
import mk.bosa.breakfast.search.UsdaPartSearch
import mk.bosa.breakfast.testFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A name in double quotes is one name, and a part after a comma may be the rest of a name with commas. */
class QuotedNamesTest {
    private val parser = ShorthandParser()
    private fun food(id: String, name: String) = testFood(id, name, defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
    private val greek = food("greek", "Yogurt, Greek, plain")
    private val plainYogurt = food("plainy", "Yogurt, plain")
    private val banana = testFood("banana", "Banana", defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE, basisAmount = 1.0, unit = MeasureUnit.PIECE)
    private val egg = testFood("egg", "Egg", defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE, basisAmount = 1.0, unit = MeasureUnit.PIECE)
    private val peppers = food("peppers", "Peppers, raw, NFS")
    private val greens = food("greens", "Green beans")
    private val cyr = food("cyr", "Јогурт, грчки, обичен")
    private val foods = listOf(greek, plainYogurt, banana, egg, peppers, greens, cyr)
    private fun planner(pins: Map<String, FoodRecord> = emptyMap()) = LogPlanner(FoodMatcher(foods), pins = pins)
    /** A suggestion that is there because its name runs on across the comma, not because the fragment starts one of its words. */
    private fun runsOn(food: RankedFood) = food.reasons.any { it.startsWith("starts as") }
    private fun offersAcross(input: String) = planner().draft(input).suggestions.any(::runsOn)
    private fun split(input: String) = planner().segments(input).map { it.text }

    // Quote rule

    @Test fun straightCurlyAndMacedonianQuotesHoldACommaName() {
        assertEquals(listOf("200g \"yogurt, greek\"", "1 banana"), parser.split("200g \"yogurt, greek\", 1 banana"))
        assertEquals(listOf("200g “yogurt, greek”", "1 banana"), parser.split("200g “yogurt, greek”, 1 banana"))
        assertEquals(listOf("200g „јогурт, грчки“", "1 banana"), parser.split("200g „јогурт, грчки“, 1 banana"))
    }

    @Test fun anUnclosedQuoteKeepsTheCommasUpToTheEndOfTheLine() {
        assertEquals(listOf("200g \"yogurt, gre"), parser.split("200g \"yogurt, gre"))
        assertEquals(listOf("200g \"yogurt, greek, plain"), parser.split("200g \"yogurt, greek, plain"))
        assertFalse(parser.endsOpen("200g \"yogurt, gre"))
        assertFalse(parser.endsOpen("200g \"yogurt, "))
        assertFalse(parser.endsOpen("200g \"yogurt,"))
        assertTrue(parser.endsOpen("200g \"yogurt, greek\", "))
    }

    @Test fun aNewlineOrASemicolonStillBreaksInsideAnUnclosedQuote() {
        assertEquals(listOf("200g \"yogurt, gre", "1 banana"), parser.split("200g \"yogurt, gre\n1 banana"))
        assertEquals(listOf("200g \"yogurt, gre", "1 banana"), parser.split("200g \"yogurt, gre; 1 banana"))
        // The quote ended at the break: a comma on the next line splits as it always does.
        assertEquals(listOf("200g \"yogurt", "1 banana", "2 eggs"), parser.split("200g \"yogurt\n1 banana, 2 eggs"))
        assertTrue(parser.endsOpen("200g \"yogurt\n"))
    }

    @Test fun wordSeparatorsInsideQuotesDoNotSplit() {
        assertEquals(listOf("\"ben & jerry\"", "1 banana"), parser.split("\"ben & jerry\" + 1 banana"))
        assertEquals(listOf("\"mac and cheese\"", "1 banana"), parser.split("\"mac and cheese\", 1 banana"))
        assertEquals(listOf("\"хляб и масло\"", "2 eggs"), parser.split("\"хляб и масло\" and 2 eggs"))
        assertEquals(listOf("\"a + b\"", "1 banana"), parser.split("\"a + b\" & 1 banana"))
        assertFalse(parser.hasWordSeparator("\"mac and cheese\""))
        assertTrue(parser.hasWordSeparator("mac and cheese"))
    }

    @Test fun aQuantityInsideAQuotedNameIsPartOfTheName() {
        assertEquals(listOf("\"protein 2 bar\"", "1 banana"), parser.split("\"protein 2 bar\", 1 banana"))
        val parsed = parser.parse("\"protein 2 bar\"")
        assertNull(parsed.amount)
        assertEquals("protein 2 bar", parsed.foodPhrase)
    }

    @Test fun apostrophesAreNeverQuotes() {
        assertEquals(listOf("Sainsbury's yogurt", "2 eggs"), parser.split("Sainsbury's yogurt, 2 eggs"))
        assertEquals(listOf("’yogurt", "greek’"), parser.split("’yogurt, greek’"))
        assertEquals(listOf("‘yogurt", "greek’"), parser.split("‘yogurt, greek’"))
        assertEquals(listOf("'yogurt", "greek'"), parser.split("'yogurt, greek'"))
        assertTrue(QuotedNames.ranges("Ben & Jerry's, 'x', ’y’").isEmpty())
    }

    @Test fun anInchMarkNeverOpensOrClosesAQuote() {
        assertTrue(QuotedNames.ranges("12\" pizza, 1 banana").isEmpty())
        assertEquals(listOf("12\" pizza", "1 banana"), parser.split("12\" pizza, 1 banana"))
        assertEquals(listOf("1 13\" long banana", "2 eggs"), parser.split("1 13\" long banana, 2 eggs"))
        assertTrue(QuotedNames.ranges("2 13-15\" diameter pizza, 2-1/2\" thick cake").isEmpty())
        // Inside an open quote an inch mark followed by a letter or a digit closes nothing: the quote stays open to the end of the line.
        assertEquals(listOf("\"yogurt 12\"x, pizza"), parser.split("\"yogurt 12\"x, pizza"))
        assertEquals(listOf("\"yogurt 12\"3, pizza"), parser.split("\"yogurt 12\"3, pizza"))
        // A closing quote must not run into a letter.
        assertEquals(listOf("\"yogurt\"s, x"), parser.split("\"yogurt\"s, x"))
    }

    @Test fun aQuotedNameEndingInADigitCloses() {
        assertEquals(listOf("\"vitamin b12\"", "200g oats"), parser.split("\"vitamin b12\", 200g oats"))
        assertEquals(listOf("\"Coke Zero 330\"", "1 banana"), parser.split("\"Coke Zero 330\", 1 banana"))
        assertEquals(listOf("“Omega 3”"), parser.split("“Omega 3”"))
        assertEquals(listOf("“Omega 3”", "1 banana"), parser.split("“Omega 3”, 1 banana"))
        assertEquals(listOf("„Омега 3“", "1 banana"), parser.split("„Омега 3“, 1 banana"))
        assertEquals(listOf("\"vitamin b12\" 200g", "1 banana"), parser.split("\"vitamin b12\" 200g, 1 banana"))
        // End of text, a separator and punctuation after the digit close it too.
        assertEquals(listOf(QuotedRange(0, 13, closed = true)), QuotedNames.ranges("\"vitamin b12\""))
        assertEquals(listOf(QuotedRange(0, 13, closed = true)), QuotedNames.ranges("\"vitamin b12\";"))
        assertEquals(listOf(QuotedRange(0, 13, closed = true)), QuotedNames.ranges("\"vitamin b12\")"))
        assertEquals("vitamin b12", parser.parse("\"vitamin b12\"").foodPhrase)
        // A quote that is still open reads as the name typed so far.
        assertEquals("vitamin b12", QuotedNames.trailingInner("\"vitamin b12"))
        assertEquals("vitamin b12", QuotedNames.trailingInner("\"vitamin b12\""))
        // After a digit a quote still never opens one.
        assertTrue(QuotedNames.ranges("2\"x, y\"3").isEmpty())
    }

    @Test fun aServingNameWithInchesWrittenIntoTheDraftNeverOpensAQuote() {
        val pizza = testFood(
            "pizza", "Pizza", defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE, basisAmount = 1.0, unit = MeasureUnit.PIECE,
            conversions = listOf(
                ServingConversion(MeasureUnit.PIECE, 1.0, "usda", label = "13\" long"),
                ServingConversion(MeasureUnit.PIECE, 1.0, "usda", label = "13-15\" diameter"),
                ServingConversion(MeasureUnit.PIECE, 1.0, "usda", label = "slice (1/8 of 12\" pie)"),
            ),
        )
        val planner = LogPlanner(FoodMatcher(listOf(pizza, banana)))
        listOf("13\" long", "13-15\" diameter", "slice (1/8 of 12\" pie)").forEach { serving ->
            val text = planner.partText(pizza, 1.0, Measure(MeasureUnit.SERVING, serving))
            assertTrue(text, QuotedNames.ranges(text).isEmpty())
            assertEquals(text, listOf(text, "1 banana"), planner.segments("$text, 1 banana").map { it.text }.let { listOf(it.first(), it.last()) })
        }
    }

    @Test fun cyrillicQuotedNameStaysWhole() {
        assertEquals(listOf("200 г \"јогурт, грчки\"", "1 banana"), parser.split("200 г \"јогурт, грчки\", 1 banana"))
        assertEquals("јогурт, грчки", parser.parse("200 г \"јогурт, грчки\"").foodPhrase)
    }

    @Test fun parseStripsTheQuoteMarksButKeepsTheRawText() {
        val parsed = parser.parse("200g \"yogurt, greek\"")
        assertEquals("200g \"yogurt, greek\"", parsed.raw)
        assertEquals("yogurt, greek", parsed.foodPhrase)
        assertEquals(200.0, parsed.amount!!, 0.0)
        assertEquals("yogurt, greek", parser.parse("\"yogurt, greek\"").foodPhrase)
        assertEquals("yogurt, gre", parser.parse("200g \"yogurt, gre").foodPhrase)
    }

    @Test fun draftAndSaveReadTheSameParts() {
        val input = "200g \"yogurt, greek, plain\", 1 banana; \"peppers, raw, nfs\" 50 g"
        val planner = planner()
        val parts = planner.segments(input).map { it.text }
        assertEquals(listOf("200g \"yogurt, greek, plain\"", "1 banana", "\"peppers, raw, nfs\" 50 g"), parts)
        val plan = planner.plan(input)
        assertEquals(listOf("greek", "banana", "peppers"), plan.entries.map { it.food.id })
        assertTrue(plan.unresolved.isEmpty())
        val rows = planner.draft(input, typing = false).rows.filterIsInstance<DraftRow.Entry>().map { it.resolution.raw }
        assertEquals(parts, rows)
        assertEquals(parts, planner.countedParts(input, typing = false))
        assertEquals(200.0, plan.entries.first().amount, 0.0)
    }

    @Test fun theRecipeIngredientsReadAQuotedNameLikeTheLog() {
        val plan = planner().plan("200g \"yogurt, greek, plain\"\n\"peppers, raw, nfs\" 50 g")
        assertEquals(listOf("greek" to 200.0, "peppers" to 50.0), ingredientsOf(plan).map { it.foodId to it.enteredAmount })
    }

    // Across the comma

    @Test fun aPartAfterACommaOffersTheNamesThatRunOn() {
        val view = planner().draft("200g yogurt, gre")
        assertEquals("greek", view.suggestions.first().food.id)
        assertEquals("yogurt, gre", view.catalogueQuery)
        val span = planner().partBeingTyped("200g yogurt, gre")!!
        assertEquals("200g yogurt, gre", span.text)
        assertEquals(0, span.start)
    }

    @Test fun theFoodsStartingWithTheFragmentAloneStayBehindTheAcrossOnes() {
        val ids = planner().draft("200g yogurt, gre").suggestions.map { it.food.id }
        assertEquals("greek", ids.first())
        assertTrue(ids.toString(), "greens" in ids)
    }

    @Test fun tappingOneReplacesTheWholeSpanKeepingTheAmountAndPinsIt() {
        val planner = planner()
        val chosen = planner.choose("2 eggs, 200g yogurt, gre", greek)
        assertEquals("2 eggs, 200 g Yogurt, Greek, plain", chosen.input)
        assertEquals("200 g Yogurt, Greek, plain", chosen.part)
        val pins = DraftPins()
        pins.add(chosen.input, chosen.part!!, greek, 0)
        val keys = pins.keys(chosen.input, ::planner)
        assertEquals(setOf(chosen.part), keys.keys)
        val plan = planner(keys).plan(chosen.input)
        assertEquals(listOf("egg", "greek"), plan.entries.map { it.food.id })
        assertEquals(200.0, plan.entries.last().amount, 0.0)
        assertEquals(MeasureUnit.GRAM, plan.entries.last().unit)
        assertEquals(listOf("2 eggs", "200 g Yogurt, Greek, plain"), planner(keys).segments(chosen.input).map { it.text })
    }

    @Test fun withNoAmountTheNameAndRoomToType() {
        assertEquals("Yogurt, Greek, plain ", planner().choose("yogurt, gre", greek).input)
    }

    @Test fun aLongerNameReadsBackThroughSeveralCommas() {
        val view = planner().draft("peppers, raw, n")
        assertEquals("peppers", view.suggestions.first().food.id)
        assertEquals("Peppers, Raw, NFS".lowercase(), planner().choose("peppers, raw, n", peppers).input.lowercase().trim())
    }

    @Test fun nothingIsOfferedWhenThePartHasAnAmountOrIsAfterASemicolonOrNewline() {
        fun across(input: String) = offersAcross(input)
        assertTrue(across("200g yogurt, gre"))
        assertFalse(across("2 eggs, 1 banana"))
        assertFalse(across("200g yogurt, 3 gre"))
        assertFalse(across("200g yogurt; gre"))
        assertFalse(across("200g yogurt\ngre"))
        assertFalse(across("2 eggs, banana"))
        assertEquals(listOf("2 eggs", "banana"), split("2 eggs, banana"))
    }

    @Test fun aPickedPartBeforeTheCommaIsNotTheStartOfAName() {
        val pinned = LogPlanner(FoodMatcher(foods), pins = mapOf("200 g Yogurt, plain" to plainYogurt))
        assertFalse(pinned.draft("200 g Yogurt, plain, gre").suggestions.any(::runsOn))
    }

    @Test fun scriptsNeverCrossAcrossTheComma() {
        val matcher = FoodMatcher(foods)
        fun ids(head: String, fragment: String) = matcher.startingAs("$head $fragment").map { it.food.id }
        assertEquals(listOf("greek"), ids("yogurt", "gre"))
        assertEquals(listOf("cyr"), ids("јогурт", "грч"))
        assertTrue(ids("јогурт", "grc").isEmpty())
        assertTrue(ids("yogurt", "грч").isEmpty())
        assertTrue(ids("јогурт", "gre").isEmpty())
        assertTrue(offersAcross("200 г јогурт, грч"))
        assertFalse(offersAcross("200 г јогурт, gre"))
    }

    @Test fun theRecipeBoxOffersAndPicksTheSameWay() {
        val planner = planner()
        val view = logDraftViewOf(planner, "75 g cheese\n200g yogurt, gre", committed = false, ready = true)
        assertEquals("greek", view.choices.first().food.id)
        assertEquals("75 g cheese, 200 g Yogurt, Greek, plain", planner.choose("75 g cheese\n200g yogurt, gre", greek).input)
    }

    @Test fun suggestionsForAQuotedPartSearchTheTextInsideTheQuotes() {
        val view = planner().draft("200g \"yogurt, gre")
        assertEquals("greek", view.suggestions.first().food.id)
        assertEquals("yogurt, gre", view.catalogueQuery)
        assertEquals("200 g Yogurt, Greek, plain", planner().choose("200g \"yogurt, gre", greek).input)
        assertEquals("greek", planner().draft("200g \"yogurt, gre\"").choices.first().food.id)
    }

    @Test fun pickedTextKeepsTheTypedAmountWhenTheQuotesDropOutOfTheFoodText() {
        val planner = planner()
        assertEquals("Yogurt, Greek, plain 200g", planner.pickedText("200g \"yogurt, greek\"", greek))
        assertEquals("Yogurt, Greek, plain 200g", planner.pickedText("\"yogurt, greek\" 200g", greek))
        assertEquals("Yogurt, Greek, plain 200g", planner.pickedText("yogurt 200g", greek))
        assertEquals("Yogurt, Greek, plain", planner.pickedText("\"yogurt, greek\"", greek))
    }

    // Pins

    @Test fun aDecimalCommaIsNoBreakForAPin() {
        val a = testFood("a", "Mleko", defaultAmount = 100.0, defaultUnit = MeasureUnit.MILLILITER, unit = MeasureUnit.MILLILITER)
        val b = testFood("b", "Mleko", defaultAmount = 100.0, defaultUnit = MeasureUnit.MILLILITER, unit = MeasureUnit.MILLILITER)
        val matcher = FoodMatcher(listOf(a, b))
        fun planner(keys: Map<String, FoodRecord>) = LogPlanner(matcher, pins = keys)
        val draft = "2,5 l Mleko"
        val pins = DraftPins()
        pins.add(draft, draft, b)
        val keys = pins.keys(draft, ::planner)
        assertEquals(setOf(draft), keys.keys)
        val only = planner(keys).plan(draft).entries.single()
        assertEquals("b", only.food.id)
        assertEquals(2500.0, only.amount, 0.0)
    }

    @Test fun aPinBeforeAQuotedCommaNameFindsItsPart() {
        val a = testFood("a", "Mleko", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
        val b = testFood("b", "Mleko", defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM)
        val ab = food("ab", "A, b")
        val matcher = FoodMatcher(listOf(a, b, ab))
        fun planner(keys: Map<String, FoodRecord>) = LogPlanner(matcher, pins = keys)
        val draft = "200 g Mleko, \"a, b\""
        val pins = DraftPins()
        pins.add(draft, "200 g Mleko", b, 0)
        val keys = pins.keys(draft, ::planner)
        assertEquals(mapOf("200 g Mleko" to b), keys)
        assertEquals(listOf("b", "ab"), planner(keys).plan(draft).entries.map { it.food.id })
    }

    // Names with quote marks of their own (USDA has eleven, and a portion label)

    private val wings = food("wings", "Chicken \"wings\", plain, from precooked")
    private val crazyLabel = "\"Crazy Bread\" stick"
    private val breadsticks = testFood(
        "crazy", "Breadsticks", defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE, basisAmount = 1.0, unit = MeasureUnit.PIECE,
        conversions = listOf(ServingConversion(MeasureUnit.SERVING, 1.0, "usda", label = crazyLabel)),
    )
    private fun quotedPlanner(keys: Map<String, FoodRecord> = emptyMap()) = LogPlanner(FoodMatcher(foods + wings + breadsticks), pins = keys)

    @Test fun aFoodNamedWithQuotesIsRecognisedTypedInFull() {
        val typed = "Chicken \"wings\", plain, from precooked"
        assertEquals(listOf(typed), quotedPlanner().segments(typed).map { it.text })
        assertEquals(listOf("wings"), quotedPlanner().plan(typed).entries.map { it.food.id })
        val withAmount = quotedPlanner().plan("200 g $typed, 1 banana")
        assertEquals(listOf("wings" to 200.0, "banana" to 1.0), withAmount.entries.map { it.food.id to it.amount })
        // Without its quotes too.
        assertEquals(listOf("wings"), quotedPlanner().plan("Chicken wings, plain, from precooked").entries.map { it.food.id })
    }

    @Test fun aFoodNamedWithQuotesIsWrittenAndPinnedAsTheSameFood() {
        val chosen = quotedPlanner().choose("200g chicken, wi", wings)
        assertEquals("200 g Chicken \"wings\", plain, from precooked", chosen.input)
        val pins = DraftPins()
        pins.add(chosen.input, chosen.part!!, wings, 0)
        val keys = pins.keys(chosen.input, ::quotedPlanner)
        assertEquals(mapOf(chosen.part!! to wings), keys)
        val plan = quotedPlanner(keys).plan(chosen.input + ", 1 banana")
        assertEquals(listOf("wings" to 200.0, "banana" to 1.0), plan.entries.map { it.food.id to it.amount })
        assertEquals(listOf(chosen.part, "1 banana"), quotedPlanner(keys).segments(chosen.input + ", 1 banana").map { it.text })
        // The recipe's picked text keeps the amount after the name, and still reads as that food.
        val picked = quotedPlanner().pickedText("200g chicken wings", wings)
        assertEquals("Chicken \"wings\", plain, from precooked 200g", picked)
    }

    @Test fun aPortionLabelWithQuotesIsWrittenAndPinnedAsTheSameFood() {
        listOf(1.0, 2.0).forEach { amount ->
            val text = quotedPlanner().partText(breadsticks, amount, Measure(MeasureUnit.SERVING, crazyLabel))
            val draft = "$text, 1 banana"
            val pins = DraftPins()
            pins.add(draft, text, breadsticks, 0)
            val keys = pins.keys(draft, ::quotedPlanner)
            assertEquals(text, mapOf(text to breadsticks), keys)
            val plan = quotedPlanner(keys).plan(draft)
            assertEquals(text, listOf("crazy" to amount, "banana" to 1.0), plan.entries.map { it.food.id to it.amount })
            assertEquals(text, crazyLabel, plan.entries.first().label)
        }
    }

    // Across the comma, for the USDA catalogue

    private val usdaGreek = food("usda-greek", "Yogurt, Greek, plain")
    private val library = LogPlanner(FoodMatcher(listOf(banana, egg)))

    private class FakeCatalogue(private val answers: Map<String, List<FoodRecord>>) : UsdaCatalogue {
        val asked = mutableListOf<String>()
        override suspend fun search(text: String): List<FoodRecord> = answers[text].orEmpty().also { asked += text }
    }

    private fun search(catalogue: UsdaCatalogue) =
        UsdaPartSearch(catalogue, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, Dispatchers.Unconfined) { }

    @Test fun theCatalogueIsAskedTheJoinedPhraseWhenTheLibraryHasNoSuchFood() {
        val view = library.draft("200g yogurt, gre")
        assertEquals("gre", view.catalogueQuery)
        assertEquals(listOf(CatalogueJoin("yogurt gre", 0, 16)), view.catalogueJoins)
        val usda = search(FakeCatalogue(mapOf("yogurt gre" to listOf(usdaGreek, plainYogurt), "gre" to listOf(greens))))
        val keeps = view.catalogueJoins.map { library.runsOn(it.phrase) }
        usda.typed(view.catalogueQuery, view.catalogueJoins, keeps)
        assertEquals(listOf("usda-greek"), usda.rowsFor(view.catalogueQuery, view.catalogueJoins).map { it.id })
        val join = usda.joinFor(view.catalogueQuery, view.catalogueJoins)!!
        assertEquals("yogurt gre", join.phrase)
        // The pick takes the whole span, keeping the amount typed.
        val input = "200g yogurt, gre"
        val part = SegmentSpan(input.substring(join.start, join.end), join.start, join.end)
        val typed = library.typed(part.text)
        assertEquals(200.0, typed.amount!!, 0.0)
        assertEquals("200 g Yogurt, Greek, plain", replaceDraftPart(input, join.start, join.end, library.partText(usdaGreek, 200.0, MeasureUnit.GRAM)))
    }

    @Test fun withoutARunOnNameTheLastPartIsAskedAsToday() {
        val view = library.draft("2 eggs, banana")
        assertEquals("banana", view.catalogueQuery)
        val catalogue = FakeCatalogue(mapOf("eggs banana" to listOf(usdaGreek), "banana" to listOf(greens)))
        val usda = search(catalogue)
        usda.typed(view.catalogueQuery, view.catalogueJoins, view.catalogueJoins.map { library.runsOn(it.phrase) })
        assertEquals(listOf("greens"), usda.rowsFor(view.catalogueQuery, view.catalogueJoins).map { it.id })
        assertNull(usda.joinFor(view.catalogueQuery, view.catalogueJoins))
        assertEquals(listOf("2 eggs", "banana"), library.segments("2 eggs, banana").map { it.text })
    }

    @Test fun aLibraryFoodThatRunsOnNeedsNoJoinForTheCatalogue() {
        val view = planner().draft("200g yogurt, gre")
        assertEquals("yogurt, gre", view.catalogueQuery)
        assertTrue(view.catalogueJoins.isEmpty())
    }

    @Test fun theRecipeBoxJoinsForTheCatalogueTheSameWay() {
        val view = logDraftViewOf(library, "75 g cheese\n200g yogurt, gre", committed = false, ready = true)
        assertEquals("gre", view.catalogueQuery)
        assertEquals(listOf(CatalogueJoin("yogurt gre", 12, 28)), view.catalogueJoins)
        assertEquals(listOf(CatalogueJoin("eggs banana", 12, 26)), logDraftViewOf(library, "75 g cheese\n2 eggs, banana", committed = false, ready = true).catalogueJoins)
    }

    @Test fun aFragmentThatIsOnlyAStopWordOffersNothingAcrossTheComma() {
        assertFalse(offersAcross("200g yogurt, with"))
        assertFalse(offersAcross("200g yogurt, the"))
        assertTrue(planner().draft("200g yogurt, with").catalogueJoins.isEmpty())
        assertTrue(library.draft("200g yogurt, with").catalogueJoins.isEmpty())
        assertTrue(FoodMatcher(foods).startingAs("yogurt with").isEmpty())
        assertNull(FoodMatcher(foods).runsOn("yogurt with"))
        // A real fragment still offers.
        assertTrue(offersAcross("200g yogurt, gre"))
    }
}
