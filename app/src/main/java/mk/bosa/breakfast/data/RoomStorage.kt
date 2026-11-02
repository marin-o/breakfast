package mk.bosa.breakfast.data

import mk.bosa.breakfast.domain.TargetRow
import mk.bosa.breakfast.domain.WeightPoint
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
import mk.bosa.breakfast.domain.FoodOrigin
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionBasis
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.Preference
import mk.bosa.breakfast.domain.Recipe
import mk.bosa.breakfast.domain.RecipeIngredient
import mk.bosa.breakfast.domain.ServingConversion
import mk.bosa.breakfast.domain.dayKeyForEpochMs

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
    @ColumnInfo(name = "personal_override") val personalOverride: Boolean = false,
    /** Grams in 100 ml; null when unknown (every food before schema 9). */
    @ColumnInfo(name = "density_g_per_100ml") val density: Double? = null,
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
    @ColumnInfo(name = "day_key") val dayKey: String,
    /** The serving name or converted unit the entry was logged in (since schema 9); null for a plain unit. */
    @ColumnInfo(name = "entered_unit_label") val enteredUnitLabel: String? = null,
)

/** A recipe: its yield in portions, grams or millilitres of the finished dish (at least one; see `domain/Recipes.kt`). */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val portions: Double?,
    @ColumnInfo(name = "total_weight") val totalWeight: Double?,
    /** Since schema 8; null for a recipe without a volume, and for every recipe saved before. */
    @ColumnInfo(name = "total_volume_ml") val totalVolume: Double? = null,
)

@Entity(tableName = "recipe_aliases", primaryKeys = ["recipe_id", "alias"])
data class RecipeAliasEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val alias: String,
)

/**
 * One ingredient of a recipe, in typed order ([position]): its food, the amount as entered and as resolved, and the
 * nutrition snapshot it keeps once the food is deleted (while the food exists, the recipe follows it).
 */
@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipe_id", "position"],
    indices = [androidx.room.Index(value = ["food_id"])],
)
data class RecipeIngredientEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val position: Int,
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "food_name") val foodName: String,
    @ColumnInfo(name = "entered_amount") val enteredAmount: Double,
    @ColumnInfo(name = "entered_unit") val enteredUnit: String,
    @ColumnInfo(name = "resolved_base_amount") val resolvedBaseAmount: Double,
    @ColumnInfo(name = "resolved_base_unit") val resolvedBaseUnit: String,
    @ColumnInfo(name = "snapshot_calories") val snapshotCalories: Double,
    @ColumnInfo(name = "snapshot_protein") val snapshotProtein: Double?,
    @ColumnInfo(name = "snapshot_carbohydrates") val snapshotCarbohydrates: Double?,
    @ColumnInfo(name = "snapshot_fat") val snapshotFat: Double?,
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

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY rowid")
    suspend fun all(): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(recipes: List<RecipeEntity>)

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()
}

@Dao
interface RecipeAliasDao {
    @Query("SELECT * FROM recipe_aliases ORDER BY rowid")
    suspend fun all(): List<RecipeAliasEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(aliases: List<RecipeAliasEntity>)

    @Query("DELETE FROM recipe_aliases WHERE recipe_id = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(aliases: List<RecipeAliasEntity>)

    @Query("DELETE FROM recipe_aliases")
    suspend fun deleteAll()
}

@Dao
interface RecipeIngredientDao {
    @Query("SELECT * FROM recipe_ingredients ORDER BY recipe_id, position")
    suspend fun all(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE food_id = :foodId")
    suspend fun byFood(foodId: String): List<RecipeIngredientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ingredients: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipe_id = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(ingredients: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredients")
    suspend fun deleteAll()
}

/** One point of the profile's weight curve; a day can hold several, and times can repeat, so the row has its own [id]. */
@Entity(tableName = "weight_points")
data class WeightPointEntity(
    @ColumnInfo(name = "day_key") val dayKey: String,
    val kg: Double,
    @ColumnInfo(name = "saved_at_epoch_ms") val savedAtEpochMs: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

fun WeightPointEntity.toDomain() = WeightPoint(dayKey, kg, savedAtEpochMs, id)
fun WeightPoint.toEntity() = WeightPointEntity(dayKey, kg, savedAtEpochMs, id)

@Dao
interface WeightPointDao {
    @Query("SELECT * FROM weight_points ORDER BY day_key, saved_at_epoch_ms, id")
    suspend fun all(): List<WeightPointEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(point: WeightPointEntity)

    @Query("DELETE FROM weight_points WHERE id = (SELECT id FROM weight_points WHERE day_key = :dayKey ORDER BY saved_at_epoch_ms DESC, id DESC LIMIT 1)")
    suspend fun deleteLatestOfDay(dayKey: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(points: List<WeightPointEntity>)

    @Query("DELETE FROM weight_points WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM weight_points")
    suspend fun deleteAll()
}

/** The targets in effect from [dayKey]; one row per day a target took effect. */
@Entity(tableName = "target_history")
data class TargetHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "day_key") val dayKey: String,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
)

fun TargetHistoryEntity.toDomain() = TargetRow(dayKey, kcal, protein, carbs, fat)
fun TargetRow.toEntity() = TargetHistoryEntity(dayKey, kcal, protein, carbs, fat)

@Dao
interface TargetHistoryDao {
    @Query("SELECT * FROM target_history ORDER BY day_key")
    suspend fun all(): List<TargetHistoryEntity>

    /** A second save on one day replaces that day's row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: TargetHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(rows: List<TargetHistoryEntity>)

    @Query("DELETE FROM target_history")
    suspend fun deleteAll()

    @Query("DELETE FROM target_history WHERE day_key > :dayKey")
    suspend fun deleteAfter(dayKey: String)
}

@Database(
    entities = [
        FoodEntity::class,
        FoodAliasEntity::class,
        ServingConversionEntity::class,
        PreferenceEntity::class,
        MealEntryEntity::class,
        RecipeEntity::class,
        RecipeAliasEntity::class,
        RecipeIngredientEntity::class,
        WeightPointEntity::class,
        TargetHistoryEntity::class,
    ],
    version = 12,
    exportSchema = true
)
abstract class BreakfastDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun foodAliasDao(): FoodAliasDao
    abstract fun servingConversionDao(): ServingConversionDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeAliasDao(): RecipeAliasDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao
    abstract fun weightPointDao(): WeightPointDao
    abstract fun targetHistoryDao(): TargetHistoryDao
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

/** Recipes: three new tables; nothing that exists is touched. */
val ROOM_MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `portions` REAL, `total_weight` REAL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `recipe_aliases` (`recipe_id` TEXT NOT NULL, `alias` TEXT NOT NULL, PRIMARY KEY(`recipe_id`, `alias`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe_ingredients` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`food_id` TEXT NOT NULL, `food_name` TEXT NOT NULL, `entered_amount` REAL NOT NULL, `entered_unit` TEXT NOT NULL, " +
                "`resolved_base_amount` REAL NOT NULL, `resolved_base_unit` TEXT NOT NULL, `snapshot_calories` REAL NOT NULL, " +
                "`snapshot_protein` REAL, `snapshot_carbohydrates` REAL, `snapshot_fat` REAL, PRIMARY KEY(`recipe_id`, `position`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_food_id` ON `recipe_ingredients` (`food_id`)")
    }
}

/** A recipe's total volume in ml: one nullable column; every row there is gets null, nothing else is touched. */
val ROOM_MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recipes` ADD COLUMN `total_volume_ml` REAL")
    }
}

/**
 * Measurements: a food's density and an entry's unit label, both nullable, and every serving named by what it is
 * matched by ("piece", "tbsp", "tsp" for the rows that had no name). Numbers are not touched; a row whose new name
 * would clash with one already there keeps its blank name, which reads as the same name.
 */
val ROOM_MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `foods` ADD COLUMN `density_g_per_100ml` REAL")
        db.execSQL("ALTER TABLE `meal_entries` ADD COLUMN `entered_unit_label` TEXT")
        listOf("PIECE" to "piece", "TABLESPOON" to "tbsp", "TEASPOON" to "tsp").forEach { (unit, name) ->
            db.execSQL("UPDATE OR IGNORE `serving_conversions` SET `label` = ? WHERE `unit` = ? AND `label` = ''", arrayOf<Any>(name, unit))
        }
    }
}

/** Additive: one new table for the profile's weight curve; nothing that exists is touched. */
val ROOM_MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `weight_points` (`day_key` TEXT NOT NULL, `kg` REAL NOT NULL, `saved_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`day_key`))")
    }
}

/** Rebuilds only `weight_points` so a day can hold several: every row is copied and takes its own id; no other table is touched. */
val ROOM_MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `weight_points_new` (`day_key` TEXT NOT NULL, `kg` REAL NOT NULL, `saved_at_epoch_ms` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        db.execSQL("INSERT INTO `weight_points_new` (`day_key`, `kg`, `saved_at_epoch_ms`) SELECT `day_key`, `kg`, `saved_at_epoch_ms` FROM `weight_points` ORDER BY `day_key`")
        db.execSQL("DROP TABLE `weight_points`")
        db.execSQL("ALTER TABLE `weight_points_new` RENAME TO `weight_points`")
    }
}

/** Additive: one new table for the targets by day; nothing that exists is touched. */
val ROOM_MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `target_history` (`day_key` TEXT NOT NULL, `kcal` REAL, `protein` REAL, `carbs` REAL, `fat` REAL, PRIMARY KEY(`day_key`))")
    }
}

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
    personalOverride = personalOverride,
    density = density,
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
        label = conversion.name.orEmpty()
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
        personalOverride = personalOverride,
        density = density,
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
    dayKey = dayKey.ifBlank { dayKeyForEpochMs(savedAtEpochMs) },
    enteredUnitLabel = enteredUnitLabel,
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
    dayKey = dayKey.ifBlank { dayKeyForEpochMs(savedAtEpochMs) },
    enteredUnitLabel = enteredUnitLabel,
)



fun Recipe.toEntity() = RecipeEntity(id = id, name = name, portions = portions, totalWeight = totalWeight, totalVolume = totalVolume)

fun Recipe.toAliasEntities(): List<RecipeAliasEntity> = aliases.distinct().map { RecipeAliasEntity(recipeId = id, alias = it) }

fun Recipe.toIngredientEntities(): List<RecipeIngredientEntity> = ingredients.mapIndexed { position, it -> it.toEntity(id, position) }

fun RecipeIngredient.toEntity(recipeId: String, position: Int) = RecipeIngredientEntity(
    recipeId = recipeId,
    position = position,
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
)

fun RecipeIngredientEntity.toDomain() = RecipeIngredient(
    foodId = foodId,
    foodName = foodName,
    enteredAmount = enteredAmount,
    enteredUnit = runCatching { MeasureUnit.valueOf(enteredUnit) }.getOrDefault(MeasureUnit.UNKNOWN),
    resolvedBaseAmount = resolvedBaseAmount,
    resolvedBaseUnit = runCatching { MeasureUnit.valueOf(resolvedBaseUnit) }.getOrDefault(MeasureUnit.UNKNOWN),
    nutritionSnapshot = NutritionValues(snapshotCalories, snapshotProtein, snapshotCarbohydrates, snapshotFat),
)

/** The recipes with their aliases and ingredients (in typed order), in the order the recipes are stored. */
internal fun recipesOf(
    recipes: List<RecipeEntity>,
    aliases: List<RecipeAliasEntity>,
    ingredients: List<RecipeIngredientEntity>,
): List<Recipe> {
    val aliasesBy = aliases.groupBy { it.recipeId }
    val ingredientsBy = ingredients.groupBy { it.recipeId }
    return recipes.map { entity ->
        Recipe(
            id = entity.id,
            name = entity.name,
            aliases = aliasesBy[entity.id].orEmpty().map { it.alias },
            portions = entity.portions,
            totalWeight = entity.totalWeight,
            totalVolume = entity.totalVolume,
            ingredients = ingredientsBy[entity.id].orEmpty().sortedBy { it.position }.map { it.toDomain() },
        )
    }
}
