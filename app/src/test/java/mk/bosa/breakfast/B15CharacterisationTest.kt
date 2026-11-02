package mk.bosa.breakfast

import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.displayLabel
import mk.bosa.breakfast.domain.parseStrictAmountUnit
import mk.bosa.breakfast.resolve.resolvePosition
import mk.bosa.breakfast.ui.plain
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behaviour B15 moves out of `BreakfastViewModel` and the screens, pinned before the move (read from 6e1e42f).
 */
class B15CharacterisationTest {
    /** `resolveQueuePosition` in ScanScreens.kt at 6e1e42f, the review sheet's own copy of the "n of m" rule. */
    private fun sheetPosition(total: Int, remaining: Int): String {
        val shown = maxOf(total, remaining)
        return "${(shown - remaining + 1).coerceIn(1, maxOf(1, shown))} of $shown"
    }

    @Test fun theReviewSheetsQueuePositionIsResolvePosition() {
        for (total in 0..6) for (remaining in 1..6) {
            val (position, shown) = resolvePosition(total, remaining)
            assertEquals("total $total, remaining $remaining", sheetPosition(total, remaining), "$position of $shown")
        }
    }

    /**
     * Quick edit saves the text "amount unit" (the sheet's amount, trimmed, and the chosen unit's label), which the
     * engine reads with [parseStrictAmountUnit]; before B15 it went through the Library bar's `input`. Every unit the
     * sheet offers reads back as itself, and the text `beginQuickEdit` started from reads back as the entry's values.
     */
    @Test fun theQuickEditTextReadsBackAsTheAmountAndUnit() {
        // A serving is typed by its name, not a label of its own (ServingsTest covers it).
        for (unit in MeasureUnit.entries - MeasureUnit.UNKNOWN - MeasureUnit.SERVING) {
            val parsed = parseStrictAmountUnit("${"1.5".trim()} ${unit.displayLabel()}")
            assertEquals(unit.name, 1.5, parsed?.amount)
            assertEquals(unit.name, unit, parsed?.unit)
            assertEquals(unit.name, 120.0, parseStrictAmountUnit("${120.0.plain()} ${unit.displayLabel()}")?.amount)
        }
        // An amount left empty is refused by the engine, not by the sheet.
        assertEquals(null, parseStrictAmountUnit(" ${MeasureUnit.GRAM.displayLabel()}"))
    }
}
