package com.bosa.breakfast.log

import com.bosa.breakfast.app.DiaryChange
import com.bosa.breakfast.app.SavedLogDraft
import com.bosa.breakfast.domain.DraftRow
import com.bosa.breakfast.domain.TestFoods
import com.bosa.breakfast.domain.FoodMatcher
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.LogPlanner
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.Measure
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.SavedMeal
import com.bosa.breakfast.resolve.LogResolveRequest
import com.bosa.breakfast.resolve.ResolveSource
import com.bosa.breakfast.resolve.food
import com.bosa.breakfast.today.OpenDiary
import com.bosa.breakfast.today.entry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LogComposerTest {
    private class FakeStore : LogStore {
        val saves = mutableListOf<String>()
        var result = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO)
        var failure: Throwable? = null
        var held: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override fun planner(pins: Map<String, FoodRecord>) = LogPlanner(FoodMatcher(TestFoods.fixtures), pins = pins)
        override suspend fun save(text: String, meal: MealSlot, dayKey: String, pins: Map<String, FoodRecord>): SavedMeal {
            failure?.let { throw it }
            held?.await()
            saves += "save \"$text\" $meal $dayKey pins=${pins.keys}"
            return result
        }
    }

    private class AfterSave(val unresolved: List<String>, val meal: MealSlot, val source: ResolveSource, val typedAtSave: String)

    private val diary = OpenDiary()
    private val store = FakeStore()
    private var automatic: MealSlot? = null
    private var onScreen: MealSlot? = null
    private var editing = false
    private val afterSaves = mutableListOf<AfterSave>()
    private val parts = mutableListOf<String>()
    private var sentRequest: LogResolveRequest? = null
    private fun composer(initial: SavedLogDraft = SavedLogDraft("", false)) = LogComposer(
        diary.session, store,
        selectedDay = { "2026-10-28" },
        automaticMeal = { automatic },
        openMeal = { onScreen },
        editing = { editing },
        afterSave = { unresolved, meal, source, typed -> afterSaves += AfterSave(unresolved, meal, source, typed) },
        resolvePart = { request, meal, query -> sentRequest = request; parts += "${request.part} -> $meal \"$query\"" },
        compute = Dispatchers.Unconfined,
        initial = initial,
    )
    private val log = composer()

    @Test fun theDraftComesBackAsSavedAndClearingItDropsItsPins() {
        val restored = composer(SavedLogDraft("2 eggs", committed = true))
        assertEquals(SavedLogDraft("2 eggs", true), restored.savedDraft())
        restored.chooseSuggestion(TestFoods.fixtures.first { it.id == "egg" })
        restored.onDraft("2 eggs, ban")
        restored.chooseSuggestion(TestFoods.fixtures.first { it.id == "banana" })
        assertTrue(restored.pinsSnapshot().isNotEmpty())
        restored.onDraft(" ")
        assertEquals(emptyMap<String, FoodRecord>(), restored.pinsSnapshot())
        assertFalse(restored.committed)
    }

    @Test fun addSavesTheDraftToTheTargetMealAndHandsWhatIsLeftToThePostSaveHandling() {
        log.open(MealSlot.LUNCH)
        log.onDraft("2 eggs, ayran")
        store.result = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO, unresolvedSegments = listOf("ayran"))
        log.submit()
        assertEquals(listOf("save \"2 eggs, ayran\" LUNCH 2026-10-28 pins=[]"), store.saves)
        assertEquals(listOf<DiaryChange>(DiaryChange.BatchLogged("Logged!", store.result.entries)), diary.published)
        assertTrue("the Log stays with what is left", log.open)
        val handed = afterSaves.single()
        assertEquals(listOf("ayran"), handed.unresolved)
        assertEquals(MealSlot.LUNCH, handed.meal)
        assertSame("the draft is the queue's source", log, handed.source)
        assertEquals("2 eggs, ayran", handed.typedAtSave)
        assertEquals("Some foods need a match.", log.error)
        handed.source.putBack("ayran")
        assertEquals("ayran", log.draft)
    }

    @Test fun aSaveThatMatchedEverythingLeavesNoError() {
        log.open(MealSlot.LUNCH)
        log.onDraft("2 eggs")
        log.submit()
        assertNull(log.error)
        assertEquals(emptyList<String>(), afterSaves.single().unresolved)
    }

    @Test fun aSaveThatLoggedNothingKeepsTheLogOpenAndPublishesNothing() {
        log.open(MealSlot.LUNCH)
        log.onDraft("ayran")
        store.result = SavedMeal(emptyList(), NutritionValues.ZERO, unresolvedSegments = listOf("ayran"))
        log.submit()
        assertTrue(log.open)
        assertEquals("ayran", log.draft)
        assertEquals("Some foods need a match.", log.error)
        assertEquals(emptyList<DiaryChange>(), diary.published)
        assertEquals(listOf("ayran"), afterSaves.single().unresolved)
    }

    @Test fun theDraftTakesNoEditDuringAddsWrite() {
        val held = kotlinx.coroutines.CompletableDeferred<Unit>()
        diary.session.write(change = { _: Unit -> null }, onSuccess = {}, onFailure = {}) { held.await() }
        log.onDraft("2 eggs")
        assertEquals("", log.draft)
        held.complete(Unit)
        log.onDraft("2 eggs")
        assertEquals("2 eggs", log.draft)
    }

    @Test fun backDuringTheWriteStartsNoQueueAndLeavesOnlyWhatDidNotLog() {
        log.open(MealSlot.LUNCH)
        log.onDraft("2 eggs, ayran")
        store.result = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO, unresolvedSegments = listOf("ayran"))
        store.held = kotlinx.coroutines.CompletableDeferred()
        log.submit()
        log.close()
        store.held!!.complete(Unit)
        assertEquals(emptyList<AfterSave>(), afterSaves)
        assertEquals("ayran", log.draft)
        assertFalse(log.open)
    }

    @Test fun aCleanSaveClosesTheLogAndEditingOrLeavingTellsTheQueue() {
        log.open(MealSlot.LUNCH)
        log.onDraft("2 eggs")
        log.submit()
        assertFalse(log.open)
    }

    @Test fun withNoMealKnownAddAsksAndTheChosenMealSaves() {
        log.open(null)
        log.onDraft("2 eggs")
        log.submit()
        assertTrue(log.mealMenuOpen)
        assertEquals(emptyList<String>(), store.saves)
        log.chooseMeal(MealSlot.DINNER)
        assertEquals(listOf("save \"2 eggs\" DINNER 2026-10-28 pins=[]"), store.saves)
        // A meal chosen without Add asking only changes the target.
        log.open(null)
        log.onDraft("1 banana")
        log.openMealMenu()
        log.chooseMeal(MealSlot.SNACKS)
        assertEquals(MealSlot.SNACKS, log.targetMeal())
        assertEquals(1, store.saves.size)
    }

    @Test fun theTargetMealFollowsChosenThenTypedThenPresetThenTheSetting() {
        automatic = MealSlot.SNACKS
        log.open(null)
        assertEquals(MealSlot.SNACKS, log.targetMeal())
        log.open(MealSlot.LUNCH)
        assertEquals(MealSlot.LUNCH, log.targetMeal())
        log.onDraft("dinner, 2 eggs")
        assertEquals(MealSlot.DINNER, log.targetMeal())
        log.chooseMeal(MealSlot.BREAKFAST)
        assertEquals(MealSlot.BREAKFAST, log.targetMeal())
    }

    @Test fun addRefusesWhatItCannotSave() {
        log.open(MealSlot.LUNCH)
        log.submit()
        assertEquals("Add at least one food.", log.error)
        log.onDraft("breakfast, lunch, 2 eggs")
        log.submit()
        assertEquals("Choose one meal marker, or remove the extra markers.", log.error)
        log.onDraft("lunch")
        log.submit()
        assertEquals("Add at least one food.", log.error)
        store.failure = IllegalStateException("disk full")
        log.onDraft("2 eggs")
        log.submit()
        assertEquals("disk full", log.error)
        assertTrue(log.open)
        assertEquals(emptyList<String>(), store.saves)
    }

    @Test fun theLogDoesNotOpenOverAnEntryEdit() {
        editing = true
        log.open(MealSlot.LUNCH)
        assertFalse(log.open)
    }

    @Test fun aNotInYourFoodsPartGoesToTheLibraryAndTheLogComesBackWithThePick() {
        onScreen = MealSlot.DINNER
        log.open(MealSlot.LUNCH)
        log.onDraft("2 eggs, kiselo mleko 200ml")
        val row = DraftRow.Unresolved("kiselo mleko 200ml", 8, 26)
        log.resolveNotInFoods(row)
        assertFalse(log.open)
        assertEquals(listOf("kiselo mleko 200ml -> LUNCH \"kiselo mleko\""), parts)
        val request = sentRequest!!
        assertEquals(LogResolveRequest("2 eggs, kiselo mleko 200ml", 8, 26, "kiselo mleko 200ml", MealSlot.LUNCH, null, MealSlot.DINNER), request)
        val pesto = food("pesto", "Branded pesto")
        log.resume(request, pesto)
        assertTrue(log.open)
        assertEquals("2 eggs, Branded pesto 200ml", log.draft)
        assertEquals(mapOf("Branded pesto 200ml" to pesto), log.pinsSnapshot())
        assertEquals(MealSlot.LUNCH, log.preset)
        // Add then saves the pick through its pin, once.
        log.submit()
        assertEquals(listOf("save \"2 eggs, Branded pesto 200ml\" LUNCH 2026-10-28 pins=[Branded pesto 200ml]"), store.saves)
        assertEquals("the pins that save used are dropped", emptyMap<String, FoodRecord>(), log.pinsSnapshot())
    }

    @Test fun aPartLeftWithoutAPickComesBackAsItWas() {
        log.open(null)
        log.onDraft("ayran")
        log.resolveNotInFoods(DraftRow.Unresolved("ayran", 0, 5))
        assertEquals("no meal known: Breakfast", listOf("ayran -> BREAKFAST \"ayran\""), parts)
        log.onDraft("changed meanwhile")
        log.resume(sentRequest!!, null)
        assertEquals("ayran", log.draft)
        assertTrue(log.open)
    }

    private val pesto = food("pesto", "Pesto")
    private val greenPesto = pesto.copy(name = "Green pesto")

    @Test fun renamingAPickedFoodChangesTheDraftAndKeepsThePick() {
        log.open(MealSlot.LUNCH)
        log.onDraft("ayran")
        log.addScannedPart(pesto, 200.0, Measure(MeasureUnit.GRAM))
        log.refreshPins(listOf(greenPesto))
        assertEquals("the part never picked stays as typed", "ayran, 200 g Green pesto", log.draft)
        assertEquals(mapOf("200 g Green pesto" to greenPesto), log.pinsSnapshot())
        log.submit()
        assertEquals(listOf("save \"ayran, 200 g Green pesto\" LUNCH 2026-10-28 pins=[200 g Green pesto]"), store.saves)
    }

    @Test fun aRenameInTheLibraryIsKeptWhenThePartSentThereComesBack() {
        log.open(MealSlot.LUNCH)
        log.addScannedPart(pesto, 200.0, Measure(MeasureUnit.GRAM))
        log.onDraft("200 g Pesto, ayran")
        log.resolveNotInFoods(DraftRow.Unresolved("ayran", 13, 18))
        log.refreshPins(listOf(greenPesto))
        log.resume(sentRequest!!, null)
        assertEquals("200 g Green pesto, ayran", log.draft)
        assertEquals(mapOf("200 g Green pesto" to greenPesto), log.pinsSnapshot())

        log.resolveNotInFoods(DraftRow.Unresolved("ayran", 19, 24))
        log.refreshPins(listOf(pesto))
        val kefir = food("kefir", "Kefir")
        log.resume(sentRequest!!, kefir)
        assertEquals("200 g Pesto, Kefir", log.draft)
        assertEquals(mapOf("200 g Pesto" to pesto, "Kefir" to kefir), log.pinsSnapshot())
    }

    @Test fun aRowFromAnOlderDraftDoesNothing() {
        log.onDraft("2 eggs, ayran")
        log.resolveNotInFoods(DraftRow.Unresolved("kefir", 8, 13))
        assertEquals(emptyList<String>(), parts)
    }
}
