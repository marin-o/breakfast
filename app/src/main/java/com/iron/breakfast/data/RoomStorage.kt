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
    val archived: Boolean = false,
    @ColumnInfo(name = "personal_override") val personalOverride: Boolean = false
)

@Entity(tableName = "food_aliases", primaryKeys = ["foodId", "alias"])
data class FoodAliasEntity(
    val foodId: String,
    val alias: String
)

@Entity(tableName = "serving_conversions", primaryKeys = ["foodId", "unit"])
data class ServingConversionEntity(
    val foodId: String,
    val unit: String,
    @ColumnInfo(name = "base_units_per_serving") val baseUnitsPerServing: Double,
    val provenance: String,
    val approximate: Boolean
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

    @Query("SELECT * FROM foods")
    suspend fun all(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: FoodEntity)
}

@Dao
interface FoodAliasDao {
    @Query("SELECT * FROM food_aliases")
    suspend fun all(): List<FoodAliasEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(aliases: List<FoodAliasEntity>)

    @Query("DELETE FROM food_aliases WHERE foodId = :foodId")
    suspend fun deleteForFood(foodId: String)
}

@Dao
interface ServingConversionDao {
    @Query("SELECT * FROM serving_conversions")
    suspend fun all(): List<ServingConversionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(conversions: List<ServingConversionEntity>)

    @Query("DELETE FROM serving_conversions WHERE foodId = :foodId")
    suspend fun deleteForFood(foodId: String)
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences")
    suspend fun all(): List<PreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: PreferenceEntity)
}

@Dao
interface MealEntryDao {
    @Query("SELECT * FROM meal_entries ORDER BY saved_at_epoch_ms ASC")
    suspend fun all(): List<MealEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MealEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(entry: MealEntryEntity)
}

@Database(
    entities = [
        FoodEntity::class,
        FoodAliasEntity::class,
        ServingConversionEntity::class,
        PreferenceEntity::class,
        MealEntryEntity::class
    ],
    version = 4,
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
    archived = archived,
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
        approximate = conversion.approximate
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
        archived = archived,
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

