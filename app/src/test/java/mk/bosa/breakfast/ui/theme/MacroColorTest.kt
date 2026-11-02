package mk.bosa.breakfast.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each macro has its own approved colour in each theme and palette, and a palette changes nothing else. */
class MacroColorTest {
    private fun colors(palette: BreakfastColors) = Macro.entries.map(palette::macroColor)

    @Test fun lightMacros() {
        assertEquals(listOf(Color(0xFF2A63B8), Color(0xFFAB1F7F), Color(0xFFB08600)), colors(LightBreakfastColors))
    }

    @Test fun darkMacros() {
        assertEquals(listOf(Color(0xFF6AA5F0), Color(0xFFF36BC4), Color(0xFFE3B52E)), colors(DarkBreakfastColors))
    }

    private fun marks(dark: Boolean, palette: MacroPalette) = colors(breakfastColors(dark, palette))
    private fun both(check: (dark: Boolean, base: BreakfastColors) -> Unit) {
        check(false, LightBreakfastColors)
        check(true, DarkBreakfastColors)
    }

    @Test fun standardIsTheThemeAsItWas() {
        assertEquals(LightBreakfastColors, breakfastColors(false, MacroPalette.STANDARD))
        assertEquals(DarkBreakfastColors, breakfastColors(true, MacroPalette.STANDARD))
    }

    @Test fun protanopiaColours() {
        assertEquals(listOf(Color(0xFF2F6FD0), Color(0xFF2A1A3E), Color(0xFFB8890A)), marks(false, MacroPalette.PROTANOPIA))
        assertEquals(listOf(Color(0xFF5C9CF5), Color(0xFFF2F0FA), Color(0xFFE0AE20)), marks(true, MacroPalette.PROTANOPIA))
    }

    @Test fun deuteranopiaColours() {
        assertEquals(listOf(Color(0xFF2F6FD0), Color(0xFF2A1A3E), Color(0xFFB8890A)), marks(false, MacroPalette.DEUTERANOPIA))
        assertEquals(listOf(Color(0xFF5C9CF5), Color(0xFFF2F0FA), Color(0xFFE0AE20)), marks(true, MacroPalette.DEUTERANOPIA))
    }

    @Test fun protanopiaEqualsDeuteranopia() = both { dark, _ ->
        assertEquals(breakfastColors(dark, MacroPalette.PROTANOPIA), breakfastColors(dark, MacroPalette.DEUTERANOPIA))
    }

    @Test fun tritanopiaColours() {
        assertEquals(listOf(Color(0xFF0F7C8C), Color(0xFFD23A3A), Color(0xFF3B2A20)), marks(false, MacroPalette.TRITANOPIA))
        assertEquals(listOf(Color(0xFF3FC1D0), Color(0xFFF2736E), Color(0xFFE8DDD0)), marks(true, MacroPalette.TRITANOPIA))
    }

    @Test fun monochromeIsInk() = both { dark, base ->
        assertEquals(List(3) { base.ink }, marks(dark, MacroPalette.MONOCHROME))
    }

    @Test fun okAndBadNeverChange() = both { dark, base ->
        MacroPalette.entries.forEach { palette ->
            assertEquals(base.ok, breakfastColors(dark, palette).ok)
            assertEquals(base.bad, breakfastColors(dark, palette).bad)
        }
    }

    @Test fun aPaletteChangesOnlyTheMacroColours() = both { dark, base ->
        MacroPalette.entries.forEach { palette ->
            assertEquals(base, breakfastColors(dark, palette).copy(protein = base.protein, carbs = base.carbs, fat = base.fat))
        }
    }

    /** The swipe Delete's label is not a macro mark, so a palette leaves it as it is. */
    @Test fun theSwipeLabelIgnoresThePalette() = both { dark, base ->
        MacroPalette.entries.forEach { palette ->
            assertEquals(swipeLabelOnBad(base), swipeLabelOnBad(breakfastColors(dark, palette)))
        }
    }

    @Test fun theOrderIsProteinCarbsFat() {
        assertEquals(listOf(Macro.PROTEIN, Macro.CARBS, Macro.FAT), Macro.entries)
    }
}
