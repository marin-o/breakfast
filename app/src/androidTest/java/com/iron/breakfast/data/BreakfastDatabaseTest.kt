package com.iron.breakfast.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
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
    fun freshSeedIsIdempotentAndMealSnapshotSurvivesReopen() = runBlocking {
        val fixtures = fixtureRowsMissingFrom(emptySet())
        seed(fixtures)
        val firstCount = database.foodDao().all().size
        seed(fixtureRowsMissingFrom(database.foodDao().ids().toSet()))
        assertEquals(firstCount, database.foodDao().all().size)

        val food = FoodCatalog.fixtures.first()
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
        val food = FoodCatalog.fixtures.first()
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

    private suspend fun seed(foods: List<com.iron.breakfast.domain.FoodRecord>) {
        database.withTransaction {
            database.foodDao().insertAll(foods.map { it.toEntity() })
            database.foodAliasDao().insertAll(foods.flatMap { it.toAliasEntities() })
            database.servingConversionDao().insertAll(foods.flatMap { it.toConversionEntities() })
        }
    }
}
