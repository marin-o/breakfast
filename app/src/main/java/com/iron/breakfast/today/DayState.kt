package com.iron.breakfast.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.SavedDay
import com.iron.breakfast.domain.dayKeyFor
import com.iron.breakfast.resolveSelectedDay
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today and the day the diary shows.
 *
 * - [todayKey]: today by [clock] and the day start ([dayStartHour]); recomputed by [check] (the 60-second loop, every
 *   return, a new day start) and whenever a day is picked.
 * - [selectedDayKey]: the day on show, picked with [shift], [goToday] and [goTo]. Days after today are refused, so
 *   Next stops at today. A user on today follows it: [check] with `returning` brings them to the new today, while a
 *   past day they picked stays ([resolveSelectedDay]). Opened or restored ([restored]) counts as a return.
 *
 * Every selection is handed to [save] (kept for process death); [onSelected] runs when another day is picked.
 */
internal class DayState(
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val dayStartHour: () -> Int,
    restored: SavedDay,
    private val save: (SavedDay) -> Unit,
    private val onSelected: () -> Unit,
) {
    /** Today's day by the clock and the day start setting. The 60-second loop and every return keep it current. */
    var todayKey by mutableStateOf(dayKeyFor(clock(), zone(), dayStartHour())); private set
    /** True while the user is on today, not on a day they picked: a return from the background then opens the new today. */
    private var followingToday = restored.followingToday
    /** Opened or restored after being closed: a user following today lands on today; a past day they picked stays. */
    var selectedDayKey by mutableStateOf(
        resolveSelectedDay(restored.dayKey, restored.followingToday, returning = true, clock(), zone(), dayStartHour())
    ); private set

    init {
        if (selectedDayKey == todayKey) followingToday = true
        save(SavedDay(selectedDayKey, followingToday))
    }

    /** Recomputes today and applies [resolveSelectedDay]; the selection moves only when [returning]. */
    fun check(returning: Boolean) {
        val now = clock()
        todayKey = dayKeyFor(now, zone(), dayStartHour())
        val day = resolveSelectedDay(selectedDayKey, followingToday, returning, now, zone(), dayStartHour())
        if (day != selectedDayKey) select(day, following = true)
    }

    /** The arrows and the swipe: [delta] days from the day on show. */
    fun shift(delta: Long) { goTo(runCatching { LocalDate.parse(selectedDayKey).plusDays(delta).toString() }.getOrDefault(selectedDayKey)) }

    fun goToday() = goTo(todayNow())

    /** Selects a day from the arrows, swipe or date picker. Days after today are refused, so Next stops at today. */
    fun goTo(dayKey: String) {
        val today = todayNow()
        if (dayKey > today) return
        select(dayKey, following = dayKey == today)
    }

    private fun todayNow(): String = dayKeyFor(clock(), zone(), dayStartHour()).also { todayKey = it }

    private fun select(value: String, following: Boolean) {
        selectedDayKey = value
        followingToday = following
        save(SavedDay(value, following))
        onSelected()
    }
}
