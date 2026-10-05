package com.iron.breakfast.data

import android.content.Context
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.Preference
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class StorageModel(
    val foods: List<FoodRecord> = emptyList(),
    val entries: List<MealEntry> = emptyList(),
    val preferences: List<Preference> = emptyList()
)

object StorageCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(model: StorageModel): String = json.encodeToString(StorageModel.serializer(), model)

    fun encodeEntries(entries: List<MealEntry>): String =
        json.encodeToString(ListSerializer(MealEntry.serializer()), entries)

    fun decodeOrNull(value: String): StorageModel? = runCatching {
        json.decodeFromString(StorageModel.serializer(), value)
    }.getOrNull()

    fun decode(value: String): StorageModel = decodeOrNull(value) ?: StorageModel()

    fun decodeEntriesOrNull(value: String): List<MealEntry>? = runCatching {
        json.decodeFromString(ListSerializer(MealEntry.serializer()), value)
    }.getOrNull()
}

object StorageMigrations {
    fun addFixtureConversions(model: StorageModel, fixtures: List<FoodRecord> = FoodCatalog.fixtures): StorageModel {
        val fixtureByKey = fixtures.associateBy { it.id }
        val migratedFoods = model.foods.map { existing ->
            val fixture = fixtureByKey[existing.id]
            if (fixture == null || existing.name != fixture.name || existing.origin != fixture.origin) {
                existing
            } else {
                val mergedConversions = existing.conversions + fixture.conversions.filter { candidate ->
                    existing.conversions.none { it.unit == candidate.unit }
                }
                existing.copy(conversions = mergedConversions)
            }
        }
        return model.copy(foods = migratedFoods)
    }
}

class BreakfastStore(context: Context) {
    private val preferences = context.getSharedPreferences("breakfast_store", Context.MODE_PRIVATE)
    private var model = loadModel()

    init {
        var changed = false
        if (model.foods.isEmpty()) {
            model = model.copy(foods = FoodCatalog.fixtures)
            changed = true
        }
        val migrated = StorageMigrations.addFixtureConversions(model)
        if (migrated != model) {
            model = migrated
            changed = true
        }
        if (changed) {
            persist()
        }
    }

    fun foods(): List<FoodRecord> = model.foods

    fun entries(): List<MealEntry> = model.entries

    fun preferenceMap(): Map<String, Preference> = model.preferences.associateBy { it.foodId }

    fun saveEntries(entries: List<MealEntry>) {
        model = model.copy(entries = model.entries + entries)
        persist()
    }

    fun replaceEntry(updated: MealEntry) {
        model = model.copy(entries = model.entries.map { entry ->
            if (entry.id == updated.id) updated else entry
        })
        persist()
    }

    fun reinforce(foodId: String) {
        val existing = preferenceMap()[foodId] ?: Preference(foodId)
        val updated = existing.copy(
            explicitCount = existing.explicitCount + 1,
            lastReinforcedEpochMs = System.currentTimeMillis()
        )
        model = model.copy(preferences = model.preferences.filterNot { it.foodId == foodId } + updated)
        persist()
    }

    fun upsertFood(food: FoodRecord) {
        model = model.copy(foods = model.foods.filterNot { it.id == food.id } + food)
        persist()
    }

    private fun persist() {
        preferences.edit()
            .putString(KEY, StorageCodec.encode(model))
            .putString(ENTRIES_KEY, StorageCodec.encodeEntries(model.entries))
            .commit()
    }

    private fun loadModel(): StorageModel {
        val raw = preferences.getString(KEY, null)
        val decoded = raw?.let(StorageCodec::decodeOrNull)
        if (decoded != null) return decoded
        val recoveredEntries = preferences.getString(ENTRIES_KEY, null)
            ?.let(StorageCodec::decodeEntriesOrNull)
            ?: emptyList()
        return StorageModel(entries = recoveredEntries)
    }

    companion object {
        private const val KEY = "breakfast_payload"
        private const val ENTRIES_KEY = "breakfast_entries_v1"
    }
}
