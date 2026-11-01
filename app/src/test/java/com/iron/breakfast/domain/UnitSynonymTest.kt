package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitSynonymTest {
    private val parser = ShorthandParser()
    private val planner = LogPlanner(FoodMatcher(TestFoods.fixtures))

    private fun quantity(input: String) = parser.parse(input).let { Triple(it.amount, it.unit, it.foodPhrase) }

    @Test fun metricWeightsAndVolumesConvertToTheStoredUnits() {
        assertEquals(Triple(1000.0, MeasureUnit.GRAM, "rolled oats"), quantity("1 kg rolled oats"))
        assertEquals(Triple(1500.0, MeasureUnit.GRAM, "rolled oats"), quantity("1.5kg rolled oats"))
        assertEquals(Triple(100.0, MeasureUnit.GRAM, "rolled oats"), quantity("100 gr rolled oats"))
        assertEquals(Triple(100.0, MeasureUnit.GRAM, "rolled oats"), quantity("100 grams rolled oats"))
        assertEquals(Triple(500.0, MeasureUnit.MILLILITER, "oat milk"), quantity("0.5 l oat milk"))
        assertEquals(Triple(500.0, MeasureUnit.MILLILITER, "oat milk"), quantity("0,5 litre oat milk"))
        assertEquals(Triple(200.0, MeasureUnit.MILLILITER, "oat milk"), quantity("2 dl oat milk"))
        assertEquals(Triple(50.0, MeasureUnit.MILLILITER, "oat milk"), quantity("5 cl oat milk"))
        assertEquals(Triple(150.0, MeasureUnit.MILLILITER, "oat milk"), quantity("150 milliliters oat milk"))
    }

    @Test fun pieceSynonymsAllMeanPiece() {
        for (word in listOf("pc", "pcs", "slice", "slices", "piece", "pieces")) {
            assertEquals(word, Triple(3.0, MeasureUnit.PIECE, "bread"), quantity("3 $word bread"))
        }
    }

    @Test fun macedonianUnitsMapToTheStoredUnits() {
        assertEquals(Triple(200.0, MeasureUnit.GRAM, "овесни снегулки"), quantity("200 г овесни снегулки"))
        assertEquals(Triple(200.0, MeasureUnit.GRAM, "овесни снегулки"), quantity("200 гр овесни снегулки"))
        assertEquals(Triple(200.0, MeasureUnit.GRAM, "овесни снегулки"), quantity("200 грам овесни снегулки"))
        assertEquals(Triple(1000.0, MeasureUnit.GRAM, "овесни снегулки"), quantity("1 кг овесни снегулки"))
        assertEquals(Triple(150.0, MeasureUnit.MILLILITER, "овесно млеко"), quantity("150 мл овесно млеко"))
        assertEquals(Triple(1000.0, MeasureUnit.MILLILITER, "овесно млеко"), quantity("1 л овесно млеко"))
        assertEquals(Triple(2.0, MeasureUnit.TABLESPOON, "путер од кикирики"), quantity("2 лажици путер од кикирики"))
        assertEquals(Triple(1.0, MeasureUnit.TEASPOON, "путер од кикирики"), quantity("1 лажиче путер од кикирики"))
        assertEquals(Triple(1.0, MeasureUnit.TEASPOON, "путер од кикирики"), quantity("1 лажичка путер од кикирики"))
        assertEquals(Triple(2.0, MeasureUnit.PIECE, "банана"), quantity("2 парчиња банана"))
        assertEquals(Triple(1.0, MeasureUnit.PIECE, "банана"), quantity("1 парче банана"))
    }

    @Test fun attachedCyrillicUnitsReadToo() {
        assertEquals(Triple(100.0, MeasureUnit.GRAM, "овесни снегулки"), quantity("100гр овесни снегулки"))
    }

    /** Measurements: a cup is a volume unit of 240 ml (owner, 2026-10-29); it used to be no unit at all. */
    @Test fun aCupIsAUnit() {
        assertEquals(MeasureUnit.CUP, parser.parse("1 cup oats").unit)
        assertEquals(MeasureUnit.CUP, parser.parse("2 cups milk").unit)
        assertNull(parser.parse("1 mug oats").unit)
    }

    @Test fun convertedUnitsResolveToTheirFoods() {
        val oats = requireNotNull(planner.resolve("1 kg rolled oats"))
        assertEquals("rolled_oats", oats.food.id)
        assertEquals(1000.0, oats.amount, 0.0)
        assertEquals(MeasureUnit.GRAM, oats.unit)
        val milk = requireNotNull(planner.resolve("0.5 l oat milk"))
        assertEquals("oat_milk", milk.food.id)
        assertEquals(500.0, milk.amount, 0.0)
        assertEquals(true, milk.supported)
        assertEquals("peanut_butter_smooth", planner.resolve("2 лажици путер од кикирики")?.food?.id) // Cyrillic alias
    }
}
