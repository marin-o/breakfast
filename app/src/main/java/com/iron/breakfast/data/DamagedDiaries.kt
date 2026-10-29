package com.iron.breakfast.data

import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diaries SQLite reported as damaged (D15), kept, never deleted: each one is moved whole, the database with its
 * `-wal`, `-shm` and `-journal`, into its own folder under [root] (`no_backup/damaged/<time>/` in the app, so it is
 * never backed up), and storage then starts a fresh diary. Nothing here deletes a damaged copy; only the owner does,
 * by hand.
 *
 * Each folder carries a [NOTICE] marker until the user saw "The diary could not be read. Import an export to restore
 * it." ([noticeSeen]), so the notice comes back after the app was killed before it was seen.
 *
 * Plain files, no Android: the open-helper callback (`SetAsideOnCorruption`) calls [moveAside], the store asks
 * [takeMoved] whether it must reopen, the app asks [noticePending].
 */
internal class DamagedDiaries(private val root: File, private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var moved = false

    /**
     * Moves [database] and whichever of its side files exist into a new folder under [root], marking the notice first,
     * so a move is never left unannounced. The side files go first and the database last: a damaged database is never
     * left next to a journal or WAL that SQLite could read over a fresh one. Returns the folder, or null when there was
     * nothing to move. A file that cannot be moved throws, and nothing is deleted.
     */
    @Synchronized
    fun moveAside(database: File): File? {
        val files = (SIDE_FILES.map { File(database.path + it) } + database).filter { it.exists() }
        if (files.isEmpty()) return null
        val folder = newFolder()
        File(folder, NOTICE).writeText(database.name)
        pending = true
        files.forEach { Files.move(it.toPath(), File(folder, it.name).toPath()) }
        moved = true
        return folder
    }

    /** A [moveAside] happened that [takeMoved] has not taken yet; reading it takes nothing. */
    val movedSinceTaken: Boolean get() = moved

    /** True once after a [moveAside]: the database the store has open is gone, so it opens the fresh one. */
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
        private val SIDE_FILES = listOf("-wal", "-shm", "-journal")
    }
}

/** The notice after a damaged diary was set aside (approved wording, D15). */
internal const val DIARY_DAMAGED = "The diary could not be read. Import an export to restore it."
