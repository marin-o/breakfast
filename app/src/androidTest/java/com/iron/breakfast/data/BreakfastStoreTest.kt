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
import org.junit.Assert.assertNull
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

    /** Delete food: the food, its aliases, conversions and learned pick go; its entries stay with their numbers. */
    @Test
    fun deleteFoodRemovesItsRowsAndKeepsItsEntries() = runBlocking {
        val food = FoodRecord(
            id = "custom-delete", name = "Old bar", aliases = listOf("ob"), category = "Personal food", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(400.0, 10.0, 50.0, 15.0)),
            conversions = listOf(com.iron.breakfast.domain.ServingConversion(MeasureUnit.PIECE, 40.0, "user", approximate = false, label = "bar")),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
        )
        val logged = entry("eaten").copy(foodId = food.id, foodName = food.name)
        store.addFoodAndEntry(food, logged)
        store.reinforce(food.id)

        store.deleteFood(food.id)

        assertNull(database.foodDao().byId(food.id))
        assertTrue(database.foodAliasDao().all().none { it.foodId == food.id })
        assertTrue(database.servingConversionDao().all().none { it.foodId == food.id })
        assertNull(database.preferenceDao().byFood(food.id))
        assertTrue(store.foods().none { it.id == food.id })
        assertEquals(listOf(logged), store.entries().filter { it.id == "eaten" })
        assertEqualsFullRead()
    }

    /** Foods are no longer archived: a row an older build archived is deleted when the store opens, once. */
    @Test
    fun theStoreDeletesArchivedFoodsWhenItOpensAndOnlyThem() = runBlocking {
        val food = FoodRecord(
            id = "custom-archived", name = "Old jam", aliases = listOf("jam"), category = "Personal food", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(250.0, 0.5, 60.0, 0.1)),
            defaultAmount = 20.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
        )
        val kept = food.copy(id = "custom-kept", name = "New jam", aliases = emptyList())
        store.addFoodAndEntry(food, entry("jam").copy(foodId = food.id, foodName = food.name))
        store.addFood(kept)
        store.reinforce(food.id)
        // As an older build left it.
        database.foodDao().upsert(database.foodDao().byId(food.id)!!.copy(archived = true))
        val catalogue = database.foodDao().all().filter { it.origin != FoodOrigin.CUSTOM.name }

        val reopened = BreakfastStore(database)
        reopened.initialize()

        assertNull(database.foodDao().byId(food.id))
        assertTrue(database.foodAliasDao().all().none { it.foodId == food.id })
        assertNull(database.preferenceDao().byFood(food.id))
        assertEquals("New jam", database.foodDao().byId(kept.id)?.name)
        assertEquals(catalogue, database.foodDao().all().filter { it.origin != FoodOrigin.CUSTOM.name })
        assertEquals(food.name, reopened.entries().single { it.id == "jam" }.foodName)
        assertTrue(database.foodDao().archivedIds().isEmpty())

        // Opening again changes nothing.
        val after = database.foodDao().all()
        BreakfastStore(database).initialize()
        assertEquals(after, database.foodDao().all())
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

        store.deleteFood(FoodCatalog.fixtures.first().id)
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
        store.deleteFood(food.id)
        assertEqualsFullRead()

        val second = food.copy(id = "custom-reload-2", name = "Second bar", aliases = listOf("sb"))
        store.addFood(second)
        store.addFoodAndEntry(food.copy(id = "custom-reload-3", name = "Third bar"), entry("full-c").copy(foodId = "custom-reload-3"))
        store.saveEntries(listOf(entry("full-d"), entry("full-e")))
        store.deleteEntries(listOf(entry("full-d")))
        assertEqualsFullRead()

        // Import: every table replaced at once, from the rows as they are now plus one more entry.
        val rows = store.exportRows()
        store.replaceDiary(rows.copy(entries = rows.entries + entry("full-f").toEntity()))
        assertEqualsFullRead()
    }

    /** MOB-028 with MOB-025: after a failed reload, the next write (entry-only) reloads every table, foods too. */
    @Test
    fun aStaleSnapshotIsReloadedInFullByTheNextWrite() = runBlocking {
        val food = FoodRecord(
            id = "custom-stale", name = "Stale bar", aliases = emptyList(), category = "Personal food", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(250.0, 5.0, 30.0, 8.0)),
            defaultAmount = 40.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
        )
        store.failNextRefreshForTesting = true
        store.upsertFood(food)
        // Committed, but the snapshot still lacks the food.
        assertTrue(database.foodDao().byId(food.id) != null)
        assertTrue(store.foods().none { it.id == food.id })

        store.saveEntries(listOf(entry("stale-a")))
        assertTrue(store.foods().any { it.id == food.id })
        assertEqualsFullRead()
    }

    private suspend fun assertEqualsFullRead() {
        val fresh = BreakfastStore(database).apply { initialize() }
        assertEquals(fresh.foods(), store.foods())
        assertEquals(fresh.entries(), store.entries())
        assertEquals(fresh.preferenceMap(), store.preferenceMap())
    }

    /**
     * MOB-028: an entry-only write keeps the engine's matcher instance, a preference write keeps it too (preferences
     * reach ranking through a new planner), and a food write rebuilds it.
     */
    @Test
    fun onlyAFoodWriteRebuildsTheMatcher() = runBlocking {
        val engine = BreakfastEngine(store)
        val matcher = engine.matcherForTesting()
        val planner = engine.planner()
        store.saveEntries(listOf(entry("matcher-a")))
        assertTrue(matcher === engine.matcherForTesting())
        assertTrue(planner === engine.planner())

        engine.reinforce(FoodCatalog.fixtures.first().id)
        assertTrue(matcher === engine.matcherForTesting())
        assertTrue(planner !== engine.planner())

        store.deleteFood(FoodCatalog.fixtures.first().id)
        assertTrue(matcher !== engine.matcherForTesting())
    }

    /** A second start (initialize on the same database) rewrites no catalogue row, so the table order stays. */
    @Test
    fun aSecondStartRewritesNothing() = runBlocking {
        val before = database.foodDao().all()
        BreakfastStore(database).initialize()
        assertEquals(before, database.foodDao().all())
        assertEquals(store.foods(), BreakfastStore(database).apply { initialize() }.foods())
    }

    private val personal = FoodRecord(
        id = "custom-delete-all", name = "Домашен бурек", aliases = listOf("burek"), category = "Personal food", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(280.0, 9.0, 30.0, 17.5)),
        defaultAmount = 150.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
    )

    /** A diary with a personal food, an edited built-in food, entries and two learned picks. */
    private suspend fun fillForDeleteAll() {
        store.upsertFood(personal)
        val builtIn = FoodCatalog.fixtures.first()
        store.upsertFood(builtIn.copy(name = "Edited ${builtIn.name}", personalOverride = true))
        store.saveEntries(listOf(entry("all-a"), entry("all-b")))
        store.reinforce(personal.id)
        store.reinforce(builtIn.id)
    }

    /** B9 Delete all data: entries and foods go, the built-in catalogue comes back as on a first start, picks stay. */
    @Test
    fun deleteAllRemovesEntriesAndFoodsAndSeedsTheCatalogueAgain() = runBlocking {
        val fresh = database.foodDao().all()
        fillForDeleteAll()
        val preferences = database.preferenceDao().all()

        store.deleteAllData(alsoPreferences = false)

        assertEquals(emptyList<MealEntry>(), store.entries())
        assertEquals(emptyList<String>(), database.mealEntryDao().all().map { it.id })
        assertEquals(null, database.foodDao().byId(personal.id))
        assertEquals(fresh, database.foodDao().all())
        assertEquals(FoodCatalog.fixtures.map { it.id }.sorted(), store.foods().map { it.id }.sorted())
        assertEquals(preferences, database.preferenceDao().all())
    }

    /** B9 Delete all data with "Also settings and learned picks": the learned preferences go in the same transaction. */
    @Test
    fun deleteAllWithLearnedPicksAlsoDeletesThePreferences() = runBlocking {
        fillForDeleteAll()

        store.deleteAllData(alsoPreferences = true)

        assertEquals(emptyList<String>(), database.preferenceDao().all().map { it.foodId })
        assertTrue(store.preferenceMap().isEmpty())
        assertEquals(emptyList<MealEntry>(), store.entries())
    }

    /** B9 Delete all data is one transaction: a failure after the deletes leaves the diary exactly as it was. */
    @Test
    fun aFailingDeleteAllLeavesTheDiary() = runBlocking {
        fillForDeleteAll()
        val before = store.exportRows()

        val failed = runCatching { store.deleteAllData(alsoPreferences = true) { error("injected failure") } }

        assertTrue(failed.isFailure)
        assertEquals(before, store.exportRows())
        assertEquals(listOf("all-a", "all-b"), store.entries().map { it.id }.sorted())
    }
}
