package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MOB-029(e): a quantity glued to the end of a food word ("mleko200g") reads like its spaced form ("mleko 200g"). */
class GluedQuantityTest {
    private val mleko = FoodRecord(
        id = "custom_mleko", name = "Mleko", aliases = emptyList(), category = "Dairy", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(60.0, 3.2, 4.7, 3.2)),
        defaultAmount = 200.0, defaultUnit = MeasureUnit.MILLILITER,
        conversions = listOf(ServingConversion(MeasureUnit.GRAM, 1.0, "1 g")),
    )
    private val vitamin = FoodRecord(
        id = "custom_b12", name = "Vitamin B12 tablet", aliases = emptyList(), category = "Supplement", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(1.0, MeasureUnit.PIECE, NutritionValues(0.0, 0.0, 0.0, 0.0)),
        defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE,
    )
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures + mleko + vitamin))

    private data class Saved(val foodId: String, val amount: Double, val unit: MeasureUnit)

    private fun saved(input: String) = planner.plan(input).entries.map { Saved(it.food.id, it.amount, it.unit) }

    private fun assertReadsLikeSpaced(glued: String, spaced: String) {
        val expected = saved(spaced)
        assertTrue("$spaced resolves", expected.isNotEmpty())
        assertEquals(glued, expected, saved(glued))
        assertEquals(glued, planner.plan(spaced).unresolved, planner.plan(glued).unresolved)
    }

    @Test fun aGluedQuantityReadsLikeTheSpacedForm() {
        assertReadsLikeSpaced("mleko200g", "mleko 200g")
        assertReadsLikeSpaced("banana2", "banana 2")
        assertReadsLikeSpaced("oats60g", "oats 60g")
        assertEquals(listOf(Saved("custom_mleko", 200.0, MeasureUnit.GRAM)), saved("mleko200g"))
        assertEquals(listOf(Saved("banana", 2.0, MeasureUnit.PIECE)), saved("banana2"))
        assertEquals(listOf(Saved("rolled_oats", 60.0, MeasureUnit.GRAM)), saved("oats60g"))
    }

    @Test fun cyrillicGluedQuantitiesRead() {
        assertReadsLikeSpaced("млеко200мл", "млеко 200мл")
        assertReadsLikeSpaced("банана2", "банана 2")
        assertEquals(listOf(Saved("banana", 2.0, MeasureUnit.PIECE)), saved("банана2"))
        assertEquals(listOf(Saved("custom_home_milk", 200.0, MeasureUnit.MILLILITER)), saved("млеко200мл"))
        assertEquals(listOf(Saved("custom_home_milk", 1500.0, MeasureUnit.MILLILITER)), saved("млеко1,5л"))
        // Домашно млеко has no gram conversion: "млеко200г" reads as "млеко 200г", which does not log either.
        assertEquals(TypedPart("млеко", 200.0, MeasureUnit.GRAM), planner.typed("млеко200г"))
        assertEquals(emptyList<Saved>(), saved("млеко 200г"))
        assertEquals(listOf("млеко200г"), planner.plan("млеко200г").unresolved)
    }

    @Test fun aGluedCommaDecimalReads() {
        assertReadsLikeSpaced("mleko0,5l", "mleko 0,5l")
        assertEquals(listOf(Saved("custom_mleko", 500.0, MeasureUnit.MILLILITER)), saved("mleko0,5l"))
        assertEquals(listOf(Saved("custom_mleko", 1500.0, MeasureUnit.MILLILITER)), saved("mleko1.5l"))
    }

    @Test fun gluedPartsSplitLikeSpacedOnes() {
        assertEquals(listOf("oats60g", "banana2"), planner.segments("oats60g banana2").map { it.text })
        assertEquals(planner.segments("oats 60g milk").size, planner.segments("oats60g milk").size)
        assertReadsLikeSpaced("oats60g milk", "oats 60g milk")
        assertReadsLikeSpaced("2 eggs, mleko200g", "2 eggs, mleko 200g")
    }

    @Test fun aNameWithItsOwnNumberIsNotSplit() {
        assertEquals(listOf(Saved("custom_b12", 1.0, MeasureUnit.PIECE)), saved("Vitamin B12 tablet"))
        assertEquals(listOf(Saved("custom_b12", 2.0, MeasureUnit.PIECE)), saved("2 vitamin b12 tablet"))
        assertEquals(listOf("Vitamin B12 tablet", "1 banana"), planner.segments("Vitamin B12 tablet 1 banana").map { it.text })
        assertEquals("Vitamin B12 tablet", planner.phraseOf("Vitamin B12 tablet"))
    }

    @Test fun aMixedScriptGlueMatchesNothing() {
        listOf("mleko200г", "млеко200g", "bananа2", "банана2g", "домашно млеко200ml").forEach { input ->
            assertEquals(input, emptyList<Saved>(), saved(input))
            assertEquals(input, listOf(input), planner.plan(input).unresolved)
        }
    }

    @Test fun aGlueThatDoesNotResolveStaysOneUnresolvedPart() {
        assertEquals(listOf("xyzzy200g"), planner.plan("xyzzy200g").unresolved)
        assertEquals(listOf("mleko200cups"), planner.plan("mleko200cups").unresolved)
    }

    @Test fun withoutAProbeTheParserLeavesAGlueAlone() {
        val parsed = ShorthandParser().parse("mleko200g")
        assertEquals(null, parsed.amount)
        assertEquals("mleko200g", parsed.foodPhrase)
    }

    @Test fun theLibraryQueryAndPickedTextKeepTheTypedAmount() {
        assertEquals("mleko", planner.libraryQueryFor("mleko200g"))
        assertEquals("Mleko 200g", planner.pickedText("mleko200g", mleko))
        assertEquals("200 g Mleko", planner.chosenText("mleko200g", mleko))
    }
}
