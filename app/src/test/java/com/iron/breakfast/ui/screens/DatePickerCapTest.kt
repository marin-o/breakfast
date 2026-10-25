package com.iron.breakfast.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DatePickerCapTest {
    private val skopje = ZoneId.of("Europe/Skopje")

    @Test fun thePickerEndsWithToday() {
        val max = datePickerMaxMs("2026-10-25", skopje)
        assertEquals(LocalDateTime.parse("2026-10-25T23:59:59.999"), Instant.ofEpochMilli(max).atZone(skopje).toLocalDateTime())
    }

    @Test fun aDaylightSavingDayEndsAtItsOwnMidnight() {
        // 2026-10-25 has 25 hours in Skopje; the cap is still the end of that date.
        val max = datePickerMaxMs("2026-10-25", skopje)
        assertEquals(LocalDateTime.parse("2026-10-26T00:00").atZone(skopje).toInstant().toEpochMilli() - 1, max)
    }
}
