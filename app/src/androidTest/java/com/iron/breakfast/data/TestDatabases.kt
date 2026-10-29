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
