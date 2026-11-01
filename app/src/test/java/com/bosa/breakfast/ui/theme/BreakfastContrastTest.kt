package com.bosa.breakfast.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG spot checks for the Monochrome light and dark token pairs. */
class BreakfastContrastTest {
    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val l1 = luminance(a); val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }
    private fun assertAa(name: String, fg: Color, bg: Color) = assertAtLeast(name, fg, bg, 4.5)
    private fun assertAtLeast(name: String, fg: Color, bg: Color, minimum: Double) {
        assertTrue("$name contrast %.2f:1 is below %.1f:1".format(ratio(fg, bg), minimum), ratio(fg, bg) >= minimum)
    }
    @Test fun lightTextPairsMeetAa() {
        assertAa("light ink", LightBreakfastColors.ink, LightBreakfastColors.bg)
        assertAa("light soft", LightBreakfastColors.soft, LightBreakfastColors.bg)
        assertAa("light inverse", LightBreakfastColors.bg, LightBreakfastColors.ink)
    }
    @Test fun darkTextPairsMeetAa() {
        assertAa("dark ink", DarkBreakfastColors.ink, DarkBreakfastColors.bg)
        assertAa("dark soft", DarkBreakfastColors.soft, DarkBreakfastColors.bg)
        assertAa("dark inverse", DarkBreakfastColors.bg, DarkBreakfastColors.ink)
    }

    /**
     * MOB-035, semantic pairs. `bad` is text (field errors, "Could not ..." lines, destructive buttons), so 4.5:1.
     * `ok` fills the constructive button: its label is text (4.5:1) and the fill against the page is a non-text
     * boundary (3:1, WCAG 1.4.11).
     */
    @Test fun semanticPairsMeetTheirThresholds() {
        for ((theme, c) in listOf("light" to LightBreakfastColors, "dark" to DarkBreakfastColors)) {
            assertAa("$theme bad text", c.bad, c.bg)
            assertAa("$theme onOk label", c.onOk, c.ok)
            assertAtLeast("$theme ok fill", c.ok, c.bg, 3.0)
        }
    }

    /** The swipe Delete label on its `bad` fill is 16sp text: 4.5:1 in both modes; light mode keeps its white label. */
    @Test fun swipeLabelOnBadMeetsAa() {
        assertAa("light swipe label on bad", swipeLabelOnBad(LightBreakfastColors), LightBreakfastColors.bad)
        assertAa("dark swipe label on bad", swipeLabelOnBad(DarkBreakfastColors), DarkBreakfastColors.bad)
        assertEquals(LightBreakfastColors.cameraMark, swipeLabelOnBad(LightBreakfastColors))
        assertEquals(DarkBreakfastColors.bg, swipeLabelOnBad(DarkBreakfastColors))
    }

    /**
     * MOB-035, `line`. It is about 1.25:1 on `bg`, below the 3:1 a meaningful non-text indicator needs, so it may only
     * separate or outline at rest (hairlines, tracks, unselected and unfocused borders). Every state is drawn in ink or
     * bad over it (selected choice, focused field, error ring), and ink must stand 3:1 from line. The 1.2:1 floor only
     * keeps the hairline visible; raising `line` to 3:1 would be a design change (owner).
     */
    @Test fun lineIsOnlyDecorativeAndStatesStandOutFromIt() {
        for ((theme, c) in listOf("light" to LightBreakfastColors, "dark" to DarkBreakfastColors)) {
            assertAtLeast("$theme line hairline", c.line, c.bg, 1.2)
            assertAtLeast("$theme ink state over line", c.ink, c.line, 3.0)
            assertAtLeast("$theme bad ring", c.bad, c.bg, 3.0)
        }
    }
}
