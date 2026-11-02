package com.bosa.breakfast.data

import com.bosa.breakfast.domain.TargetRow
import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.searchKey
import com.bosa.breakfast.domain.searchFinds
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.Preference
import com.bosa.breakfast.domain.Recipe
import com.bosa.breakfast.domain.following
import com.bosa.breakfast.domain.barcodeForms
import com.bosa.breakfast.domain.canonicalBarcode
import com.bosa.breakfast.domain.sameBarcode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private data class StoreSnapshot(
    val foods: List<FoodRecord> = emptyList(),
    val entries: List<MealEntry> = emptyList(),
    val preferences: List<Preference> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val weightPoints: List<WeightPoint> = emptyList(),
    val targetHistory: List<TargetRow> = emptyList(),
)

/**
 * The diary database: [name] in the app's databases (`breakfast.db` in the app; a test passes its own name, never
 * that one), with every migration, and a damaged file set aside in [damaged] instead of deleted (see
 * [DamageSafeOpenHelperFactory]). The store and its instrumented test open it the same way.
 */
internal fun openBreakfastDatabase(context: Context, name: String, damaged: DamagedDiaries): BreakfastDatabase =
    Room.databaseBuilder(context.applicationContext, BreakfastDatabase::class.java, name)
        .openHelperFactory(DamageSafeOpenHelperFactory(damaged))
        .addMigrations(ROOM_MIGRATION_1_2, ROOM_MIGRATION_2_3, ROOM_MIGRATION_3_4, ROOM_MIGRATION_4_5, ROOM_MIGRATION_5_6, ROOM_MIGRATION_6_7, ROOM_MIGRATION_7_8, ROOM_MIGRATION_8_9, ROOM_MIGRATION_9_10, ROOM_MIGRATION_10_11, ROOM_MIGRATION_11_12)
        .build()

/** A write refused because the diary was found damaged and the fresh one is not open yet (see [BreakfastStore]). */
internal class DiaryDamagedException : IllegalStateException("The diary is damaged; the fresh one opens on the next reload.")

/**
 * The only writer. [openDatabase] opens `breakfast.db` ([databaseFile]) in the app; tests pass an in-memory one, with
 * no [damaged].
 *
 * A damaged diary ([damaged], D15) is set aside, never deleted, and the store goes on with a fresh one:
 * - a file SQLite could not even read is set aside before it opens ([initialize]);
 * - found while opening, SQLite set it aside and opened the fresh file itself;
 * - found by a statement during the session, that call fails; then, under the write lock and once it has unwound, the
 *   store closes the whole database, the damaged files are set aside, and it opens and loads the fresh diary
 *   ([reloadIfStale], which the session runs after the failed write; or [initialize] when it was found there). The
 *   session then resets as after an import ([takeDiaryReplaced]). Until then a write is refused
 *   ([DiaryDamagedException]), so nothing commits into the damaged file; every database access, reads included, runs
 *   under [writeMutex], so none is still running on it when it closes.
 */
class BreakfastStore internal constructor(
    private val openDatabase: () -> BreakfastDatabase,
    private val damaged: DamagedDiaries? = null,
    private val databaseFile: File? = null,
) {
    internal constructor(database: BreakfastDatabase) : this({ database })
    constructor(context: Context) : this(DamagedDiaries(File(context.noBackupFilesDir, "damaged")), context)
    private constructor(damaged: DamagedDiaries, context: Context) : this(
        { openBreakfastDatabase(context, "breakfast.db", damaged) }, damaged, context.getDatabasePath("breakfast.db"),
    )

    /** Replaced and used only under [writeMutex] (a fresh diary after damage). */
    @Volatile
    private var database: BreakfastDatabase = openDatabase()

    /** The store went on with a fresh diary during the session; the session has not reset for it yet. */
    @Volatile
    private var replacedAfterDamage = false
    private val writeMutex = Mutex()

    @Volatile
    private var model = StoreSnapshot()

    @Volatile
    private var initialized = false

    /** A write committed but the snapshot read after it failed: [model] is older than the database until a reload. */
    @Volatile
    private var snapshotStale = false

    /** Tests only: the reload after the next commit fails once, as a full cursor or disk error would. */
    @Volatile
    internal var failNextRefreshForTesting = false

    suspend fun initialize() {
        if (initialized) return
        writeMutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                // Before SQLite opens it: a file it cannot read is set aside as it is, so nothing acts on its WAL first.
                databaseFile?.let { damaged?.setAsideIfUnreadable(it) }
                try {
                    load()
                } catch (e: Exception) {
                    // Damage found by a statement here, not by the open.
                    if (damaged?.needsReopen != true) throw e
                    recoverFromDamage()
                }
                // Damage found while opening (or before): the fresh file is the one [load] just read.
                damaged?.takeMoved()
                replacedAfterDamage = false
                // Read the notice markers here, off the main thread, which asks after every reload.
                damaged?.noticePending()
                initialized = true
            }
        }
    }

    /** Clears archived foods, then reads the diary: what opening storage does. Stored foods are never rewritten here. */
    private suspend fun load() {
        database.withTransaction { deleteArchivedFoods() }
        model = readSnapshot()
    }

    /**
     * The fresh diary after damage, under [writeMutex] on IO, once the call that found it has unwound: the whole
     * database closes first (no connection is left on the damaged files), then they are set aside, then the fresh one
     * opens and loads as at start.
     */
    private suspend fun recoverFromDamage() {
        val damaged = damaged ?: return
        runCatching { database.close() }.onFailure { Log.w("Breakfast", "The damaged diary did not close cleanly", it) }
        damaged.setAsideAfterClose()
        damaged.takeMoved()
        database = openDatabase()
        snapshotStale = false
        load()
    }

    /** True once after the store went on with a fresh diary during the session: everything about the old one goes. */
    fun takeDiaryReplaced(): Boolean {
        if (!replacedAfterDamage) return false
        replacedAfterDamage = false
        return true
    }

    /** A damaged diary was set aside and the user has not seen the notice yet. */
    fun damagedNoticePending(): Boolean = damaged?.noticePending() == true

    /** The user saw the notice; the damaged copies stay. */
    fun damagedNoticeSeen() { damaged?.noticeSeen() }

    fun foods(): List<FoodRecord> = model.foods

    fun entries(): List<MealEntry> = model.entries

    /** The recipes as stored, each ingredient with the numbers it keeps (see [Recipe]); the same list until a recipe write. */
    fun recipes(): List<Recipe> = model.recipes

    /** The profile's weight curve. */
    fun weightPoints(): List<WeightPoint> = model.weightPoints

    /** Stores [point] beside any on its day. */
    suspend fun addWeightPoint(point: WeightPoint) {
        requireReady()
        write(WEIGHTS) { database.weightPointDao().insert(point.toEntity()) }
    }

    /** Stores [point] in place of its day's latest, if there is one, in one transaction. */
    suspend fun replaceLatestWeightPoint(point: WeightPoint) {
        requireReady()
        write(WEIGHTS) {
            database.weightPointDao().deleteLatestOfDay(point.dayKey)
            database.weightPointDao().insert(point.toEntity())
        }
    }

    suspend fun deleteWeightPoint(id: Long) {
        requireReady()
        write(WEIGHTS) { database.weightPointDao().delete(id) }
    }

    /** The targets by the day they took effect. */
    fun targetHistory(): List<TargetRow> = model.targetHistory

    /** Stores [row], in place of any on its day. */
    suspend fun saveTargetRow(row: TargetRow) {
        requireReady()
        write(TARGETS) { database.targetHistoryDao().put(row.toEntity()) }
    }

    private var preferenceMapSource: List<Preference>? = null
    private var cachedPreferenceMap: Map<String, Preference> = emptyMap()

    /**
     * Built once per preferences list: the Log page asks for it on every keystroke. A write that did not touch the
     * preferences keeps the same list, so the same map, so the engine keeps its planner and its per-part cache.
     */
    @Synchronized
    fun preferenceMap(): Map<String, Preference> {
        val current = model.preferences
        if (current !== preferenceMapSource) {
            cachedPreferenceMap = current.associateBy { it.foodId }
            preferenceMapSource = current
        }
        return cachedPreferenceMap
    }

    suspend fun saveEntries(entries: List<MealEntry>) {
        requireReady()
        if (entries.isEmpty()) return
        write(ENTRIES) { database.mealEntryDao().insertAll(entries.map { it.toEntity() }) }
    }

    /**
     * Entry edits (MOB-025): reads entry [id] inside the write lock and transaction, so [edit] starts from the stored
     * row, not a snapshot, and writes what it returns with UPDATE ... WHERE id, so a deleted entry is never re-created.
     * [edit] returns null to write nothing. Returns false when the entry is gone.
     */
    suspend fun editEntry(id: String, edit: (MealEntry) -> MealEntry?): Boolean {
        requireReady()
        return write(ENTRIES) {
            val stored = database.mealEntryDao().byIds(listOf(id)).firstOrNull()?.toDomain() ?: return@write false
            edit(stored)?.let { database.mealEntryDao().update(it.copy(id = id).toEntity()) }
            true
        }
    }

    suspend fun reinforce(foodId: String) {
        requireReady()
        write(PREFERENCES) {
            // The stored row, read under the lock: the in-memory snapshot may be older than the database.
            val existing = database.preferenceDao().byFood(foodId)?.toDomain() ?: Preference(foodId)
            val updated = existing.copy(
                explicitCount = existing.explicitCount + 1,
                lastReinforcedEpochMs = System.currentTimeMillis()
            )
            database.preferenceDao().upsert(updated.toEntity())
        }
    }

    /** Saves an edited or new food. */
    suspend fun upsertFood(food: FoodRecord) {
        requireReady()
        write(FOODS) { insertFoodRows(food) }
    }

    suspend fun foodByBarcode(barcode: String): FoodRecord? {
        requireReady()
        // Under the lock, as every database access: a fresh diary after damage must not open while a read still runs
        // on the damaged one (that connection, closed last, would checkpoint into the moved file and unlink the WAL).
        return writeMutex.withLock {
            withContext(Dispatchers.IO) {
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
    }

    suspend fun foodsMatching(query: String = ""): List<FoodRecord> {
        requireReady()
        return model.foods.filter { food -> foodMatchesQuery(food, query) }
    }

    suspend fun foodById(id: String): FoodRecord? {
        requireReady()
        return model.foods.firstOrNull { it.id == id }
    }

    /**
     * Deletes the food for good, with its aliases, conversions and learned pick. Entries are not touched: each keeps its
     * own food name and nutrition snapshot, and nothing requires its food id to name a stored food.
     */
    suspend fun deleteFood(id: String) {
        requireReady()
        write(FOODS + PREFERENCES + RECIPES) {
            deleteFoodRows(id)
        }
    }

    suspend fun deleteEntry(id: String) {
        requireReady()
        write(ENTRIES) { database.openHelper.writableDatabase.execSQL("DELETE FROM meal_entries WHERE id = ?", arrayOf(id)) }
    }

    /**
     * Deletes [expected] in one transaction, skipping every entry whose stored row is no longer exactly its snapshot
     * (edited or already gone). Returns the ids deleted.
     */
    suspend fun deleteEntries(expected: List<MealEntry>): List<String> {
        requireReady()
        if (expected.isEmpty()) return emptyList()
        return write(ENTRIES) {
            val ids = unchangedEntryIds(expected, database.mealEntryDao().byIds(expected.map { it.id }))
            if (ids.isNotEmpty()) database.mealEntryDao().deleteByIds(ids)
            ids
        }
    }

    /** Saves a new or edited recipe with its aliases and ingredients, replacing any stored ones. */
    suspend fun saveRecipe(recipe: Recipe) {
        requireReady()
        write(RECIPES) {
            database.recipeDao().upsert(recipe.toEntity())
            database.recipeAliasDao().deleteForRecipe(recipe.id)
            database.recipeIngredientDao().deleteForRecipe(recipe.id)
            database.recipeAliasDao().insertAll(recipe.toAliasEntities())
            database.recipeIngredientDao().insertAll(recipe.toIngredientEntities())
        }
    }

    /** Deletes a recipe for good, with its aliases, ingredients and learned pick; past entries keep their own numbers. */
    suspend fun deleteRecipe(id: String) {
        requireReady()
        write(RECIPES + PREFERENCES) {
            database.recipeDao().deleteById(id)
            database.recipeAliasDao().deleteForRecipe(id)
            database.recipeIngredientDao().deleteForRecipe(id)
            database.preferenceDao().deleteForFood(id)
        }
    }

    /** Stores [food] without an entry. */
    suspend fun addFood(food: FoodRecord) {
        requireReady()
        write(FOODS) { insertFoodRows(food) }
    }

    suspend fun addFoodAndEntry(food: FoodRecord, entry: MealEntry) {
        requireReady()
        write(FOODS + ENTRIES) {
            insertFoodRows(food)
            database.mealEntryDao().replace(entry.toEntity())
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
                        recipes = database.recipeDao().all(),
                        recipeAliases = database.recipeAliasDao().all(),
                        recipeIngredients = database.recipeIngredientDao().all(),
                        weightPoints = database.weightPointDao().all(),
                        targetHistory = database.targetHistoryDao().all(),
                    )
                }
            }
        }
    }

    /**
     * Import diary: replaces every table with [rows] in one transaction, then, as at start, deletes the foods an older
     * file holds as archived. Any failure rolls the whole transaction back, so the
     * stored diary and [model] stay as they were. [rows] must already have passed [validateDiary].
     */
    internal suspend fun replaceDiary(rows: DiaryRows) {
        requireReady()
        write(ALL_TABLES) {
            deleteRecipeRows()
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
            database.recipeDao().insertAllOrAbort(rows.recipes)
            database.recipeAliasDao().insertAllOrAbort(rows.recipeAliases)
            database.recipeIngredientDao().insertAllOrAbort(rows.recipeIngredients)
            // Null: a file from before the curve says nothing about it, and the phone's stays.
            rows.weightPoints?.let { points ->
                database.weightPointDao().deleteAll()
                database.weightPointDao().insertAllOrAbort(points)
            }
            rows.targetHistory?.let { history ->
                database.targetHistoryDao().deleteAll()
                database.targetHistoryDao().insertAllOrAbort(history)
            }
            deleteArchivedFoods()
        }
    }

    /**
     * Delete all data: in one transaction, every entry, every recipe and every food (with its aliases and conversions) is
     * deleted, as on a first start, which has no foods. [alsoPreferences] also deletes the learned preferences; otherwise
     * they stay. Any failure rolls the whole transaction back. [afterDeletes] runs inside it (tests make it throw).
     */
    internal suspend fun deleteAllData(alsoPreferences: Boolean, afterDeletes: suspend () -> Unit = {}) {
        requireReady()
        write(ALL_TABLES) {
            deleteRecipeRows()
            database.mealEntryDao().deleteAll()
            if (alsoPreferences) database.preferenceDao().deleteAll()
            database.servingConversionDao().deleteAll()
            database.foodAliasDao().deleteAll()
            database.foodDao().deleteAll()
            database.weightPointDao().deleteAll()
            database.targetHistoryDao().deleteAll()
            afterDeletes()
        }
    }

    /**
     * Every user-confirmed write: under [writeMutex], on IO, one transaction for [block], then a reload of the [touched]
     * tables only (MOB-028); the snapshot keeps the very same lists for the others, so a cache keyed on them (the
     * matcher on [foods], the planner on [preferenceMap]) survives. It is NonCancellable once it holds the lock, so a
     * write the user confirmed is never rolled back by a cancel (MOB-027); a cancel while waiting for the lock writes
     * nothing.
     */
    private suspend fun <T> write(touched: Set<StoreTable>, block: suspend () -> T): T = writeMutex.withLock {
        // Damage was found and the fresh diary is not open yet: nothing commits into the damaged file. The write fails;
        // the session then reloads, which opens the fresh diary, and the user tries again there.
        if (damaged?.needsReopen == true) throw DiaryDamagedException()
        withContext(NonCancellable + Dispatchers.IO) {
            commitThenRefresh(
                commit = { database.withTransaction { block() } },
                refresh = {
                    if (failNextRefreshForTesting) { failNextRefreshForTesting = false; error("injected refresh failure") }
                    model = reload(model, tablesToReload(touched, snapshotStale))
                    snapshotStale = false
                },
            ) { error ->
                snapshotStale = true
                Log.w("Breakfast", "Saved, but the diary could not be reloaded; it reloads later", error)
            }
        }
    }

    /**
     * Reloads the snapshot if the read after a committed write failed, or the fresh diary after a damaged one was set
     * aside during the session (the database is opened again and loaded as at start). True when it reloaded.
     */
    suspend fun reloadIfStale(): Boolean {
        if (!snapshotStale && damaged?.needsReopen != true) return false
        return writeMutex.withLock {
            withContext(Dispatchers.IO) {
                if (damaged?.needsReopen == true) {
                    recoverFromDamage()
                    replacedAfterDamage = true
                    return@withContext true
                }
                if (!snapshotStale) return@withContext false
                model = readSnapshot()
                snapshotStale = false
                true
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

    /**
     * A food's rows: the food, its aliases and conversions, and its learned pick. Runs inside a transaction. A recipe that
     * uses it keeps that ingredient's last numbers: the snapshot is brought up to the food as it is now, first.
     */
    private suspend fun deleteFoodRows(id: String) {
        keepIngredientNumbers(id)
        database.foodDao().deleteById(id)
        database.foodAliasDao().deleteForFood(id)
        database.servingConversionDao().deleteForFood(id)
        database.preferenceDao().deleteForFood(id)
    }

    /** The ingredients made of food [foodId] store what they count now, so they keep those numbers once it is gone. */
    private suspend fun keepIngredientNumbers(foodId: String) {
        val ingredients = database.recipeIngredientDao().byFood(foodId)
        if (ingredients.isEmpty()) return
        val entity = database.foodDao().byId(foodId) ?: return
        val food = entity.toDomain(
            aliases = emptyList(),
            conversions = database.servingConversionDao().all().filter { it.foodId == foodId }.map { it.toDomain() },
        )
        database.recipeIngredientDao().insertAll(ingredients.map { it.toDomain().following(food).toEntity(it.recipeId, it.position) })
    }

    private suspend fun deleteRecipeRows() {
        database.recipeIngredientDao().deleteAll()
        database.recipeAliasDao().deleteAll()
        database.recipeDao().deleteAll()
    }

    /**
     * Foods are deleted, not archived: a row an older build archived, or an older diary file brought in, is deleted
     * here. With none left it does nothing, so it runs at every start.
     */
    private suspend fun deleteArchivedFoods() {
        database.foodDao().archivedIds().forEach { deleteFoodRows(it) }
    }

    private suspend fun readSnapshot(): StoreSnapshot = reload(StoreSnapshot(), ALL_TABLES)

    /** [current] with the [tables] read again from the database; every other list is kept as the same object. */
    private suspend fun reload(current: StoreSnapshot, tables: Set<StoreTable>): StoreSnapshot = StoreSnapshot(
        foods = if (StoreTable.FOODS in tables) readFoods() else current.foods,
        entries = if (StoreTable.ENTRIES in tables) database.mealEntryDao().all().map { it.toDomain() } else current.entries,
        preferences = if (StoreTable.PREFERENCES in tables) database.preferenceDao().all().map { it.toDomain() } else current.preferences,
        recipes = if (StoreTable.RECIPES in tables) readRecipes() else current.recipes,
        weightPoints = if (StoreTable.WEIGHTS in tables) database.weightPointDao().all().map { it.toDomain() } else current.weightPoints,
        targetHistory = if (StoreTable.TARGETS in tables) database.targetHistoryDao().all().map { it.toDomain() } else current.targetHistory,
    )

    private suspend fun readRecipes(): List<Recipe> = recipesOf(
        database.recipeDao().all(), database.recipeAliasDao().all(), database.recipeIngredientDao().all(),
    )

    /** The foods with their aliases and conversions. */
    private suspend fun readFoods(): List<FoodRecord> {
        val aliases = database.foodAliasDao().all().groupBy { it.foodId }
        val conversions = database.servingConversionDao().all().groupBy { it.foodId }
        val foods = database.foodDao().all().map { entity ->
            entity.toDomain(
                aliases = aliases[entity.id].orEmpty().map { it.alias },
                // The same mapping as every other read: a stored "" label is no label (null).
                conversions = conversions[entity.id].orEmpty().map { it.toDomain() }
            )
        }
        return foods
    }

    private fun requireReady() {
        check(initialized) { "BreakfastStore.initialize() must complete before reading or writing" }
    }
}

/** The groups of tables a write can touch: foods (with aliases and conversions), entries, preferences, recipes (with theirs). */
internal enum class StoreTable { FOODS, ENTRIES, PREFERENCES, RECIPES, WEIGHTS, TARGETS }

private val WEIGHTS = setOf(StoreTable.WEIGHTS)
private val TARGETS = setOf(StoreTable.TARGETS)
private val FOODS = setOf(StoreTable.FOODS)
private val ENTRIES = setOf(StoreTable.ENTRIES)
private val PREFERENCES = setOf(StoreTable.PREFERENCES)
private val RECIPES = setOf(StoreTable.RECIPES)
internal val ALL_TABLES: Set<StoreTable> = StoreTable.entries.toSet()

/**
 * What to read again after a commit: the tables it [touched], or everything when the snapshot is [stale] (an earlier
 * reload failed, so tables this write did not touch may be older than the database too).
 */
internal fun tablesToReload(touched: Set<StoreTable>, stale: Boolean): Set<StoreTable> = if (stale) ALL_TABLES else touched

/**
 * A write's outcome is its [commit] (MOB-025): once that returns, the write is saved. A [refresh] that fails after it is
 * handed to [onRefreshFailed] (reload later), never thrown as a failed save, so a retry cannot log the same food twice.
 */
internal suspend fun <T> commitThenRefresh(
    commit: suspend () -> T,
    refresh: suspend () -> Unit,
    onRefreshFailed: (Throwable) -> Unit,
): T {
    val result = commit()
    try {
        refresh()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        onRefreshFailed(error)
    }
    return result
}

/** The ids of [expected] whose [stored] row is still exactly the snapshot; an edited or missing entry is left out. */
internal fun unchangedEntryIds(expected: List<MealEntry>, stored: List<MealEntryEntity>): List<String> {
    val byId = stored.associateBy { it.id }
    return expected.filter { byId[it.id] == it.toEntity() }.map { it.id }
}

/**
 * Of the rows holding [barcode] in any of its forms: the exact spelling first, then the canonical spelling, then the
 * lowest id, so the pick is the same every time.
 */
internal fun pickBarcodeMatch(rows: List<FoodEntity>, barcode: String): FoodEntity? {
    val exact = barcode.trim()
    val canonical = canonicalBarcode(barcode)
    return rows.sortedWith(
        compareBy<FoodEntity>({ if (it.barcode?.trim() == exact) 0 else 1 }, { if (it.barcode?.trim() == canonical) 0 else 1 }, { it.id })
    ).firstOrNull()
}

/**
 * The Library's search: [query] is part of a name, an alias or the brand, or the barcode. Names read as the Log matcher
 * reads them ([searchFinds]): a hyphen between letters is a word break on both sides, "coca-cola zero" finds "Coca Cola
 * Zero", "cevapi" finds "Ćevapi", and a query never crosses scripts.
 */
internal fun foodMatchesQuery(food: FoodRecord, query: String): Boolean {
    if (query.isBlank()) return true
    // Only punctuation ("&", "-") names nothing: it does not list every food.
    if (searchKey(query).isBlank()) return false
    return food.allNames().any { searchFinds(it, query) } ||
        food.brand?.let { searchFinds(it, query) } == true || barcodeMatchesQuery(food.barcode, query.trim())
}

/** Library search on a barcode: part of the stored digits, or the whole code in any equivalent spelling. */
internal fun barcodeMatchesQuery(barcode: String?, query: String): Boolean =
    barcode?.contains(query) == true || sameBarcode(barcode, query)
