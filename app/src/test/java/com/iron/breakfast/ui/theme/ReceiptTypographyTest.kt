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
}
