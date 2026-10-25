package com.iron.breakfast

import com.iron.breakfast.domain.dayKeyFor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DayBoundaryTest {
    private val skopje = ZoneId.of("Europe/Skopje")
    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(skopje).toInstant().toEpochMilli()
    private fun day(text: String, start: Int) = dayKeyFor(at(text), skopje, start)

    @Test fun midnightStartIsTheCalendarDate() {
        assertEquals("2026-10-25", day("2026-10-25T00:00", 0))
        assertEquals("2026-10-25", day("2026-10-25T23:59", 0))
    }

    @Test fun beforeTheStartHourItIsStillThePreviousDay() {
        assertEquals("2026-10-24", day("2026-10-25T01:30", 4))
        assertEquals("2026-10-24", day("2026-10-25T03:59", 4))
        assertEquals("2026-10-25", day("2026-10-25T04:00", 4))
        assertEquals("2026-10-25", day("2026-10-25T05:00", 4))
        assertEquals("2026-10-24", day("2026-10-25T22:59", 23))
        assertEquals("2026-10-25", day("2026-10-25T23:00", 23))
    }

    @Test fun aDaylightSavingDayIsStillOneDay() {
        // 2026-03-29: 02:00 jumps to 03:00. 2026-10-25: 03:00 falls back to 02:00, so 02:30 happens twice.
        assertEquals("2026-03-28", day("2026-03-29T01:30", 3))
        assertEquals("2026-03-29", day("2026-03-29T03:00", 3))
        val firstHalfPast = LocalDateTime.parse("2026-10-25T02:30").atZone(skopje).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        val secondHalfPast = LocalDateTime.parse("2026-10-25T02:30").atZone(skopje).withLaterOffsetAtOverlap().toInstant().toEpochMilli()
        assertEquals("2026-10-24", dayKeyFor(firstHalfPast, skopje, 3))
        assertEquals("2026-10-24", dayKeyFor(secondHalfPast, skopje, 3))
        assertEquals("2026-10-25", day("2026-10-25T03:00", 3))
    }

    private fun resolve(selected: String?, following: Boolean, returning: Boolean, now: String, start: Int = 0) =
        resolveSelectedDay(selected, following, returning, at(now), skopje, start)

    @Test fun nothingSavedOpensOnToday() {
        assertEquals("2026-10-25", resolve(null, following = true, returning = true, now = "2026-10-25T08:00"))
        assertEquals("2026-10-24", resolve(null, following = true, returning = true, now = "2026-10-25T01:30", start = 4))
    }

    @Test fun onScreenTheSelectedDayNeverMoves() {
        // Midnight passes while the diary is open: it keeps showing the day, which is now yesterday.
        assertEquals("2026-10-24", resolve("2026-10-24", following = true, returning = false, now = "2026-10-25T00:01"))
        assertEquals("2026-10-20", resolve("2026-10-20", following = false, returning = false, now = "2026-10-25T00:01"))
    }

    @Test fun returningWhileFollowingTodayOpensTheNewToday() {
        assertEquals("2026-10-25", resolve("2026-10-24", following = true, returning = true, now = "2026-10-25T07:00"))
    }

    @Test fun returningKeepsAnOlderPickedDay() {
        assertEquals("2026-10-20", resolve("2026-10-20", following = false, returning = true, now = "2026-10-25T07:00"))
    }

    @Test fun dayStartAtFourDecidesWhenTodayChanges() {
        // At 01:30 the new date has not started yet; at 05:00 it has.
        assertEquals("2026-10-24", resolve("2026-10-24", following = true, returning = true, now = "2026-10-25T01:30", start = 4))
        assertEquals("2026-10-25", resolve("2026-10-24", following = true, returning = true, now = "2026-10-25T05:00", start = 4))
        assertEquals("2026-10-23", resolve("2026-10-23", following = false, returning = true, now = "2026-10-25T05:00", start = 4))
    }

    @Test fun aDayThatIsNoLongerPastBecomesTodayOnReturn() {
        // The day start moved later (or the clock moved back): the selected day is now after today.
        assertEquals("2026-10-24", resolve("2026-10-25", following = false, returning = true, now = "2026-10-25T01:30", start = 4))
    }

    @Test fun returningOnADaylightSavingNightUsesTheWallClock() {
        assertEquals("2026-03-28", resolve("2026-03-28", following = true, returning = true, now = "2026-03-29T01:30", start = 3))
        assertEquals("2026-03-29", resolve("2026-03-28", following = true, returning = true, now = "2026-03-29T03:30", start = 3))
    }
}
