package com.iron.breakfast.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iron.breakfast.domain.TestFoods
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.ui.components.LongDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Export diary and Import diary on an in-memory database, through the store as the app uses it. */
@RunWith(AndroidJUnit4::class)
class DiaryImportTest {
    private lateinit var database: BreakfastDatabase
    private lateinit var store: BreakfastStore
    private val settings = DiarySettings(4, LongDraft.START)

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

    private val custom = TestFoods.fixtures.first().copy(
        id = "custom-import", name = "Домашен бурек", aliases = listOf("burek", "бурек"), origin = FoodOrigin.CUSTOM,
        nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(280.0, 9.0, null, 17.5)),
        conversions = listOf(ServingConversion(MeasureUnit.PIECE, 150.0, "user", approximate = true, label = "slice")),
        barcode = null,
    )

    private fun entry(id: String, foodId: String, calories: Double) = MealEntry(
        id = id, foodId = foodId, foodName = "Snapshot $id", enteredAmount = 1.0, enteredUnit = MeasureUnit.PIECE,
        resolvedBaseAmount = 150.0, nutritionSnapshot = NutritionValues(calories, 1.0, null, 2.0), conversionProvenance = "1 slice",
        approximateConversion = true, savedAtEpochMs = 1_000L + calories.toLong(), dayKey = "2026-10-25", resolvedBaseUnit = MeasureUnit.GRAM,
    )

    /** A diary with a custom food, a deleted food, a preference and entries, two of them for foods that are gone. */
    private suspend fun fillDiary() {
        store.addFood(custom)
        store.addFood(custom.copy(id = "deleted-import", name = "Old jam", aliases = emptyList(), conversions = emptyList()))
        store.deleteFood("deleted-import")
        store.saveEntries(listOf(entry("a", custom.id, 420.0), entry("b", "deleted-import", 80.0), entry("c", "never-stored", 55.0)))
        store.reinforce(custom.id)
    }

    private suspend fun exportText(): String = encodeDiary(diaryFileOf(store.exportRows(), settings, exportedAtEpochMs = 0L))

    private fun checksum(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private suspend fun counts(): List<Int> = store.exportRows().let {
        listOf(it.foods.size, it.aliases.size, it.conversions.size, it.preferences.size, it.entries.size)
    }

    @Test
    fun exportWipeImportGivesBackTheSameDiary() = runBlocking {
        fillDiary()
        val before = exportText()
        val beforeCounts = counts()
        val beforeEntries = store.entries()

        // Wipe: an empty diary, as after a reinstall.
        store.replaceDiary(DiaryRows(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        assertEquals(0, store.entries().size)
        assertNotEquals(beforeCounts, counts())

        val imported = decodeDiary(before) as DiaryImport.Valid
        store.replaceDiary(imported.rows)

        assertEquals(beforeCounts, counts())
        assertEquals(checksum(before), checksum(exportText()))
        assertEquals(beforeEntries, store.entries())
        assertTrue(store.foods().none { it.id == "deleted-import" })
        assertEquals(custom.aliases.toSet(), store.foods().single { it.id == custom.id }.aliases.toSet())
        assertEquals(1, store.preferenceMap().getValue(custom.id).explicitCount)
    }

    @Test
    fun aFailingInsertLeavesThePreviousDiary() = runBlocking {
        fillDiary()
        val before = exportText()
        val good = (decodeDiary(before) as DiaryImport.Valid).rows
        // Not validated on purpose: the second copy of an entry id fails the insert after foods and aliases went in.
        val broken = good.copy(entries = good.entries + good.entries.first().copy(foodName = "clash"))

        try {
            store.replaceDiary(broken)
            fail("the clashing insert should have failed")
        } catch (expected: Exception) {
            // The transaction rolled back.
        }

        assertEquals(checksum(before), checksum(exportText()))
        assertEquals(3, store.entries().size)
    }

    /** A file from an older build with an archived food is accepted: the food is left out, its entries kept. */
    @Test
    fun anOlderFileWithAnArchivedFoodImportsWithoutIt() = runBlocking {
        fillDiary()
        val rows = store.exportRows()
        val old = custom.copy(id = "archived-old", name = "Archived jam", aliases = listOf("ajam"), conversions = emptyList())
        val file = diaryFileOf(
            rows.copy(
                foods = rows.foods + old.toEntity().copy(archived = true),
                aliases = rows.aliases + old.toAliasEntities(),
                preferences = rows.preferences + PreferenceEntity(old.id, 2, 5L),
                entries = rows.entries + entry("d", old.id, 90.0).toEntity(),
            ),
            settings, exportedAtEpochMs = 0L,
        )
        val imported = decodeDiary(encodeDiary(file)) as DiaryImport.Valid
        store.replaceDiary(imported.rows)

        assertTrue(store.foods().none { it.id == old.id })
        assertTrue(store.exportRows().aliases.none { it.foodId == old.id })
        assertTrue(store.preferenceMap()[old.id] == null)
        assertEquals(90.0, store.entries().single { it.id == "d" }.nutritionSnapshot.calories, 0.0)
        assertEquals(4, store.entries().size)
    }
}
