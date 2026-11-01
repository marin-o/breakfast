package com.bosa.breakfast.today

import com.bosa.breakfast.app.SavedDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Today and the day on show, as the view model held them before (`app/BreakfastViewModel.kt` at 5ba4012, 355-375). */
class DayStateTest {
    private var now = at("2026-10-28T12:00")
    private var dayStart = 0
    private val saved = mutableListOf<SavedDay>()
    private var selections = 0

    private fun at(text: String): Long = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun days(restored: SavedDay = SavedDay(null, followingToday = true)) = DayState(
        clock = { now },
        zone = { ZoneOffset.UTC },
        dayStartHour = { dayStart },
        restored = restored,
        save = { saved += it },
        onSelected = { selections++ },
    )

    @Test fun nothingSavedOpensOnTodayAndFollowsIt() {
        val day = days()
        assertEquals("2026-10-28", day.todayKey)
        assertEquals("2026-10-28", day.selectedDayKey)
        assertEquals(listOf(SavedDay("2026-10-28", true)), saved)
        assertEquals("opening is not a pick", 0, selections)
    }

    @Test fun aRestoredPastDayStaysAndARestoredTodayFollows() {
        assertEquals("2026-10-20", days(SavedDay("2026-10-20", followingToday = false)).selectedDayKey)
        assertEquals(SavedDay("2026-10-20", false), saved.last())
        // Restored on the day it was picked: it is today, so it follows today from now on.
        val today = days(SavedDay("2026-10-28", followingToday = false))
        assertEquals(SavedDay("2026-10-28", true), saved.last())
        now = at("2026-10-29T08:00")
        today.check(returning = true)
        assertEquals("2026-10-29", today.selectedDayKey)
    }

    @Test fun aFollowedTodayRestoredLaterIsTheNewToday() {
        now = at("2026-10-30T09:00")
        assertEquals("2026-10-30", days(SavedDay("2026-10-28", followingToday = true)).selectedDayKey)
    }

    @Test fun daysAfterTodayAreRefusedSoNextStopsAtToday() {
        val day = days()
        day.shift(1)
        day.goTo("2026-11-02")
        assertEquals("2026-10-28", day.selectedDayKey)
        assertEquals(0, selections)
        assertEquals(1, saved.size)
    }

    @Test fun aPickedPastDayIsSavedAndNoLongerFollowsToday() {
        val day = days()
        day.shift(-1)
        assertEquals("2026-10-27", day.selectedDayKey)
        assertEquals(SavedDay("2026-10-27", false), saved.last())
        assertEquals(1, selections)
        day.goTo("2026-10-01")
        assertEquals(SavedDay("2026-10-01", false), saved.last())
        day.goToday()
        assertEquals(SavedDay("2026-10-28", true), saved.last())
        assertEquals(3, selections)
    }

    @Test fun whileOnScreenTheSelectionStaysAndOnAReturnAFollowerMovesToTheNewToday() {
        val day = days()
        now = at("2026-10-29T00:30")
        day.check(returning = false)
        assertEquals("2026-10-29", day.todayKey)
        assertEquals("the old day stays on screen", "2026-10-28", day.selectedDayKey)
        assertEquals(0, selections)
        day.check(returning = true)
        assertEquals("2026-10-29", day.selectedDayKey)
        assertEquals(SavedDay("2026-10-29", true), saved.last())
        assertEquals(1, selections)
    }

    @Test fun aReturnKeepsAPastDayThePickedOne() {
        val day = days()
        day.shift(-3)
        now = at("2026-10-29T10:00")
        day.check(returning = true)
        assertEquals("2026-10-25", day.selectedDayKey)
        assertEquals("2026-10-29", day.todayKey)
    }

    @Test fun aLaterDayStartMakesTodayYesterdayAgainAndAFollowerGoesWithIt() {
        now = at("2026-10-28T03:00")
        val day = days()
        assertEquals("2026-10-28", day.selectedDayKey)
        dayStart = 4
        day.check(returning = true)
        assertEquals("2026-10-27", day.todayKey)
        assertEquals("2026-10-27", day.selectedDayKey)
    }
}
