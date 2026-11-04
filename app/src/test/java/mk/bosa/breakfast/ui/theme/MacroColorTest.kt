package mk.bosa.breakfast.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
            assertEquals(base, breakfastColors(dark, palette).copy(protein = base.protein, carbs = base.carbs, fat = base.fat, fibre = base.fibre))
        }
    }

    /** The swipe Delete's label is not a macro mark, so a palette leaves it as it is. */
    @Test fun theSwipeLabelIgnoresThePalette() = both { dark, base ->
        MacroPalette.entries.forEach { palette ->
            assertEquals(swipeLabelOnBad(base), swipeLabelOnBad(breakfastColors(dark, palette)))
        }
    }

    private fun fibre(dark: Boolean, palette: MacroPalette) = breakfastColors(dark, palette).markColor(Mark.Fibre)

    @Test fun fibreColours() {
        assertEquals(Color(0xFF3A6230), fibre(false, MacroPalette.STANDARD))
        assertEquals(Color(0xFFCFE08A), fibre(true, MacroPalette.STANDARD))
        listOf(MacroPalette.PROTANOPIA, MacroPalette.DEUTERANOPIA).forEach {
            assertEquals(Color(0xFF1F4D2B), fibre(false, it))
            assertEquals(Color(0xFF5E8070), fibre(true, it))
        }
        assertEquals(Color(0xFF5A2DB4), fibre(false, MacroPalette.TRITANOPIA))
        assertEquals(Color(0xFF8C5AF0), fibre(true, MacroPalette.TRITANOPIA))
    }

    @Test fun monochromeFibreIsInk() = both { dark, base ->
        assertEquals(base.ink, fibre(dark, MacroPalette.MONOCHROME))
    }

    /** A macro's mark is its macro's colour; fibre's is its own, never the action green. */
    @Test fun marksReadTheirColour() = both { dark, base ->
        MacroPalette.entries.forEach { palette ->
            val colors = breakfastColors(dark, palette)
            Macro.entries.forEach { assertEquals(colors.macroColor(it), colors.markColor(Mark.OfMacro(it))) }
            if (palette != MacroPalette.MONOCHROME) assertNotEquals(base.ok, colors.markColor(Mark.Fibre))
        }
    }

    /** Fibre's bar is a small mark: at least 3:1 on `bg` in every palette and theme. */
    @Test fun fibreHasContrastOnBg() = both { dark, base ->
        fun luminance(color: Color) = listOf(color.red, color.green, color.blue)
            .map { if (it <= 0.04045f) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            .let { (r, g, b) -> 0.2126 * r + 0.7152 * g + 0.0722 * b }
        MacroPalette.entries.forEach { palette ->
            val (high, low) = listOf(luminance(fibre(dark, palette)), luminance(base.bg)).sortedDescending()
            assertTrue("$palette dark=$dark", (high + 0.05) / (low + 0.05) >= 3.0)
        }
    }

    @Test fun theOrderIsProteinCarbsFat() {
        assertEquals(listOf(Macro.PROTEIN, Macro.CARBS, Macro.FAT), Macro.entries)
    }
}
