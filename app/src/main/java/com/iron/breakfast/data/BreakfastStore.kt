package com.iron.breakfast.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.Preference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
                existing.copy(
                    aliases = (existing.aliases + fixture.aliases).distinct(),
                    conversions = mergedConversions,
                    sourceId = existing.sourceId ?: fixture.sourceId,
                    sourceName = existing.sourceName ?: fixture.sourceName,
                    sourceRelease = existing.sourceRelease ?: fixture.sourceRelease,
                    preparation = existing.preparation ?: fixture.preparation,
                    sourceDescription = existing.sourceDescription ?: fixture.sourceDescription
                )
            }
        }
        val existingIds = model.foods.mapTo(mutableSetOf()) { it.id }
        val missingFixtures = fixtures.filter {
            it.origin == FoodOrigin.GENERIC && it.id !in existingIds
        }
        return model.copy(foods = migratedFoods + missingFixtures)
    }
}

private data class StoreSnapshot(
    val foods: List<FoodRecord> = emptyList(),
    val entries: List<MealEntry> = emptyList(),
    val preferences: List<Preference> = emptyList()
)

class BreakfastStore(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        BreakfastDatabase::class.java,
        "breakfast.db"
    ).addMigrations(ROOM_MIGRATION_1_2, ROOM_MIGRATION_2_3, ROOM_MIGRATION_3_4, ROOM_MIGRATION_4_5, ROOM_MIGRATION_5_6).build()
    private val writeMutex = Mutex()

    @Volatile
    private var model = StoreSnapshot()

    @Volatile
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        writeMutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    seedMissingFixtures()
                }
                model = readSnapshot()
                initialized = true
            }
        }
    }

    fun foods(): List<FoodRecord> = model.foods

    fun entries(): List<MealEntry> = model.entries

    fun preferenceMap(): Map<String, Preference> = model.preferences.associateBy { it.foodId }

    suspend fun saveEntries(entries: List<MealEntry>) {
        requireReady()
        if (entries.isEmpty()) return
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    database.mealEntryDao().insertAll(entries.map { it.toEntity() })
                }
                model = readSnapshot()
            }
        }
    }

    suspend fun replaceEntry(updated: MealEntry) {
        requireReady()
        writeMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                database.withTransaction {
                    database.mealEntryDao().replace(updated.toEntity())
                }
                model = readSnapshot()
            }
        }
    }

    suspend fun reinforce(foodId: String) {
        requireReady()
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val existing = preferenceMap()[foodId] ?: Preference(foodId)
                val updated = existing.copy(
                    explicitCount = existing.explicitCount + 1,
                    lastReinforcedEpochMs = System.currentTimeMillis()
                )
                database.withTransaction {
                    database.preferenceDao().upsert(updated.toEntity())
                }
                model = readSnapshot()
            }
        }
    }

    suspend fun upsertFood(food: FoodRecord) {
        requireReady()
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    database.foodDao().upsert(food.toEntity())
                    database.foodAliasDao().deleteForFood(food.id)
                    database.servingConversionDao().deleteForFood(food.id)
                    database.foodAliasDao().insertAll(food.toAliasEntities())
                    database.servingConversionDao().insertAll(food.toConversionEntities())
                }
                model = readSnapshot()
            }
        }
    }

    suspend fun foodByBarcode(barcode: String): FoodRecord? {
        requireReady()
        return withContext(Dispatchers.IO) {
            val entity = database.foodDao().byBarcode(barcode)?.takeUnless { it.archived } ?: return@withContext null
            val aliases = database.foodAliasDao().all().filter { it.foodId == entity.id }.map { it.alias }
            val conversions = database.servingConversionDao().all().filter { it.foodId == entity.id }.map {
                it.toDomain()
            }
            entity.toDomain(aliases, conversions)
        }
    }

    suspend fun foodsMatching(query: String = "", includeArchived: Boolean = false): List<FoodRecord> {
        requireReady()
        val normalized = query.trim().lowercase()
        return model.foods.filter { food ->
            (includeArchived || !food.archived) &&
                (normalized.isBlank() || food.allNames().any { it.lowercase().contains(normalized) } ||
                    food.brand?.lowercase()?.contains(normalized) == true || food.barcode?.contains(normalized) == true)
        }
    }

    suspend fun foodById(id: String): FoodRecord? {
        requireReady()
        return model.foods.firstOrNull { it.id == id }
    }

    suspend fun archiveFood(id: String) {
        requireReady()
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val current = database.foodDao().all().firstOrNull { it.id == id } ?: return@withContext
                database.withTransaction { database.foodDao().upsert(current.copy(archived = true)) }
                model = readSnapshot()
            }
        }
    }

    suspend fun restoreFood(id: String) {
        requireReady()
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val current = database.foodDao().all().firstOrNull { it.id == id } ?: return@withContext
                database.withTransaction { database.foodDao().upsert(current.copy(archived = false)) }
                model = readSnapshot()
            }
        }
    }

    suspend fun deleteEntry(id: String) {
        requireReady()
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                database.withTransaction { database.openHelper.writableDatabase.execSQL("DELETE FROM meal_entries WHERE id = ?", arrayOf(id)) }
                model = readSnapshot()
            }
        }
    }

    suspend fun addFoodAndEntry(food: FoodRecord, entry: MealEntry) {
        requireReady()
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    val current = database.foodDao().all().firstOrNull { it.id == food.id }
                    if (current?.archived != true) {
                        database.foodDao().upsert(food.toEntity())
                        database.foodAliasDao().deleteForFood(food.id)
                        database.servingConversionDao().deleteForFood(food.id)
                        database.foodAliasDao().insertAll(food.toAliasEntities())
                        database.servingConversionDao().insertAll(food.toConversionEntities())
                    }
                    database.mealEntryDao().replace(entry.toEntity())
                }
                model = readSnapshot()
            }
        }
    }

    private suspend fun seedMissingFixtures() {
        val existingIds = database.foodDao().ids().toSet()
        val missing = fixtureRowsMissingFrom(existingIds)
        if (missing.isEmpty()) return
        database.foodDao().insertAll(missing.map { it.toEntity() })
        database.foodAliasDao().insertAll(missing.flatMap { it.toAliasEntities() })
        database.servingConversionDao().insertAll(missing.flatMap { it.toConversionEntities() })
    }

    private suspend fun readSnapshot(): StoreSnapshot {
        val aliases = database.foodAliasDao().all().groupBy { it.foodId }
        val conversions = database.servingConversionDao().all().groupBy { it.foodId }
        val foods = database.foodDao().all().map { entity ->
            entity.toDomain(
                aliases = aliases[entity.id].orEmpty().map { it.alias },
                conversions = conversions[entity.id].orEmpty().map {
                    ServingConversion(
                        unit = MeasureUnit.valueOf(it.unit),
                        baseUnitsPerServing = it.baseUnitsPerServing,
                        provenance = it.provenance,
                        approximate = it.approximate,
                        label = it.label
                    )
                }
            )
        }
        return StoreSnapshot(
            foods = foods,
            entries = database.mealEntryDao().all().map { it.toDomain() },
            preferences = database.preferenceDao().all().map { it.toDomain() }
        )
    }

    private fun requireReady() {
        check(initialized) { "BreakfastStore.initialize() must complete before reading or writing" }
    }
}
