package com.iron.breakfast.profile

import android.content.Context
import com.iron.breakfast.domain.ProfileValues
import com.iron.breakfast.settings.SettingsStore
import com.iron.breakfast.settings.SharedPreferencesStore

/**
 * The profile beside the curve, in its own plain SharedPreferences ([FILE_NAME]), so Reset settings never touches it and
 * the backup rules can leave it out. Numbers are stored as text, whole: a Double has no SharedPreferences type.
 */
internal class ProfilePrefs(private val store: SettingsStore) {
    constructor(context: Context) : this(SharedPreferencesStore(context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)))

    fun read() = ProfileValues(
        store.getString(NAME, null).orEmpty(), number(PROTEIN), number(CARBS), number(FAT), number(CURRENT), number(TARGET),
    )

    /** The page's Save: applied in the background, as settings are. */
    fun write(values: ProfileValues) = store.edit { fill(values) }

    /** An import's: on disk before it returns, so "done" is true. */
    fun writeNow(values: ProfileValues) = store.commit { fill(values) }

    /** Delete all's: on disk before it returns, so "done" is true. */
    fun clearNow() = store.commit { clear() }

    private fun SettingsStore.Editor.fill(values: ProfileValues) {
        clear()
        putString(NAME, values.name)
        listOf(PROTEIN to values.proteinTarget, CARBS to values.carbsTarget, FAT to values.fatTarget, CURRENT to values.currentWeight, TARGET to values.targetWeight)
            .forEach { (key, value) -> if (value != null) putString(key, value.toString()) }
    }

    private fun number(key: String): Double? = store.getString(key, null)?.toDoubleOrNull()

    private companion object {
        const val FILE_NAME = "profile"
        const val NAME = "name"
        const val PROTEIN = "protein_target"
        const val CARBS = "carbs_target"
        const val FAT = "fat_target"
        const val CURRENT = "current_weight"
        const val TARGET = "target_weight"
    }
}
