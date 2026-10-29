package com.iron.breakfast.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.random.Random

/**
 * D15 on a device: a damaged diary is set aside intact, not deleted, and a fresh one opens and works. Its own database
 * and its own damaged folder (unique names, never `breakfast.db` or `no_backup/damaged`), all deleted afterwards.
 */
@RunWith(AndroidJUnit4::class)
class DamagedDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val id = UUID.randomUUID()
    private val name = "damaged-test-$id.db"
    private val damagedRoot = File(context.cacheDir, "damaged-test-$id")
    private val opened = mutableListOf<BreakfastDatabase>()

    /** Opens [name] as the store opens `breakfast.db` ([openBreakfastDatabase]), keeping each for [tearDown]. */
    private fun open(damaged: DamagedDiaries) = openBreakfastDatabase(context, name, damaged).also { opened += it }

    @After
    fun tearDown() {
        opened.forEach { runCatching { it.close() } }
        deleteTestDatabase(context, name)
        damagedRoot.deleteRecursively()
    }

    @Test
    fun aDamagedDiaryIsSetAsideIntactAndAFreshOneOpensAndWorks() = runBlocking {
        val damaged = DamagedDiaries(damagedRoot)
        // A real diary first, seeded and closed.
        BreakfastStore({ open(damaged) }, damaged).initialize()
        opened.forEach { it.close() }
        val file = context.getDatabasePath(name)
        assertTrue(file.exists())

        // Garbage behind a valid header, and no WAL left: the emulator check of D15.
        val garbage = "SQLite format 3\u0000".toByteArray() + Random(15).nextBytes(64 * 1024)
        file.writeBytes(garbage)
        listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }

        val store = BreakfastStore({ open(damaged) }, damaged)
        store.initialize()

        val folder = damagedRoot.listFiles()!!.single()
        assertArrayEquals("set aside as it was", garbage, File(folder, name).readBytes())
        assertTrue("the notice waits", File(folder, DamagedDiaries.NOTICE).exists())
        assertTrue(store.damagedNoticePending())
        assertTrue("a fresh, empty diary", store.entries().isEmpty())
        assertTrue("with the catalogue seeded", store.foods().isNotEmpty())
        assertFalse("nothing to reopen: it opened on the fresh file", store.reloadIfStale())

        // It works: a write, read back through the snapshot and after a reopen.
        val food = FoodCatalog.fixtures.first()
        val entry = MealEntry(
            id = "after-damage", foodId = food.id, foodName = food.name, enteredAmount = 1.0, enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 1.0, nutritionSnapshot = NutritionValues(1.0, 0.0, 2.0, 3.0), conversionProvenance = "1 g",
            approximateConversion = false, savedAtEpochMs = 1L,
        )
        store.saveEntries(listOf(entry))
        assertEquals(listOf("after-damage"), store.entries().map { it.id })
        opened.forEach { it.close() }
        val reopened = BreakfastStore({ open(damaged) }, damaged).apply { initialize() }
        assertEquals(listOf("after-damage"), reopened.entries().map { it.id })
        assertEquals("one damaged copy, kept", 1, damagedRoot.listFiles()!!.size)

        store.damagedNoticeSeen()
        assertFalse(store.damagedNoticePending())
        assertTrue("the copy stays after the notice was seen", File(folder, name).exists())
    }
}
