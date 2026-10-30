package com.iron.breakfast.ui

import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.macroTotalsOf
import com.iron.breakfast.ui.screens.basisLabel
import com.iron.breakfast.ui.screens.editableNumber
import com.iron.breakfast.ui.screens.kcalBasisLabel
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
        assertEquals("P 8, C —, F 4.5", shortMacroLine(partly))
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
        assertEquals("kcal per 100 ml", kcalBasisLabel(100.0, MeasureUnit.MILLILITER))
    }
}
