package com.iron.breakfast.resolve

import com.iron.breakfast.QueueDispatcher
import com.iron.breakfast.app.DiaryChange
import com.iron.breakfast.app.Notices
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.SavedMeal
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.food.FoodFormStore
import com.iron.breakfast.food.NewFood
import com.iron.breakfast.library.LibraryState
import com.iron.breakfast.library.LibraryStore
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.log.LogStore
import com.iron.breakfast.today.OpenDiary
import com.iron.breakfast.today.entry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract between the Log, the Library, the food form and the resolve flow, over fakes: the resolve frame
 * (Log ↔ Library), the post-save handling of both saves, and where a queue's leftovers go.
 *
 * The view model's own wiring is not under test: [ResolveFlow]'s `returnToLog` and the food form's `openMeal` are
 * stand-ins written here. What the view model adds there (leaving the Library through its re-entrant `cancelPart`
 * check, closing Settings, hiding the search and product review, the queue abandoned by `openFocusedLibrary`, the
 * Meal screen coming back) is not covered; it cannot be built on the JVM until B17.
 */
class ResolveFrameTest {
    private val diary = OpenDiary()
    private val notices = Notices()
    private val calls = mutableListOf<String>()
    private var logSaved = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO)
    private val plannerFor = { pins: Map<String, FoodRecord> -> LogPlanner(FoodMatcher(FoodCatalog.fixtures), pins = pins) }
    private val events = mutableListOf<String>()

    private val resolve: ResolveFlow = ResolveFlow(
        diary.session,
        object : ResolveStore {
            override fun typed(raw: String) = plannerFor(emptyMap()).typed(raw)
            override fun search(query: String) = emptyList<RankedFood>()
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                calls += "queue log ${food.id} $meal"
            }
        },
        notices, diary.scope, Dispatchers.Unconfined,
        selectedDay = { "2026-10-28" },
        openLibrary = { meal, query -> library.show(meal, query) },
        // As the view model returns: the Library and what was over it close, the Log opens.
        returnToLog = { request, pick -> library.close(); foodForm.hide(); log.resume(request, pick) },
    )
    private val log: LogComposer = LogComposer(
        diary.session,
        object : LogStore {
            override fun planner(pins: Map<String, FoodRecord>) = plannerFor(pins)
            override suspend fun save(text: String, meal: MealSlot, dayKey: String, pins: Map<String, FoodRecord>): SavedMeal {
                calls += "log save \"$text\" $meal pins=${pins.keys}"
                return logSaved
            }
        },
        selectedDay = { "2026-10-28" },
        automaticMeal = { null },
        openMeal = { null },
        editing = { false },
        afterSave = resolve::afterSave,
        resolvePart = resolve::beginPart,
    )
    private val library: LibraryState = LibraryState(
        diary.session,
        object : LibraryStore {
            override suspend fun foodsMatching(query: String) = emptyList<FoodRecord>()
            override fun planner() = plannerFor(emptyMap())
            override fun search(phrase: String) = emptyList<RankedFood>()
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                calls += "library log ${food.id}"
            }
            override suspend fun save(text: String, meal: MealSlot, dayKey: String): SavedMeal {
                calls += "library save \"$text\" $meal"
                return SavedMeal(listOf(entry("e2")), NutritionValues.ZERO, unresolvedSegments = listOf("ayran", "kefir"))
            }
            override suspend fun deleteFood(id: String) {}
        },
        notices, diary.scope, QueueDispatcher(), Dispatchers.Unconfined, Dispatchers.Unconfined,
        selectedDay = { "2026-10-28" },
        resolvingPart = { resolve.resolvingPart },
        onPartPicked = resolve::finishPart,
        onDeleted = {},
        afterSave = resolve::afterSave,
    )
    private val foodForm: FoodForm = FoodForm(
        diary.session,
        object : FoodFormStore {
            override suspend fun updateFood(food: FoodRecord) { calls += "update ${food.id}" }
            override suspend fun saveFood(food: NewFood): FoodRecord { calls += "save food ${food.name}"; return stored(food) }
            override suspend fun saveFoodAndLog(food: NewFood, meal: MealSlot, dayKey: String): FoodRecord {
                calls += "save and log ${food.name} $meal"
                return stored(food)
            }
            private fun stored(new: NewFood) = food(new.name.lowercase(), new.name)
        },
        notices,
        selectedDay = { "2026-10-28" },
        openMeal = { if (library.open) library.target else null },
        queue = resolve::queueView,
        resolvingPart = { resolve.resolvingPart },
        offerContribution = {},
        startScanFor = {},
        onOpen = { fromResolver -> resolve.childOpened(fromResolver); events += "form open" },
        onClose = resolve::childClosed,
        onSaved = resolve::foodSaved,
    )

    private fun sendPartToTheLibrary() {
        log.open(MealSlot.LUNCH)
        log.onDraft("2 eggs, ayran 200ml")
        log.resolveNotInFoods(DraftRow.Unresolved("ayran 200ml", 8, 19))
    }

    @Test fun aPartPickedInTheLibraryComesBackPinnedAndAddLogsItOnce() {
        sendPartToTheLibrary()
        assertTrue(library.open)
        assertEquals(MealSlot.LUNCH, library.target)
        assertEquals("ayran", library.query)
        val ayran = food("ayran", "Ayran")
        library.openFood(ayran)
        assertFalse(library.open)
        assertTrue(log.open)
        assertEquals("2 eggs, Ayran 200ml", log.draft)
        log.submit()
        assertEquals(listOf("log save \"2 eggs, Ayran 200ml\" LUNCH pins=[Ayran 200ml]"), calls)
    }

    /** B15 fix kept: a New food made for a Log part is saved alone and pinned into the draft once, never logged twice. */
    @Test fun aNewFoodMadeForALogPartIsSavedAloneAndPinnedOnce() {
        sendPartToTheLibrary()
        foodForm.open("ayran")
        foodForm.draft.name = "Ayran"
        foodForm.draft.calories = "40"
        foodForm.save()
        assertEquals("saved alone, though the Library adds to Lunch", listOf("save food Ayran"), calls)
        assertEquals(listOf<DiaryChange>(DiaryChange.FoodsChanged), diary.published)
        assertEquals("Saved to your food library.", notices.message)
        assertFalse(foodForm.open)
        assertFalse(library.open)
        assertEquals("2 eggs, Ayran 200ml", log.draft)
        assertEquals(setOf("Ayran 200ml"), log.pinsSnapshot().keys)
        log.submit()
        assertEquals(listOf("save food Ayran", "log save \"2 eggs, Ayran 200ml\" LUNCH pins=[Ayran 200ml]"), calls)
    }

    @Test fun leavingTheLibraryWithoutAPickGivesTheDraftBack() {
        sendPartToTheLibrary()
        log.onDraft("not this")
        assertTrue(resolve.cancelPart())
        assertEquals("2 eggs, ayran 200ml", log.draft)
        assertTrue(log.open)
        assertFalse(library.open)
    }

    @Test fun addsLeftoversAreResolvedFromTheLogDraft() {
        logSaved = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO, unresolvedSegments = listOf("ayran", "kefir"))
        log.open(MealSlot.DINNER)
        log.onDraft("2 eggs, ayran, kefir")
        log.submit()
        assertEquals("ayran, kefir", log.draft)
        assertTrue(resolve.resolverOpen)
        assertEquals("Some foods need a match.", log.error)
        resolve.resolve(food("ayran"))
        assertEquals("queue log ayran DINNER", calls.last())
        assertEquals("kefir", log.draft)
        // A New food made from the resolver resolves the next part and logs it to the queue's meal.
        foodForm.open("kefir", fromResolver = true)
        foodForm.draft.name = "Kefir"
        foodForm.draft.calories = "60"
        foodForm.save()
        assertEquals("save and log Kefir DINNER", calls.last())
        assertEquals("", log.draft)
        assertFalse(resolve.resolverOpen)
    }

    private fun addLeavesAyranAndKefir() {
        logSaved = SavedMeal(listOf(entry("e1")), NutritionValues.ZERO, unresolvedSegments = listOf("ayran", "kefir"))
        log.open(MealSlot.DINNER)
        log.onDraft("2 eggs, ayran, kefir")
        log.submit()
        assertEquals("Some foods need a match.", log.error)
    }

    /** Deliberate (B17): once every part Add left is matched in the resolver, Daily no longer says "Some foods need a match.". */
    @Test fun matchingEveryLeftPartClearsTheLogsError() {
        addLeavesAyranAndKefir()
        resolve.resolve(food("ayran"))
        assertEquals("a part is still left", "Some foods need a match.", log.error)
        foodForm.open("kefir", fromResolver = true)
        foodForm.draft.name = "Kefir"
        foodForm.draft.calories = "60"
        foodForm.save()
        assertFalse(resolve.resolverOpen)
        assertNull(log.error)
    }

    /** A skipped part was never logged, so the message stays; so it does when the resolver is closed with parts left. */
    @Test fun aSkippedOrLeftPartKeepsTheLogsError() {
        addLeavesAyranAndKefir()
        resolve.resolve(food("ayran"))
        resolve.skip()
        assertFalse(resolve.resolverOpen)
        assertEquals("Some foods need a match.", log.error)
        addLeavesAyranAndKefir()
        resolve.close()
        assertEquals("ayran, kefir", log.draft)
        assertEquals("Some foods need a match.", log.error)
    }

    /** Deliberate (B16): a mixed batch's leftovers go back to the Library bar, never into the Log draft. */
    @Test fun aMixedBatchsLeftoversStayInTheLibraryBar() {
        log.onDraft("my own draft")
        library.show(MealSlot.LUNCH, "")
        library.onInput("2 egg, ayran, kefir")
        library.submitInput()
        assertEquals("library save \"2 egg, ayran, kefir\" LUNCH", calls.last())
        assertEquals("ayran, kefir", library.input)
        assertTrue(resolve.resolverOpen)
        assertEquals("the Library's batch sets no Log error", null, log.error)
        // Left while the queue lives: the parts still go back to where they were typed.
        library.close()
        resolve.skip()
        assertEquals("kefir", library.input)
        assertEquals("my own draft", log.draft)
    }
}
