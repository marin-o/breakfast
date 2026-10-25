package com.iron.breakfast

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test fun dayStartStepsByOneHourAndWraps() {
        assertEquals(5, shiftDayStartHour(4, 1))
        assertEquals(3, shiftDayStartHour(4, -1))
        assertEquals(0, shiftDayStartHour(23, 1))
        assertEquals(23, shiftDayStartHour(0, -1))
    }

    @Test fun dayStartReadsAsAClockHour() {
        assertEquals("00:00", dayStartLabel(0))
        assertEquals("04:00", dayStartLabel(4))
        assertEquals("23:00", dayStartLabel(23))
    }
}
