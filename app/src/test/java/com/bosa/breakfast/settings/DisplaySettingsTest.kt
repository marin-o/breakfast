package com.bosa.breakfast.settings

import com.bosa.breakfast.ui.screens.dailyMacroNote
import android.app.UiModeManager
import android.content.res.Configuration
import com.bosa.breakfast.domain.MacroTotal
import com.bosa.breakfast.domain.MacroTotals
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.ui.screens.quantityRateDisplay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.bosa.breakfast.ui.pretty
import com.bosa.breakfast.ui.plain

/** B9 Display group: Theme, Macros on today, Missing values note, Decimal mark. */
class DisplaySettingsTest {
    @After fun pointAgain() { DisplayDecimal.mark = DecimalMark.POINT }

    @Test fun systemFollowsThePhoneAndTheOthersForceTheirMode() {
        assertTrue(darkThemeOf(ThemeChoice.SYSTEM, systemDark = true))
        assertFalse(darkThemeOf(ThemeChoice.SYSTEM, systemDark = false))
        assertFalse(darkThemeOf(ThemeChoice.LIGHT, systemDark = true))
        assertTrue(darkThemeOf(ThemeChoice.DARK, systemDark = false))
    }

    @Test fun thePerAppNightModeMatchesTheChoice() {
        // AUTO is "no override": the app, and its launch window, follow the phone.
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, applicationNightModeOf(ThemeChoice.SYSTEM))
        assertEquals(UiModeManager.MODE_NIGHT_NO, applicationNightModeOf(ThemeChoice.LIGHT))
        assertEquals(UiModeManager.MODE_NIGHT_YES, applicationNightModeOf(ThemeChoice.DARK))
    }

    @Test fun belowApi31OnlyTheNightBitsOfTheActivityAreForced() {
        val normalDark = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
        val normalLight = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
        assertNull(forcedUiMode(normalDark, ThemeChoice.SYSTEM))
        assertEquals(normalLight, forcedUiMode(normalDark, ThemeChoice.LIGHT))
        assertEquals(normalDark, forcedUiMode(normalLight, ThemeChoice.DARK))
        assertEquals(normalDark, forcedUiMode(normalDark, ThemeChoice.DARK))
    }

    @Test fun displayDefaultsAreTodays() {
        assertEquals(ThemeChoice.SYSTEM, enumOf(null, ThemeChoice.SYSTEM))
        assertEquals(DecimalMark.POINT, enumOf("SOMETHING", DecimalMark.POINT))
        assertEquals(DecimalMark.POINT, DisplayDecimal.mark)
        assertEquals("1.5", 1.5.pretty())
    }

    private val partial = MacroTotals(MacroTotal(12.0, known = 1, unknown = 1), MacroTotal(10.0, 2, 0), MacroTotal(3.0, 2, 0))

    @Test fun theMissingValuesNoteShowsOnlyWithMacrosAndNoteShown() {
        assertEquals("? some foods have no value", dailyMacroNote(partial))
        assertNull(dailyMacroNote(partial, showMacros = true, showNote = false))
        // Hiding the macros hides their "?" note too.
        assertNull(dailyMacroNote(partial, showMacros = false, showNote = true))
    }

    @Test fun commaShowsEveryDecimalWithAComma() {
        assertEquals("1.5 g, 0.25 kcal/g", markDecimals("1.5 g, 0.25 kcal/g", DecimalMark.POINT))
        assertEquals("1,5 g, 0,25 kcal/g", markDecimals("1.5 g, 0.25 kcal/g", DecimalMark.COMMA))
        // A full stop that is not between two digits stays.
        assertEquals("Diary exported.", markDecimals("Diary exported.", DecimalMark.COMMA))
        assertEquals("2 eggs, 1 banana", markDecimals("2 eggs, 1 banana", DecimalMark.COMMA))
        DisplayDecimal.mark = DecimalMark.COMMA
        assertEquals("1,5", 1.5.pretty())
        assertEquals("2", 2.0.pretty())
    }

    @Test fun commaIsForShowingOnlyFieldsKeepThePoint() {
        DisplayDecimal.mark = DecimalMark.COMMA
        // A value put in a field the user edits stays a plain number that the field and the parser read.
        assertEquals("1.5", 1.5.plain())
        val entry = MealEntry(
            "e", "oats", "Rolled oats", 1.5, MeasureUnit.GRAM, 1.5, NutritionValues(5.7, null, null, null), "1.5 g", false, 0L,
            resolvedBaseUnit = MeasureUnit.GRAM,
        )
        val line = quantityRateDisplay(entry, null).line
        assertFalse(line, Regex("""\d\.\d""").containsMatchIn(line))
        assertTrue(line, line.contains("1,5"))
    }
}
