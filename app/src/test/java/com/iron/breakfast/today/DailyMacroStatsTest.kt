package com.iron.breakfast.today

import com.iron.breakfast.ui.screens.dailyMacroNote
import com.iron.breakfast.ui.screens.macroStats
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.macroTotalsOf
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
}
