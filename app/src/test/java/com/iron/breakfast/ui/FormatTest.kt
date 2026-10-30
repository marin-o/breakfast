package com.iron.breakfast.ui

import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.macroTotalsOf
import com.iron.breakfast.ui.screens.basisLabel
import com.iron.breakfast.domain.editableNumber
import com.iron.breakfast.ui.screens.macroStats
import com.iron.breakfast.ui.screens.previewQuantityLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/** One formatter for each thing written on more than one screen. */
class FormatTest {
    private val partly = NutritionValues(233.0, 8.0, null, 4.5)

    @Test fun anUnknownMacroIsADashEverywhere() {
        assertEquals("—", macroText(null))
        assertEquals("Protein 8 g, carbs — g, fat 4.5 g", macroLine(partly))
    }

    @Test fun oneEntrysMacrosReadAsADaysDo() {
        val (protein, carbs, fat) = macroStats(233.0, macroTotalsOf(listOf(partly)))
        assertEquals("8" to "g", protein.value to protein.unit)
        assertEquals("—" to "g", carbs.value to carbs.unit)
        assertEquals("4.5" to "g", fat.value to fat.unit)
        assertEquals((8.0 * 4 / 233).toFloat(), protein.share)
        assertEquals((4.5 * 9 / 233).toFloat(), fat.share)
    }

    @Test fun aDayIsWrittenOneWay() {
        assertEquals("Wed 14 Oct", shortDateLabel("2026-10-14"))
        assertEquals("not a day", shortDateLabel("not a day"))
    }

    @Test fun aClockReadsInTheGivenZone() {
        assertEquals("01:00", clockLabel(0L, ZoneOffset.ofHours(1)))
    }

    @Test fun aFieldPreFillsExactly() {
        assertEquals("0.25", editableNumber(0.25))
        assertEquals("12.5", editableNumber(12.5))
        assertEquals("100", editableNumber(100.0))
        assertEquals("", editableNumber(null))
        assertEquals("0.125", editableNumber(0.125))
        assertEquals("0.333", editableNumber(1.0 / 3))
        assertEquals("0", editableNumber(0.0))
    }

    @Test fun aFieldLeftAsItWasStandsForTheExactValue() {
        val third = 1.0 / 3
        assertEquals(third, com.iron.breakfast.domain.fieldNumber("0.333", third)!!, 0.0)
        assertEquals(0.5, com.iron.breakfast.domain.fieldNumber("0.5", third)!!, 0.0)
        assertEquals("0.3333333333333333", com.iron.breakfast.domain.fieldText("0.333", third))
        assertEquals("0.5", com.iron.breakfast.domain.fieldText("0.5", third))
    }

    @Test fun aTinyValueIsNeverExponentOrZero() {
        assertEquals("0.0005", editableNumber(5.0E-4))
        assertEquals(5.0E-4, com.iron.breakfast.domain.parseDecimal(editableNumber(5.0E-4))!!, 0.0)
        assertEquals("0.00003", editableNumber(3.2E-5))
    }

    @Test fun aTypedAmountOfAnUnknownPartUsesThePlural() {
        assertEquals("2 cups", com.iron.breakfast.ui.screens.unknownDisplay("2 cups milk").quantity)
        assertEquals("1 cup", com.iron.breakfast.ui.screens.unknownDisplay("1 cup milk").quantity)
        assertEquals("2 pieces", com.iron.breakfast.ui.screens.unknownDisplay("2 pieces oats").quantity)
        assertEquals("2 tbsp", com.iron.breakfast.ui.screens.unknownDisplay("2 tbsp oats").quantity)
    }

    @Test fun aRecipeWithNoIngredientsKnowsNoMacroButAnEmptyDayIsZero() {
        val none = com.iron.breakfast.domain.MacroTotal(0.0, 0, 0)
        assertEquals("—" to "g", com.iron.breakfast.ui.screens.macroFact(none, noneIsUnknown = true))
        assertEquals("0" to "g", com.iron.breakfast.ui.screens.macroFact(none))
    }

    @Test fun plainsAgreeWithTheAmount() {
        assertEquals("2 pieces", previewQuantityLine(2.0, MeasureUnit.PIECE))
        assertEquals("2 cups", previewQuantityLine(2.0, MeasureUnit.CUP))
        assertEquals("1 cup", previewQuantityLine(1.0, MeasureUnit.CUP))
        assertEquals("1.5 portions", previewQuantityLine(1.5, MeasureUnit.PORTION))
        assertEquals("2 tbsp", previewQuantityLine(2.0, MeasureUnit.TABLESPOON))
    }

    @Test fun aBasisReadsFromOneFamily() {
        assertEquals("Per 100 ml", basisLabel(100.0, MeasureUnit.MILLILITER))
    }
}
