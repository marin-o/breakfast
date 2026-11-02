package com.bosa.breakfast.profile

import android.content.Context
import com.bosa.breakfast.domain.Activity
import com.bosa.breakfast.domain.Goal
import com.bosa.breakfast.domain.Macro
import com.bosa.breakfast.domain.ProfileValues
import com.bosa.breakfast.domain.Sex
import com.bosa.breakfast.settings.SettingsStore
import com.bosa.breakfast.settings.SharedPreferencesStore

/**
 * The profile beside the curve, in its own plain SharedPreferences ([FILE_NAME]), so Reset settings never touches it and
 * the backup rules can leave it out. Numbers are stored as text, whole: a Double has no SharedPreferences type.
 */
internal class ProfilePrefs(private val store: SettingsStore) {
    constructor(context: Context) : this(SharedPreferencesStore(context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)))

    fun read() = ProfileValues(
        store.getString(NAME, null).orEmpty(), number(PROTEIN), number(CARBS), number(FAT), number(CURRENT), number(TARGET), number(KCAL), store.getBoolean(SEVERAL_WEIGHTS, false),
        number(HEIGHT), store.getString(AGE, null)?.toIntOrNull(), named<Sex>(SEX), named<Activity>(ACTIVITY), named<Goal>(GOAL), number(PACE), store.getBoolean(TRACKING_ONLY, false),
        number(PROTEIN_PERCENT), number(CARBS_PERCENT), number(FAT_PERCENT),
        store.getString(FOLLOWING, null).orEmpty().split(',').mapNotNullTo(LinkedHashSet()) { name -> Macro.entries.firstOrNull { it.name == name } },
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
        listOf(PROTEIN to values.proteinTarget, CARBS to values.carbsTarget, FAT to values.fatTarget, CURRENT to values.currentWeight, TARGET to values.targetWeight, KCAL to values.kcalTarget, HEIGHT to values.heightCm, PACE to values.paceKgPerWeek, PROTEIN_PERCENT to values.proteinPercent, CARBS_PERCENT to values.carbsPercent, FAT_PERCENT to values.fatPercent)
            .forEach { (key, value) -> if (value != null) putString(key, value.toString()) }
        putBoolean(SEVERAL_WEIGHTS, values.severalWeights)
        putBoolean(TRACKING_ONLY, values.trackingOnly)
        listOf(AGE to values.age?.toString(), SEX to values.sex?.name, ACTIVITY to values.activity?.name, GOAL to values.goal?.name)
            .forEach { (key, value) -> if (value != null) putString(key, value) }
        if (values.followingMacros.isNotEmpty()) putString(FOLLOWING, values.followingMacros.joinToString(",") { it.name })
    }

    private fun number(key: String): Double? = store.getString(key, null)?.toDoubleOrNull()

    private inline fun <reified T : Enum<T>> named(key: String): T? =
        store.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } }

    private companion object {
        const val FILE_NAME = "profile"
        const val NAME = "name"
        const val PROTEIN = "protein_target"
        const val CARBS = "carbs_target"
        const val FAT = "fat_target"
        const val CURRENT = "current_weight"
        const val TARGET = "target_weight"
        const val KCAL = "kcal_target"
        const val SEVERAL_WEIGHTS = "several_weights"
        const val HEIGHT = "height_cm"
        const val AGE = "age"
        const val SEX = "sex"
        const val ACTIVITY = "activity"
        const val GOAL = "goal"
        const val PACE = "pace_kg_per_week"
        const val TRACKING_ONLY = "tracking_only"
        const val PROTEIN_PERCENT = "protein_percent"
        const val CARBS_PERCENT = "carbs_percent"
        const val FAT_PERCENT = "fat_percent"
        const val FOLLOWING = "following_macros"
    }
}
