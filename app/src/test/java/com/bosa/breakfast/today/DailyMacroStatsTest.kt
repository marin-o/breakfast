package com.bosa.breakfast.today

import com.bosa.breakfast.domain.ProfileValues
import com.bosa.breakfast.ui.screens.dailyMacroNote
import com.bosa.breakfast.ui.screens.kcalTargetLine
import com.bosa.breakfast.ui.screens.macroStats
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.macroTotalsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Daily macro columns: "—" when no entry has the macro, "12 g ?" when some do, and one line when any shows "?". */
class DailyMacroStatsTest {
    private fun stats(vararg values: NutritionValues) = macroTotalsOf(values.toList()).let { macros ->
        macroStats(values.sumOf { it.calories }, macros) to dailyMacroNote(macros)
    }

    @Test fun knownMacrosReadAsToday() {
        val (columns, note) = stats(NutritionValues(200.0, 10.0, 20.0, 5.0), NutritionValues(100.0, 2.0, 4.0, 1.0))
        assertEquals(listOf("12" to "g", "24" to "g", "6" to "g"), columns.map { it.value to it.unit })
        assertEquals(12.0 * 4.0 / 300.0, columns[0].share.toDouble(), 1e-6)
        assertNull(note)
    }

    @Test fun aPartlyKnownMacroReadsItsKnownSumWithAQuestionMarkAndTheLineShows() {
        val (columns, note) = stats(NutritionValues(200.0, 10.0, 20.0, 5.0), NutritionValues(100.0, null, 4.0, 1.0))
        assertEquals("10" to "g ?", columns[0].value to columns[0].unit)
        assertEquals("24" to "g", columns[1].value to columns[1].unit)
        assertEquals("? some foods have no value", note)
    }

    @Test fun aMacroNoEntryHasReadsADashAndNeedsNoLine() {
        val (columns, note) = stats(NutritionValues(200.0, null, 20.0, 5.0), NutritionValues(100.0, null, 4.0, 1.0))
        assertEquals("—" to "g", columns[0].value to columns[0].unit)
        assertEquals(0f, columns[0].share)
        assertNull(note)
    }

    @Test fun anEmptyDayReadsZeroWithoutAQuestionMark() {
        val (columns, note) = stats()
        assertEquals(List(3) { "0" to "g" }, columns.map { it.value to it.unit })
        assertEquals(List(3) { 0f }, columns.map { it.share })
        assertNull(note)
    }

    private val oneFood = macroTotalsOf(listOf(NutritionValues(200.0, 28.6, 10.0, 5.0)))

    @Test fun withoutATargetTheBarIsTheShareOfCalories() {
        val protein = macroStats(200.0, oneFood).first()
        assertEquals("g", protein.unit)
        assertNull(protein.target)
        assertEquals((28.6 * 4 / 200).toFloat(), protein.share, 0.001f)
    }

    @Test fun aKcalTargetReadsLikeAMacroTargetInWholeKcal() {
        assertEquals("/ 2000 kcal", kcalTargetLine(ProfileValues(kcalTarget = 2000.0)))
        assertEquals("/ 2101 kcal", kcalTargetLine(ProfileValues(kcalTarget = 2100.6)))
    }

    @Test fun noKcalTargetHasNoLine() {
        assertNull(kcalTargetLine(ProfileValues(proteinTarget = 120.0)))
    }

    @Test fun withATargetTheBarFillsTowardIt() {
        val protein = macroStats(200.0, oneFood, ProfileValues(proteinTarget = 120.0)).first()
        assertEquals("28.6", protein.value)
        assertEquals("g", protein.unit)
        assertEquals("/ 120 g", protein.target)
        assertEquals((28.6 / 120).toFloat(), protein.share, 0.001f)
    }

    @Test fun overTheTargetTheBarIsFullAtMost() {
        assertEquals(1f, macroStats(200.0, oneFood, ProfileValues(proteinTarget = 10.0)).first().share, 0f)
    }

    @Test fun aTargetChangesOnlyItsOwnMacro() {
        val (_, carbs, fat) = macroStats(200.0, oneFood, ProfileValues(proteinTarget = 10.0))
        assertNull(carbs.target)
        assertNull(fat.target)
    }

    @Test fun aPartlyKnownMacroKeepsItsMarkOnTheAmountLine() {
        val partly = macroTotalsOf(listOf(NutritionValues(100.0, 10.0, null, 1.0), NutritionValues(100.0, null, 2.0, 1.0)))
        val protein = macroStats(200.0, partly, ProfileValues(proteinTarget = 120.0)).first()
        assertEquals("g ?", protein.unit)
        assertEquals("/ 120 g", protein.target)
    }
}
