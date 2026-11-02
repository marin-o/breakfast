package mk.bosa.breakfast.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mk.bosa.breakfast.domain.TestFoods
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionValues
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreakfastDatabaseTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: BreakfastDatabase
    /** A rule, so the databases it opened are closed when each test ends ([sweep] then removes what is left). */
    @get:Rule
    val migrationHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BreakfastDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    /** The migration tests' own databases, deleted in [tearDown] also when a test fails. */
    private val migrationNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "breakfast-test-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, BreakfastDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        deleteTestDatabase(context, databaseName)
        migrationNames.forEach { deleteTestDatabase(context, it) }
    }

    companion object {
        /** After the whole class, every rule and close included: no test file or lock is left on the device. */
        @JvmStatic
        @AfterClass
        fun sweep() = sweepTestDatabases(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun storedFoodsAndAMealSnapshotSurviveReopen() = runBlocking {
        seed(TestFoods.fixtures)

        val food = TestFoods.fixtures.first()
        val entry = MealEntry(
            id = "relaunch-entry",
            foodId = food.id,
            foodName = food.name,
            enteredAmount = 1.0,
            enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 1.0,
            nutritionSnapshot = NutritionValues(1.0, 0.0, 2.0, 3.0),
            conversionProvenance = "1 g",
            approximateConversion = false,
            savedAtEpochMs = 1L
        )
        database.mealEntryDao().replace(entry.toEntity())
        database.close()
        database = Room.databaseBuilder(context, BreakfastDatabase::class.java, databaseName).build()

        val restored = database.mealEntryDao().all().single().toDomain()
        assertEquals(entry, restored)
        assertTrue(database.foodDao().ids().contains(food.id))
    }

    @Test
    fun foodUpsertReplacesAliasesAndConversionsTransactionally() = runBlocking {
        val food = TestFoods.fixtures.first()
        seed(listOf(food))
        val edited = food.copy(
            name = "Edited ${food.name}",
            aliases = listOf("edited alias"),
            conversions = emptyList()
        )
        database.withTransaction {
            database.foodDao().upsert(edited.toEntity())
            database.foodAliasDao().deleteForFood(food.id)
            database.servingConversionDao().deleteForFood(food.id)
            database.foodAliasDao().insertAll(edited.toAliasEntities())
            database.servingConversionDao().insertAll(edited.toConversionEntities())
        }
        assertEquals(edited.name, database.foodDao().all().single().name)
        assertEquals(listOf("edited alias"), database.foodAliasDao().all().map { it.alias })
        assertTrue(database.servingConversionDao().all().isEmpty())
    }

    @Test
    fun migration1To4PreservesPopulatedRowsAndAddsLocalOverrideFlag() {
        val name = "migration-${System.nanoTime()}.db".also(migrationNames::add)
        val old = migrationHelper.createDatabase(name, 1)
        old.execSQL("CREATE TABLE IF NOT EXISTS foods (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, origin TEXT NOT NULL, nutrition_amount REAL NOT NULL, nutrition_unit TEXT NOT NULL, calories REAL NOT NULL, protein REAL, carbohydrates REAL, fat REAL, default_amount REAL NOT NULL, default_unit TEXT NOT NULL, source_id TEXT, source_name TEXT, source_release TEXT, preparation TEXT, source_description TEXT)")
        old.execSQL("CREATE TABLE IF NOT EXISTS food_aliases (foodId TEXT NOT NULL, alias TEXT NOT NULL, PRIMARY KEY(foodId, alias))")
        old.execSQL("CREATE TABLE IF NOT EXISTS serving_conversions (foodId TEXT NOT NULL, unit TEXT NOT NULL, base_units_per_serving REAL NOT NULL, provenance TEXT NOT NULL, approximate INTEGER NOT NULL, PRIMARY KEY(foodId, unit))")
        old.execSQL("CREATE TABLE IF NOT EXISTS preferences (foodId TEXT NOT NULL PRIMARY KEY, explicit_count INTEGER NOT NULL, last_reinforced_epoch_ms INTEGER NOT NULL)")
        old.execSQL("CREATE TABLE IF NOT EXISTS meal_entries (id TEXT NOT NULL PRIMARY KEY, food_id TEXT NOT NULL, food_name TEXT NOT NULL, entered_amount REAL NOT NULL, entered_unit TEXT NOT NULL, resolved_base_amount REAL NOT NULL, snapshot_calories REAL NOT NULL, snapshot_protein REAL, snapshot_carbohydrates REAL, snapshot_fat REAL, conversion_provenance TEXT NOT NULL, approximate_conversion INTEGER NOT NULL, saved_at_epoch_ms INTEGER NOT NULL, source_phrase TEXT NOT NULL, meal_slot TEXT NOT NULL, day_key TEXT NOT NULL)")
        old.execSQL("INSERT INTO foods VALUES ('legacy','Legacy food','test','CUSTOM',100.0,'GRAM',250.0,NULL,12.0,5.0,100.0,'GRAM',NULL,NULL,NULL,NULL,NULL)")
        old.execSQL("INSERT INTO food_aliases VALUES ('legacy','old alias')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('legacy','GRAM',100.0,'legacy',0)")
        old.execSQL("INSERT INTO preferences VALUES ('legacy',3,1234)")
        old.execSQL("INSERT INTO meal_entries VALUES ('entry','legacy','Legacy food',1.0,'PIECE',100.0,250.0,NULL,12.0,5.0,'medium banana reference',1,1234,'legacy','BREAKFAST','1970-01-01')")
        old.execSQL("INSERT INTO meal_entries VALUES ('missing-entry','missing','Missing food',1.0,'TEASPOON',5.3,31.0,NULL,NULL,NULL,'standard teaspoon',0,1234,'missing','BREAKFAST','1970-01-01')")
        old.close()
        val migrated = migrationHelper.runMigrationsAndValidate(name, 5, true, ROOM_MIGRATION_1_2, ROOM_MIGRATION_2_3, ROOM_MIGRATION_3_4, ROOM_MIGRATION_4_5)
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM foods").use { it.moveToFirst(); it.getInt(0) })
        assertEquals("old alias", migrated.query("SELECT alias FROM food_aliases").use { it.moveToFirst(); it.getString(0) })
        assertEquals(2, migrated.query("SELECT COUNT(*) FROM meal_entries").use { it.moveToFirst(); it.getInt(0) })
        assertEquals("GRAM", migrated.query("SELECT resolved_base_unit FROM meal_entries WHERE id = 'entry'").use { it.moveToFirst(); it.getString(0) })
        assertEquals("UNKNOWN", migrated.query("SELECT resolved_base_unit FROM meal_entries WHERE id = 'missing-entry'").use { it.moveToFirst(); it.getString(0) })
        migrated.execSQL("UPDATE foods SET nutrition_unit = 'MILLILITER' WHERE id = 'legacy'")
        assertEquals("GRAM", migrated.query("SELECT resolved_base_unit FROM meal_entries WHERE id = 'entry'").use { it.moveToFirst(); it.getString(0) })
        assertEquals(0, migrated.query("SELECT archived FROM foods WHERE id = 'legacy'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, migrated.query("SELECT personal_override FROM foods WHERE id = 'legacy'").use { it.moveToFirst(); it.getInt(0) })
        migrated.close(); deleteTestDatabase(context, name)
    }

    @Test
    fun migration5To6AddsNullableServingLabelWithoutChangingExistingRows() {
        val name = "migration-v6-${System.nanoTime()}.db".also(migrationNames::add)
        val old = migrationHelper.createDatabase(name, 5)
        old.execSQL("INSERT INTO serving_conversions (foodId, unit, base_units_per_serving, provenance, approximate) VALUES ('legacy','MILLILITER',330.0,'legacy',0)")
        old.close()

        val migrated = migrationHelper.runMigrationsAndValidate(name, 6, true, ROOM_MIGRATION_5_6)
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM serving_conversions").use { it.moveToFirst(); it.getInt(0) })
        assertEquals("", migrated.query("SELECT label FROM serving_conversions WHERE foodId = 'legacy'").use { it.moveToFirst(); it.getString(0) })
        migrated.execSQL("INSERT INTO serving_conversions VALUES ('legacy','MILLILITER',330.0,'personal',0,'bottle')")
        migrated.execSQL("INSERT INTO serving_conversions VALUES ('legacy','MILLILITER',250.0,'personal',1,'glass')")
        assertEquals(3, migrated.query("SELECT COUNT(*) FROM serving_conversions WHERE foodId = 'legacy'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals("bottle", migrated.query("SELECT label FROM serving_conversions WHERE foodId = 'legacy' AND label = 'bottle'").use { it.moveToFirst(); it.getString(0) })
        migrated.close(); deleteTestDatabase(context, name)
    }

    @Test
    fun migration2To3PreservesFoodAliasAndEntry() {
        val name = "migration-v2-${System.nanoTime()}.db".also(migrationNames::add)
        val old = migrationHelper.createDatabase(name, 2)
        old.execSQL("INSERT INTO foods (id,name,category,origin,nutrition_amount,nutrition_unit,calories,default_amount,default_unit,barcode) VALUES ('saved','Saved food','test','CUSTOM',100.0,'GRAM',250.0,100.0,'GRAM','1234567890123')")
        old.execSQL("INSERT INTO food_aliases VALUES ('saved','usual food')")
        old.execSQL("INSERT INTO meal_entries VALUES ('saved-entry','saved','Saved food',100.0,'GRAM',100.0,250.0,NULL,NULL,NULL,'label',0,1234,'usual food','BREAKFAST','1970-01-01')")
        old.close()

        val migrated = migrationHelper.runMigrationsAndValidate(name, 4, true, ROOM_MIGRATION_2_3, ROOM_MIGRATION_3_4)
        assertEquals("1234567890123", migrated.query("SELECT barcode FROM foods WHERE id = 'saved'").use { it.moveToFirst(); it.getString(0) })
        assertEquals(0, migrated.query("SELECT archived FROM foods WHERE id = 'saved'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, migrated.query("SELECT personal_override FROM foods WHERE id = 'saved'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals("usual food", migrated.query("SELECT alias FROM food_aliases WHERE foodId = 'saved'").use { it.moveToFirst(); it.getString(0) })
        assertEquals(250.0, migrated.query("SELECT snapshot_calories FROM meal_entries WHERE id = 'saved-entry'").use { it.moveToFirst(); it.getDouble(0) }, 0.0)
        migrated.close(); deleteTestDatabase(context, name)
    }

    /**
     * The phone's diary is installed over (`adb install -r`), so 6 to 7 must keep every row as it was: a diary as the
     * owner has it (personal and edited foods, Cyrillic names and aliases, labelled servings, learned picks, entries
     * whose food was deleted) comes through unchanged, and the three recipe tables start empty and take rows.
     */
    @Test
    fun migration6To7KeepsTheWholeDiaryAndAddsEmptyRecipeTables() {
        val name = "migration-v7-${System.nanoTime()}.db".also(migrationNames::add)
        val old = migrationHelper.createDatabase(name, 6)
        old.execSQL("INSERT INTO foods VALUES ('custom-burek','Бурек со сирење','Personal food','CUSTOM',100.0,'GRAM',282.0,9.4,26.1,15.2,100.0,'GRAM',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,1)")
        old.execSQL("INSERT INTO foods VALUES ('custom-kefir','Kefir 2.8%','Personal food','CUSTOM',100.0,'MILLILITER',55.0,3.2,4.1,NULL,330.0,'MILLILITER','5310382009733','Open Food Facts',NULL,NULL,'Saved from an incomplete Open Food Facts barcode result','Bitola','5310382009733',0,1)")
        old.execSQL("INSERT INTO foods VALUES ('egg','Egg','Eggs','GENERIC',100.0,'GRAM',143.0,12.6,0.7,9.5,1.0,'PIECE','fdc-748967','USDA','2019-04',NULL,'Egg, whole, raw, fresh',NULL,NULL,0,1)")
        old.execSQL("INSERT INTO food_aliases VALUES ('custom-burek','бурек')")
        old.execSQL("INSERT INTO food_aliases VALUES ('custom-burek','burek')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-kefir','MILLILITER',330.0,'personal serving',0,'bottle')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-kefir','MILLILITER',100.0,'custom basis',0,'')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('egg','PIECE',50.0,'USDA medium egg',1,'')")
        old.execSQL("INSERT INTO preferences VALUES ('custom-burek',7,1761650000000)")
        old.execSQL("INSERT INTO meal_entries (id, food_id, food_name, entered_amount, entered_unit, resolved_base_amount, snapshot_calories, snapshot_protein, snapshot_carbohydrates, snapshot_fat, conversion_provenance, approximate_conversion, saved_at_epoch_ms, source_phrase, meal_slot, day_key, resolved_base_unit) VALUES ('e-1','custom-burek','Бурек со сирење',250.0,'GRAM',250.0,705.0,23.5,65.25,38.0,'entered g on 100.0g basis',0,1761650000000,'250 g бурек','BREAKFAST','2026-10-28','GRAM')")
        old.execSQL("INSERT INTO meal_entries (id, food_id, food_name, entered_amount, entered_unit, resolved_base_amount, snapshot_calories, snapshot_protein, snapshot_carbohydrates, snapshot_fat, conversion_provenance, approximate_conversion, saved_at_epoch_ms, source_phrase, meal_slot, day_key, resolved_base_unit) VALUES ('e-2','custom-kefir','Kefir 2.8%',1.0,'PIECE',330.0,181.5,10.56,13.53,NULL,'personal serving',0,1761650000001,'1 bottle kefir','BREAKFAST','2026-10-28','MILLILITER')")
        old.execSQL("INSERT INTO meal_entries (id, food_id, food_name, entered_amount, entered_unit, resolved_base_amount, snapshot_calories, snapshot_protein, snapshot_carbohydrates, snapshot_fat, conversion_provenance, approximate_conversion, saved_at_epoch_ms, source_phrase, meal_slot, day_key, resolved_base_unit) VALUES ('e-3','custom-gone','Ајвар',2.0,'TABLESPOON',30.0,60.0,NULL,NULL,NULL,'Approximate fallback',1,1761600000000,'2 tbsp ајвар','DINNER','2026-10-27','GRAM')")
        old.close()

        val migrated = migrationHelper.runMigrationsAndValidate(name, 7, true, ROOM_MIGRATION_6_7)
        fun count(table: String) = migrated.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(3, count("foods"))
        assertEquals(2, count("food_aliases"))
        assertEquals(3, count("serving_conversions"))
        assertEquals(1, count("preferences"))
        assertEquals(3, count("meal_entries"))
        assertEquals(0, count("recipes"))
        assertEquals(0, count("recipe_aliases"))
        assertEquals(0, count("recipe_ingredients"))
        assertEquals("Бурек со сирење", migrated.query("SELECT name FROM foods WHERE id = 'custom-burek'").use { it.moveToFirst(); it.getString(0) })
        assertEquals("bottle", migrated.query("SELECT label FROM serving_conversions WHERE foodId = 'custom-kefir' AND base_units_per_serving = 330.0").use { it.moveToFirst(); it.getString(0) })
        assertEquals(7, migrated.query("SELECT explicit_count FROM preferences WHERE foodId = 'custom-burek'").use { it.moveToFirst(); it.getInt(0) })
        migrated.query("SELECT food_name, snapshot_calories, snapshot_protein, snapshot_fat, meal_slot, day_key FROM meal_entries WHERE id = 'e-3'").use {
            it.moveToFirst()
            assertEquals("Ајвар", it.getString(0))
            assertEquals(60.0, it.getDouble(1), 0.0)
            assertTrue(it.isNull(2) && it.isNull(3))
            assertEquals("DINNER", it.getString(4))
            assertEquals("2026-10-27", it.getString(5))
        }
        // The new tables take rows, a portion-less recipe and a missing macro included.
        migrated.execSQL("INSERT INTO recipes VALUES ('recipe-chili','Чили',4.0,NULL)")
        migrated.execSQL("INSERT INTO recipe_aliases VALUES ('recipe-chili','chili')")
        migrated.execSQL("INSERT INTO recipe_ingredients VALUES ('recipe-chili',0,'custom-burek','Бурек со сирење',500.0,'GRAM',500.0,'GRAM',1410.0,47.0,130.5,NULL)")
        assertEquals(1, count("recipe_ingredients"))
        migrated.close()

        // The app's own entities read the migrated file back: every old row, and the recipe as the store maps it.
        val room = Room.databaseBuilder(context, BreakfastDatabase::class.java, name).addMigrations(ROOM_MIGRATION_6_7, ROOM_MIGRATION_7_8, ROOM_MIGRATION_8_9, ROOM_MIGRATION_9_10, ROOM_MIGRATION_10_11, ROOM_MIGRATION_11_12).build()
        try {
            runBlocking {
                assertEquals(listOf("e-3", "e-1", "e-2"), room.mealEntryDao().all().map { it.toDomain().id })
                assertEquals(MeasureUnit.PIECE, room.mealEntryDao().all().single { it.id == "e-2" }.toDomain().enteredUnit)
                assertEquals(setOf("custom-burek", "custom-kefir", "egg"), room.foodDao().all().map { it.id }.toSet())
                val recipe = recipesOf(room.recipeDao().all(), room.recipeAliasDao().all(), room.recipeIngredientDao().all()).single()
                assertEquals("Чили", recipe.name)
                assertEquals(listOf("chili"), recipe.aliases)
                assertEquals(4.0, recipe.portions!!, 0.0)
                assertEquals(null, recipe.totalWeight)
                assertEquals(1410.0, recipe.ingredients.single().nutritionSnapshot.calories, 0.0)
                assertEquals(null, recipe.ingredients.single().nutritionSnapshot.fat)
            }
        } finally {
            room.close()
        }
        deleteTestDatabase(context, name)
    }

    /**
     * The phone runs schema 7 with recipes in it, so 7 to 8 must keep every row as it was and only add the total volume,
     * null for every recipe there is. A diary as the owner has it: foods, entries, recipes with Cyrillic names, aliases
     * and ingredients (one whose food was deleted, one with a macro unknown).
     */
    @Test
    fun migration7To8KeepsTheDiaryAndItsRecipesAndAddsAnEmptyTotalVolume() {
        val name = "migration-v8-${System.nanoTime()}.db".also(migrationNames::add)
        val old = migrationHelper.createDatabase(name, 7)
        old.execSQL("INSERT INTO foods VALUES ('custom-mince','Beef mince','Personal food','CUSTOM',100.0,'GRAM',250.0,20.0,0.0,18.0,100.0,'GRAM',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,1)")
        old.execSQL("INSERT INTO food_aliases VALUES ('custom-mince','мелено')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-mince','GRAM',100.0,'custom basis',0,'')")
        old.execSQL("INSERT INTO preferences VALUES ('recipe-chili',4,1761650000000)")
        old.execSQL("INSERT INTO meal_entries (id, food_id, food_name, entered_amount, entered_unit, resolved_base_amount, resolved_base_unit, snapshot_calories, snapshot_protein, snapshot_carbohydrates, snapshot_fat, conversion_provenance, approximate_conversion, saved_at_epoch_ms, source_phrase, meal_slot, day_key) VALUES ('e-chili','recipe-chili','Чили',1.5,'PORTION',450.0,'GRAM',581.25,37.5,NULL,34.1,'recipe portion',0,1761700000000,'1.5 chili','DINNER','2026-10-29')")
        old.execSQL("INSERT INTO recipes VALUES ('recipe-chili','Чили',4.0,1200.0)")
        old.execSQL("INSERT INTO recipes VALUES ('recipe-soup','Lentil soup',NULL,900.0)")
        old.execSQL("INSERT INTO recipe_aliases VALUES ('recipe-chili','chili')")
        old.execSQL("INSERT INTO recipe_ingredients VALUES ('recipe-chili',0,'custom-mince','Beef mince',500.0,'GRAM',500.0,'GRAM',1250.0,100.0,0.0,90.0)")
        old.execSQL("INSERT INTO recipe_ingredients VALUES ('recipe-chili',1,'custom-gone','Kidney beans',1.0,'PIECE',240.0,'GRAM',300.0,20.0,NULL,1.0)")
        old.close()

        val migrated = migrationHelper.runMigrationsAndValidate(name, 8, true, ROOM_MIGRATION_7_8)
        fun count(table: String) = migrated.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count("foods"))
        assertEquals(1, count("meal_entries"))
        assertEquals(1, count("preferences"))
        assertEquals(2, count("recipes"))
        assertEquals(1, count("recipe_aliases"))
        assertEquals(2, count("recipe_ingredients"))
        assertEquals(2, migrated.query("SELECT COUNT(*) FROM recipes WHERE total_volume_ml IS NULL").use { it.moveToFirst(); it.getInt(0) })
        migrated.execSQL("UPDATE recipes SET total_volume_ml = 1500.0 WHERE id = 'recipe-soup'")
        migrated.close()

        val room = Room.databaseBuilder(context, BreakfastDatabase::class.java, name).addMigrations(ROOM_MIGRATION_7_8, ROOM_MIGRATION_8_9, ROOM_MIGRATION_9_10, ROOM_MIGRATION_10_11, ROOM_MIGRATION_11_12).build()
        try {
            runBlocking {
                val recipes = recipesOf(room.recipeDao().all(), room.recipeAliasDao().all(), room.recipeIngredientDao().all()).associateBy { it.id }
                val chili = recipes.getValue("recipe-chili")
                assertEquals("Чили", chili.name)
                assertEquals(4.0, chili.portions!!, 0.0)
                assertEquals(1200.0, chili.totalWeight!!, 0.0)
                assertEquals(null, chili.totalVolume)
                assertEquals(listOf("chili"), chili.aliases)
                assertEquals(listOf(1250.0, 300.0), chili.ingredients.map { it.nutritionSnapshot.calories })
                assertEquals(null, chili.ingredients[1].nutritionSnapshot.carbohydrates)
                assertEquals(1500.0, recipes.getValue("recipe-soup").totalVolume!!, 0.0)
                val entry = room.mealEntryDao().all().single().toDomain()
                assertEquals(MeasureUnit.PORTION, entry.enteredUnit)
                assertEquals(581.25, entry.nutritionSnapshot.calories, 0.0)
            }
        } finally {
            room.close()
        }
        deleteTestDatabase(context, name)
    }

    /**
     * Measurements, over the diary the phone has at schema 8: every row stays as it was, a food's density and an entry's
     * unit label come in empty, and each serving is named by what it is matched by ("piece", "tbsp", "tsp"), its numbers
     * kept. A named serving keeps its name; a piece row that would clash with one already named "piece" keeps its blank
     * name (which reads the same).
     */
    @Test
    fun migration8To9NamesTheServingsAndAddsEmptyDensityAndUnitLabel() {
        val name = "migration-v9-${System.nanoTime()}.db".also(migrationNames::add)
        val old = migrationHelper.createDatabase(name, 8)
        old.execSQL("INSERT INTO foods VALUES ('banana','Banana','Fruit','GENERIC',100.0,'GRAM',89.0,1.1,22.8,0.3,1.0,'PIECE','fdc-1105314','USDA','2019-04',NULL,'Bananas, raw',NULL,NULL,0,0)")
        old.execSQL("INSERT INTO foods VALUES ('custom-pb','Кикирики путер','Personal food','CUSTOM',100.0,'GRAM',588.0,25.1,20.0,50.0,1.0,'TABLESPOON',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,1)")
        old.execSQL("INSERT INTO foods VALUES ('custom-kefir','Kefir','Personal food','CUSTOM',100.0,'MILLILITER',55.0,3.2,4.1,NULL,330.0,'MILLILITER',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,1)")
        old.execSQL("INSERT INTO serving_conversions VALUES ('banana','PIECE',118.0,'USDA medium banana',1,'')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-pb','TABLESPOON',16.0,'standard tablespoon',0,'')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-pb','TEASPOON',5.3,'standard teaspoon',0,'')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-pb','PIECE',40.0,'personal serving',0,'piece')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-pb','PIECE',35.0,'personal serving',0,'')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-kefir','MILLILITER',100.0,'custom basis',0,'')")
        old.execSQL("INSERT INTO serving_conversions VALUES ('custom-kefir','MILLILITER',330.0,'personal serving',0,'bottle')")
        old.execSQL("INSERT INTO meal_entries (id, food_id, food_name, entered_amount, entered_unit, resolved_base_amount, resolved_base_unit, snapshot_calories, snapshot_protein, snapshot_carbohydrates, snapshot_fat, conversion_provenance, approximate_conversion, saved_at_epoch_ms, source_phrase, meal_slot, day_key) VALUES ('e-banana','banana','Banana',2.0,'PIECE',236.0,'GRAM',210.04,2.6,53.8,0.7,'USDA medium banana',1,1761700000000,'2 bananas','BREAKFAST','2026-10-29')")
        old.execSQL("INSERT INTO recipes VALUES ('recipe-soup','Супа',NULL,NULL,1500.0)")
        old.close()

        val migrated = migrationHelper.runMigrationsAndValidate(name, 9, true, ROOM_MIGRATION_8_9)
        fun count(table: String) = migrated.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(3, count("foods"))
        assertEquals(7, count("serving_conversions"))
        assertEquals(1, count("meal_entries"))
        assertEquals(1, count("recipes"))
        assertEquals(3, migrated.query("SELECT COUNT(*) FROM foods WHERE density_g_per_100ml IS NULL").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM meal_entries WHERE entered_unit_label IS NULL").use { it.moveToFirst(); it.getInt(0) })
        fun label(food: String, unit: String, amount: Double) =
            migrated.query("SELECT label FROM serving_conversions WHERE foodId = ? AND unit = ? AND base_units_per_serving = ?", arrayOf<Any>(food, unit, amount)).use { it.moveToFirst(); it.getString(0) }
        assertEquals("piece", label("banana", "PIECE", 118.0))
        assertEquals("tbsp", label("custom-pb", "TABLESPOON", 16.0))
        assertEquals("tsp", label("custom-pb", "TEASPOON", 5.3))
        assertEquals("piece", label("custom-pb", "PIECE", 40.0))
        // Its new name would clash with the row already named "piece": it keeps its blank name, and its numbers.
        assertEquals("", label("custom-pb", "PIECE", 35.0))
        assertEquals("", label("custom-kefir", "MILLILITER", 100.0))
        assertEquals("bottle", label("custom-kefir", "MILLILITER", 330.0))
        migrated.close()

        val room = Room.databaseBuilder(context, BreakfastDatabase::class.java, name).addMigrations(ROOM_MIGRATION_8_9, ROOM_MIGRATION_9_10, ROOM_MIGRATION_10_11, ROOM_MIGRATION_11_12).build()
        try {
            runBlocking {
                val entry = room.mealEntryDao().all().single().toDomain()
                assertEquals(MeasureUnit.PIECE, entry.enteredUnit)
                assertEquals(null, entry.enteredUnitLabel)
                assertEquals(210.04, entry.nutritionSnapshot.calories, 0.0)
                val banana = room.foodDao().byId("banana")!!.toDomain(emptyList(), room.servingConversionDao().all().filter { it.foodId == "banana" }.map { it.toDomain() })
                assertEquals(null, banana.density)
                assertEquals(listOf("piece"), banana.conversions.map { it.name })
                assertEquals(1500.0, recipesOf(room.recipeDao().all(), emptyList(), emptyList()).single().totalVolume!!, 0.0)
            }
        } finally {
            room.close()
        }
        deleteTestDatabase(context, name)
    }

    @Test
    fun migration9To10AddsTheEmptyWeightTableAndKeepsTheDiary() {
        val name = "migration-v10-${System.nanoTime()}.db".also(migrationNames::add)
        migrationHelper.createDatabase(name, 9).use { old ->
            old.execSQL("INSERT INTO recipes VALUES ('recipe-soup','Soup',NULL,NULL,1500.0)")
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 10, true, ROOM_MIGRATION_9_10)
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM recipes").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, migrated.query("SELECT COUNT(*) FROM weight_points").use { it.moveToFirst(); it.getInt(0) })
        migrated.execSQL("INSERT INTO weight_points VALUES ('2026-10-01',80.0,1)")
        migrated.execSQL("INSERT OR REPLACE INTO weight_points VALUES ('2026-10-01',79.5,2)")
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM weight_points").use { it.moveToFirst(); it.getInt(0) })
        migrated.close(); deleteTestDatabase(context, name)
    }

    /** Every point survives with its weight and time, repeated times included; no other table changes; a day can then hold several. */
    @Test
    fun migration10To11KeepsEveryPointAndAllowsSeveralADay() {
        val name = "migration-v11-${System.nanoTime()}.db".also(migrationNames::add)
        migrationHelper.createDatabase(name, 10).use { old ->
            old.execSQL("INSERT INTO recipes VALUES ('recipe-soup','Soup',NULL,NULL,1500.0)")
            old.execSQL("INSERT INTO weight_points VALUES ('2026-10-03',79.25,1761700000000)")
            old.execSQL("INSERT INTO weight_points VALUES ('2026-10-01',81.5,1761700000000)")
            old.execSQL("INSERT INTO weight_points VALUES ('2026-10-02',80.0,0)")
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 11, true, ROOM_MIGRATION_10_11)
        val rows = migrated.query("SELECT day_key, kg, saved_at_epoch_ms, id FROM weight_points ORDER BY day_key").use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getString(0), c.getDouble(1), c.getLong(2), c.getLong(3))) }
        }
        assertEquals(
            listOf(listOf("2026-10-01", 81.5, 1761700000000L), listOf("2026-10-02", 80.0, 0L), listOf("2026-10-03", 79.25, 1761700000000L)),
            rows.map { it.take(3) },
        )
        assertEquals(3, rows.map { it[3] }.distinct().size)
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM recipes").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, migrated.query("SELECT COUNT(*) FROM sqlite_master WHERE name = 'weight_points_new'").use { it.moveToFirst(); it.getInt(0) })
        migrated.execSQL("INSERT INTO weight_points (day_key, kg, saved_at_epoch_ms) VALUES ('2026-10-01',81.0,1761700000000)")
        assertEquals(2, migrated.query("SELECT COUNT(*) FROM weight_points WHERE day_key = '2026-10-01'").use { it.moveToFirst(); it.getInt(0) })
        migrated.close(); deleteTestDatabase(context, name)
    }

    /** Additive: every other table keeps its rows and their values, and the new one starts empty and takes one row a day. */
    @Test
    fun migration11To12OnlyAddsTheTargetHistory() {
        val name = "migration-v12-${System.nanoTime()}.db".also(migrationNames::add)
        migrationHelper.createDatabase(name, 11).use { old ->
            old.execSQL("INSERT INTO recipes VALUES ('recipe-soup','Soup',NULL,NULL,1500.0)")
            old.execSQL("INSERT INTO weight_points (day_key, kg, saved_at_epoch_ms) VALUES ('2026-10-01',81.5,1761700000000)")
            old.execSQL("INSERT INTO meal_entries VALUES ('entry','food','Soup',2.0,'PIECE',300.0,'GRAM',250.5,12.5,NULL,5.0,'2 x 150 g',1,1234,'2 soup','LUNCH','2026-10-01','bowl')")
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 12, true, ROOM_MIGRATION_11_12)
        migrated.query("SELECT id, name, total_volume_ml FROM recipes").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("recipe-soup", it.getString(0))
            assertEquals("Soup", it.getString(1))
            assertEquals(1500.0, it.getDouble(2), 0.0)
        }
        migrated.query("SELECT day_key, kg, saved_at_epoch_ms FROM weight_points").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("2026-10-01", it.getString(0))
            assertEquals(81.5, it.getDouble(1), 0.0)
            assertEquals(1761700000000L, it.getLong(2))
        }
        migrated.query("SELECT id, food_name, entered_amount, entered_unit, snapshot_calories, snapshot_protein, snapshot_carbohydrates, saved_at_epoch_ms, meal_slot, day_key, entered_unit_label FROM meal_entries").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("entry", it.getString(0))
            assertEquals("Soup", it.getString(1))
            assertEquals(2.0, it.getDouble(2), 0.0)
            assertEquals("PIECE", it.getString(3))
            assertEquals(250.5, it.getDouble(4), 0.0)
            assertEquals(12.5, it.getDouble(5), 0.0)
            assertTrue(it.isNull(6))
            assertEquals(1234L, it.getLong(7))
            assertEquals("LUNCH", it.getString(8))
            assertEquals("2026-10-01", it.getString(9))
            assertEquals("bowl", it.getString(10))
        }
        assertEquals(0, migrated.query("SELECT COUNT(*) FROM target_history").use { it.moveToFirst(); it.getInt(0) })
        migrated.execSQL("INSERT INTO target_history VALUES ('2026-10-01',2000.0,NULL,NULL,NULL)")
        migrated.execSQL("INSERT OR REPLACE INTO target_history VALUES ('2026-10-01',1900.0,120.0,NULL,NULL)")
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM target_history").use { it.moveToFirst(); it.getInt(0) })
        migrated.close(); deleteTestDatabase(context, name)
    }

    /** The whole chain from the first schema ends where a fresh install starts: the recipe tables included. */
    @Test
    fun migration1To12RunsTheWholeChain() {
        val name = "migration-chain-${System.nanoTime()}.db".also(migrationNames::add)
        migrationHelper.createDatabase(name, 1).also { old ->
            old.execSQL("CREATE TABLE IF NOT EXISTS foods (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, origin TEXT NOT NULL, nutrition_amount REAL NOT NULL, nutrition_unit TEXT NOT NULL, calories REAL NOT NULL, protein REAL, carbohydrates REAL, fat REAL, default_amount REAL NOT NULL, default_unit TEXT NOT NULL, source_id TEXT, source_name TEXT, source_release TEXT, preparation TEXT, source_description TEXT)")
            old.execSQL("CREATE TABLE IF NOT EXISTS food_aliases (foodId TEXT NOT NULL, alias TEXT NOT NULL, PRIMARY KEY(foodId, alias))")
            old.execSQL("CREATE TABLE IF NOT EXISTS serving_conversions (foodId TEXT NOT NULL, unit TEXT NOT NULL, base_units_per_serving REAL NOT NULL, provenance TEXT NOT NULL, approximate INTEGER NOT NULL, PRIMARY KEY(foodId, unit))")
            old.execSQL("CREATE TABLE IF NOT EXISTS preferences (foodId TEXT NOT NULL PRIMARY KEY, explicit_count INTEGER NOT NULL, last_reinforced_epoch_ms INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE IF NOT EXISTS meal_entries (id TEXT NOT NULL PRIMARY KEY, food_id TEXT NOT NULL, food_name TEXT NOT NULL, entered_amount REAL NOT NULL, entered_unit TEXT NOT NULL, resolved_base_amount REAL NOT NULL, snapshot_calories REAL NOT NULL, snapshot_protein REAL, snapshot_carbohydrates REAL, snapshot_fat REAL, conversion_provenance TEXT NOT NULL, approximate_conversion INTEGER NOT NULL, saved_at_epoch_ms INTEGER NOT NULL, source_phrase TEXT NOT NULL, meal_slot TEXT NOT NULL, day_key TEXT NOT NULL)")
            old.execSQL("INSERT INTO foods VALUES ('legacy','Legacy food','test','CUSTOM',100.0,'GRAM',250.0,NULL,12.0,5.0,100.0,'GRAM',NULL,NULL,NULL,NULL,NULL)")
            old.execSQL("INSERT INTO meal_entries VALUES ('entry','legacy','Legacy food',100.0,'GRAM',100.0,250.0,NULL,12.0,5.0,'legacy',0,1234,'legacy','LUNCH','1970-01-01')")
            old.close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(
            name, 12, true, ROOM_MIGRATION_1_2, ROOM_MIGRATION_2_3, ROOM_MIGRATION_3_4, ROOM_MIGRATION_4_5, ROOM_MIGRATION_5_6, ROOM_MIGRATION_6_7,
            ROOM_MIGRATION_7_8, ROOM_MIGRATION_8_9, ROOM_MIGRATION_9_10, ROOM_MIGRATION_10_11, ROOM_MIGRATION_11_12,
        )
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM meal_entries").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, migrated.query("SELECT COUNT(*) FROM weight_points").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, migrated.query("SELECT COUNT(*) FROM recipes").use { it.moveToFirst(); it.getInt(0) })
        migrated.execSQL("INSERT INTO recipes VALUES ('recipe-soup','Soup',NULL,NULL,1500.0)")
        migrated.close(); deleteTestDatabase(context, name)
    }

    private suspend fun seed(foods: List<mk.bosa.breakfast.domain.FoodRecord>) {
        database.withTransaction {
            database.foodDao().insertAll(foods.map { it.toEntity() })
            database.foodAliasDao().insertAll(foods.flatMap { it.toAliasEntities() })
            database.servingConversionDao().insertAll(foods.flatMap { it.toConversionEntities() })
        }
    }
}
