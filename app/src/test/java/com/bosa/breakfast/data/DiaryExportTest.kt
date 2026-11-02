package com.bosa.breakfast.data

import com.bosa.breakfast.domain.Activity
import com.bosa.breakfast.domain.Goal
import com.bosa.breakfast.domain.Macro
import com.bosa.breakfast.domain.Sex

import kotlin.test.assertFalse
import com.bosa.breakfast.domain.ProfileValues
import com.bosa.breakfast.domain.TestFoods
import com.bosa.breakfast.ui.components.LongDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryExportTest {
    private val settings = DiarySettings(dayStartHour = 4, longDraft = LongDraft.TWO_LINES)

    private fun food(id: String, archived: Boolean = false, override: Boolean = false) = FoodEntity(
        id = id, name = "Food $id", category = "Custom", origin = "CUSTOM", nutritionAmount = 100.0, nutritionUnit = "GRAM",
        calories = 250.0, protein = 10.5, carbohydrates = null, fat = 0.0, defaultAmount = 1.0, defaultUnit = "PIECE",
        sourceId = null, sourceName = "Open Food Facts", sourceRelease = null, preparation = null, sourceDescription = null,
        brand = "Brand", barcode = "5310382009733", archived = archived, personalOverride = override,
    )

    private fun entry(id: String, foodId: String, savedAt: Long) = MealEntryEntity(
        id = id, foodId = foodId, foodName = "Snapshot name $id", enteredAmount = 2.0, enteredUnit = "PIECE",
        resolvedBaseAmount = 120.0, resolvedBaseUnit = "GRAM", snapshotCalories = 300.0, snapshotProtein = 12.6,
        snapshotCarbohydrates = null, snapshotFat = 0.0, conversionProvenance = "1 piece = 60 g", approximateConversion = true,
        savedAtEpochMs = savedAt, sourcePhrase = "2 бурек", mealSlot = "DINNER", dayKey = "2026-10-25",
    )

    private fun rows() = DiaryRows(
        foods = listOf(food("custom-1"), food("archived-1", archived = true), food("edited-builtin", override = true).copy(density = 103.0)),
        aliases = listOf(FoodAliasEntity("custom-1", "бурек"), FoodAliasEntity("custom-1", "burek")),
        conversions = listOf(
            ServingConversionEntity("custom-1", "PIECE", 60.0, "user", approximate = true, label = ""),
            ServingConversionEntity("custom-1", "PIECE", 250.0, "user", approximate = false, label = "jar"),
        ),
        preferences = listOf(PreferenceEntity("custom-1", 3, 1_700_000_000_000L), PreferenceEntity("recipe-chili", 2, 1_700_000_000_000L)),
        // The second entry's food is gone: its snapshot keeps it readable, so it must survive the round trip.
        entries = listOf(
            entry("e-1", "custom-1", 2_000L), entry("e-2", "deleted-food", 1_000L), entry("e-3", "archived-1", 3_000L),
            entry("e-4", "custom-1", 4_000L).copy(enteredUnit = "PIECE", enteredUnitLabel = "slice"),
        ),
        recipes = listOf(RecipeEntity("recipe-chili", "Чили", 4.0, 1200.0), RecipeEntity("recipe-soup", "Soup", null, 900.0), RecipeEntity("recipe-broth", "Broth", null, null, 1500.0)),
        recipeAliases = listOf(RecipeAliasEntity("recipe-chili", "chili")),
        // The second ingredient's food is gone: it keeps its own numbers, as an entry does.
        recipeIngredients = listOf(ingredient("recipe-chili", 0, "custom-1"), ingredient("recipe-chili", 1, "deleted-food"), ingredient("recipe-soup", 0, "custom-1")),
    )

    private fun ingredient(recipeId: String, position: Int, foodId: String) = RecipeIngredientEntity(
        recipeId = recipeId, position = position, foodId = foodId, foodName = "Ingredient $foodId", enteredAmount = 500.0,
        enteredUnit = "GRAM", resolvedBaseAmount = 500.0, resolvedBaseUnit = "GRAM", snapshotCalories = 1250.0,
        snapshotProtein = 52.5, snapshotCarbohydrates = null, snapshotFat = 0.0,
    )

    private fun sorted(rows: DiaryRows) = DiaryRows(
        rows.foods.sortedBy { it.id },
        rows.aliases.sortedWith(compareBy({ it.foodId }, { it.alias })),
        rows.conversions.sortedWith(compareBy({ it.foodId }, { it.unit }, { it.label })),
        rows.preferences.sortedBy { it.foodId },
        rows.entries.sortedBy { it.id },
        rows.recipes.sortedBy { it.id },
        rows.recipeAliases.sortedWith(compareBy({ it.recipeId }, { it.alias })),
        rows.recipeIngredients.sortedWith(compareBy({ it.recipeId }, { it.position })),
    )

    private fun valid(text: String) = assertIs<DiaryImport.Valid>(decodeDiary(text))

    private fun refused(file: DiaryFile) = assertIs<DiaryImport.Invalid>(decodeDiary(encodeDiary(file)))

    private fun file() = diaryFileOf(rows(), settings, exportedAtEpochMs = 5_000L)

    @Test fun roundTripGivesBackEveryRow() {
        val text = encodeDiary(file())
        val imported = valid(text)
        assertEquals(sorted(rows()), sorted(imported.rows))
        assertTrue(imported.rows.foods.single { it.id == "archived-1" }.archived)
        assertTrue(imported.rows.foods.single { it.id == "edited-builtin" }.personalOverride)
        assertEquals(entry("e-2", "deleted-food", 1_000L), imported.rows.entries.single { it.id == "e-2" })
    }

    @Test fun theProfileAndTheCurveComeBack() {
        val profile = ProfileValues("Ana", 120.0, null, 60.5, 82.5, 75.0, kcalTarget = 2100.0)
        val points = listOf(WeightPointEntity("2026-10-01", 80.0, 1L), WeightPointEntity("2026-10-02", 79.5, 2L))
        val imported = valid(encodeDiary(diaryFileOf(rows().copy(profile = profile, weightPoints = points), settings, 5_000L)))
        assertEquals(profile, imported.rows.profile)
        assertEquals(points, imported.rows.weightPoints)
    }

    /** Format 3 and on speak for both: an empty profile and curve in the file replace the phone's. */
    @Test fun aCurrentFileWithNothingInItStillSpeaksForBoth() {
        val imported = valid(encodeDiary(diaryFileOf(rows().copy(profile = ProfileValues(), weightPoints = emptyList()), settings, 5_000L)))
        assertEquals(ProfileValues(), imported.rows.profile)
        assertEquals(emptyList(), imported.rows.weightPoints)
    }

    /** A file from before the profile has neither field: it imports, and says nothing about the phone's profile or curve. */
    @Test fun anOlderFileWithoutAProfileStillImports() {
        val old = encodeDiary(file()).replace("\"formatVersion\":$DIARY_FORMAT_VERSION,\"schemaVersion\":$DIARY_SCHEMA_VERSION,", "\"formatVersion\":2,\"schemaVersion\":9,")
            .replace("\"profile\":null,", "").replace(",\"weightPoints\":[]", "")
        assertFalse("weightPoints" in old)
        val imported = valid(old)
        assertNull(imported.rows.profile)
        assertNull(imported.rows.weightPoints)
    }

    /** The kcal target came later in format 3: a file of 3 without it imports, with none set. */
    @Test fun aProfileWithoutAKcalTargetStillImports() {
        val profile = ProfileValues("Ana", proteinTarget = 120.0)
        val encoded = encodeDiary(diaryFileOf(rows().copy(profile = profile, weightPoints = emptyList()), settings, 5_000L))
        val without = encoded.replace(",\"kcalTarget\":null", "")
        assertFalse("kcalTarget" in without)
        assertEquals(profile, valid(without).rows.profile)
    }

    @Test fun theNewProfileFieldsAndTheTargetHistoryComeBack() {
        val profile = ProfileValues(
            "Ana", 150.0, 175.0, 78.0, 80.0, 75.0, 2000.0, false, 165.5, 30, Sex.FEMALE, Activity.ACTIVE, Goal.LOSE, 0.5, true, 30.0, null, null, setOf(Macro.CARBS, Macro.FAT),
        )
        val history = listOf(TargetHistoryEntity("2026-10-01", 2000.0, 150.0, null, null), TargetHistoryEntity("2026-10-02", 1900.0, null, null, null))
        val imported = valid(encodeDiary(diaryFileOf(rows().copy(profile = profile, targetHistory = history.reversed()), settings, 5_000L)))
        assertEquals(profile, imported.rows.profile)
        assertEquals(history, imported.rows.targetHistory)
    }

    /** A file of format 4 as it was written has none of the new fields: it imports, and its profile starts a new history. */
    @Test fun aFormat4FileImportsWithNoneOfTheNewFields() {
        val encoded = encodeDiary(diaryFileOf(rows().copy(profile = ProfileValues("Ana", kcalTarget = 2000.0), weightPoints = emptyList()), settings, 5_000L))
        val old = encoded.replace("\"formatVersion\":$DIARY_FORMAT_VERSION,\"schemaVersion\":$DIARY_SCHEMA_VERSION,", "\"formatVersion\":4,\"schemaVersion\":11,")
            .replace(Regex(",\"heightCm\":null.*?\"followingMacros\":\\[\\]"), "")
            .replace(",\"targetHistory\":[]", "")
        assertFalse("targetHistory" in old)
        assertFalse("heightCm" in old)
        val imported = valid(old)
        assertEquals(ProfileValues("Ana", kcalTarget = 2000.0), imported.rows.profile)
        assertEquals(emptyList(), imported.rows.targetHistory)
    }

    @Test fun badNewProfileFieldsAndTargetRowsAreRefused() {
        val empty = ProfileDto("", null, null, null, null, null)
        refused(file().copy(profile = empty.copy(heightCm = 49.0)))
        refused(file().copy(profile = empty.copy(age = 0)))
        refused(file().copy(profile = empty.copy(age = 121)))
        refused(file().copy(profile = empty.copy(sex = "OTHER")))
        refused(file().copy(profile = empty.copy(activity = "LAZY")))
        refused(file().copy(profile = empty.copy(goal = "BULK")))
        refused(file().copy(profile = empty.copy(paceKgPerWeek = 0.0)))
        refused(file().copy(profile = empty.copy(fatPercent = 101.0)))
        refused(file().copy(profile = empty.copy(followingMacros = listOf("FIBRE"))))
        refused(file().copy(targetHistory = listOf(TargetHistoryDto("not a day", 2000.0, null, null, null))))
        refused(file().copy(targetHistory = listOf(TargetHistoryDto("2026-10-01", 0.5, null, null, null))))
        refused(file().copy(targetHistory = listOf(TargetHistoryDto("2026-10-01", 2000.0, 0.0, null, null))))
        refused(file().copy(targetHistory = List(2) { TargetHistoryDto("2026-10-01", 2000.0, null, null, null) }))
    }

    @Test fun aKcalTargetOutOfRangeIsRefused() {
        refused(file().copy(profile = ProfileDto("", null, null, null, null, null, kcalTarget = 0.5)))
        refused(file().copy(profile = ProfileDto("", null, null, null, null, null, kcalTarget = 20001.0)))
    }

    @Test fun badProfileNumbersAndPointsAreRefused() {
        refused(file().copy(profile = ProfileDto("", 0.0, null, null, null, null)))
        refused(file().copy(profile = ProfileDto("", 8000.0, null, null, null, null)))
        refused(file().copy(profile = ProfileDto("", null, null, null, 0.04, null)))
        refused(file().copy(profile = ProfileDto("", null, null, null, null, 8000.0)))
        refused(file().copy(weightPoints = listOf(WeightPointDto("2026-10-01", 8000.0, 1L))))
        refused(file().copy(weightPoints = listOf(WeightPointDto("2026-10-01", 0.04, 1L))))
        refused(file().copy(weightPoints = listOf(WeightPointDto("not a day", 80.0, 1L))))
        refused(file().copy(weightPoints = listOf(WeightPointDto("2026-10-01", -1.0, 1L))))
    }

    private val twoOnADay = listOf(WeightPointDto("2026-10-01", 80.0, 1L), WeightPointDto("2026-10-01", 81.0, 2L))

    /** One a day was the rule up to format 3, and such a file is still held to it. */
    @Test fun twoPointsOnADayAreRefusedOnlyInFormatsOfOneADay() {
        refused(file().copy(formatVersion = 3, schemaVersion = 10, weightPoints = twoOnADay))
        refused(file().copy(formatVersion = 2, schemaVersion = 9, weightPoints = twoOnADay))
        assertEquals(5, DIARY_FORMAT_VERSION)
        val imported = valid(encodeDiary(file().copy(weightPoints = twoOnADay)))
        assertEquals(listOf(1L, 2L), imported.rows.weightPoints?.map { it.savedAtEpochMs })
    }

    @Test fun severalPointsOnADayAndTheSettingComeBackWithTheirTimes() {
        val profile = ProfileValues(currentWeight = 80.0, severalWeights = true)
        // Stored ids stay on the phone: an import gives each row a new one.
        val points = listOf(
            WeightPointEntity("2026-10-02", 79.0, 9L, id = 7),
            WeightPointEntity("2026-10-01", 80.0, 5L, id = 3),
            WeightPointEntity("2026-10-01", 81.0, 5L, id = 8),
            WeightPointEntity("2026-10-01", 79.5, 2L, id = 4),
        )
        val text = encodeDiary(diaryFileOf(rows().copy(profile = profile, weightPoints = points), settings, 5_000L))
        assertFalse("\"id\":7" in text)
        val imported = valid(text)
        assertEquals(profile, imported.rows.profile)
        assertEquals(
            listOf(WeightPointEntity("2026-10-01", 79.5, 2L), WeightPointEntity("2026-10-01", 80.0, 5L), WeightPointEntity("2026-10-01", 81.0, 5L), WeightPointEntity("2026-10-02", 79.0, 9L)),
            imported.rows.weightPoints,
        )
    }

    /** A file of format 3 as it was written: its points keep their stored time, and the setting is off. */
    @Test fun aFormat3FileImportsWithItsTimesAndTheSettingOff() {
        val points = listOf(WeightPointEntity("2026-10-01", 80.0, 1_700_000_000_000L))
        val encoded = encodeDiary(diaryFileOf(rows().copy(profile = ProfileValues("Ana"), weightPoints = points), settings, 5_000L))
        val old = encoded.replace("\"formatVersion\":$DIARY_FORMAT_VERSION,\"schemaVersion\":$DIARY_SCHEMA_VERSION,", "\"formatVersion\":3,\"schemaVersion\":10,")
            .replace(",\"severalWeights\":false", "")
        assertFalse("severalWeights" in old)
        val imported = valid(old)
        assertEquals(ProfileValues("Ana"), imported.rows.profile)
        assertEquals(points, imported.rows.weightPoints)
    }

    /** Owner decision: an import leaves the phone's settings alone, so the file's settings never reach the result. */
    @Test fun settingsInTheFileDoNotChangeWhatIsImported() {
        val other = diaryFileOf(rows(), DiarySettings(0, LongDraft.END), exportedAtEpochMs = 5_000L)
        assertEquals(valid(encodeDiary(file())), valid(encodeDiary(other)))
    }

    @Test fun theFileNamesItsVersionsAndFields() {
        val text = encodeDiary(file())
        assertTrue(text.startsWith("{\"format\":\"breakfast-diary\",\"formatVersion\":5,\"schemaVersion\":12,\"exportedAtEpochMs\":5000,"))
        assertTrue("\"foodName\":\"Snapshot name e-1\"" in text)
        assertTrue("\"settings\":{\"dayStartHour\":4,\"longDraft\":\"TWO_LINES\"}" in text)
        assertTrue("\"snapshotCarbohydrates\":null" in text)
        assertTrue("{\"id\":\"recipe-chili\",\"name\":\"Чили\",\"portions\":4.0,\"totalWeight\":1200.0,\"totalVolume\":null}" in text)
        assertTrue("\"totalVolume\":1500.0" in text)
        assertTrue("\"totalWeight\":900.0" in text && "\"portions\":null" in text)
    }

    /** A file from before recipes (format 1, schema 6) has no recipe fields: it still imports, as a diary without recipes. */
    @Test fun aFileFromBeforeRecipesStillImports() {
        val old = diaryFileOf(rows().copy(recipes = emptyList(), recipeAliases = emptyList(), recipeIngredients = emptyList()), settings, 5_000L)
            .copy(formatVersion = 1, schemaVersion = 6, preferences = listOf(PreferenceDto("custom-1", 3, 1_700_000_000_000L)))
        val text = encodeDiary(old)
            .replace(",\"recipes\":[]", "").replace(",\"recipeAliases\":[]", "").replace(",\"recipeIngredients\":[]", "")
        assertTrue("recipe" !in text)
        val imported = valid(text)
        assertEquals(3, imported.rows.foods.size)
        assertEquals(4, imported.rows.entries.size)
        assertTrue(imported.rows.recipes.isEmpty() && imported.rows.recipeIngredients.isEmpty())
    }

    /** A file from before schema 9 has no density and no unit label: it imports with none. */
    @Test fun aFileFromBeforeMeasurementsStillImports() {
        val text = encodeDiary(file().copy(schemaVersion = 8, mealEntries = file().mealEntries.filter { it.enteredUnitLabel == null }))
            .replace(",\"density\":null", "").replace(",\"density\":103.0", "").replace(",\"enteredUnitLabel\":null", "")
        assertTrue("density" !in text && "enteredUnitLabel" !in text)
        val imported = valid(text)
        assertTrue(imported.rows.foods.all { it.density == null })
        assertTrue(imported.rows.entries.all { it.enteredUnitLabel == null })
    }

    @Test fun measurementFieldsAreChecked() {
        val f = file()
        refused(f.copy(foods = f.foods.map { it.copy(density = 0.0) }))
        refused(f.copy(mealEntries = f.mealEntries.map { it.copy(enteredUnitLabel = " ") }))
        assertTrue(encodeDiary(f).contains("\"density\":103.0"))
        assertTrue(encodeDiary(f).contains("\"enteredUnitLabel\":\"slice\""))
    }

    /** A format 2 file from schema 7 has no total volume: it imports with none. */
    @Test fun aFileFromBeforeTotalVolumeStillImports() {
        val text = encodeDiary(file().copy(schemaVersion = 7, recipes = file().recipes.filter { it.totalVolume == null }))
            .replace(",\"totalVolume\":null", "")
        assertTrue("totalVolume" !in text)
        val imported = valid(text)
        assertEquals(2, imported.rows.recipes.size)
        assertTrue(imported.rows.recipes.all { it.totalVolume == null })
    }

    @Test fun recipeRowsAreChecked() {
        val f = file()
        refused(f.copy(recipes = f.recipes + f.recipes.first()))
        refused(f.copy(recipes = f.recipes + RecipeDto("custom-1", "Clash with a food", 1.0, null)))
        // Ids say what they are: a recipe's starts "recipe-", and a food's never does.
        refused(f.copy(recipes = f.recipes + RecipeDto("hand-made", "No prefix", 1.0, null)))
        refused(f.copy(foods = f.foods + f.foods.first().copy(id = "recipe-food")))
        refused(f.copy(recipes = f.recipes.map { it.copy(name = " ") }))
        refused(f.copy(recipes = f.recipes.map { it.copy(portions = 0.0) }))
        refused(f.copy(recipes = f.recipes.map { it.copy(totalWeight = -1.0) }))
        refused(f.copy(recipes = f.recipes.map { it.copy(portions = null, totalWeight = null, totalVolume = null) }))
        refused(f.copy(recipes = f.recipes.map { it.copy(totalVolume = 0.0) }))
        assertNull(diaryProblem(f.copy(recipes = f.recipes.map { it.copy(portions = null, totalWeight = null, totalVolume = 1.0) })))
        refused(f.copy(recipeAliases = f.recipeAliases + RecipeAliasDto("recipe-missing", "x")))
        refused(f.copy(recipeAliases = f.recipeAliases + f.recipeAliases.first()))
        refused(f.copy(recipeIngredients = f.recipeIngredients + f.recipeIngredients.first()))
        refused(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(recipeId = "recipe-missing") }))
        refused(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(enteredUnit = "QUART") }))
        refused(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(snapshotCalories = -1.0) }))
        refused(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(snapshotProtein = -1.0) }))
        refused(f.copy(preferences = f.preferences + PreferenceDto("recipe-missing", 1, 1L)))
        // An ingredient's food need not be in the file: it keeps its own name and numbers.
        assertNull(diaryProblem(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(foodId = "missing") })))
    }

    /** A new schema version or table must reach the file too: this fails until [DIARY_SCHEMA_VERSION] and [DiaryRows] follow. */
    @Test fun theFileFollowsTheLatestSchemaAndCoversEveryTable() {
        val dir = File("schemas/debug/com.bosa.breakfast.data.BreakfastDatabase")
        val latest = dir.listFiles()!!.maxOf { it.nameWithoutExtension.toInt() }
        assertEquals(latest, DIARY_SCHEMA_VERSION)
        val schema = Json.parseToJsonElement(File(dir, "$latest.json").readText()).jsonObject
        val tables = schema["database"]!!.jsonObject["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet()
        assertEquals(
            setOf("foods", "food_aliases", "serving_conversions", "preferences", "meal_entries", "recipes", "recipe_aliases", "recipe_ingredients", "weight_points", "target_history"),
            tables,
        )
    }

    @Test fun exportIsTheSameEveryTime() {
        val shuffled = rows().let { it.copy(foods = it.foods.reversed(), entries = it.entries.reversed(), aliases = it.aliases.reversed()) }
        assertEquals(encodeDiary(file()), encodeDiary(diaryFileOf(shuffled, settings, 5_000L)))
    }

    @Test fun childRowsOfAMissingFoodAreLeftOut() {
        val withOrphans = rows().let {
            it.copy(
                aliases = it.aliases + FoodAliasEntity("gone", "x"),
                conversions = it.conversions + ServingConversionEntity("gone", "GRAM", 1.0, "p", false, ""),
                preferences = it.preferences + PreferenceEntity("gone", 1, 1L),
                recipeAliases = it.recipeAliases + RecipeAliasEntity("recipe-gone", "x"),
                recipeIngredients = it.recipeIngredients + ingredient("recipe-gone", 0, "custom-1"),
            )
        }
        val imported = valid(encodeDiary(diaryFileOf(withOrphans, settings, 0L)))
        assertEquals(sorted(rows()), sorted(imported.rows))
    }

    @Test fun theBundledCatalogueAndAnEmptyDiaryPass() {
        val catalogue = TestFoods.fixtures
        val rows = DiaryRows(
            foods = catalogue.map { it.toEntity() },
            aliases = catalogue.flatMap { it.toAliasEntities() },
            conversions = catalogue.flatMap { it.toConversionEntities() },
            preferences = emptyList(),
            entries = emptyList(),
        )
        assertNull(diaryProblem(diaryFileOf(rows, settings, 0L)))
        assertNull(diaryProblem(diaryFileOf(DiaryRows(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), settings, 0L)))
    }

    @Test fun notADiaryIsRefused() {
        assertIs<DiaryImport.Invalid>(decodeDiary(""))
        assertIs<DiaryImport.Invalid>(decodeDiary("{}"))
        assertIs<DiaryImport.Invalid>(decodeDiary("[1,2,3]"))
        assertIs<DiaryImport.Invalid>(decodeDiary(encodeDiary(file()).dropLast(20)))
        refused(file().copy(format = "something-else"))
    }

    @Test fun anUnknownFormatOrNewerSchemaIsRefused() {
        refused(file().copy(formatVersion = DIARY_FORMAT_VERSION + 1))
        refused(file().copy(formatVersion = 0))
        refused(file().copy(schemaVersion = DIARY_SCHEMA_VERSION + 1))
        refused(file().copy(schemaVersion = 0))
    }

    @Test fun unknownOrMissingFieldsAreRefused() {
        val text = encodeDiary(file())
        assertIs<DiaryImport.Invalid>(decodeDiary(text.replaceFirst("\"exportedAtEpochMs\":5000,", "\"exportedAtEpochMs\":5000,\"extra\":1,")))
        assertIs<DiaryImport.Invalid>(decodeDiary(text.replaceFirst("\"brand\":\"Brand\",", "")))
        assertIs<DiaryImport.Invalid>(decodeDiary(text.replaceFirst("\"name\":\"Food archived-1\"", "\"name\":null")))
    }

    @Test fun nanAndNegativeNutritionAreRefused() {
        val text = encodeDiary(file())
        assertIs<DiaryImport.Invalid>(decodeDiary(text.replaceFirst("\"calories\":250.0", "\"calories\":NaN")))
        val f = file()
        refused(f.copy(foods = f.foods.mapIndexed { i, it -> if (i == 0) it.copy(calories = -1.0) else it }))
        refused(f.copy(foods = f.foods.mapIndexed { i, it -> if (i == 0) it.copy(protein = -0.1) else it }))
        refused(f.copy(foods = f.foods.mapIndexed { i, it -> if (i == 0) it.copy(nutritionAmount = 0.0) else it }))
        refused(f.copy(mealEntries = f.mealEntries.mapIndexed { i, it -> if (i == 0) it.copy(snapshotCalories = -5.0) else it }))
        refused(f.copy(mealEntries = f.mealEntries.mapIndexed { i, it -> if (i == 0) it.copy(snapshotFat = -1.0) else it }))
        refused(f.copy(mealEntries = f.mealEntries.mapIndexed { i, it -> if (i == 0) it.copy(enteredAmount = -2.0) else it }))
        refused(f.copy(servingConversions = f.servingConversions.mapIndexed { i, it -> if (i == 0) it.copy(baseUnitsPerServing = 0.0) else it }))
        refused(f.copy(preferences = f.preferences.map { it.copy(explicitCount = -1) }))
    }

    @Test fun unknownEnumValuesAreRefused() {
        val f = file()
        refused(f.copy(foods = f.foods.mapIndexed { i, it -> if (i == 0) it.copy(origin = "IMPORTED") else it }))
        refused(f.copy(foods = f.foods.mapIndexed { i, it -> if (i == 0) it.copy(nutritionUnit = "OUNCE") else it }))
        refused(f.copy(mealEntries = f.mealEntries.mapIndexed { i, it -> if (i == 0) it.copy(mealSlot = "BRUNCH") else it }))
        refused(f.copy(mealEntries = f.mealEntries.mapIndexed { i, it -> if (i == 0) it.copy(resolvedBaseUnit = "QUART") else it }))
        refused(f.copy(servingConversions = f.servingConversions.mapIndexed { i, it -> if (i == 0) it.copy(unit = "QUART") else it }))
        refused(f.copy(settings = DiarySettingsDto(4, "MIDDLE")))
        refused(f.copy(settings = DiarySettingsDto(24, "END")))
    }

    @Test fun duplicateIdsAreRefused() {
        val f = file()
        refused(f.copy(foods = f.foods + f.foods.first()))
        refused(f.copy(mealEntries = f.mealEntries + f.mealEntries.first().copy(foodName = "other")))
        refused(f.copy(foodAliases = f.foodAliases + f.foodAliases.first()))
        refused(f.copy(servingConversions = f.servingConversions + f.servingConversions.first().copy(baseUnitsPerServing = 9.0)))
        refused(f.copy(preferences = f.preferences + f.preferences.first()))
        refused(f.copy(mealEntries = f.mealEntries + f.mealEntries.first().copy(id = " ")))
    }

    @Test fun childRowsMustNameAFoodInTheFileButEntriesNeedNot() {
        val f = file()
        refused(f.copy(foodAliases = f.foodAliases + FoodAliasDto("missing", "x")))
        refused(f.copy(servingConversions = f.servingConversions + ServingConversionDto("missing", "GRAM", 1.0, "p", false, "")))
        refused(f.copy(preferences = f.preferences + PreferenceDto("missing", 1, 1L)))
        assertNull(diaryProblem(f.copy(mealEntries = f.mealEntries.map { it.copy(foodId = "missing") })))
    }

    @Test fun aBadDayKeyIsRefused() {
        val f = file()
        refused(f.copy(mealEntries = f.mealEntries.mapIndexed { i, it -> if (i == 0) it.copy(dayKey = "2026-13-01") else it }))
        refused(f.copy(mealEntries = f.mealEntries.mapIndexed { i, it -> if (i == 0) it.copy(dayKey = "") else it }))
    }

    @Test fun aFileOverTheLimitIsNotRead() {
        assertNull(readDiaryText(ByteArrayInputStream(ByteArray(2_000)), maxBytes = 1_000))
        assertEquals("{\"a\":1}", readDiaryText(ByteArrayInputStream("\uFEFF{\"a\":1}".toByteArray()), maxBytes = 1_000))
        assertEquals("бурек", readDiaryText(ByteArrayInputStream("бурек".toByteArray())))
    }

    @Test fun anExportCountsOnlyWhenItsCopyReadsBackWhole() {
        var written: ByteArray? = null
        assertNull(writeDiaryChecked(file(), { written = it }, { written?.toString(Charsets.UTF_8) }))
        // A provider that kept only part of the file, or nothing, or gives nothing back.
        assertTrue(writeDiaryChecked(file(), { written = it.copyOf(it.size / 2) }, { written?.toString(Charsets.UTF_8) }) != null)
        assertTrue(writeDiaryChecked(file(), { written = ByteArray(0) }, { written?.toString(Charsets.UTF_8) }) != null)
        assertTrue(writeDiaryChecked(file(), { written = it }, { null }) != null)
    }

    @Test fun anInvalidDiaryIsNeverWritten() {
        val bad = file().let { f -> f.copy(foods = f.foods + f.foods.first()) }
        var writes = 0
        assertTrue(writeDiaryChecked(bad, { writes++ }, { "" }) != null)
        assertEquals(0, writes)
    }

    @Test fun theSafetyCopyReplacesThePreviousOneAndLeavesNoTempFile() {
        val dir = Files.createTempDirectory("safety").toFile()
        try {
            writeSafetyCopy(dir, diaryFileOf(rows(), settings, 1L))
            val second = writeSafetyCopy(dir, file())
            assertEquals(listOf(SAFETY_COPY_NAME), dir.list()!!.toList())
            assertEquals(encodeDiary(file()), second.readText())
            assertIs<DiaryImport.Valid>(decodeDiary(second.readText()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun aFailedSafetyCopyKeepsThePreviousOne() {
        val dir = Files.createTempDirectory("safety").toFile()
        try {
            val first = writeSafetyCopy(dir, file())
            val bad = file().let { f -> f.copy(foods = f.foods + f.foods.first()) }
            assertTrue(runCatching { writeSafetyCopy(dir, bad) }.isFailure)
            assertEquals(listOf(SAFETY_COPY_NAME), dir.list()!!.toList())
            assertEquals(encodeDiary(file()), first.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun theSuggestedNameCarriesTheDate() {
        assertEquals("breakfast-2026-10-26.json", diaryFileName(LocalDate.of(2026, 10, 26)))
    }

    @Test fun aYearOfLoggingWritesAndReadsQuickly() {
        val foods = TestFoods.fixtures.map { it.toEntity() } + (1..200).map { food("custom-$it") }
        val entries = (1..3_000).map { entry("year-$it", "custom-${it % 200 + 1}", it * 60_000L) }
        val rows = DiaryRows(foods, TestFoods.fixtures.flatMap { it.toAliasEntities() }, TestFoods.fixtures.flatMap { it.toConversionEntities() }, emptyList(), entries)
        val started = System.nanoTime()
        val text = encodeDiary(diaryFileOf(rows, settings, 0L))
        val imported = valid(text)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(3_000, imported.rows.entries.size)
        assertTrue(text.length < 5_000_000, "a year is ${text.length} characters")
        assertTrue(elapsedMs < 5_000, "a year took $elapsedMs ms")
    }
}
