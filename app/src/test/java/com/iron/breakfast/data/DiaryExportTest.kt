package com.iron.breakfast.data

import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.ui.components.LongDraft
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
        foods = listOf(food("custom-1"), food("archived-1", archived = true), food("edited-builtin", override = true)),
        aliases = listOf(FoodAliasEntity("custom-1", "бурек"), FoodAliasEntity("custom-1", "burek")),
        conversions = listOf(
            ServingConversionEntity("custom-1", "PIECE", 60.0, "user", approximate = true, label = ""),
            ServingConversionEntity("custom-1", "PIECE", 250.0, "user", approximate = false, label = "jar"),
        ),
        preferences = listOf(PreferenceEntity("custom-1", 3, 1_700_000_000_000L), PreferenceEntity("recipe-chili", 2, 1_700_000_000_000L)),
        // The second entry's food is gone: its snapshot keeps it readable, so it must survive the round trip.
        entries = listOf(entry("e-1", "custom-1", 2_000L), entry("e-2", "deleted-food", 1_000L), entry("e-3", "archived-1", 3_000L)),
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

    /** Owner decision: an import leaves the phone's settings alone, so the file's settings never reach the result. */
    @Test fun settingsInTheFileDoNotChangeWhatIsImported() {
        val other = diaryFileOf(rows(), DiarySettings(0, LongDraft.END), exportedAtEpochMs = 5_000L)
        assertEquals(valid(encodeDiary(file())), valid(encodeDiary(other)))
    }

    @Test fun theFileNamesItsVersionsAndFields() {
        val text = encodeDiary(file())
        assertTrue(text.startsWith("{\"format\":\"breakfast-diary\",\"formatVersion\":2,\"schemaVersion\":8,\"exportedAtEpochMs\":5000,"))
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
        assertEquals(3, imported.rows.entries.size)
        assertTrue(imported.rows.recipes.isEmpty() && imported.rows.recipeIngredients.isEmpty())
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
        refused(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(enteredUnit = "CUP") }))
        refused(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(snapshotCalories = -1.0) }))
        refused(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(snapshotProtein = -1.0) }))
        refused(f.copy(preferences = f.preferences + PreferenceDto("recipe-missing", 1, 1L)))
        // An ingredient's food need not be in the file: it keeps its own name and numbers.
        assertNull(diaryProblem(f.copy(recipeIngredients = f.recipeIngredients.map { it.copy(foodId = "missing") })))
    }

    /** A new schema version or table must reach the file too: this fails until [DIARY_SCHEMA_VERSION] and [DiaryRows] follow. */
    @Test fun theFileFollowsTheLatestSchemaAndCoversEveryTable() {
        val dir = File("schemas/debug/com.iron.breakfast.data.BreakfastDatabase")
        val latest = dir.listFiles()!!.maxOf { it.nameWithoutExtension.toInt() }
        assertEquals(latest, DIARY_SCHEMA_VERSION)
        val schema = Json.parseToJsonElement(File(dir, "$latest.json").readText()).jsonObject
        val tables = schema["database"]!!.jsonObject["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet()
        assertEquals(
            setOf("foods", "food_aliases", "serving_conversions", "preferences", "meal_entries", "recipes", "recipe_aliases", "recipe_ingredients"),
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
        val catalogue = FoodCatalog.fixtures
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
        refused(f.copy(mealEntries = f.mealEntries.mapIndexed { i, it -> if (i == 0) it.copy(resolvedBaseUnit = "cup") else it }))
        refused(f.copy(servingConversions = f.servingConversions.mapIndexed { i, it -> if (i == 0) it.copy(unit = "CUP") else it }))
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
        val foods = FoodCatalog.fixtures.map { it.toEntity() } + (1..200).map { food("custom-$it") }
        val entries = (1..3_000).map { entry("year-$it", "custom-${it % 200 + 1}", it * 60_000L) }
        val rows = DiaryRows(foods, FoodCatalog.fixtures.flatMap { it.toAliasEntities() }, FoodCatalog.fixtures.flatMap { it.toConversionEntities() }, emptyList(), entries)
        val started = System.nanoTime()
        val text = encodeDiary(diaryFileOf(rows, settings, 0L))
        val imported = valid(text)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(3_000, imported.rows.entries.size)
        assertTrue(text.length < 5_000_000, "a year is ${text.length} characters")
        assertTrue(elapsedMs < 5_000, "a year took $elapsedMs ms")
    }
}
