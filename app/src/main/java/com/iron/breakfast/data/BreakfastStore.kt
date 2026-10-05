package com.iron.breakfast.data

import android.content.Context
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.Preference
import com.iron.breakfast.domain.NutritionValues
import kotlinx.serialization.Serializable
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

    fun decode(value: String): StorageModel = runCatching {
        json.decodeFromString(StorageModel.serializer(), value)
    }.getOrDefault(StorageModel())
}

class BreakfastStore(context: Context) {
    private val preferences = context.getSharedPreferences("breakfast_store", Context.MODE_PRIVATE)
    private var model = StorageCodec.decode(preferences.getString(KEY, "") ?: "")

    init {
        if (model.foods.isEmpty()) {
            model = model.copy(foods = FoodCatalog.fixtures)
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
        preferences.edit().putString(KEY, StorageCodec.encode(model)).apply()
    }

    companion object {
        private const val KEY = "breakfast_payload"
    }
}
