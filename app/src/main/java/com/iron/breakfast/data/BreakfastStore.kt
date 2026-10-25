package com.iron.breakfast.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.BuiltInCatalogSync
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.Preference
import com.iron.breakfast.domain.barcodeForms
import com.iron.breakfast.domain.canonicalBarcode
import com.iron.breakfast.domain.sameBarcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class StoreSnapshot(
    val foods: List<FoodRecord> = emptyList(),
    val entries: List<MealEntry> = emptyList(),
    val preferences: List<Preference> = emptyList()
)

/** The only writer. [database] is `breakfast.db` in the app; tests pass an in-memory one. */
class BreakfastStore internal constructor(private val database: BreakfastDatabase) {
    constructor(context: Context) : this(
        Room.databaseBuilder(
            context.applicationContext,
            BreakfastDatabase::class.java,
            "breakfast.db"
        ).addMigrations(ROOM_MIGRATION_1_2, ROOM_MIGRATION_2_3, ROOM_MIGRATION_3_4, ROOM_MIGRATION_4_5, ROOM_MIGRATION_5_6).build()
    )
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
                    syncBuiltInFoods()
                }
                model = readSnapshot()
                initialized = true
            }
        }
    }

    fun foods(): List<FoodRecord> = model.foods

    fun entries(): List<MealEntry> = model.entries

    private var preferenceMapModel: StoreSnapshot? = null
    private var cachedPreferenceMap: Map<String, Preference> = emptyMap()

    /** Built once per snapshot: the Log page asks for it on every keystroke. */
    @Synchronized
    fun preferenceMap(): Map<String, Preference> {
        val current = model
        if (current !== preferenceMapModel) {
            cachedPreferenceMap = current.preferences.associateBy { it.foodId }
            preferenceMapModel = current
        }
        return cachedPreferenceMap
    }

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
            // A row matches in whichever form it was stored (UPC-A or its EAN-13); an exact spelling is preferred.
            val forms = barcodeForms(barcode).ifEmpty { listOf(barcode) }
            val entity = pickBarcodeMatch(database.foodDao().byBarcodes(forms), barcode) ?: return@withContext null
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
                    food.brand?.lowercase()?.contains(normalized) == true || barcodeMatchesQuery(food.barcode, normalized))
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

    /**
     * Deletes [expected] in one transaction, skipping every entry whose stored row is no longer exactly its snapshot
     * (edited or already gone). Returns the ids deleted.
     */
    suspend fun deleteEntries(expected: List<MealEntry>): List<String> {
        requireReady()
        if (expected.isEmpty()) return emptyList()
        return writeMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                val deleted = database.withTransaction {
                    val ids = unchangedEntryIds(expected, database.mealEntryDao().byIds(expected.map { it.id }))
                    if (ids.isNotEmpty()) database.mealEntryDao().deleteByIds(ids)
                    ids
                }
                model = readSnapshot()
                deleted
            }
        }
    }

    /** Stores [food] without an entry. An archived food stays as it is. */
    suspend fun addFood(food: FoodRecord) {
        requireReady()
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    if (database.foodDao().all().firstOrNull { it.id == food.id }?.archived != true) insertFoodRows(food)
                }
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
                    if (current?.archived != true) insertFoodRows(food)
                    database.mealEntryDao().replace(entry.toEntity())
                }
                model = readSnapshot()
            }
        }
    }

    /** Every stored row, read in one transaction so the copy is consistent. For Export diary. */
    internal suspend fun exportRows(): DiaryRows {
        requireReady()
        return writeMutex.withLock {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    DiaryRows(
                        foods = database.foodDao().all(),
                        aliases = database.foodAliasDao().all(),
                        conversions = database.servingConversionDao().all(),
                        preferences = database.preferenceDao().all(),
                        entries = database.mealEntryDao().all(),
                    )
                }
            }
        }
    }

    /**
     * Import diary: replaces every table with [rows] in one transaction, then seeds and syncs the catalogue as at start.
     * Any failure rolls the whole transaction back, so the stored diary and [model] stay as they were. [rows] must
     * already have passed [validateDiary].
     */
    internal suspend fun replaceDiary(rows: DiaryRows) {
        requireReady()
        writeMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                database.withTransaction {
                    database.mealEntryDao().deleteAll()
                    database.preferenceDao().deleteAll()
                    database.servingConversionDao().deleteAll()
                    database.foodAliasDao().deleteAll()
                    database.foodDao().deleteAll()
                    database.foodDao().insertAllOrAbort(rows.foods)
                    database.foodAliasDao().insertAllOrAbort(rows.aliases)
                    database.servingConversionDao().insertAllOrAbort(rows.conversions)
                    database.preferenceDao().insertAllOrAbort(rows.preferences)
                    database.mealEntryDao().insertAllOrAbort(rows.entries)
                    seedMissingFixtures()
                    syncBuiltInFoods()
                }
                model = readSnapshot()
            }
        }
    }

    /** Writes the food row with its aliases and conversions, replacing any stored ones. Runs inside a transaction. */
    private suspend fun insertFoodRows(food: FoodRecord) {
        database.foodDao().upsert(food.toEntity())
        database.foodAliasDao().deleteForFood(food.id)
        database.servingConversionDao().deleteForFood(food.id)
        database.foodAliasDao().insertAll(food.toAliasEntities())
        database.servingConversionDao().insertAll(food.toConversionEntities())
    }

    /** Rewrites the stored built-in foods whose catalogue content changed; see [BuiltInCatalogSync]. */
    private suspend fun syncBuiltInFoods() {
        val changed = BuiltInCatalogSync.changedRows(readSnapshot().foods, FoodCatalog.fixtures)
        changed.forEach { food ->
            database.foodDao().upsert(food.toEntity())
            database.foodAliasDao().deleteForFood(food.id)
            database.servingConversionDao().deleteForFood(food.id)
            database.foodAliasDao().insertAll(food.toAliasEntities())
            database.servingConversionDao().insertAll(food.toConversionEntities())
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

/** The ids of [expected] whose [stored] row is still exactly the snapshot; an edited or missing entry is left out. */
internal fun unchangedEntryIds(expected: List<MealEntry>, stored: List<MealEntryEntity>): List<String> {
    val byId = stored.associateBy { it.id }
    return expected.filter { byId[it.id] == it.toEntity() }.map { it.id }
}

/**
 * Of the rows holding [barcode] in any of its forms, a live one: the exact spelling first, then the canonical
 * spelling, then the lowest id, so the pick is the same every time. Null when only archived rows match.
 */
internal fun pickBarcodeMatch(rows: List<FoodEntity>, barcode: String): FoodEntity? {
    val exact = barcode.trim()
    val canonical = canonicalBarcode(barcode)
    return rows.filterNot { it.archived }.sortedWith(
        compareBy<FoodEntity>({ if (it.barcode?.trim() == exact) 0 else 1 }, { if (it.barcode?.trim() == canonical) 0 else 1 }, { it.id })
    ).firstOrNull()
}

/** Library search on a barcode: part of the stored digits, or the whole code in any equivalent spelling. */
internal fun barcodeMatchesQuery(barcode: String?, query: String): Boolean =
    barcode?.contains(query) == true || sameBarcode(barcode, query)
