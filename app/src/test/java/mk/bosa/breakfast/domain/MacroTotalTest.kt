package mk.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A day's macro sum never turns an unknown value into zero (MOB-020). */
class MacroTotalTest {
    @Test fun aKnownAndAnUnknownValueIsAPartialSum() {
        val total = macroTotalOf(listOf(10.0, null))
        assertEquals(MacroTotal(10.0, known = 1, unknown = 1), total)
        assertTrue(total.partial)
        assertFalse(total.allUnknown)
    }

    @Test fun onlyUnknownValuesAreUnknown() {
        val total = macroTotalOf(listOf(null, null))
        assertTrue(total.allUnknown)
        assertFalse(total.partial)
    }

    @Test fun knownValuesAreACompleteSum() {
        val total = macroTotalOf(listOf(10.0, 5.0))
        assertEquals(MacroTotal(15.0, known = 2, unknown = 0), total)
        assertFalse(total.partial)
        assertFalse(total.allUnknown)
    }

    @Test fun anEmptyDayIsZeroNotUnknown() {
        val totals = macroTotalsOf(emptyList())
        listOf(totals.protein, totals.carbohydrates, totals.fat).forEach {
            assertEquals(MacroTotal(0.0, known = 0, unknown = 0), it)
            assertFalse(it.partial)
            assertFalse(it.allUnknown)
        }
        assertFalse(totals.anyPartial)
    }

    @Test fun eachMacroIsFoldedOnItsOwn() {
        val totals = macroTotalsOf(listOf(
            NutritionValues(100.0, 10.0, null, null),
            NutritionValues(50.0, 2.0, null, 1.0),
        ))
        assertEquals(MacroTotal(12.0, 2, 0), totals.protein)
        assertTrue(totals.carbohydrates.allUnknown)
        assertTrue(totals.fat.partial)
        assertEquals(1.0, totals.fat.sum, 0.0)
        assertTrue(totals.anyPartial)
    }
}
