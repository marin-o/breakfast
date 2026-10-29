package com.iron.breakfast.data

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.random.Random

/**
 * D15 on a device: a damaged diary is set aside byte for byte, never deleted, and a fresh one opens and works. Each
 * test has its own database and damaged folder (unique names, never `breakfast.db` or `no_backup/damaged`), all deleted
 * afterwards, locks included.
 */
@RunWith(AndroidJUnit4::class)
class DamagedDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val id = UUID.randomUUID()
    private val name = "damaged-test-$id.db"
    private val file = context.getDatabasePath(name)
    private val damagedRoot = File(context.cacheDir, "damaged-test-$id")
    private val damaged = DamagedDiaries(damagedRoot)
    private val opened = mutableListOf<BreakfastDatabase>()

    /** A store over [name], opened as the app opens `breakfast.db` ([openBreakfastDatabase]); each database kept for [tearDown]. */
    private fun store() = BreakfastStore({ openBreakfastDatabase(context, name, damaged).also { opened += it } }, damaged, file)

    private fun entry(id: String): MealEntry {
        val food = FoodCatalog.fixtures.first()
        return MealEntry(
            id = id, foodId = food.id, foodName = food.name, enteredAmount = 1.0, enteredUnit = MeasureUnit.GRAM,
            resolvedBaseAmount = 1.0, nutritionSnapshot = NutritionValues(1.0, 0.0, 2.0, 3.0), conversionProvenance = "1 g",
            approximateConversion = false, savedAtEpochMs = 1L,
        )
    }

    private fun closeAll() = opened.forEach { runCatching { it.close() } }

    private fun bytes(suffix: String = ""): ByteArray? = File(file.path + suffix).takeIf { it.exists() }?.readBytes()

    @After
    fun tearDown() {
        closeAll()
        deleteTestDatabase(context, name)
        damagedRoot.deleteRecursively()
    }

    /** The emulator's D15: garbage behind a valid header, with the stale WAL and shared memory of the diary it was. */
    @Test
    fun aDiaryDamagedBeforeOpenIsSetAsideByteForByteWithItsStaleWal() = runBlocking {
        val before = store().apply { initialize(); saveEntries(listOf(entry("lost"))) }
        assertEquals(1, before.entries().size)
        // The WAL and shared memory as a killed app leaves them (a clean close would checkpoint and remove them).
        val wal = bytes("-wal")
        val shm = bytes("-shm")
        closeAll()
        val garbage = "SQLite format 3\u0000".toByteArray() + Random(15).nextBytes(64 * 1024)
        file.writeBytes(garbage)
        wal?.let { File(file.path + "-wal").writeBytes(it) }
        shm?.let { File(file.path + "-shm").writeBytes(it) }

        val after = store().apply { initialize() }

        val folder = damagedRoot.listFiles()!!.single()
        assertArrayEquals("the database as found, nothing checkpointed into it", garbage, File(folder, name).readBytes())
        wal?.let { assertArrayEquals("its WAL as found", it, File(folder, "$name-wal").readBytes()) }
        shm?.let { assertArrayEquals("its shared memory as found", it, File(folder, "$name-shm").readBytes()) }
        assertTrue(File(folder, DamagedDiaries.NOTICE).exists())
        assertTrue("a fresh, empty diary", after.entries().isEmpty())
        assertTrue("with the catalogue seeded", after.foods().isNotEmpty())
        assertFalse("nothing to reopen: it opened on the fresh file", after.reloadIfStale())
        after.saveEntries(listOf(entry("fresh")))
        closeAll()
        assertEquals(listOf("fresh"), store().apply { initialize() }.entries().map { it.id })
        assertEquals("one damaged copy, kept", 1, damagedRoot.listFiles()!!.size)
        after.damagedNoticeSeen()
        assertFalse(after.damagedNoticePending())
        assertTrue("the copy stays after the notice was seen", File(folder, name).exists())
    }

    /**
     * Damage a statement finds during the session: the copy as found is exact, no connection is left on the damaged
     * files, the fresh diary works, and its WAL survives the old connections' finalizers, a close and a reopen.
     */
    @Test
    fun aDiaryDamagedDuringTheSessionIsCopiedAsFoundThenClosedSetAsideAndReplaced() = runBlocking {
        val store = store().apply { initialize(); saveEntries(listOf(entry("before"))) }
        val database = opened.last().openHelper.writableDatabase
        // Everything into the main file, so the damage below is what the next statement reads.
        database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val root = database.query("SELECT rootpage FROM sqlite_master WHERE name = 'meal_entries'").use { it.moveToFirst(); it.getInt(0) }
        val pageSize = database.query("PRAGMA page_size").use { it.moveToFirst(); it.getInt(0) }
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek((root - 1L) * pageSize)
            raf.write(Random(19).nextBytes(pageSize))
        }
        // Another connection writes page 1 only, so the store's connection drops its page cache and reads the damage.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS, DatabaseErrorHandler { })
            .use { it.execSQL("PRAGMA user_version = 77") }
        val asFound = bytes()!!

        try {
            store.saveEntries(listOf(entry("found it")))
            fail("the damaged table should have been found")
        } catch (expected: Exception) {
        }

        assertTrue("copied as found, still to be closed and set aside", damaged.closePending)
        val folder = damagedRoot.listFiles()!!.single()
        assertArrayEquals("the database as found", asFound, File(folder, name).readBytes())
        // Until the fresh diary is open, a write is refused, so nothing commits into the damaged file.
        val refused = runCatching { store.saveEntries(listOf(entry("too early"))) }.exceptionOrNull()
        assertTrue("refused: $refused", refused is DiaryDamagedException)
        assertTrue(store.reloadIfStale())
        assertTrue("the session resets as after an import", store.takeDiaryReplaced())
        assertFalse(damaged.closePending)
        assertNotNull("the files as the close left them", File(folder, DamagedDiaries.AFTER_CLOSE).listFiles()?.singleOrNull { it.name == name })
        assertTrue("a fresh, empty diary", store.entries().isEmpty())
        store.saveEntries(listOf(entry("fresh")))
        assertEquals(listOf("fresh"), store.entries().map { it.id })

        // The old database's connections are closed, so no finalizer can checkpoint into or unlink the fresh files.
        repeat(3) { System.gc(); System.runFinalization() }
        store.saveEntries(listOf(entry("after gc")))
        closeAll()
        assertEquals(setOf("fresh", "after gc"), store().apply { initialize() }.entries().map { it.id }.toSet())
        assertArrayEquals("the copy as found is untouched", asFound, File(folder, name).readBytes())
    }

    companion object {
        /** After the whole class: no test database file or lock is left on the device. */
        @JvmStatic
        @AfterClass
        fun sweep() = sweepTestDatabases(ApplicationProvider.getApplicationContext())
    }
}
