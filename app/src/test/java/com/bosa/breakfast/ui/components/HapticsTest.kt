package com.bosa.breakfast.ui.components

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Haptics setting's tick on Add and Undo. */
class HapticsTest {
    @Test fun hapticsOffByDefaultAndAStandardTickWhenOn() {
        assertNull(tickHapticFor(enabled = false, sdk = 34))
        assertEquals(HapticFeedbackConstants.CONFIRM, tickHapticFor(enabled = true, sdk = 34))
        assertEquals(HapticFeedbackConstants.CONFIRM, tickHapticFor(enabled = true, sdk = 30))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, tickHapticFor(enabled = true, sdk = 29))
    }
}
