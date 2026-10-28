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
        override suspend fun foodsMatching(query: String, includeArchived: Boolean) =
            library.filter { it.name.contains(query, ignoreCase = true) && (includeArchived || !it.archived) }
        override fun planner() = LogPlanner(FoodMatcher(FoodCatalog.fixtures))
        override fun search(phrase: String) = FoodCatalog.fixtures.filter { phrase in it.aliases || phrase == it.id }.map { RankedFood(it, 1.0, emptyList()) }
        override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
            calls += "log ${food.id} $amount ${unit.name} $meal $dayKey"
        }
        override suspend fun save(text: String, meal: MealSlot, dayKey: String): SavedMeal { calls += "save \"$text\" $meal $dayKey"; return saved }
        override suspend fun archiveFood(id: String) { calls += "archive $id" }
        override suspend fun restoreFood(id: String) { calls += "restore $id" }
    }

    private class AfterSave(val unresolved: List<String>, val meal: MealSlot, val source: ResolveSource, val typedAtSave: String)

    private val diary = OpenDiary()
    private val store = FakeStore()
    private val notices = Notices()
    private val io = QueueDispatcher()
    private val compute = QueueDispatcher()
    private var resolving = false
    private val picked = mutableListOf<String>()
    private val archived = mutableListOf<String>()
    private val afterSaves = mutableListOf<AfterSave>()
    private val library = LibraryState(
        diary.session, store, notices, diary.scope, io, Dispatchers.Unconfined, compute,
        selectedDay = { "2026-10-28" },
        resolvingPart = { resolving },
        onPartPicked = { picked += it.id },
        onArchived = { archived += it.id },
        afterSave = { unresolved, meal, source, typed -> afterSaves += AfterSave(unresolved, meal, source, typed) },
    )
    private val kefir = food("kefir", "Kefir")
    private val egg = FoodCatalog.fixtures.first { it.id == "egg" }

    @Test fun aRefreshAppliesOnlyWhileItStillMatchesWhatIsShown() {
        store.library = listOf(kefir, food("ayran", "Ayran"))
        library.show(MealSlot.LUNCH, "kef", archivedOnly = false)
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
        library.show(null, "", archivedOnly = false)
        library.onInput("200 g kefir")
        assertEquals("kefir", library.query)
        assertFalse("still running", library.noMatch(otherScreenOpen = false))
        io.runAll()
        assertTrue(library.noMatch(otherScreenOpen = false))
        assertFalse(library.noMatch(otherScreenOpen = true))
    }

    @Test fun addingAFoodLogsItToTheMeal() {
        library.show(MealSlot.LUNCH, "", archivedOnly = false)
        library.log(kefir, MealSlot.LUNCH, 200.0, MeasureUnit.MILLILITER)
        assertEquals(listOf("log kefir 200.0 MILLILITER LUNCH 2026-10-28"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsLogged), diary.published)
        assertEquals("Added 1 food to Lunch on 2026-10-28", notices.message)
        library.log(kefir.copy(archived = true), MealSlot.LUNCH, 1.0, MeasureUnit.GRAM)
        assertEquals("an archived food is not logged", 1, store.calls.size)
    }

    @Test fun whileAPartIsResolvedATapOrAnAddIsThePickAndDoneOnlySearches() {
        resolving = true
        library.show(MealSlot.BREAKFAST, "kefir", archivedOnly = false)
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
        library.show(MealSlot.DINNER, "", archivedOnly = false)
        library.onInput("2 egg")
        library.submitInput()
        assertEquals("the lookup waits for the compute dispatcher", emptyList<String>(), store.calls)
        compute.runAll()
        assertEquals(listOf("log egg 2.0 PIECE DINNER 2026-10-28"), store.calls)
        assertEquals("", library.input)
        library.close()
        library.show(null, "", archivedOnly = false)
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
        library.show(MealSlot.DINNER, "", archivedOnly = false)
        library.onInput("2 egg, 100 g unknownium")
        library.submitInput()
        compute.runAll()
        assertEquals("One or more foods are not in your local library.", notices.message)
        assertEquals(emptyList<String>(), store.calls)
    }

    @Test fun aMixedBatchSavesTheBarAndHandsWhatIsLeftToThePostSaveHandling() {
        library.show(MealSlot.LUNCH, "", archivedOnly = false)
        library.onInput("2 egg, kefir")
        store.saved = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO, unresolvedSegments = listOf("kefir"))
        library.submitInput()
        assertEquals(listOf("save \"2 egg, kefir\" LUNCH 2026-10-28"), store.calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.BatchLogged("1 food logged to Lunch", store.saved.entries)), diary.published)
        val handed = afterSaves.single()
        assertEquals(listOf("kefir"), handed.unresolved)
        assertEquals(MealSlot.LUNCH, handed.meal)
        assertSame("the bar is the queue's source", library, handed.source)
        assertEquals("2 egg, kefir", handed.typedAtSave)
        handed.source.putBack("kefir")
        assertEquals("kefir", library.input)
    }

    @Test fun aMixedBatchWithoutAMealAsksFirst() {
        library.show(null, "", archivedOnly = false)
        library.onInput(" 2 egg, kefir ")
        library.submitInput()
        assertTrue(library.mixedMealPickerOpen)
        assertEquals("Choose a meal before adding a mixed batch with an unquantified food.", notices.message)
        library.chooseMixedMeal(MealSlot.BREAKFAST)
        assertFalse(library.mixedMealPickerOpen)
        assertEquals(listOf("save \"2 egg, kefir\" BREAKFAST 2026-10-28"), store.calls)
        assertEquals(" 2 egg, kefir ", afterSaves.single().typedAtSave)
    }

    @Test fun archiveAndRestoreTellTheCoordinatorAndReopenThePage() {
        val custom = kefir
        library.showFood(custom)
        library.archive(custom)
        assertEquals(listOf("archive kefir"), store.calls)
        assertEquals(listOf("kefir"), archived)
        assertNull(library.selectedFood)
        assertEquals("Food archived; saved diary entries remain readable.", notices.message)
        diary.foods = listOf(custom)
        library.restore(custom.copy(archived = true))
        assertEquals("restore kefir", store.calls.last())
        assertEquals(custom, library.selectedFood)
        assertEquals("Food restored to your library.", notices.message)
    }

    @Test fun theLastLoggedEntryGivesTheRepeatAmount() {
        diary.rows = listOf(entry("old").copy(foodId = "kefir", savedAtEpochMs = 1L), entry("new").copy(foodId = "kefir", savedAtEpochMs = 2L))
        diary.session.refresh()
        assertEquals("new", library.lastLoggedEntry(kefir)?.id)
        assertNull(library.lastLoggedEntry(egg))
    }
}
