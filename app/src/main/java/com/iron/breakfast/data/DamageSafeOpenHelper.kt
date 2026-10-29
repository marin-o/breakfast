package com.iron.breakfast.data

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File

/**
 * The framework open helper with one change: a database SQLite reports as damaged is set aside ([DamagedDiaries]),
 * not deleted, which is what the default `onCorruption` does. Everything else (Room's own callback: create, the
 * migrations, open) is the callback Room passed in, unchanged.
 *
 * SQLite reports damage while opening (it then opens again, on the fresh file) and when a later statement finds it
 * (it is copied as found, and the store closes, sets aside and reopens, see `BreakfastStore`).
 */
internal class DamageSafeOpenHelperFactory(
    private val damaged: DamagedDiaries,
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper = delegate.create(
        SupportSQLiteOpenHelper.Configuration(
            configuration.context,
            configuration.name,
            SetAsideOnCorruption(configuration.callback, damaged),
            configuration.useNoBackupDirectory,
            configuration.allowDataLossOnRecovery,
        )
    )
}

/** [delegate] in all but [onCorruption]. */
private class SetAsideOnCorruption(
    private val delegate: SupportSQLiteOpenHelper.Callback,
    private val damaged: DamagedDiaries,
) : SupportSQLiteOpenHelper.Callback(delegate.version) {
    override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)
    override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)
    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = delegate.onUpgrade(db, oldVersion, newVersion)
    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = delegate.onDowngrade(db, oldVersion, newVersion)
    override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)

    /**
     * Sets the damaged database aside, never deletes it. Found while opening, no connection is open on it, so its files
     * move now (SQLite then opens again, on a fresh file). Found by a statement, that statement is still running on an
     * open connection: nothing is closed or moved under it, only copied as found, and the store sets it aside after
     * the call has unwound (see [DamagedDiaries]).
     */
    override fun onCorruption(db: SupportSQLiteDatabase) {
        val path = db.path
        Log.e("Breakfast", "The diary database is damaged; it is set aside, and a fresh one is opened")
        // An in-memory database has nothing on disk to keep.
        if (path == null || path == ":memory:" || path.isBlank()) return
        if (!db.isOpen) {
            damaged.moveAside(File(path))
            return
        }
        try {
            damaged.snapshot(File(path))
        } catch (e: Exception) {
            // The store still sets the damaged files aside after it closed them; only the copy as found is missing.
            Log.e("Breakfast", "The damaged diary could not be copied as found", e)
        }
    }
}
