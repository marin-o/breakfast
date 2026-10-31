package com.iron.breakfast.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {
    @Test fun aWeightIsEmptyOrFrom1To500WithEitherDecimalMark() {
        listOf("", "  ", "82.5", "82,5", "1", "500").forEach { assertTrue(weightTextValid(it), it) }
        listOf("0", "-1", "abc", "1.2.3", "0.04", "0.5", "500.5", "8000").forEach { assertFalse(weightTextValid(it), it) }
    }

    @Test fun aMacroTargetIsEmptyOrFrom1To5000() {
        listOf("", "1", "120,5", "5000").forEach { assertTrue(macroTargetTextValid(it), it) }
        listOf("0", "0.04", "5000.5", "8000", "abc").forEach { assertFalse(macroTargetTextValid(it), it) }
    }

    @Test fun aDraftWithABadNumberHasNoValues() {
        assertNull(profileOf(ProfileDraft(current = "0"), ProfileValues()))
        assertNull(profileOf(ProfileDraft(fat = "x"), ProfileValues()))
    }

    @Test fun anEmptyDraftIsAnEmptyProfile() {
        assertEquals(ProfileValues(), profileOf(ProfileDraft(), ProfileValues()))
    }

    @Test fun anUneditedFieldKeepsItsExactNumber() {
        val saved = ProfileValues(currentWeight = 82.123456)
        assertEquals(saved, profileOf(ProfileDraft.of(saved), saved))
    }

    @Test fun typedNumbersAreRead() {
        val values = profileOf(ProfileDraft(name = " Ana ", protein = "120", current = "82,5"), ProfileValues())
        assertEquals(ProfileValues("Ana", proteinTarget = 120.0, currentWeight = 82.5), values)
    }

    @Test fun aPointComesFromAChangedWeightWithTheTickOn() {
        val before = ProfileValues(currentWeight = 80.0)
        val after = ProfileValues(currentWeight = 79.0)
        assertEquals(79.0, curveWeightOf(before, after, addToCurve = true, hasPointToday = true))
        assertNull(curveWeightOf(before, after, addToCurve = false, hasPointToday = true))
        assertNull(curveWeightOf(before, before, addToCurve = true, hasPointToday = true))
        assertNull(curveWeightOf(before, ProfileValues(), addToCurve = true, hasPointToday = false))
    }

    @Test fun theSameWeightMakesAPointOnADayWithoutOne() {
        val same = ProfileValues(currentWeight = 80.0)
        assertEquals(80.0, curveWeightOf(same, same, addToCurve = true, hasPointToday = false))
        assertNull(curveWeightOf(same, same, addToCurve = false, hasPointToday = false))
    }

    @Test fun theTickIsOfferedForAWeightChangedOrWithNoPointToday() {
        val saved = ProfileValues(currentWeight = 80.0)
        assertFalse(curveOffered(ProfileDraft.of(saved), saved, hasPointToday = true))
        assertTrue(curveOffered(ProfileDraft.of(saved), saved, hasPointToday = false))
        assertTrue(curveOffered(ProfileDraft(current = "79"), saved, hasPointToday = true))
        assertFalse(curveOffered(ProfileDraft(current = ""), saved, hasPointToday = false))
    }

    @Test fun pointsListNewestFirst() {
        val points = listOf(WeightPoint("2026-10-01", 80.0, 1L), WeightPoint("2026-10-03", 79.0, 2L), WeightPoint("2026-10-02", 81.0, 3L))
        assertEquals(listOf("2026-10-03", "2026-10-02", "2026-10-01"), newestFirst(points).map { it.dayKey })
    }

    @Test fun curveXIsDaysApart() {
        assertEquals(2.0, curveX("2026-10-03")!! - curveX("2026-10-01")!!)
        assertNull(curveX("not a day"))
    }

    @Test fun aKcalTargetIsEmptyOrFrom1To20000() {
        listOf("", "1", "2000,5", "20000").forEach { assertTrue(kcalTargetTextValid(it), it) }
        listOf("0", "0.5", "20000.5", "abc").forEach { assertFalse(kcalTargetTextValid(it), it) }
        assertNull(profileOf(ProfileDraft(kcal = "20001"), ProfileValues()))
        assertEquals(ProfileValues(kcalTarget = 2000.5), profileOf(ProfileDraft(kcal = "2000,5"), ProfileValues()))
    }

    @Test fun anUneditedKcalTargetKeepsItsExactNumber() {
        val saved = ProfileValues(kcalTarget = 2000.123456)
        assertEquals(saved, profileOf(ProfileDraft.of(saved), saved))
    }

    @Test fun balancedIs30Protein40Carbs30Fat() {
        assertEquals(MacroGrams(150, 200, 67), presetGrams(MacroPreset.BALANCED, 2000.0, null))
    }

    @Test fun highProteinIs40Protein30Carbs30Fat() {
        assertEquals(MacroGrams(200, 150, 67), presetGrams(MacroPreset.HIGH_PROTEIN, 2000.0, null))
    }

    @Test fun lowCarbIs30Protein20Carbs50Fat() {
        assertEquals(MacroGrams(150, 100, 111), presetGrams(MacroPreset.LOW_CARB, 2000.0, null))
    }

    @Test fun kcalAndProteinIsTwoGramsPerKgAndLeavesCarbsAndFatEmpty() {
        assertEquals(MacroGrams(165, null, null), presetGrams(MacroPreset.KCAL_AND_PROTEIN, 2000.0, 82.5))
    }

    @Test fun kcalAndProteinWithNoWeightIsAQuarterOfTheKcal() {
        assertEquals(MacroGrams(125, null, null), presetGrams(MacroPreset.KCAL_AND_PROTEIN, 2000.0, null))
    }

    @Test fun kcalAndProteinWithAWeightNeedsNoKcal() {
        assertEquals(MacroGrams(160, null, null), presetGrams(MacroPreset.KCAL_AND_PROTEIN, null, 80.0))
    }

    @Test fun aPresetThatNeedsKcalHasNoGramsWithoutIt() {
        assertNull(presetGrams(MacroPreset.KCAL_AND_PROTEIN, null, null))
        listOf(MacroPreset.BALANCED, MacroPreset.HIGH_PROTEIN, MacroPreset.LOW_CARB).forEach { assertNull(presetGrams(it, null, 80.0), it.name) }
    }

    @Test fun theWeightDoesNotChangeThePercentPresets() {
        assertEquals(MacroGrams(150, 200, 67), presetGrams(MacroPreset.BALANCED, 2000.0, 80.0))
    }

    @Test fun gramsRoundToTheNearestWholeAndHalfGoesUp() {
        // 25% of 2008 is 502 kcal, 125.5 g; 25% of 2004 is 125.25 g.
        assertEquals(126, presetGrams(MacroPreset.KCAL_AND_PROTEIN, 2008.0, null)?.protein)
        assertEquals(125, presetGrams(MacroPreset.KCAL_AND_PROTEIN, 2004.0, null)?.protein)
        assertEquals(165, presetGrams(MacroPreset.KCAL_AND_PROTEIN, null, 82.3)?.protein)
    }

    @Test fun everyPresetGramIsAValidTargetAtTheEndsOfTheRanges() {
        MacroPreset.entries.forEach { preset ->
            listOf(1.0 to null, 1.0 to 1.0, 20000.0 to null, 20000.0 to 500.0).forEach { (kcal, kg) ->
                val grams = presetGrams(preset, kcal, kg)!!
                listOfNotNull(grams.protein, grams.carbs, grams.fat).forEach { assertTrue(macroTargetValid(it.toDouble()), "$preset $kcal $kg $it") }
            }
        }
    }

    @Test fun aPresetFillsTheDraftFromWhatIsTypedAndTouchesNothingElse() {
        val draft = ProfileDraft(name = "Ana", kcal = "2000,0", current = "80", target = "75")
        assertEquals(draft.copy(protein = "150", carbs = "200", fat = "67"), draft.withPreset(MacroPreset.BALANCED))
    }

    @Test fun kcalAndProteinEmptiesCarbsAndFat() {
        val draft = ProfileDraft(kcal = "2000", carbs = "10", fat = "20")
        assertEquals(draft.copy(protein = "125", carbs = "", fat = ""), draft.withPreset(MacroPreset.KCAL_AND_PROTEIN))
    }

    @Test fun aKcalOrWeightOutOfRangeCountsAsNotSet() {
        assertNull(ProfileDraft(kcal = "0").withPreset(MacroPreset.BALANCED))
        assertNull(ProfileDraft(kcal = "20001", current = "0").withPreset(MacroPreset.KCAL_AND_PROTEIN))
        assertEquals("125", ProfileDraft(kcal = "2000", current = "0").withPreset(MacroPreset.KCAL_AND_PROTEIN)?.protein)
    }
}
