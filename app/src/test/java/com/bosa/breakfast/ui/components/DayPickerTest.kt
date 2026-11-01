package com.bosa.breakfast.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DayPickerTest {
    @Test fun aDayRoundTripsThroughThePickersMillis() {
        assertEquals("2026-10-25", dayKeyOfUtcMillis(dayUtcMillis("2026-10-25")))
    }
}
