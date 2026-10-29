package com.iron.breakfast.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Deletes the test database [name] (a name in the app's databases, or an absolute path) with everything SQLite and Room
 * leave beside it: `-wal`, `-shm`, `-journal`, and Room's lock files, next to it and in the cache dir. These tests run on
 * the owner's phone, so it refuses the app's own `breakfast.db`, whose lock in the cache is the app's.
 */
internal fun deleteTestDatabase(context: Context, name: String) {
    val file = if (File(name).isAbsolute) File(name) else context.getDatabasePath(name)
    check(file.name != "breakfast.db") { "Never the app's own diary" }
    SQLiteDatabase.deleteDatabase(file)
    listOf("", "-wal", "-shm", "-journal", ".lck").forEach { File(file.path + it).delete() }
    File(context.cacheDir, file.name + ".lck").delete()
}

/** The names the instrumented tests give their databases; nothing else is ever swept. */
private val TEST_PREFIXES = listOf("breakfast-test-", "migration-", "damaged-test-", "bench-")

/**
 * The last cleanup of a test class, after every database it opened was closed (its rules included): any file in the
 * app's databases or cache dir whose name starts with a test database's name goes, whatever left it (a lock file
 * Room or the open helper made again after the per-test delete, for one). Finalizers run first, so a connection only
 * they would close has closed. The app's own `breakfast.db` and its files never match.
 */
internal fun sweepTestDatabases(context: Context) {
    repeat(3) { System.gc(); System.runFinalization() }
    listOfNotNull(context.getDatabasePath("x").parentFile, context.cacheDir).forEach { dir ->
        dir.listFiles()?.filter { file -> TEST_PREFIXES.any { file.name.startsWith(it) } && !file.name.startsWith("breakfast.db") }
            ?.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
    }
}
