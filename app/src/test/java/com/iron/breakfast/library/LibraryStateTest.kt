package com.iron.breakfast.library

import com.iron.breakfast.QueueDispatcher
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.SavedMeal
import com.iron.breakfast.resolve.ResolveSource
import com.iron.breakfast.resolve.food
import com.iron.breakfast.today.OpenDiary
import com.iron.breakfast.today.entry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateTest {
    private class FakeStore : LibraryStore {
        val calls = mutableListOf<String>()
        var library: List<FoodRecord> = emptyList()
        var saved = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO)
        override suspend fun foodsMatching(query: String) = library.filter { it.name.contains(query, ignoreCase = true) }
        override fun planner() = LogPlanner(FoodMatcher(FoodCatalog.fixtures))
        override fun search(phrase: String) = FoodCatalog.fixtures.filter { phrase in it.aliases || phrase == it.id }.map { RankedFood(it, 1.0, emptyList()) }
        override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String, servingName: String?) {
            calls += "log ${food.id} $amount ${unit.name} $meal $dayKey" + (servingName?.let { " $it" } ?: "")
        }
        override suspend fun save(text: String, meal: MealSlot, dayKey: String): SavedMeal { calls += "save \"$text\" $meal $dayKey"; return saved }
        var failDelete = false
        override suspend fun deleteFood(id: String) { if (failDelete) throw IllegalStateException(); calls += "delete $id" }
    }

    private class AfterSave(val unresolved: List<String>, val meal: MealSlot, val source: ResolveSource, val typedAtSave: String)

    private val diary = OpenDiary()
    private val store = FakeStore()
    private val notices = Notices()
    /** How often "Logged!" was asked for. */
    private var confirmed = 0
    private val io = QueueDispatcher()
    private val compute = QueueDispatcher()
    private var resolving = false
    private val picked = mutableListOf<String>()
    private val deleted = mutableListOf<String>()
    private val afterSaves = mutableListOf<AfterSave>()
    private val library = LibraryState(
        diary.session, store, notices, diary.scope, io, Dispatchers.Unconfined, compute,
        selectedDay = { "2026-10-28" },
        resolvingPart = { resolving },
        onPartPicked = { picked += it.id },
        onDeleted = { deleted += it.id },
        afterSave = { unresolved, meal, source, typed -> afterSaves += AfterSave(unresolved, meal, source, typed) },
        confirmLogged = { confirmed++ },
    )
    private val kefir = food("kefir", "Kefir")
    private val egg = FoodCatalog.fixtures.first { it.id == "egg" }

    @Test fun aRefreshAppliesOnlyWhileItStillMatchesWhatIsShown() {
        store.library = listOf(kefir, food("ayran", "Ayran"))
        library.show(MealSlot.LUNCH, "kef")
        assertEquals("kef", library.input)
        library.onQuery("ayr")
        io.runAll()
        assertEquals(listOf("ayran"), library.foods.map { it.id })
        assertEquals("ayr", library.resultsQuery)
        // A refresh still running when the Library closes is dropped.
        library.onQuery("kef")
        library.close()
        io.runAll()
        assertEquals(listOf("ayran"), library.foods.map { it.id })
        assertFalse(library.open)
        assertNull(library.target)
        assertEquals("", library.input)
    }

    @Test fun theBarFiltersByItsFoodTextAndTheNoMatchScreenWaitsForTheResult() {
        library.show(null, "")
        library.onInput("200 g kefir")
        assertEquals("kefir", library.query)
        assertFalse("still running", library.noMatch(otherScreenOpen = false))
        io.runAll()
        assertTrue(library.noMatch(otherScreenOpen = false))
        assertFalse(library.noMatch(otherScreenOpen = true))
    }

    @Test fun addingAFoodLogsItToTheMeal() {
        library.show(MealSlot.LUNCH, "")
        library.log(kefir, MealSlot.LUNCH, 200.0, MeasureUnit.MILLILITER)
        assertEquals(listOf("log kefir 200.0 MILLILITER LUNCH 2026-10-28"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsLogged), diary.published)
        assertEquals("\"Logged!\", not a dialog naming the meal and day just chosen", 1, confirmed)
        assertNull(notices.message)
    }

    /** The amount sheet's serving is logged by its name, so the day reads "2 slices". */
    @Test fun aServingIsLoggedByItsName() {
        library.show(MealSlot.LUNCH, "")
        library.log(kefir, MealSlot.LUNCH, 2.0, MeasureUnit.SERVING, "bottle")
        assertEquals(listOf("log kefir 2.0 SERVING LUNCH 2026-10-28 bottle"), store.calls)
    }

    @Test fun whileAPartIsResolvedATapOrAnAddIsThePickAndDoneOnlySearches() {
        resolving = true
        library.show(MealSlot.BREAKFAST, "kefir")
        library.openFood(kefir)
        library.log(kefir, MealSlot.BREAKFAST, 100.0, MeasureUnit.GRAM)
        assertEquals(listOf("kefir", "kefir"), picked)
        assertNull("no food page", library.selectedFood)
        library.onInput("2 eggs")
        library.submitInput()
        assertEquals("2 eggs", library.query)
        assertEquals(emptyList<String>(), store.calls)
        assertEquals(emptyList<DiaryChange>(), diary.published)
    }

    @Test fun aQuantifiedBatchLogsToTheTypedOrTargetMealOrAsksForOne() {
        library.show(MealSlot.DINNER, "")
        library.onInput("2 egg")
        library.submitInput()
        assertEquals("the lookup waits for the compute dispatcher", emptyList<String>(), store.calls)
        compute.runAll()
        assertEquals(listOf("log egg 2.0 PIECE DINNER 2026-10-28"), store.calls)
        assertEquals("", library.input)
        library.close()
        library.show(null, "")
        library.onInput("2 egg")
        library.submitInput()
        compute.runAll()
        assertTrue(library.batchMealPickerOpen)
        library.chooseBatchMeal(MealSlot.SNACKS)
        assertEquals("log egg 2.0 PIECE SNACKS 2026-10-28", store.calls.last())
        assertFalse(library.batchMealPickerOpen)
        library.onInput("lunch, dinner, 2 egg")
        library.submitInput()
        assertEquals("Choose one meal marker.", notices.message)
    }

    @Test fun aBatchWithAFoodNotInTheLibrarySearchesInstead() {
        library.show(MealSlot.DINNER, "")
        library.onInput("2 egg, 100 g unknownium")
        library.submitInput()
        compute.runAll()
        assertEquals("One or more foods are not in your local library.", notices.message)
        assertEquals(emptyList<String>(), store.calls)
    }

    @Test fun aMixedBatchSavesTheBarAndHandsWhatIsLeftToThePostSaveHandling() {
        library.show(MealSlot.LUNCH, "")
        library.onInput("2 egg, kefir")
        store.saved = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO, unresolvedSegments = listOf("kefir"))
        library.submitInput()
        assertEquals(listOf("save \"2 egg, kefir\" LUNCH 2026-10-28"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.BatchLogged("Logged!", store.saved.entries)), diary.published)
        val handed = afterSaves.single()
        assertEquals(listOf("kefir"), handed.unresolved)
        assertEquals(MealSlot.LUNCH, handed.meal)
        assertSame("the bar is the queue's source", library, handed.source)
        assertEquals("2 egg, kefir", handed.typedAtSave)
        handed.source.putBack("kefir")
        assertEquals("kefir", library.input)
    }

    @Test fun aMixedBatchWithoutAMealAsksFirst() {
        library.show(null, "")
        library.onInput(" 2 egg, kefir ")
        library.submitInput()
        assertTrue(library.mixedMealPickerOpen)
        assertEquals("Choose a meal before adding a mixed batch with an unquantified food.", notices.message)
        library.chooseMixedMeal(MealSlot.BREAKFAST)
        assertFalse(library.mixedMealPickerOpen)
        assertEquals(listOf("save \"2 egg, kefir\" BREAKFAST 2026-10-28"), store.calls)
        assertEquals(" 2 egg, kefir ", afterSaves.single().typedAtSave)
    }

    @Test fun deleteFoodAsksFirstThenDeletesAndClosesThePage() {
        val custom = kefir
        library.showFood(custom)
        library.askDelete(custom)
        assertEquals(custom, library.deleteRequest)
        assertEquals("nothing is deleted before the confirmation", emptyList<String>(), store.calls)
        library.cancelDelete()
        assertNull(library.deleteRequest)
        assertEquals(custom, library.selectedFood)
        assertEquals(emptyList<String>(), store.calls)

        library.askDelete(custom)
        library.confirmDelete()
        assertNull(library.deleteRequest)
        assertEquals(listOf("delete kefir"), store.calls)
        assertEquals(listOf("kefir"), deleted)
        assertNull(library.selectedFood)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged), diary.published)
        assertNull("no Undo and no notice", notices.message)
    }

    @Test fun aDeleteWhileAnotherWriteRunsKeepsTheDialog() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        diary.session.write(DiaryChange.FoodsChanged) { gate.await() }
        assertTrue(diary.session.busy)
        library.askDelete(kefir)
        library.confirmDelete()
        assertEquals(kefir, library.deleteRequest)
        assertEquals(emptyList<String>(), store.calls)
        gate.complete(Unit)
        library.confirmDelete()
        assertEquals(listOf("delete kefir"), store.calls)
    }

    /** The session's gate (`DiarySession.canWrite`) refuses these writes; what the user picked closes as before. */
    @Test fun whileAnotherWriteRunsALogOrAMixedBatchWritesNothing() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        diary.session.write(DiaryChange.FoodsChanged) { gate.await() }
        library.show(MealSlot.LUNCH, "")
        library.log(kefir, MealSlot.LUNCH, 200.0, MeasureUnit.MILLILITER)
        library.onInput("2 eggs, ayran")
        library.chooseMixedMeal(MealSlot.LUNCH)
        assertFalse(library.mixedMealPickerOpen)
        assertEquals("nothing was written", emptyList<String>(), store.calls)
        assertEquals(emptyList<String>(), picked)
        assertNull(notices.message)
        gate.complete(Unit)
    }

    @Test fun onlyAPersonalFoodCanBeDeleted() {
        library.askDelete(egg)
        assertNull(library.deleteRequest)
        library.confirmDelete()
        assertEquals(emptyList<String>(), store.calls)
    }

    @Test fun aFailedDeleteSaysSoAndKeepsThePage() {
        library.showFood(kefir)
        store.failDelete = true
        library.askDelete(kefir)
        library.confirmDelete()
        assertEquals("Could not delete this food.", notices.message)
        assertEquals(kefir, library.selectedFood)
        assertEquals(emptyList<String>(), deleted)
    }

    @Test fun theLastLoggedEntryGivesTheRepeatAmount() {
        diary.rows = listOf(entry("old").copy(foodId = "kefir", savedAtEpochMs = 1L), entry("new").copy(foodId = "kefir", savedAtEpochMs = 2L))
        diary.session.refresh()
        assertEquals("new", library.lastLoggedEntry(kefir)?.id)
        assertNull(library.lastLoggedEntry(egg))
    }
}
