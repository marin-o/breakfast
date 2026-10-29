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
    private val damaged by lazy { DamagedDiaries(root, clock = { now }) }

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
        val afterRestart = DamagedDiaries(root, clock = { now })
        assertTrue(afterRestart.noticePending())
        assertFalse("a move in the old process is not the new store's to reopen", afterRestart.takeMoved())
        afterRestart.noticeSeen()
        assertFalse(DamagedDiaries(root, clock = { now }).noticePending())
    }

    @Test fun aMoveThatFailsHalfwayPutsBackWhatItMovedAndMarksNothing() {
        val db = file("breakfast.db", byteArrayOf(1))
        val wal = file("breakfast.db-wal", byteArrayOf(2))
        val failing = DamagedDiaries(root, clock = { now }, move = { from, to ->
            if (from.name == "breakfast.db") throw java.io.IOException("no room")
            java.nio.file.Files.move(from.toPath(), to.toPath())
        })
        val failure = runCatching { failing.moveAside(db) }.exceptionOrNull()
        assertEquals("no room", failure?.message)
        assertArrayEquals("the WAL is back beside its database", byteArrayOf(2), wal.readBytes())
        assertArrayEquals(byteArrayOf(1), db.readBytes())
        assertFalse(failing.noticePending())
        assertFalse(failing.needsReopen)
        assertTrue("no folder is left", root.listFiles().orEmpty().isEmpty())
    }

    /** A full disk: the marker cannot be written while open, yet the notice shows, and the marker comes with the close. */
    @Test fun aMarkerThatCannotBeWrittenStillLeavesTheNotice() {
        var diskFull = true
        val failing = DamagedDiaries(root, clock = { now }, writeMarker = { file, text ->
            if (diskFull) throw java.io.IOException("no space") else file.writeText(text)
        })
        val db = file("breakfast.db", byteArrayOf(1))
        assertEquals("no space", runCatching { failing.snapshot(db) }.exceptionOrNull()?.message)
        assertTrue("the notice still shows", failing.noticePending())
        assertTrue("and the store still closes and sets it aside", failing.closePending)
        diskFull = false
        failing.setAsideAfterClose()
        val folder = root.listFiles()!!.single()
        assertTrue("written with the set-aside, so it survives a restart", File(folder, DamagedDiaries.NOTICE).exists())
        assertTrue(DamagedDiaries(root, clock = { now }).noticePending())
        assertArrayEquals(byteArrayOf(1), File(folder, "${DamagedDiaries.AFTER_CLOSE}/breakfast.db").readBytes())
    }

    /** A notice already dismissed is not marked again by the set-aside. */
    @Test fun aSeenNoticeIsNotMarkedAgainAfterTheClose() {
        damaged.snapshot(file("breakfast.db", byteArrayOf(1)))
        damaged.noticeSeen()
        damaged.setAsideAfterClose()
        assertFalse(damaged.noticePending())
        assertFalse(DamagedDiaries(root, clock = { now }).noticePending())
    }

    /** Damage found with a connection open: copied as found now, moved only after the store closed it. */
    @Test fun aDiaryDamagedWhileOpenIsCopiedAsFoundAndMovedAfterTheClose() {
        val db = file("breakfast.db", byteArrayOf(1, 1))
        val wal = file("breakfast.db-wal", byteArrayOf(2, 2))
        damaged.snapshot(db)
        val folder = root.listFiles()!!.single()
        assertArrayEquals(byteArrayOf(1, 1), File(folder, "breakfast.db").readBytes())
        assertArrayEquals(byteArrayOf(2, 2), File(folder, "breakfast.db-wal").readBytes())
        assertTrue("the originals stay under the open connection", db.exists() && wal.exists())
        assertTrue(damaged.closePending)
        assertTrue(damaged.needsReopen)
        assertTrue(damaged.noticePending())
        // A second report before the close copies nothing again.
        damaged.snapshot(db)
        assertEquals(1, root.listFiles()!!.size)
        // The close checkpointed into the database and removed the WAL; that state goes beside the copy.
        db.writeBytes(byteArrayOf(3))
        wal.delete()
        damaged.setAsideAfterClose()
        assertFalse(db.exists())
        assertArrayEquals(byteArrayOf(3), File(folder, "${DamagedDiaries.AFTER_CLOSE}/breakfast.db").readBytes())
        assertArrayEquals("the copy as found is untouched", byteArrayOf(1, 1), File(folder, "breakfast.db").readBytes())
        assertFalse(damaged.closePending)
        assertTrue(damaged.takeMoved())
        assertFalse(damaged.needsReopen)
    }

    @Test fun aHeaderSqliteCannotReadIsSetAsideBeforeItOpens() {
        val garbage = "SQLite format 3\u0000".toByteArray() + ByteArray(200) { (it * 37 + 11).toByte() }
        val db = file("breakfast.db", garbage)
        val wal = file("breakfast.db-wal", byteArrayOf(5))
        assertTrue(damaged.setAsideIfUnreadable(db))
        val folder = root.listFiles()!!.single()
        assertArrayEquals(garbage, File(folder, "breakfast.db").readBytes())
        assertArrayEquals(byteArrayOf(5), File(folder, "breakfast.db-wal").readBytes())
        assertFalse(wal.exists())
    }

    @Test fun aReadableHeaderAndAnEmptyOrMissingFileStay() {
        assertFalse(damaged.setAsideIfUnreadable(File(databases, "breakfast.db")))
        assertFalse(damaged.setAsideIfUnreadable(file("empty.db", ByteArray(0))))
        assertFalse(damaged.setAsideIfUnreadable(file("short.db", byteArrayOf(1, 2, 3))))
        assertFalse(damaged.setAsideIfUnreadable(file("good.db", validHeader())))
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun theHeaderCheckIsSqlitesOwn() {
        assertTrue(readableHeader(validHeader()))
        assertTrue("page size 1 means 65536", readableHeader(validHeader().also { it[16] = 0; it[17] = 1 }))
        assertFalse("not SQLite", readableHeader(ByteArray(100)))
        assertFalse("page size not a power of two", readableHeader(validHeader().also { it[16] = 0x0f; it[17] = 0xa0.toByte() }))
        assertTrue("write version above 2: SQLite opens it read-only", readableHeader(validHeader().also { it[18] = 3 }))
        assertTrue("versions 0 are accepted", readableHeader(validHeader().also { it[18] = 0; it[19] = 0 }))
        assertFalse("read version above 2: SQLite refuses it", readableHeader(validHeader().also { it[19] = 3 }))
        assertFalse("read version 255", readableHeader(validHeader().also { it[19] = 0xff.toByte() }))
        assertFalse("fewer than 480 usable bytes", readableHeader(validHeader().also { it[16] = 2; it[17] = 0; it[20] = 40 }))
        assertTrue("480 usable bytes", readableHeader(validHeader().also { it[16] = 2; it[17] = 0; it[20] = 32 }))
        assertFalse("page size 256", readableHeader(validHeader().also { it[16] = 1; it[17] = 0 }))
        assertFalse("payload fractions", readableHeader(validHeader().also { it[21] = 63 }))
        assertTrue("shorter than a header: left to SQLite", readableHeader(ByteArray(10)))
    }

    /** The first 100 bytes of a real SQLite database with 4096-byte pages in WAL mode. */
    private fun validHeader(): ByteArray = ByteArray(100).also {
        "SQLite format 3\u0000".toByteArray().copyInto(it)
        it[16] = 0x10; it[17] = 0; it[18] = 2; it[19] = 2; it[21] = 64; it[22] = 32; it[23] = 32
    }
}
