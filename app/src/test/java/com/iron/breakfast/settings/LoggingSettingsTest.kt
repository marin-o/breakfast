package com.iron.breakfast.settings

import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.Preference
import com.iron.breakfast.domain.rankingPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.iron.breakfast.today.UNDO_WINDOW_MS
import com.iron.breakfast.log.LogSubmitPlan
import com.iron.breakfast.log.logSubmitPlan
import com.iron.breakfast.today.undoOpen
import com.iron.breakfast.today.undoEndsAt
import com.iron.breakfast.app.DiaryChangeKind
import com.iron.breakfast.log.automaticMeal

/** B9 Logging group: Undo window, Meal, the By-time boundaries, Count what I'm typing, Learn from my picks. */
class LoggingSettingsTest {
    @Test fun everyDefaultIsTodaysBehaviour() {
        assertEquals(UndoWindow.FIFTEEN_SECONDS, enumOf(null, UndoWindow.FIFTEEN_SECONDS))
        assertEquals(UNDO_WINDOW_MS, undoWindowMs(UndoWindow.FIFTEEN_SECONDS))
        assertEquals(MealMode.ASK, enumOf("NOT_A_MODE", MealMode.ASK))
        assertEquals(MealBounds(12, 17, 22), MealBounds())
        // Ask is the meal menu: no automatic meal, so a draft with no meal still opens it.
        assertNull(automaticMeal(MealMode.ASK, 9, MealBounds(), MealSlot.LUNCH))
        assertEquals(LogSubmitPlan.OpenMenu, logSubmitPlan(true, "2 eggs", emptyList(), null, null, automatic = null))
    }

    @Test fun undoWindowsAreFiveFifteenThirtyOrUntilTheNextAction() {
        assertEquals(5_000L, undoWindowMs(UndoWindow.FIVE_SECONDS))
        assertEquals(30_000L, undoWindowMs(UndoWindow.THIRTY_SECONDS))
        assertNull(undoWindowMs(UndoWindow.UNTIL_NEXT_ACTION))
        val offered = 1_000L
        assertTrue(undoOpen(offered, offered + 4_999L, 5_000L))
        assertFalse(undoOpen(offered, offered + 5_000L, 5_000L))
        assertTrue(undoOpen(offered, offered + 29_999L, 30_000L))
        // Until next action never runs out by itself; a clock set back still closes it.
        assertTrue(undoOpen(offered, offered + 86_400_000L, null))
        assertFalse(undoOpen(offered, offered - 1L, null))
    }

    @Test fun onlyALogEditOrDeleteEndsAnUndoOffer() {
        assertTrue(undoEndsAt(DiaryChangeKind.LOG))
        assertTrue(undoEndsAt(DiaryChangeKind.EDIT))
        assertTrue(undoEndsAt(DiaryChangeKind.DELETE))
        // B4: a "Not right?" pick keeps the batch Undo, in every Undo mode.
        assertFalse(undoEndsAt(DiaryChangeKind.NOT_RIGHT_PICK))
        assertFalse(undoEndsAt(DiaryChangeKind.CONFIRM_MATCH))
        // An edit saved after its edit closed has never ended Undo.
        assertFalse(undoEndsAt(DiaryChangeKind.EDIT_AFTER_CLOSE))
    }

    @Test fun eachBoundaryStaysLaterThanTheOneAbove() {
        val tight = MealBounds(12, 13, 14)
        assertEquals(tight, shiftMealBound(tight, MealSlot.BREAKFAST, 1))
        assertEquals(tight, shiftMealBound(tight, MealSlot.LUNCH, -1))
        assertEquals(tight, shiftMealBound(tight, MealSlot.LUNCH, 1))
        assertEquals(tight, shiftMealBound(tight, MealSlot.DINNER, -1))
        assertEquals(MealBounds(11, 13, 14), shiftMealBound(tight, MealSlot.BREAKFAST, -1))
        assertEquals(MealBounds(12, 13, 15), shiftMealBound(tight, MealSlot.DINNER, 1))
        assertFalse(canShiftMealBound(tight, MealSlot.LUNCH, 1))
        assertTrue(canShiftMealBound(tight, MealSlot.BREAKFAST, -1))
        // The ends: 01:00 at the earliest, 23:00 at the latest.
        assertEquals(MealBounds(1, 2, 23), shiftMealBound(MealBounds(1, 2, 23), MealSlot.BREAKFAST, -1))
        assertEquals(MealBounds(1, 2, 23), shiftMealBound(MealBounds(1, 2, 23), MealSlot.DINNER, 1))
        assertEquals(12, MealBounds().untilOf(MealSlot.BREAKFAST))
        assertNull(MealBounds().untilOf(MealSlot.SNACKS))
    }

    @Test fun storedBoundariesOutOfOrderFallBackToTheDefaults() {
        assertEquals(MealBounds(), mealBoundsOf(-1, -1, -1))
        assertEquals(MealBounds(), mealBoundsOf(14, 13, 22))
        assertEquals(MealBounds(), mealBoundsOf(0, 13, 22))
        assertEquals(MealBounds(10, 15, 20), mealBoundsOf(10, 15, 20))
    }

    private val on = LogPlanner(FoodMatcher(FoodCatalog.fixtures))
    private fun off(pins: Map<String, FoodRecord> = emptyMap()) =
        LogPlanner(FoodMatcher(FoodCatalog.fixtures), pins = pins, countTyping = false)

    @Test fun countOnSavesTheWordBeingTyped() {
        assertEquals(listOf("egg", "banana"), on.plan("2 eggs, ban", typing = true).entries.map { it.food.id })
        assertNull(on.heldPart("2 eggs, ban", typing = true))
    }

    @Test fun countOffAddSavesWhatTheFinishedDraftCounts() {
        val planner = off()
        // Add finishes the draft: the committed bar and the save both include the word that was being typed.
        assertEquals(listOf("egg", "banana"), planner.draft("2 eggs, ban", typing = false).logged.map { it.food.id })
        assertEquals(listOf("egg", "banana"), planner.plan("2 eggs, ban").entries.map { it.food.id })
    }

    @Test fun countOffHoldsTheWordBeingTypedUntilItIsFinished() {
        val planner = off()
        val draft = planner.draft("2 eggs, ban", typing = true)
        assertEquals(listOf("egg"), draft.logged.map { it.food.id })
        assertNull(draft.counted)
        assertFalse(draft.preselected)
        assertEquals("ban", planner.heldPart("2 eggs, ban", typing = true))
        assertEquals(listOf("egg"), planner.plan("2 eggs, ban", typing = true).entries.map { it.food.id })
        // Finished by a separator, or by Done (no longer typing): it counts.
        assertEquals(listOf("egg", "banana"), planner.plan("2 eggs, banana, ", typing = true).entries.map { it.food.id })
        assertEquals(listOf("egg", "banana"), planner.plan("2 eggs, ban", typing = false).entries.map { it.food.id })
        assertNull(planner.heldPart("2 eggs, ban", typing = false))
    }

    @Test fun countOffCountsAPickedWord() {
        val banana = FoodCatalog.fixtures.first { it.id == "banana" }
        val chosen = off().choose("2 eggs, ban", banana)
        val part = requireNotNull(chosen.part)
        val picked = off(mapOf(part to banana))
        val draft = picked.draft(chosen.input, typing = true)
        assertEquals(listOf("egg", "banana"), draft.logged.map { it.food.id })
        assertNull(picked.heldPart(chosen.input, typing = true))
        // An amount typed after the name still counts: the part is picked, not being typed.
        val withAmount = "${chosen.input}150 g"
        assertEquals(listOf("egg", "banana"), picked.draft(withAmount, typing = true).logged.map { it.food.id })
        assertNull(picked.heldPart(withAmount, typing = true))
    }

    @Test fun countOffShowsACleanMatchBeingTypedAsAChoiceNotARow() {
        val draft = off().draft("2 eggs", typing = true)
        assertTrue(draft.rows.isEmpty())
        assertEquals("egg", draft.choices.first().food.id)
        assertFalse(draft.preselected)
        assertTrue(draft.logged.isEmpty())
    }

    @Test fun learnOffIgnoresPreferencesButLeavesThemStored() {
        val stored = mapOf("banana" to Preference("banana", explicitCount = 3))
        assertSame(stored, rankingPreferences(true, stored))
        assertTrue(rankingPreferences(false, stored).isEmpty())
        // The same empty map each time, so the engine's planner cache keeps working.
        assertSame(rankingPreferences(false, stored), rankingPreferences(false, emptyMap()))
        assertEquals(3, stored.getValue("banana").explicitCount)
    }
}
