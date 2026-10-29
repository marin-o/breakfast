package com.iron.breakfast.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.Preference
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.dayKeyForEpochMs

@Entity(tableName = "foods", indices = [androidx.room.Index(value = ["barcode"]), androidx.room.Index(value = ["brand"])])
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val origin: String,
    @ColumnInfo(name = "nutrition_amount") val nutritionAmount: Double,
    @ColumnInfo(name = "nutrition_unit") val nutritionUnit: String,
    val calories: Double,
    val protein: Double?,
    val carbohydrates: Double?,
    val fat: Double?,
    @ColumnInfo(name = "default_amount") val defaultAmount: Double,
    @ColumnInfo(name = "default_unit") val defaultUnit: String,
    @ColumnInfo(name = "source_id") val sourceId: String?,
    @ColumnInfo(name = "source_name") val sourceName: String?,
    @ColumnInfo(name = "source_release") val sourceRelease: String?,
    val preparation: String?,
    @ColumnInfo(name = "source_description") val sourceDescription: String?,
    val brand: String?,
    val barcode: String?,
    /**
     * Kept for the schema only: foods are deleted, not archived. A row stored as archived by an older build is deleted
     * when the store opens ([BreakfastStore.initialize]); the app never writes true.
     */
    val archived: Boolean = false,
    @ColumnInfo(name = "personal_override") val personalOverride: Boolean = false
)

@Entity(tableName = "food_aliases", primaryKeys = ["foodId", "alias"])
data class FoodAliasEntity(
    val foodId: String,
    val alias: String
)

@Entity(tableName = "serving_conversions", primaryKeys = ["foodId", "unit", "label"])
data class ServingConversionEntity(
    val foodId: String,
    val unit: String,
    @ColumnInfo(name = "base_units_per_serving") val baseUnitsPerServing: Double,
    val provenance: String,
    val approximate: Boolean,
    /** Empty means the built-in, unlabeled conversion for this unit. */
    val label: String = ""
)

fun ServingConversionEntity.toDomain(): ServingConversion = ServingConversion(
    unit = MeasureUnit.valueOf(unit),
    baseUnitsPerServing = baseUnitsPerServing,
    provenance = provenance,
    approximate = approximate,
    label = label.takeIf { it.isNotBlank() },
)

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val foodId: String,
    @ColumnInfo(name = "explicit_count") val explicitCount: Int,
    @ColumnInfo(name = "last_reinforced_epoch_ms") val lastReinforcedEpochMs: Long
)

@Entity(tableName = "meal_entries")
data class MealEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "food_name") val foodName: String,
    @ColumnInfo(name = "entered_amount") val enteredAmount: Double,
    @ColumnInfo(name = "entered_unit") val enteredUnit: String,
    @ColumnInfo(name = "resolved_base_amount") val resolvedBaseAmount: Double,
    @ColumnInfo(name = "resolved_base_unit") val resolvedBaseUnit: String = MeasureUnit.UNKNOWN.name,
    @ColumnInfo(name = "snapshot_calories") val snapshotCalories: Double,
    @ColumnInfo(name = "snapshot_protein") val snapshotProtein: Double?,
    @ColumnInfo(name = "snapshot_carbohydrates") val snapshotCarbohydrates: Double?,
    @ColumnInfo(name = "snapshot_fat") val snapshotFat: Double?,
    @ColumnInfo(name = "conversion_provenance") val conversionProvenance: String,
    @ColumnInfo(name = "approximate_conversion") val approximateConversion: Boolean,
    @ColumnInfo(name = "saved_at_epoch_ms") val savedAtEpochMs: Long,
    @ColumnInfo(name = "source_phrase") val sourcePhrase: String,
    @ColumnInfo(name = "meal_slot") val mealSlot: String,
    @ColumnInfo(name = "day_key") val dayKey: String
)

@Dao
interface FoodDao {
    @Query("SELECT id FROM foods")
    suspend fun ids(): List<String>

    // Every query that feeds the snapshot has an explicit order; rowid is the table order a plain scan gave before.
    @Query("SELECT * FROM foods ORDER BY rowid")
    suspend fun all(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode IN (:barcodes)")
    suspend fun byBarcodes(barcodes: List<String>): List<FoodEntity>

    /** Rows an older build archived (or an older diary file brought in): they are deleted ([BreakfastStore]). */
    @Query("SELECT id FROM foods WHERE archived = 1")
    suspend fun archivedIds(): List<String>

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: FoodEntity)

    /** Diary import: a clash fails the whole transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(foods: List<FoodEntity>)

    @Query("DELETE FROM foods")
    suspend fun deleteAll()
}

@Dao
interface FoodAliasDao {
    @Query("SELECT * FROM food_aliases ORDER BY rowid")
    suspend fun all(): List<FoodAliasEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(aliases: List<FoodAliasEntity>)

    @Query("DELETE FROM food_aliases WHERE foodId = :foodId")
    suspend fun deleteForFood(foodId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(aliases: List<FoodAliasEntity>)

    @Query("DELETE FROM food_aliases")
    suspend fun deleteAll()
}

@Dao
interface ServingConversionDao {
    @Query("SELECT * FROM serving_conversions ORDER BY rowid")
    suspend fun all(): List<ServingConversionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(conversions: List<ServingConversionEntity>)

    @Query("DELETE FROM serving_conversions WHERE foodId = :foodId")
    suspend fun deleteForFood(foodId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(conversions: List<ServingConversionEntity>)

    @Query("DELETE FROM serving_conversions")
    suspend fun deleteAll()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences ORDER BY rowid")
    suspend fun all(): List<PreferenceEntity>

    @Query("SELECT * FROM preferences WHERE foodId = :foodId")
    suspend fun byFood(foodId: String): PreferenceEntity?

    @Query("DELETE FROM preferences WHERE foodId = :foodId")
    suspend fun deleteForFood(foodId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: PreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(preferences: List<PreferenceEntity>)

    @Query("DELETE FROM preferences")
    suspend fun deleteAll()
}

@Dao
interface MealEntryDao {
    @Query("SELECT * FROM meal_entries ORDER BY saved_at_epoch_ms ASC, rowid ASC")
    suspend fun all(): List<MealEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MealEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(entry: MealEntryEntity)

    /** UPDATE ... WHERE id: a row that is gone stays gone (an edit never re-creates it). */
    @Update
    suspend fun update(entry: MealEntryEntity): Int

    @Query("SELECT * FROM meal_entries WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<MealEntryEntity>

    @Query("DELETE FROM meal_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(entries: List<MealEntryEntity>)

    @Query("DELETE FROM meal_entries")
    suspend fun deleteAll()
}

@Database(
    entities = [
        FoodEntity::class,
        FoodAliasEntity::class,
        ServingConversionEntity::class,
        PreferenceEntity::class,
        MealEntryEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class BreakfastDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun foodAliasDao(): FoodAliasDao
    abstract fun servingConversionDao(): ServingConversionDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun mealEntryDao(): MealEntryDao
}

val ROOM_MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE foods ADD COLUMN brand TEXT")
        db.execSQL("ALTER TABLE foods ADD COLUMN barcode TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_foods_barcode ON foods(barcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_foods_brand ON foods(brand)")
    }
}

val ROOM_MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE foods ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    }
}

val ROOM_MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE foods ADD COLUMN personal_override INTEGER NOT NULL DEFAULT 0")
    }
}

val ROOM_MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meal_entries ADD COLUMN resolved_base_unit TEXT NOT NULL DEFAULT 'UNKNOWN'")
        // Existing entries already contain their food id and the old food row's
        // nutrition basis. Capture that basis once so future food edits cannot
        // rewrite the historical printed fact.
        db.execSQL(
            "UPDATE meal_entries SET resolved_base_unit = " +
                "(SELECT nutrition_unit FROM foods WHERE foods.id = meal_entries.food_id) " +
                "WHERE resolved_base_unit = 'UNKNOWN' AND EXISTS " +
                "(SELECT 1 FROM foods WHERE foods.id = meal_entries.food_id)"
        )
    }
}

val ROOM_MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE serving_conversions_new (
                foodId TEXT NOT NULL,
                unit TEXT NOT NULL,
                base_units_per_serving REAL NOT NULL,
                provenance TEXT NOT NULL,
                approximate INTEGER NOT NULL,
                label TEXT NOT NULL,
                PRIMARY KEY(foodId, unit, label)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO serving_conversions_new (foodId, unit, base_units_per_serving, provenance, approximate, label)
            SELECT foodId, unit, base_units_per_serving, provenance, approximate, ''
            FROM serving_conversions
        """.trimIndent())
        db.execSQL("DROP TABLE serving_conversions")
        db.execSQL("ALTER TABLE serving_conversions_new RENAME TO serving_conversions")
    }
}

internal fun fixtureRowsMissingFrom(existingIds: Set<String>): List<FoodRecord> =
    FoodCatalog.fixtures.filterNot { it.id in existingIds }

fun FoodRecord.toEntity(): FoodEntity = FoodEntity(
    id = id,
    name = name,
    category = category,
    origin = origin.name,
    nutritionAmount = nutrition.amount,
    nutritionUnit = nutrition.unit.name,
    calories = nutrition.values.calories,
    protein = nutrition.values.protein,
    carbohydrates = nutrition.values.carbohydrates,
    fat = nutrition.values.fat,
    defaultAmount = defaultAmount,
    defaultUnit = defaultUnit.name,
    sourceId = sourceId,
    sourceName = sourceName,
    sourceRelease = sourceRelease,
    preparation = preparation,
    sourceDescription = sourceDescription,
    brand = brand,
    barcode = barcode,
    personalOverride = personalOverride
)

fun FoodRecord.toAliasEntities(): List<FoodAliasEntity> = aliases.distinct().map { alias ->
    FoodAliasEntity(foodId = id, alias = alias)
}

fun FoodRecord.toConversionEntities(): List<ServingConversionEntity> = conversions.map { conversion ->
    ServingConversionEntity(
        foodId = id,
        unit = conversion.unit.name,
        baseUnitsPerServing = conversion.baseUnitsPerServing,
        provenance = conversion.provenance,
        approximate = conversion.approximate,
        label = conversion.label.orEmpty()
    )
}

fun FoodEntity.toDomain(
    aliases: List<String>,
    conversions: List<ServingConversion>
): FoodRecord = FoodRecord(
    id = id,
    name = name,
    aliases = aliases,
    category = category,
    origin = FoodOrigin.valueOf(origin),
    nutrition = NutritionBasis(
        amount = nutritionAmount,
        unit = MeasureUnit.valueOf(nutritionUnit),
        values = NutritionValues(
            calories = calories,
            protein = protein,
            carbohydrates = carbohydrates,
            fat = fat
        )
    ),
    conversions = conversions,
    defaultAmount = defaultAmount,
    defaultUnit = MeasureUnit.valueOf(defaultUnit),
    sourceId = sourceId,
    sourceName = sourceName,
    sourceRelease = sourceRelease,
    preparation = preparation,
        sourceDescription = sourceDescription,
        brand = brand,
        barcode = barcode,
        personalOverride = personalOverride
    )

fun Preference.toEntity() = PreferenceEntity(foodId, explicitCount, lastReinforcedEpochMs)

fun PreferenceEntity.toDomain() = Preference(foodId, explicitCount, lastReinforcedEpochMs)

fun MealEntry.toEntity() = MealEntryEntity(
    id = id,
    foodId = foodId,
    foodName = foodName,
    enteredAmount = enteredAmount,
    enteredUnit = enteredUnit.name,
    resolvedBaseAmount = resolvedBaseAmount,
    resolvedBaseUnit = resolvedBaseUnit.name,
    snapshotCalories = nutritionSnapshot.calories,
    snapshotProtein = nutritionSnapshot.protein,
    snapshotCarbohydrates = nutritionSnapshot.carbohydrates,
    snapshotFat = nutritionSnapshot.fat,
    conversionProvenance = conversionProvenance,
    approximateConversion = approximateConversion,
    savedAtEpochMs = savedAtEpochMs,
    sourcePhrase = sourcePhrase,
    mealSlot = mealSlot.name,
    dayKey = dayKey.ifBlank { dayKeyForEpochMs(savedAtEpochMs) }
)

fun MealEntryEntity.toDomain() = MealEntry(
    id = id,
    foodId = foodId,
    foodName = foodName,
    enteredAmount = enteredAmount,
    enteredUnit = MeasureUnit.valueOf(enteredUnit),
    resolvedBaseAmount = resolvedBaseAmount,
    resolvedBaseUnit = runCatching { MeasureUnit.valueOf(resolvedBaseUnit) }.getOrDefault(MeasureUnit.UNKNOWN),
    nutritionSnapshot = NutritionValues(
        calories = snapshotCalories,
        protein = snapshotProtein,
        carbohydrates = snapshotCarbohydrates,
        fat = snapshotFat
    ),
    conversionProvenance = conversionProvenance,
    approximateConversion = approximateConversion,
    savedAtEpochMs = savedAtEpochMs,
    sourcePhrase = sourcePhrase,
    mealSlot = runCatching { MealSlot.valueOf(mealSlot) }.getOrDefault(MealSlot.BREAKFAST),
    dayKey = dayKey.ifBlank { dayKeyForEpochMs(savedAtEpochMs) }
)

