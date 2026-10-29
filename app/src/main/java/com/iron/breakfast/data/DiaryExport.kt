package com.iron.breakfast.data

import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.isRecipeId
import com.iron.breakfast.ui.components.LongDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/*
 * The diary file written by Settings > Export diary and read back by Import diary: one JSON document holding every row
 * of every table in [BreakfastDatabase], plus the user's settings (written for reference; an import leaves the phone's
 * settings as they are). It is the diary's backup (Android cloud backup
 * leaves the database out). The bundled catalogue rows are included as stored, so built-in foods the user edited
 * or added aliases to come back exactly; after an import the store seeds and syncs the catalogue as at every start,
 * so a file from an older build still gets the current catalogue. Foods are no longer archived: the field is still
 * written (false) and read, and a food an older file holds as archived is deleted by the import, its entries kept.
 *
 * Mapping and validation are pure: nothing here touches Room or Android.
 */

/** Marks the file as a Breakfast diary, so another JSON file is refused before its rows are read. */
internal const val DIARY_FORMAT = "breakfast-diary"

/**
 * The layout of the file below. Bump it (and keep reading the old one) when a field changes. 2 added the recipes; a
 * version 1 file has none, and reads as a diary without recipes.
 */
internal const val DIARY_FORMAT_VERSION = 2

/** The Room schema the rows were read from; a file from a newer schema is refused. Keep equal to [BreakfastDatabase]. */
internal const val DIARY_SCHEMA_VERSION = 9

/** A file bigger than this is refused before it is parsed. A year of logging is well under 5 MB. */
internal const val DIARY_MAX_BYTES = 32L * 1024 * 1024

/** Every user row, as Room stores it. What the store exports and what an import replaces the database with. */
internal data class DiaryRows(
    val foods: List<FoodEntity>,
    val aliases: List<FoodAliasEntity>,
    val conversions: List<ServingConversionEntity>,
    val preferences: List<PreferenceEntity>,
    val entries: List<MealEntryEntity>,
    val recipes: List<RecipeEntity> = emptyList(),
    val recipeAliases: List<RecipeAliasEntity> = emptyList(),
    val recipeIngredients: List<RecipeIngredientEntity> = emptyList(),
)

/** The settings stored in the file; [longDraft] is a [LongDraft] name. */
@Serializable
internal data class DiarySettingsDto(val dayStartHour: Int, val longDraft: String)

/** The settings an export writes. */
internal data class DiarySettings(val dayStartHour: Int, val longDraft: LongDraft)

@Serializable
internal data class DiaryFile(
    val format: String,
    val formatVersion: Int,
    val schemaVersion: Int,
    val exportedAtEpochMs: Long,
    val settings: DiarySettingsDto,
    val foods: List<FoodDto>,
    val foodAliases: List<FoodAliasDto>,
    val servingConversions: List<ServingConversionDto>,
    val preferences: List<PreferenceDto>,
    val mealEntries: List<MealEntryDto>,
    // Since format 2; a version 1 file has none of these and reads with them empty.
    val recipes: List<RecipeDto> = emptyList(),
    val recipeAliases: List<RecipeAliasDto> = emptyList(),
    val recipeIngredients: List<RecipeIngredientDto> = emptyList(),
)

@Serializable
internal data class FoodDto(
    val id: String,
    val name: String,
    val category: String,
    val origin: String,
    val nutritionAmount: Double,
    val nutritionUnit: String,
    val calories: Double,
    val protein: Double?,
    val carbohydrates: Double?,
    val fat: Double?,
    val defaultAmount: Double,
    val defaultUnit: String,
    val sourceId: String?,
    val sourceName: String?,
    val sourceRelease: String?,
    val preparation: String?,
    val sourceDescription: String?,
    val brand: String?,
    val barcode: String?,
    val archived: Boolean,
    val personalOverride: Boolean,
    // Since schema 9; an older file has none and reads as no density.
    val density: Double? = null,
)

@Serializable
internal data class FoodAliasDto(val foodId: String, val alias: String)

@Serializable
internal data class ServingConversionDto(
    val foodId: String,
    val unit: String,
    val baseUnitsPerServing: Double,
    val provenance: String,
    val approximate: Boolean,
    val label: String,
)

@Serializable
internal data class PreferenceDto(val foodId: String, val explicitCount: Int, val lastReinforcedEpochMs: Long)

@Serializable
internal data class MealEntryDto(
    val id: String,
    val foodId: String,
    val foodName: String,
    val enteredAmount: Double,
    val enteredUnit: String,
    val resolvedBaseAmount: Double,
    val resolvedBaseUnit: String,
    val snapshotCalories: Double,
    val snapshotProtein: Double?,
    val snapshotCarbohydrates: Double?,
    val snapshotFat: Double?,
    val conversionProvenance: String,
    val approximateConversion: Boolean,
    val savedAtEpochMs: Long,
    val sourcePhrase: String,
    val mealSlot: String,
    val dayKey: String,
    // Since schema 9; an older file has none and reads as a plain unit.
    val enteredUnitLabel: String? = null,
)

@Serializable
internal data class RecipeDto(
    val id: String,
    val name: String,
    val portions: Double?,
    val totalWeight: Double?,
    // Since schema 8; a file without it (format 1, or format 2 from schema 7) reads it as none.
    val totalVolume: Double? = null,
)

@Serializable
internal data class RecipeAliasDto(val recipeId: String, val alias: String)

@Serializable
internal data class RecipeIngredientDto(
    val recipeId: String,
    val position: Int,
    val foodId: String,
    val foodName: String,
    val enteredAmount: Double,
    val enteredUnit: String,
    val resolvedBaseAmount: Double,
    val resolvedBaseUnit: String,
    val snapshotCalories: Double,
    val snapshotProtein: Double?,
    val snapshotCarbohydrates: Double?,
    val snapshotFat: Double?,
)

/** Strict: an unknown or missing field, or a null where a value is required, fails the parse. */
private val diaryJson = Json {
    ignoreUnknownKeys = false
    explicitNulls = true
    encodeDefaults = true
    allowSpecialFloatingPointValues = false
}

/** What reading a diary file gave: rows that passed every check, or the first reason it was refused. Settings in the
 * file are checked but never returned: an import does not change them. */
internal sealed interface DiaryImport {
    data class Valid(val rows: DiaryRows) : DiaryImport
    data class Invalid(val reason: String) : DiaryImport
}

/**
 * The file for [rows] and [settings]. Child rows (aliases, conversions, preferences, a recipe's aliases and ingredients)
 * whose food or recipe is not stored are left out: the app never shows them, and the file would otherwise be refused
 * on import. A preference names a food or a recipe.
 */
internal fun diaryFileOf(rows: DiaryRows, settings: DiarySettings, exportedAtEpochMs: Long): DiaryFile {
    val foodIds = rows.foods.mapTo(HashSet()) { it.id }
    val recipeIds = rows.recipes.mapTo(HashSet()) { it.id }
    return DiaryFile(
        format = DIARY_FORMAT,
        formatVersion = DIARY_FORMAT_VERSION,
        schemaVersion = DIARY_SCHEMA_VERSION,
        exportedAtEpochMs = exportedAtEpochMs,
        settings = DiarySettingsDto(settings.dayStartHour, settings.longDraft.name),
        foods = rows.foods.sortedBy { it.id }.map { it.toDto() },
        foodAliases = rows.aliases.filter { it.foodId in foodIds }.sortedWith(compareBy({ it.foodId }, { it.alias })).map { FoodAliasDto(it.foodId, it.alias) },
        servingConversions = rows.conversions.filter { it.foodId in foodIds }.sortedWith(compareBy({ it.foodId }, { it.unit }, { it.label })).map { it.toDto() },
        preferences = rows.preferences.filter { it.foodId in foodIds || it.foodId in recipeIds }.sortedBy { it.foodId }.map { PreferenceDto(it.foodId, it.explicitCount, it.lastReinforcedEpochMs) },
        mealEntries = rows.entries.sortedWith(compareBy({ it.savedAtEpochMs }, { it.id })).map { it.toDto() },
        recipes = rows.recipes.sortedBy { it.id }.map { RecipeDto(it.id, it.name, it.portions, it.totalWeight, it.totalVolume) },
        recipeAliases = rows.recipeAliases.filter { it.recipeId in recipeIds }.sortedWith(compareBy({ it.recipeId }, { it.alias }))
            .map { RecipeAliasDto(it.recipeId, it.alias) },
        recipeIngredients = rows.recipeIngredients.filter { it.recipeId in recipeIds }.sortedWith(compareBy({ it.recipeId }, { it.position }))
            .map { it.toDto() },
    )
}

/** The file as JSON text. */
internal fun encodeDiary(file: DiaryFile): String = diaryJson.encodeToString(DiaryFile.serializer(), file)

/** [text] parsed and checked in full. Nothing is returned as [DiaryImport.Valid] unless every row passes. */
internal fun decodeDiary(text: String): DiaryImport {
    val file = try {
        diaryJson.decodeFromString(DiaryFile.serializer(), text)
    } catch (e: IllegalArgumentException) { // SerializationException is one; so is a bad number.
        return DiaryImport.Invalid("not a diary file: ${e.message?.take(200)}")
    }
    return validateDiary(file)
}

/**
 * Checks every field of [file] against the rules the app and its schema rely on, then maps it to rows. The first
 * failure is returned; the database is never touched here.
 */
internal fun validateDiary(file: DiaryFile): DiaryImport {
    val problem = diaryProblem(file)
    if (problem != null) return DiaryImport.Invalid(problem)
    return DiaryImport.Valid(
        rows = DiaryRows(
            foods = file.foods.map { it.toEntity() },
            aliases = file.foodAliases.map { FoodAliasEntity(it.foodId, it.alias) },
            conversions = file.servingConversions.map { it.toEntity() },
            preferences = file.preferences.map { PreferenceEntity(it.foodId, it.explicitCount, it.lastReinforcedEpochMs) },
            entries = file.mealEntries.map { it.toEntity() },
            recipes = file.recipes.map { RecipeEntity(it.id, it.name, it.portions, it.totalWeight, it.totalVolume) },
            recipeAliases = file.recipeAliases.map { RecipeAliasEntity(it.recipeId, it.alias) },
            recipeIngredients = file.recipeIngredients.map { it.toEntity() },
        ),
    )
}

private val UNIT_NAMES = MeasureUnit.entries.mapTo(HashSet()) { it.name }
private val ORIGIN_NAMES = FoodOrigin.entries.mapTo(HashSet()) { it.name }
private val SLOT_NAMES = MealSlot.entries.mapTo(HashSet()) { it.name }
private val LONG_DRAFT_NAMES = LongDraft.entries.mapTo(HashSet()) { it.name }

private fun Double.nonNegative() = isFinite() && this >= 0.0
private fun Double.positive() = isFinite() && this > 0.0
private fun Double?.nonNegativeOrUnknown() = this == null || nonNegative()
private fun isDayKey(value: String) = value.length == 10 && runCatching { LocalDate.parse(value) }.isSuccess

/** The first rule [file] breaks, or null when it passes them all. */
internal fun diaryProblem(file: DiaryFile): String? {
    if (file.format != DIARY_FORMAT) return "format is ${file.format}"
    if (file.formatVersion !in 1..DIARY_FORMAT_VERSION) return "unknown format version ${file.formatVersion}"
    if (file.schemaVersion !in 1..DIARY_SCHEMA_VERSION) return "schema version ${file.schemaVersion}"
    if (file.settings.dayStartHour !in 0..23) return "day start hour ${file.settings.dayStartHour}"
    if (file.settings.longDraft !in LONG_DRAFT_NAMES) return "long draft ${file.settings.longDraft}"

    val foodIds = HashSet<String>()
    file.foods.forEachIndexed { i, f ->
        val at = "food $i"
        when {
            f.id.isBlank() -> return "$at: blank id"
            !foodIds.add(f.id) -> return "$at: duplicate id ${f.id}"
            // A food never takes a recipe's id: it would be logged as a recipe, never stored.
            isRecipeId(f.id) -> return "$at: recipe id ${f.id}"
            f.name.isBlank() -> return "$at: blank name"
            f.origin !in ORIGIN_NAMES -> return "$at: origin ${f.origin}"
            f.nutritionUnit !in UNIT_NAMES -> return "$at: nutrition unit ${f.nutritionUnit}"
            f.defaultUnit !in UNIT_NAMES -> return "$at: default unit ${f.defaultUnit}"
            !f.nutritionAmount.positive() -> return "$at: nutrition amount ${f.nutritionAmount}"
            !f.calories.nonNegative() -> return "$at: calories ${f.calories}"
            !f.protein.nonNegativeOrUnknown() || !f.carbohydrates.nonNegativeOrUnknown() || !f.fat.nonNegativeOrUnknown() -> return "$at: macros"
            !f.defaultAmount.nonNegative() -> return "$at: default amount ${f.defaultAmount}"
            f.density != null && !f.density.positive() -> return "$at: density ${f.density}"
        }
    }

    val aliasKeys = HashSet<Pair<String, String>>()
    file.foodAliases.forEachIndexed { i, a ->
        val at = "alias $i"
        when {
            a.foodId !in foodIds -> return "$at: unknown food ${a.foodId}"
            a.alias.isBlank() -> return "$at: blank alias"
            !aliasKeys.add(a.foodId to a.alias) -> return "$at: duplicate"
        }
    }

    val conversionKeys = HashSet<Triple<String, String, String>>()
    file.servingConversions.forEachIndexed { i, c ->
        val at = "conversion $i"
        when {
            c.foodId !in foodIds -> return "$at: unknown food ${c.foodId}"
            c.unit !in UNIT_NAMES -> return "$at: unit ${c.unit}"
            !c.baseUnitsPerServing.positive() -> return "$at: base units ${c.baseUnitsPerServing}"
            !conversionKeys.add(Triple(c.foodId, c.unit, c.label)) -> return "$at: duplicate"
        }
    }

    val recipeIds = HashSet<String>()
    file.recipes.forEachIndexed { i, r ->
        val at = "recipe $i"
        when {
            r.id.isBlank() -> return "$at: blank id"
            // A recipe's id says it is one: a hand-made id could otherwise be stored as a food.
            !isRecipeId(r.id) -> return "$at: not a recipe id ${r.id}"
            !recipeIds.add(r.id) || r.id in foodIds -> return "$at: duplicate id ${r.id}"
            r.name.isBlank() -> return "$at: blank name"
            r.portions != null && !r.portions.positive() -> return "$at: portions ${r.portions}"
            r.totalWeight != null && !r.totalWeight.positive() -> return "$at: total weight ${r.totalWeight}"
            r.totalVolume != null && !r.totalVolume.positive() -> return "$at: total volume ${r.totalVolume}"
            r.portions == null && r.totalWeight == null && r.totalVolume == null -> return "$at: no portions, total weight or total volume"
        }
    }

    val recipeAliasKeys = HashSet<Pair<String, String>>()
    file.recipeAliases.forEachIndexed { i, a ->
        val at = "recipe alias $i"
        when {
            a.recipeId !in recipeIds -> return "$at: unknown recipe ${a.recipeId}"
            a.alias.isBlank() -> return "$at: blank alias"
            !recipeAliasKeys.add(a.recipeId to a.alias) -> return "$at: duplicate"
        }
    }

    // An ingredient keeps its own food name and snapshot; its food id need not name a stored food, as an entry's.
    val ingredientKeys = HashSet<Pair<String, Int>>()
    file.recipeIngredients.forEachIndexed { i, g ->
        val at = "ingredient $i"
        when {
            g.recipeId !in recipeIds -> return "$at: unknown recipe ${g.recipeId}"
            g.position < 0 || !ingredientKeys.add(g.recipeId to g.position) -> return "$at: position ${g.position}"
            g.foodId.isBlank() -> return "$at: blank food id"
            g.enteredUnit !in UNIT_NAMES -> return "$at: unit ${g.enteredUnit}"
            g.resolvedBaseUnit !in UNIT_NAMES -> return "$at: base unit ${g.resolvedBaseUnit}"
            !g.enteredAmount.nonNegative() -> return "$at: amount ${g.enteredAmount}"
            !g.resolvedBaseAmount.nonNegative() -> return "$at: base amount ${g.resolvedBaseAmount}"
            !g.snapshotCalories.nonNegative() -> return "$at: calories ${g.snapshotCalories}"
            !g.snapshotProtein.nonNegativeOrUnknown() || !g.snapshotCarbohydrates.nonNegativeOrUnknown() || !g.snapshotFat.nonNegativeOrUnknown() -> return "$at: macros"
        }
    }

    val preferenceIds = HashSet<String>()
    file.preferences.forEachIndexed { i, p ->
        val at = "preference $i"
        when {
            p.foodId !in foodIds && p.foodId !in recipeIds -> return "$at: unknown food ${p.foodId}"
            !preferenceIds.add(p.foodId) -> return "$at: duplicate"
            p.explicitCount < 0 -> return "$at: count ${p.explicitCount}"
            p.lastReinforcedEpochMs < 0 -> return "$at: time ${p.lastReinforcedEpochMs}"
        }
    }

    // An entry keeps its own food name and nutrition snapshot; its food id need not name a stored food.
    val entryIds = HashSet<String>()
    file.mealEntries.forEachIndexed { i, e ->
        val at = "entry $i"
        when {
            e.id.isBlank() -> return "$at: blank id"
            !entryIds.add(e.id) -> return "$at: duplicate id ${e.id}"
            e.foodId.isBlank() -> return "$at: blank food id"
            e.enteredUnit !in UNIT_NAMES -> return "$at: unit ${e.enteredUnit}"
            e.resolvedBaseUnit !in UNIT_NAMES -> return "$at: base unit ${e.resolvedBaseUnit}"
            e.mealSlot !in SLOT_NAMES -> return "$at: meal ${e.mealSlot}"
            !isDayKey(e.dayKey) -> return "$at: day ${e.dayKey}"
            !e.enteredAmount.nonNegative() -> return "$at: amount ${e.enteredAmount}"
            !e.resolvedBaseAmount.nonNegative() -> return "$at: base amount ${e.resolvedBaseAmount}"
            !e.snapshotCalories.nonNegative() -> return "$at: calories ${e.snapshotCalories}"
            !e.snapshotProtein.nonNegativeOrUnknown() || !e.snapshotCarbohydrates.nonNegativeOrUnknown() || !e.snapshotFat.nonNegativeOrUnknown() -> return "$at: macros"
            e.savedAtEpochMs < 0 -> return "$at: time ${e.savedAtEpochMs}"
            e.enteredUnitLabel != null && e.enteredUnitLabel.isBlank() -> return "$at: blank unit label"
        }
    }
    return null
}

/** The text of a picked file, or null when it is bigger than [maxBytes] (then it is not read further). */
internal fun readDiaryText(input: InputStream, maxBytes: Long = DIARY_MAX_BYTES): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        if (out.size() + n > maxBytes) return null
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
}

/**
 * Checks [file], writes it with [write], then reads the copy back with [readBack]. Null only when the copy is
 * byte-for-byte what was meant and imports; otherwise why not (an invalid [file] is never written). A failing [write]
 * or [readBack] throws.
 */
internal fun writeDiaryChecked(file: DiaryFile, write: (ByteArray) -> Unit, readBack: () -> String?): String? {
    diaryProblem(file)?.let { return "invalid diary: $it" }
    val text = encodeDiary(file)
    write(text.toByteArray(Charsets.UTF_8))
    val copy = readBack() ?: return "copy unreadable"
    if (copy != text) return "copy differs (${copy.length} of ${text.length} characters)"
    return (decodeDiary(copy) as? DiaryImport.Invalid)?.reason
}

/** The diary as it was just before the last Replace diary, in app storage that is never backed up. */
internal const val SAFETY_COPY_NAME = "diary-before-import.json"

/**
 * Writes [file] to [dir]/[SAFETY_COPY_NAME], replacing the previous copy only once the new one is complete and checked
 * (a temp file, then an atomic rename). Throws when that fails; the previous copy then stays.
 */
internal fun writeSafetyCopy(dir: File, file: DiaryFile): File {
    val target = File(dir, SAFETY_COPY_NAME)
    val temp = File(dir, "$SAFETY_COPY_NAME.tmp")
    try {
        val problem = writeDiaryChecked(
            file,
            write = { bytes -> FileOutputStream(temp).use { it.write(bytes); it.fd.sync() } },
            readBack = { temp.inputStream().use { readDiaryText(it) } },
        )
        check(problem == null) { "safety copy: $problem" }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        temp.delete()
    }
    return target
}

/** Removes the safety copy in [dir] and any unfinished temp file ("Safety copy" Don't keep). True when none is left. */
internal fun deleteSafetyCopy(dir: File): Boolean {
    File(dir, "$SAFETY_COPY_NAME.tmp").delete()
    val target = File(dir, SAFETY_COPY_NAME)
    return !target.exists() || target.delete()
}

/** "breakfast-2026-10-26.json": the name the save picker suggests. */
internal fun diaryFileName(date: LocalDate): String = "breakfast-$date.json"

private fun FoodEntity.toDto() = FoodDto(
    id, name, category, origin, nutritionAmount, nutritionUnit, calories, protein, carbohydrates, fat, defaultAmount,
    defaultUnit, sourceId, sourceName, sourceRelease, preparation, sourceDescription, brand, barcode, archived, personalOverride,
    density,
)

private fun FoodDto.toEntity() = FoodEntity(
    id, name, category, origin, nutritionAmount, nutritionUnit, calories, protein, carbohydrates, fat, defaultAmount,
    defaultUnit, sourceId, sourceName, sourceRelease, preparation, sourceDescription, brand, barcode, archived, personalOverride,
    density,
)

private fun ServingConversionEntity.toDto() = ServingConversionDto(foodId, unit, baseUnitsPerServing, provenance, approximate, label)

private fun ServingConversionDto.toEntity() = ServingConversionEntity(foodId, unit, baseUnitsPerServing, provenance, approximate, label)

private fun MealEntryEntity.toDto() = MealEntryDto(
    id, foodId, foodName, enteredAmount, enteredUnit, resolvedBaseAmount, resolvedBaseUnit, snapshotCalories, snapshotProtein,
    snapshotCarbohydrates, snapshotFat, conversionProvenance, approximateConversion, savedAtEpochMs, sourcePhrase, mealSlot, dayKey,
    enteredUnitLabel,
)

private fun MealEntryDto.toEntity() = MealEntryEntity(
    id, foodId, foodName, enteredAmount, enteredUnit, resolvedBaseAmount, resolvedBaseUnit, snapshotCalories, snapshotProtein,
    snapshotCarbohydrates, snapshotFat, conversionProvenance, approximateConversion, savedAtEpochMs, sourcePhrase, mealSlot, dayKey,
    enteredUnitLabel,
)

private fun RecipeIngredientEntity.toDto() = RecipeIngredientDto(
    recipeId, position, foodId, foodName, enteredAmount, enteredUnit, resolvedBaseAmount, resolvedBaseUnit, snapshotCalories,
    snapshotProtein, snapshotCarbohydrates, snapshotFat,
)

private fun RecipeIngredientDto.toEntity() = RecipeIngredientEntity(
    recipeId, position, foodId, foodName, enteredAmount, enteredUnit, resolvedBaseAmount, resolvedBaseUnit, snapshotCalories,
    snapshotProtein, snapshotCarbohydrates, snapshotFat,
)
