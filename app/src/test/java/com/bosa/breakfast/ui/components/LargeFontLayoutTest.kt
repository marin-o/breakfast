package com.bosa.breakfast.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFontLayoutTest {
    @Test fun normalAndOnePointThreeKeepTheStandardLayout() {
        assertFalse(isLargeFont(1f))
        assertFalse(isLargeFont(1.1f))
        assertFalse(isLargeFont(1.3f))
    }

    @Test fun twoXSwitchesToTheStackedLayout() {
        assertTrue(isLargeFont(LargeFontScale))
        assertTrue(isLargeFont(2f))
    }

    @Test fun justBelowTheThresholdStaysStandard() {
        assertFalse(isLargeFont(1.79f))
    }
}
