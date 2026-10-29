package com.iron.breakfast.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** D15: a damaged diary is moved aside whole and kept; nothing here deletes a copy. */
class DamagedDiariesTest {
    @get:Rule val temp = TemporaryFolder()

    private val databases by lazy { temp.newFolder("databases") }
    private val root by lazy { File(temp.root, "no_backup/damaged") }
    private val now = LocalDateTime.parse("2026-10-29T14:05:09").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val damaged by lazy { DamagedDiaries(root) { now } }

    private fun file(name: String, bytes: ByteArray) = File(databases, name).apply { writeBytes(bytes) }

    @Test fun theDatabaseAndEverySideFileMoveTogetherIntactAndTheNoticeIsMarked() {
        val db = file("breakfast.db", "SQLite format 3\u0000garbage".toByteArray())
        val wal = file("breakfast.db-wal", byteArrayOf(1, 2, 3))
        val shm = file("breakfast.db-shm", byteArrayOf(4, 5))
        val journal = file("breakfast.db-journal", byteArrayOf(6))
        val bytes = listOf(db, wal, shm, journal).associate { it.name to it.readBytes() }
        val other = file("other.db", byteArrayOf(9))

        val folder = damaged.moveAside(db)!!

        assertEquals(File(root, "20261029-140509"), folder)
        bytes.forEach { (name, content) ->
            assertFalse("$name is no longer beside the fresh database", File(databases, name).exists())
            assertArrayEquals(name, content, File(folder, name).readBytes())
        }
        assertTrue("another database stays", other.exists())
        assertTrue(File(folder, DamagedDiaries.NOTICE).exists())
        assertTrue(damaged.noticePending())
    }

    @Test fun onlyTheSideFilesThatExistMove() {
        val db = file("breakfast.db", byteArrayOf(7))
        file("breakfast.db-wal", byteArrayOf(8))
        val folder = damaged.moveAside(db)!!
        assertEquals(setOf("breakfast.db", "breakfast.db-wal", DamagedDiaries.NOTICE), folder.list()!!.toSet())
    }

    @Test fun nothingOnDiskMovesNothingAndMarksNothing() {
        assertNull(damaged.moveAside(File(databases, "breakfast.db")))
        assertFalse(damaged.noticePending())
        assertFalse(damaged.takeMoved())
    }

    @Test fun theStoreLearnsOfAMoveOnce() {
        assertFalse(damaged.movedSinceTaken)
        damaged.moveAside(file("breakfast.db", byteArrayOf(1)))
        assertTrue(damaged.movedSinceTaken)
        assertTrue(damaged.takeMoved())
        assertFalse(damaged.takeMoved())
        assertFalse(damaged.movedSinceTaken)
    }

    @Test fun everyDamagedCopyIsKeptInItsOwnFolderAndSeeingTheNoticeKeepsThem() {
        val first = damaged.moveAside(file("breakfast.db", byteArrayOf(1)))!!
        val second = damaged.moveAside(file("breakfast.db", byteArrayOf(2)))!!
        assertNotEquals(first, second)
        assertEquals(File(root, "20261029-140509-2"), second)
        damaged.noticeSeen()
        assertFalse(damaged.noticePending())
        assertArrayEquals(byteArrayOf(1), File(first, "breakfast.db").readBytes())
        assertArrayEquals(byteArrayOf(2), File(second, "breakfast.db").readBytes())
        assertFalse(File(first, DamagedDiaries.NOTICE).exists())
    }

    @Test fun theNoticeComesBackAfterTheAppWasKilledBeforeItWasSeen() {
        damaged.moveAside(file("breakfast.db", byteArrayOf(1)))
        // A new process reads the marker from disk.
        val afterRestart = DamagedDiaries(root) { now }
        assertTrue(afterRestart.noticePending())
        assertFalse("a move in the old process is not the new store's to reopen", afterRestart.takeMoved())
        afterRestart.noticeSeen()
        assertFalse(DamagedDiaries(root) { now }.noticePending())
    }
}
