package com.iron.breakfast.profile

import com.iron.breakfast.data.DiaryRows
import com.iron.breakfast.transfer.DiaryData

/** [diary]'s export, replace and Delete all, with the profile values that live beside the database. */
internal class ProfileDiaryData(private val diary: DiaryData, private val prefs: ProfilePrefs) : DiaryData {
    override suspend fun exportRows(): DiaryRows = diary.exportRows().copy(profile = prefs.read())

    /** A file from before the profile has none: the phone's stays. */
    override suspend fun replaceDiary(rows: DiaryRows) {
        diary.replaceDiary(rows)
        rows.profile?.let(prefs::writeNow)
    }

    override suspend fun deleteAllData(alsoPreferences: Boolean) {
        diary.deleteAllData(alsoPreferences)
        prefs.clearNow()
    }
}
