package com.iron.breakfast.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        store.replaceEntry(edited)

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
}
