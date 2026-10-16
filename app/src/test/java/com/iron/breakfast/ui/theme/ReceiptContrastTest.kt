package com.iron.breakfast.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG spot checks for the Receipt v6.5 light and dark token pairs. */
class ReceiptContrastTest {
    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val l1 = luminance(a); val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }
    private fun assertAa(name: String, fg: Color, bg: Color) {
        assertTrue("$name contrast %.2f:1 is below 4.5:1".format(ratio(fg, bg)), ratio(fg, bg) >= 4.5)
    }
    @Test fun lightTextPairsMeetAa() {
        assertAa("light ink", LightReceiptColors.ink, LightReceiptColors.paper)
        assertAa("light soft", LightReceiptColors.soft, LightReceiptColors.paper)
        assertAa("light inverse", LightReceiptColors.paper, LightReceiptColors.ink)
    }
    @Test fun darkTextPairsMeetAa() {
        assertAa("dark ink", DarkReceiptColors.ink, DarkReceiptColors.paper)
        assertAa("dark soft", DarkReceiptColors.soft, DarkReceiptColors.paper)
        assertAa("dark inverse", DarkReceiptColors.paper, DarkReceiptColors.ink)
    }
}
