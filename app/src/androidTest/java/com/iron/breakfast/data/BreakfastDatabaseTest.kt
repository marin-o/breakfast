package com.iron.breakfast.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreakfastDatabaseTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: BreakfastDatabase

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
        context.deleteDatabase(databaseName)
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

    private suspend fun seed(foods: List<com.iron.breakfast.domain.FoodRecord>) {
        database.withTransaction {
            database.foodDao().insertAll(foods.map { it.toEntity() })
            database.foodAliasDao().insertAll(foods.flatMap { it.toAliasEntities() })
            database.servingConversionDao().insertAll(foods.flatMap { it.toConversionEntities() })
        }
    }
}
