package com.iron.breakfast.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.x contrast spot check for the Receipt v5 token pairs. */
class ReceiptContrastTest {
    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun assertAtLeast(min: Double, name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name contrast %.2f:1 is below %.1f:1".format(r, min), r >= min)
    }

    @Test
    fun textPairsMeetAa() {
        assertAtLeast(4.5, "ink on paper", Ink, Paper)
        assertAtLeast(4.5, "ink-soft on paper", InkSoft, Paper)
        assertAtLeast(4.5, "ink-soft on paper-fresh", InkSoft, PaperFresh)
        assertAtLeast(4.5, "ink-soft on sticker", InkSoft, Sticker)
        assertAtLeast(4.5, "on-ground on ground", OnGround, Ground)
        assertAtLeast(4.5, "on-ground-soft on ground", OnGroundSoft, Ground)
        assertAtLeast(4.5, "on-ground-soft on tray", OnGroundSoft, Tray)
        assertAtLeast(4.5, "on-ground on tray", OnGround, Tray)
        assertAtLeast(4.5, "on-pen on pen-blue", OnPen, PenBlue)
        assertAtLeast(4.5, "pen-tonal-ink on pen-tonal", PenTonalInk, PenTonal)
        assertAtLeast(4.5, "on-ground on print-red", OnGround, PrintRed)
        assertAtLeast(4.5, "on-red-soft on print-red", OnRedSoft, PrintRed)
        assertAtLeast(4.5, "pen-on-dark-ink on tray", PenOnDarkInk, Tray)
        assertAtLeast(4.5, "pen-on-dark-ink on ground", PenOnDarkInk, Ground)
        assertAtLeast(4.5, "nav indicator ink on indicator", NavIndicatorInk, NavIndicator)
        assertAtLeast(4.5, "print-red on paper", PrintRed, Paper)
    }

    @Test
    fun shapesMeetNonTextThreshold() {
        assertAtLeast(3.0, "pen-blue against tray", PenBlue, Tray)
        assertAtLeast(3.0, "pen-on-dark outline against ground", PenOnDark, Ground)
        // box-grey on paper measures 2.78:1; it only outlines the source box whose letter is ink-soft (see design gaps).
    }
}
