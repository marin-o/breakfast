package com.iron.breakfast.data

import com.iron.breakfast.domain.WeightPoint
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
import com.iron.breakfast.domain.asFood
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

    /** Only a personal food can be deleted: a bundled one is refused and stays. */
    @Test
    fun onlyAPersonalFoodIsDeleted() = runBlocking {
        val bundled = FoodCatalog.fixtures.first()
        try {
            store.deleteFood(bundled.id)
            org.junit.Assert.fail("a bundled food is not deleted")
        } catch (expected: IllegalArgumentException) {
            // Refused inside the transaction: nothing written.
        }
        assertTrue(store.foods().any { it.id == bundled.id })
        assertTrue(database.foodDao().byId(bundled.id) != null)
    }

    /** Its food deleted, an entry's amount still changes: its snapshot scales; Replace keeps it, meal and day too. */
    @Test
    fun anEntryWhoseFoodWasDeletedScalesItsAmount() = runBlocking {
        val food = FoodRecord(
            id = "custom-gone", name = "Gone bar", aliases = emptyList(), category = "Personal food", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(400.0, 10.0, 50.0, 20.0)),
            defaultAmount = 50.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
        )
        val engine = BreakfastEngine(store)
        val logged = engine.cacheFoodAndLog(food, 50.0, MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-28")
        engine.deleteFood(food.id)

        val quick = engine.replaceEntryAmountUnit(logged.id, "75 g") as ReplacementResult.Success
        assertEquals(300.0, quick.entry.nutritionSnapshot.calories, 1e-9)
        assertEquals(7.5, quick.entry.nutritionSnapshot.protein!!, 1e-9)
        assertEquals(37.5, quick.entry.nutritionSnapshot.carbohydrates!!, 1e-9)
        assertEquals(15.0, quick.entry.nutritionSnapshot.fat!!, 1e-9)
        assertEquals(75.0, store.entries().single { it.id == logged.id }.enteredAmount, 0.0)
        assertTrue(engine.replaceEntryAmountUnit(logged.id, "1 tbsp") is ReplacementResult.Failure)

        val full = engine.replaceEntryDraft(logged.id, food.id, 25.0, MeasureUnit.GRAM, MealSlot.DINNER, "2026-10-27") as ReplacementResult.Success
        assertEquals(100.0, full.entry.nutritionSnapshot.calories, 1e-9)
        assertEquals(MealSlot.DINNER, full.entry.mealSlot)
        assertEquals("2026-10-27", full.entry.dayKey)
        assertEquals(food.id, full.entry.foodId)
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

        store.upsertFood(FoodCatalog.fixtures.first().copy(name = "Edited"))
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
        assertEquals(fresh.recipes(), store.recipes())
    }

    private val mince = FoodRecord(
        id = "custom-mince", name = "Beef mince", aliases = emptyList(), category = "Personal food", origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(250.0, 20.0, 0.0, 18.0)),
        defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM, personalOverride = true,
    )

    private val chili = com.iron.breakfast.domain.Recipe(
        id = "recipe-chili", name = "Чили", aliases = listOf("chili"), portions = 4.0, totalWeight = 1200.0,
        ingredients = listOf(
            com.iron.breakfast.domain.RecipeIngredient("custom-mince", "Beef mince", 500.0, MeasureUnit.GRAM, 500.0, MeasureUnit.GRAM, NutritionValues(1250.0, 100.0, 0.0, 90.0)),
            com.iron.breakfast.domain.RecipeIngredient("gone", "Kidney beans", 1.0, MeasureUnit.PIECE, 240.0, MeasureUnit.GRAM, NutritionValues(300.0, 20.0, null, 1.0)),
        ),
    )

    /** A recipe is saved, edited and deleted with its aliases, ingredients and learned pick; entries keep their own numbers. */
    @Test
    fun aRecipeIsSavedEditedAndDeletedWithItsRows() = runBlocking {
        store.saveRecipe(chili)
        assertEquals(listOf(chili), store.recipes())
        val edited = chili.copy(name = "Chili", aliases = emptyList(), ingredients = chili.ingredients.take(1))
        store.saveRecipe(edited)
        assertEquals(listOf(edited), store.recipes())
        assertEquals(1, database.recipeIngredientDao().all().size)
        assertTrue(database.recipeAliasDao().all().isEmpty())
        val logged = entry("chili-entry").copy(foodId = chili.id, foodName = "Chili")
        store.saveEntries(listOf(logged))
        store.reinforce(chili.id)

        store.deleteRecipe(chili.id)

        assertTrue(store.recipes().isEmpty())
        assertTrue(database.recipeDao().all().isEmpty() && database.recipeIngredientDao().all().isEmpty())
        assertNull(database.preferenceDao().byFood(chili.id))
        assertEquals(listOf(logged), store.entries().filter { it.id == "chili-entry" })
        assertEqualsFullRead()
    }

    /** A deleted food leaves its ingredient with the numbers it counted last: the food as it was edited, not as first saved. */
    @Test
    fun aDeletedFoodLeavesItsIngredientWithItsLastNumbers() = runBlocking {
        store.upsertFood(mince)
        store.saveRecipe(chili)
        // Edited after the recipe was saved: the recipe follows it, and keeps that once the food is gone.
        store.upsertFood(mince.copy(name = "Lean mince", nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(150.0, 22.0, 0.0, 5.0))))
        store.deleteFood(mince.id)

        val kept = store.recipes().single().ingredients
        assertEquals("Lean mince", kept[0].foodName)
        assertEquals(750.0, kept[0].nutritionSnapshot.calories, 1e-9)
        assertEquals(110.0, kept[0].nutritionSnapshot.protein!!, 1e-9)
        assertEquals(chili.ingredients[1], kept[1])
        assertEqualsFullRead()
    }

    /**
     * A logged recipe is one entry with its own snapshot and never a food: through the Log, the Library's or the
     * resolver's log, and its numbers stay when the recipe is edited or deleted. The Log matches it; ingredients do not.
     */
    @Test
    fun aLoggedRecipeIsOneEntryWithItsOwnNumbersAndNeverAFood() = runBlocking {
        val engine = BreakfastEngine(store)
        store.upsertFood(mince)
        engine.saveRecipe(chili)
        assertEquals("recipe-chili", engine.planner().plan("1.5 chili").entries.single().food.id)
        assertTrue(engine.ingredientPlanner().plan("1.5 chili").entries.isEmpty())
        val logged = engine.save("1.5 chili", mealSlot = MealSlot.DINNER, dayKey = "2026-10-29").entries.single()
        val recipeFood = engine.recipes().single().asFood()
        val byLibrary = engine.cacheFoodAndLog(recipeFood, 300.0, MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-29")
        assertTrue(store.foods().none { it.id == chili.id })
        assertEquals(recipeFood.name, logged.foodName)
        // 1.5 of 4 portions of (1250 + 300) kcal; 300 g of 1200 g.
        assertEquals(581.25, logged.nutritionSnapshot.calories, 1e-9)
        assertEquals(387.5, byLibrary.nutritionSnapshot.calories, 1e-9)

        engine.saveRecipe(chili.copy(name = "Renamed", portions = 2.0))
        engine.deleteRecipe(chili.id)
        assertEquals(listOf(logged, byLibrary), store.entries().filter { it.foodId == chili.id })
    }

    /**
     * A recipe entry is edited against its recipe while it exists: quick edit from portions to grams and back, full
     * edit as a live recipe. Once the recipe is deleted, its amount scales the entry's own snapshot, in its own unit.
     */
    @Test
    fun aRecipeEntryIsEditedAgainstItsRecipeAndScalesOnceItIsGone() = runBlocking {
        val engine = BreakfastEngine(store)
        store.upsertFood(mince)
        engine.saveRecipe(chili)
        val logged = engine.save("1 portion chili", mealSlot = MealSlot.DINNER, dayKey = "2026-10-29").entries.single()
        assertEquals(MeasureUnit.PORTION, logged.enteredUnit)
        // (1250 + 300) kcal in 1200 g: 300 g is 387.5 kcal.
        val grams = (engine.replaceEntryAmountUnit(logged.id, "300 g") as ReplacementResult.Success).entry
        assertEquals(MeasureUnit.GRAM, grams.enteredUnit)
        assertEquals(387.5, grams.nutritionSnapshot.calories, 1e-9)
        val back = (engine.replaceEntryAmountUnit(logged.id, "2 portion") as ReplacementResult.Success).entry
        assertEquals(775.0, back.nutritionSnapshot.calories, 1e-9)
        val full = (engine.replaceEntryDraft(logged.id, chili.id, 600.0, MeasureUnit.GRAM, MealSlot.LUNCH, "2026-10-29") as ReplacementResult.Success).entry
        assertEquals(775.0, full.nutritionSnapshot.calories, 1e-9)
        assertEquals(MealSlot.LUNCH, full.mealSlot)

        engine.deleteRecipe(chili.id)
        val scaled = (engine.replaceEntryAmountUnit(logged.id, "300 g") as ReplacementResult.Success).entry
        assertEquals(387.5, scaled.nutritionSnapshot.calories, 1e-9)
        assertTrue(engine.replaceEntryAmountUnit(logged.id, "1 portion") is ReplacementResult.Failure)
    }

    /** Export, Replace diary and Delete all carry or clear the recipes with everything else. */
    @Test
    fun recipesGoWithTheDiaryThroughImportAndDeleteAll() = runBlocking {
        store.upsertFood(mince)
        store.saveRecipe(chili)
        val rows = store.exportRows()
        assertEquals(1, rows.recipes.size)
        assertEquals(2, rows.recipeIngredients.size)
        assertEquals(1, rows.recipeAliases.size)
        store.deleteRecipe(chili.id)
        store.replaceDiary(rows)
        assertEquals(listOf(chili), store.recipes())
        assertEqualsFullRead()

        store.deleteAllData(alsoPreferences = false)
        assertTrue(store.recipes().isEmpty())
        assertTrue(database.recipeIngredientDao().all().isEmpty() && database.recipeAliasDao().all().isEmpty())
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

        store.upsertFood(FoodCatalog.fixtures.first().copy(name = "Edited"))
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

    private fun point(day: String, kg: Double, at: Long = 1L) = WeightPoint(day, kg, at)

    @Test
    fun aSecondWeightPointOnADayReplacesIt() = runBlocking {
        store.saveWeightPoint(point("2026-10-01", 80.0))
        store.saveWeightPoint(point("2026-10-01", 79.5, 2L))
        store.saveWeightPoint(point("2026-10-02", 79.0))
        assertEquals(listOf(point("2026-10-01", 79.5, 2L), point("2026-10-02", 79.0)), store.weightPoints())
    }

    @Test
    fun aDeletedWeightPointIsGone() = runBlocking {
        store.saveWeightPoint(point("2026-10-01", 80.0))
        store.deleteWeightPoint("2026-10-01")
        assertTrue(store.weightPoints().isEmpty())
    }

    @Test
    fun aReplaceWithPointsReplacesThemAndOneWithoutKeepsThem() = runBlocking {
        store.saveWeightPoint(point("2026-10-01", 80.0))
        val rows = store.exportRows()
        store.replaceDiary(rows.copy(weightPoints = null))
        assertEquals(listOf(point("2026-10-01", 80.0)), store.weightPoints())
        store.replaceDiary(rows.copy(weightPoints = listOf(WeightPointEntity("2026-10-05", 70.0, 5L))))
        assertEquals(listOf(point("2026-10-05", 70.0, 5L)), store.weightPoints())
        store.replaceDiary(rows.copy(weightPoints = emptyList()))
        assertTrue(store.weightPoints().isEmpty())
    }

    @Test
    fun deleteAllClearsTheWeightPoints() = runBlocking {
        store.saveWeightPoint(point("2026-10-01", 80.0))
        store.deleteAllData(alsoPreferences = false)
        assertTrue(store.weightPoints().isEmpty())
    }
}
