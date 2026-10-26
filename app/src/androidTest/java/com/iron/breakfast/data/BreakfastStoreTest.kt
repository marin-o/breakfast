package com.iron.breakfast.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ReplacementResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The store on an in-memory database: batch Undo runs in one transaction and leaves entries edited since. */
@RunWith(AndroidJUnit4::class)
class BreakfastStoreTest {
    private lateinit var database: BreakfastDatabase
    private lateinit var store: BreakfastStore

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BreakfastDatabase::class.java).build()
        store = BreakfastStore(database)
        store.initialize()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entry(id: String): MealEntry {
        val food = FoodCatalog.fixtures.first()
        return MealEntry(
            id = id, foodId = food.id, foodName = food.name, enteredAmount = 1.0, enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 1.0, nutritionSnapshot = NutritionValues(10.0, 1.0, 1.0, 1.0), conversionProvenance = "1 g",
            approximateConversion = false, savedAtEpochMs = 1_000L, dayKey = "2026-10-25",
        )
    }

    @Test
    fun batchUndoDeletesTheBatchButAnEditedEntryStays() = runBlocking {
        val logged = listOf(entry("undo-a"), entry("undo-b"), entry("undo-c"))
        store.saveEntries(logged)
        val edited = logged[1].copy(enteredAmount = 2.0, nutritionSnapshot = NutritionValues(20.0, 2.0, 2.0, 2.0))
        store.editEntry(edited.id) { edited }

        val deleted = store.deleteEntries(logged)

        assertEquals(listOf("undo-a", "undo-c"), deleted)
        assertEquals(listOf(edited), store.entries())
        assertEquals(listOf("undo-b"), database.mealEntryDao().all().map { it.id })
    }

    @Test
    fun batchUndoSkipsAnEntryAlreadyDeleted() = runBlocking {
        val logged = listOf(entry("gone-a"), entry("gone-b"))
        store.saveEntries(logged)
        store.deleteEntry("gone-a")

        assertEquals(listOf("gone-b"), store.deleteEntries(logged))
        assertEquals(emptyList<MealEntry>(), store.entries())
    }

    @Test
    fun singleUndoRestoresTheOriginalIdAndSnapshot() = runBlocking {
        val original = entry("restore-a")
        store.saveEntries(listOf(original))
        store.deleteEntry(original.id)
        store.saveEntries(listOf(original))

        assertEquals(listOf(original), store.entries())
    }

    /** MOB-025 (A1-07): an edit of an entry deleted since is refused and does not re-create it. */
    @Test
    fun anEditOfADeletedEntryDoesNotRecreateIt() = runBlocking {
        val logged = entry("edit-gone")
        store.saveEntries(listOf(logged))
        store.deleteEntry(logged.id)

        assertFalse(store.editEntry(logged.id) { it.copy(enteredAmount = 5.0) })
        val result = BreakfastEngine(store).replaceEntryAmountUnit(logged.id, "5 g")
        assertTrue(result is ReplacementResult.Failure)
        assertEquals(emptyList<String>(), database.mealEntryDao().all().map { it.id })
    }

    /** MOB-025 (A1-07): the edit starts from the stored row, so a field it does not change keeps its stored value. */
    @Test
    fun anEditStartsFromTheStoredRow() = runBlocking {
        val logged = entry("edit-fresh")
        store.saveEntries(listOf(logged))
        store.editEntry(logged.id) { it.copy(mealSlot = MealSlot.DINNER) }

        // A second edit that only changes the amount keeps the meal the first one stored.
        store.editEntry(logged.id) { it.copy(enteredAmount = 3.0) }
        val stored = database.mealEntryDao().all().single().toDomain()
        assertEquals(MealSlot.DINNER, stored.mealSlot)
        assertEquals(3.0, stored.enteredAmount, 0.0)
    }

    /** MOB-025 (A1-11): saving an edited food keeps the stored archived state. */
    @Test
    fun upsertFoodKeepsTheStoredArchivedState() = runBlocking {
        val food = FoodRecord(
            id = "custom-archived", name = "Old bar", aliases = emptyList(), category = "Personal food", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(400.0, 10.0, 50.0, 15.0)),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
        )
        store.upsertFood(food)
        store.archiveFood(food.id)

        store.upsertFood(food.copy(name = "Old bar 2", archived = false))

        val stored = database.foodDao().byId(food.id)
        assertEquals("Old bar 2", stored?.name)
        assertEquals(true, stored?.archived)
    }

    /** MOB-028: an entry write keeps the very same foods list and preference map; a food write replaces the list. */
    @Test
    fun anEntryWriteKeepsTheFoodsListAndAFoodWriteReplacesIt() = runBlocking {
        val foods = store.foods()
        val preferences = store.preferenceMap()
        store.saveEntries(listOf(entry("keep-a")))
        store.editEntry("keep-a") { it.copy(enteredAmount = 2.0) }
        store.deleteEntry("keep-a")
        assertTrue(foods === store.foods())
        assertTrue(preferences === store.preferenceMap())

        store.reinforce(FoodCatalog.fixtures.first().id)
        assertTrue(foods === store.foods())
        assertTrue(preferences !== store.preferenceMap())

        store.archiveFood(FoodCatalog.fixtures.first().id)
        assertTrue(foods !== store.foods())
    }

    /** MOB-028: the per-table reloads add up to what a full read of the database gives. */
    @Test
    fun perTableReloadsEqualAFullReload() = runBlocking {
        val food = FoodRecord(
            id = "custom-reload", name = "Reload bar", aliases = listOf("rb"), category = "Personal food", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(300.0, 10.0, 40.0, 9.0)),
            defaultAmount = 50.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
        )
        store.saveEntries(listOf(entry("full-a"), entry("full-b")))
        store.upsertFood(food)
        store.reinforce(food.id)
        store.editEntry("full-a") { it.copy(enteredAmount = 4.0) }
        store.deleteEntry("full-b")
        store.archiveFood(food.id)

        val fresh = BreakfastStore(database).apply { initialize() }
        assertEquals(fresh.foods(), store.foods())
        assertEquals(fresh.entries(), store.entries())
        assertEquals(fresh.preferenceMap(), store.preferenceMap())
    }
}
