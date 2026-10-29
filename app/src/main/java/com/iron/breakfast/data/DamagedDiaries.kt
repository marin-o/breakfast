package com.iron.breakfast.data

import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diaries SQLite reported as damaged (D15), kept, never deleted. Each gets its own folder under [root]
 * (`no_backup/damaged/<time>/` in the app, so it is never backed up) holding the database with its `-wal`, `-shm` and
 * `-journal` exactly as they were found, and storage then starts a fresh diary. Nothing here deletes a damaged copy.
 *
 * - **No connection open on it** (found while opening, or by [setAsideIfUnreadable] before SQLite opens it): the files
 *   are moved into the folder ([moveAside]), so they are the bytes as found.
 * - **A connection still open on it** (found by a statement during the session): nothing may be moved or closed under
 *   that connection, which is inside a statement. The files are copied as found ([snapshot]), and the store, once the
 *   failing call has unwound and under its lock, closes the whole database (its close may checkpoint into the damaged
 *   file, which the copy no longer depends on), then moves what is left into the folder's [AFTER_CLOSE]
 *   ([setAsideAfterClose]), then opens a fresh one. No old connection is left on the files a fresh diary then uses.
 *
 * Each folder carries a [NOTICE] marker until the user saw "The diary could not be read. Import an export to restore
 * it." ([noticeSeen]), so the notice comes back after the app was killed before it was seen.
 *
 * Plain files, no Android: the open-helper callback (`SetAsideOnCorruption`) reports, the store asks [needsReopen].
 */
internal class DamagedDiaries(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Moves one file (a same-filesystem rename in the app); tests make it fail. */
    private val move: (File, File) -> Unit = { from, to -> Files.move(from.toPath(), to.toPath()) },
) {
    /** A damaged database was moved aside while no connection was open on it; the store has not reopened yet. */
    @Volatile private var moved = false
    /** A damaged database found with a connection open on it: copied as found into [Snapshot.folder], still in place. */
    @Volatile private var open: Snapshot? = null

    private class Snapshot(val database: File, val folder: File)

    /**
     * Sets [database] aside before SQLite opens it when its header is not one SQLite can read ([readableHeader]):
     * opening it would let SQLite act on its journal or WAL (a checkpoint into the damaged file) before it reports the
     * damage. True when it was set aside.
     */
    @Synchronized
    fun setAsideIfUnreadable(database: File): Boolean {
        if (!database.exists()) return false
        val header = database.inputStream().use { input -> ByteArray(HEADER_SIZE).let { it.copyOf(input.read(it).coerceAtLeast(0)) } }
        if (readableHeader(header)) return false
        moveAside(database)
        return true
    }

    /**
     * Moves [database] and whichever of its side files exist into a new folder under [root], marking the notice first.
     * The side files go first and the database last. If a move fails, what was moved goes back, the folder goes, and
     * the failure is thrown: the diary is left as it was, never without its WAL. Returns the folder, or null when there
     * was nothing to move.
     */
    @Synchronized
    fun moveAside(database: File): File? {
        val files = filesOf(database)
        if (files.isEmpty()) return null
        val folder = newFolder()
        val marker = File(folder, NOTICE).apply { writeText(database.name) }
        try {
            moveAll(files, folder)
        } catch (e: Exception) {
            marker.delete()
            folder.delete()
            throw e
        }
        pending = true
        moved = true
        return folder
    }

    /**
     * Copies [database] and its side files as they are now into a new folder, while a connection is still open on them
     * (inside the statement that found the damage). The originals stay for the store to set aside after it closed the
     * database ([setAsideAfterClose]). Only the first report counts until then. A copy that fails throws, but the
     * set-aside after the close still happens, so the damaged files are kept either way.
     */
    @Synchronized
    fun snapshot(database: File) {
        if (open != null) return
        val folder = newFolder()
        open = Snapshot(database, folder)
        File(folder, NOTICE).writeText(database.name)
        pending = true
        filesOf(database).forEach { Files.copy(it.toPath(), File(folder, it.name).toPath()) }
    }

    /**
     * The store closed every connection on the damaged database: what is left of it (as the close left it) moves into
     * the snapshot's [AFTER_CLOSE] folder, so a fresh diary opens in its place. A failed move puts back what was moved
     * and throws, and the store tries again on its next reload.
     */
    @Synchronized
    fun setAsideAfterClose() {
        val snapshot = open ?: return
        val files = filesOf(snapshot.database)
        if (files.isNotEmpty()) {
            val after = File(snapshot.folder, AFTER_CLOSE).apply { mkdirs() }
            try {
                moveAll(files, after)
            } catch (e: Exception) {
                after.delete()
                throw e
            }
        }
        open = null
        moved = true
    }

    /** The store must close its database first and set it aside ([setAsideAfterClose]): it was copied while open. */
    val closePending: Boolean get() = open != null

    /** The store's database is not the fresh one: it must reopen (after [setAsideAfterClose] when [closePending]). */
    val needsReopen: Boolean get() = moved || open != null

    /** A [moveAside] or [setAsideAfterClose] happened that [takeMoved] has not taken yet; reading it takes nothing. */
    val movedSinceTaken: Boolean get() = moved

    /** True once after a move: the database the store has open is gone, so it opens the fresh one. */
    fun takeMoved(): Boolean {
        if (!moved) return false
        moved = false
        return true
    }

    /** The markers as last read or written; read from disk once (the store does it while opening, off the main thread). */
    @Volatile private var pending: Boolean? = null

    /** A damaged diary was set aside and the user has not seen the notice yet. */
    fun noticePending(): Boolean = pending ?: folders().any { File(it, NOTICE).exists() }.also { pending = it }

    /** The user saw the notice: the markers go, every damaged copy stays. */
    @Synchronized
    fun noticeSeen() {
        folders().forEach { File(it, NOTICE).delete() }
        pending = false
    }

    private fun filesOf(database: File): List<File> = (SIDE_FILES.map { File(database.path + it) } + database).filter { it.exists() }

    /** Moves [files] in order into [folder]; on a failure the ones already moved go back, then it throws. */
    private fun moveAll(files: List<File>, folder: File) {
        val done = mutableListOf<File>()
        try {
            files.forEach { move(it, File(folder, it.name)); done += it }
        } catch (e: Exception) {
            done.asReversed().forEach { runCatching { move(File(folder, it.name), it) } }
            throw e
        }
    }

    private fun folders(): List<File> = root.listFiles()?.filter { it.isDirectory }.orEmpty()

    private fun newFolder(): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(clock()))
        var folder = File(root, stamp)
        var n = 2
        while (folder.exists()) folder = File(root, "$stamp-${n++}")
        check(folder.mkdirs()) { "Could not make $folder" }
        return folder
    }

    companion object {
        /** The marker of a notice not yet seen. */
        const val NOTICE = "notice-pending"
        /** Where the files of a database damaged during the session go once it was closed. */
        const val AFTER_CLOSE = "after-close"
        private val SIDE_FILES = listOf("-wal", "-shm", "-journal")
        private const val HEADER_SIZE = 100
    }
}

/**
 * True unless [header] (the first bytes of a database file) is one SQLite would refuse ("file is not a database"):
 * the magic string, a page size that is a power of two from 512 to 65536 (1 means 65536), file format versions 1 or 2,
 * and the payload fractions 64, 32 and 32, which SQLite requires. An empty or short file is left to SQLite.
 */
internal fun readableHeader(header: ByteArray): Boolean {
    if (header.size < 100) return true
    if (!header.copyOfRange(0, 16).contentEquals(MAGIC)) return false
    val pageSize = ((header[16].toInt() and 0xff) shl 8) or (header[17].toInt() and 0xff)
    val validPage = pageSize == 1 || (pageSize in 512..32768 && pageSize and (pageSize - 1) == 0)
    val versions = header[18].toInt() in 1..2 && header[19].toInt() in 1..2
    return validPage && versions && header[21].toInt() == 64 && header[22].toInt() == 32 && header[23].toInt() == 32
}

private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/** The notice after a damaged diary was set aside (approved wording, D15). */
internal const val DIARY_DAMAGED = "The diary could not be read. Import an export to restore it."
