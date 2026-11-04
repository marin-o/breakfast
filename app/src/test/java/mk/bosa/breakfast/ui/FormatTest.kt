package mk.bosa.breakfast.ui

import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.macroTotalsOf
import mk.bosa.breakfast.ui.screens.basisLabel
import mk.bosa.breakfast.domain.editableNumber
import mk.bosa.breakfast.ui.screens.macroStats
import mk.bosa.breakfast.ui.screens.previewQuantityLine
import mk.bosa.breakfast.ui.theme.Macro
import mk.bosa.breakfast.ui.theme.Mark
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/** One formatter for each thing written on more than one screen. */
class FormatTest {
    private val partly = NutritionValues(233.0, 8.0, null, 4.5)

    @Test fun anUnknownMacroIsADashEverywhere() {
        assertEquals("—", macroText(null))
    }

    @Test fun aMacroLineReadsAsOneStringWithADotOnEachMacro() {
        val words = macroLineParts(partly)
        assertEquals(listOf("Protein 8 g", "carbs — g", "fat 4.5 g"), words.map { it.text })
        assertEquals("Protein 8 g, carbs — g, fat 4.5 g", words.lineText())
        assertEquals(listOf(Macro.PROTEIN, Macro.CARBS, Macro.FAT).map(Mark::OfMacro), words.map { it.mark })
        val letters = listOf(MacroPart("Per 100 g: 233 kcal")) + macroLetterParts(partly)
        assertEquals(listOf("Per 100 g: 233 kcal", "P 8", "C —", "F 4.5"), letters.map { it.text })
        assertEquals("Per 100 g: 233 kcal, P 8, C —, F 4.5", letters.lineText())
        assertEquals(listOf(null) + listOf(Macro.PROTEIN, Macro.CARBS, Macro.FAT).map(Mark::OfMacro), letters.map { it.mark })
    }

    @Test fun withFibreShownTheMacroLineEndsWithFibre() {
        val withFibre = NutritionValues(233.0, 8.0, null, 4.5, fibre = 3.0)
        val words = macroLineParts(withFibre, fibre = true)
        assertEquals("Protein 8 g, carbs — g, fat 4.5 g, fibre 3 g", words.lineText())
        assertEquals(Mark.Fibre, words.last().mark)
        assertEquals("fibre — g", macroLineParts(partly, fibre = true).last().text)
        // Not shown: the three macros only, whatever the food knows.
        assertEquals(listOf("Protein 8 g", "carbs — g", "fat 4.5 g"), macroLineParts(withFibre).map { it.text })
    }

    @Test fun eachMacroColumnKnowsItsMacro() {
        assertEquals(listOf(Macro.PROTEIN, Macro.CARBS, Macro.FAT).map(Mark::OfMacro), macroStats(233.0, macroTotalsOf(listOf(partly))).map { it.kind })
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
        assertEquals(third, mk.bosa.breakfast.domain.fieldNumber("0.333", third)!!, 0.0)
        assertEquals(0.5, mk.bosa.breakfast.domain.fieldNumber("0.5", third)!!, 0.0)
        assertEquals("0.3333333333333333", mk.bosa.breakfast.domain.fieldText("0.333", third))
        assertEquals("0.5", mk.bosa.breakfast.domain.fieldText("0.5", third))
    }

    @Test fun aTinyValueIsNeverExponentOrZero() {
        assertEquals("0.0005", editableNumber(5.0E-4))
        assertEquals(5.0E-4, mk.bosa.breakfast.domain.parseDecimal(editableNumber(5.0E-4))!!, 0.0)
        assertEquals("0.00003", editableNumber(3.2E-5))
    }

    @Test fun aTypedAmountOfAnUnknownPartUsesThePlural() {
        assertEquals("2 cups", mk.bosa.breakfast.ui.screens.unknownDisplay("2 cups milk").quantity)
        assertEquals("1 cup", mk.bosa.breakfast.ui.screens.unknownDisplay("1 cup milk").quantity)
        assertEquals("2 pieces", mk.bosa.breakfast.ui.screens.unknownDisplay("2 pieces oats").quantity)
        assertEquals("2 tbsp", mk.bosa.breakfast.ui.screens.unknownDisplay("2 tbsp oats").quantity)
    }

    @Test fun aRecipeWithNoIngredientsKnowsNoMacroButAnEmptyDayIsZero() {
        val none = mk.bosa.breakfast.domain.MacroTotal(0.0, 0, 0)
        assertEquals("—" to "g", mk.bosa.breakfast.ui.screens.macroFact(none, noneIsUnknown = true))
        assertEquals("0" to "g", mk.bosa.breakfast.ui.screens.macroFact(none))
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
