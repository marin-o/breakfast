package com.bosa.breakfast.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each macro has its own approved colour in each theme. */
class MacroColorTest {
    private fun colors(palette: BreakfastColors) = Macro.entries.map(palette::macroColor)

    @Test fun lightMacros() {
        assertEquals(listOf(Color(0xFF2A63B8), Color(0xFFAB1F7F), Color(0xFFB08600)), colors(LightBreakfastColors))
    }

    @Test fun darkMacros() {
        assertEquals(listOf(Color(0xFF6AA5F0), Color(0xFFF36BC4), Color(0xFFE3B52E)), colors(DarkBreakfastColors))
    }

    @Test fun theOrderIsProteinCarbsFat() {
        assertEquals(listOf(Macro.PROTEIN, Macro.CARBS, Macro.FAT), Macro.entries)
    }
}
