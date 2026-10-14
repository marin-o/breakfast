package com.iron.breakfast.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTypographyTest {
    @Test
    fun singleWidthStartsAtAccessibilityFontThreshold() {
        assertFalse(usesSingleWidthAt(1.29f))
        assertTrue(usesSingleWidthAt(1.3f))
        assertTrue(usesSingleWidthAt(1.5f))
    }

    @Test
    fun doubleWidthNeedsShortTextAndNormalFontScale() {
        assertTrue(useDoubleWidth(1.0f, "Breakfast"))
        assertTrue(useDoubleWidth(1.29f, "Library"))
        assertFalse(useDoubleWidth(1.0f, "Wednesdays"))
        assertFalse(useDoubleWidth(1.3f, "Lunch"))
        assertFalse(useDoubleWidth(2.0f, "Dinner"))
    }
}
