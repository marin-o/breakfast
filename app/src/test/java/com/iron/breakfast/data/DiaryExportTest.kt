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
        preferences = listOf(PreferenceEntity("custom-1", 3, 1_700_000_000_000L)),
        // The second entry's food is gone: its snapshot keeps it readable, so it must survive the round trip.
        entries = listOf(entry("e-1", "custom-1", 2_000L), entry("e-2", "deleted-food", 1_000L), entry("e-3", "archived-1", 3_000L)),
    )

    private fun sorted(rows: DiaryRows) = DiaryRows(
        rows.foods.sortedBy { it.id },
        rows.aliases.sortedWith(compareBy({ it.foodId }, { it.alias })),
        rows.conversions.sortedWith(compareBy({ it.foodId }, { it.unit }, { it.label })),
        rows.preferences.sortedBy { it.foodId },
        rows.entries.sortedBy { it.id },
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
        assertTrue(text.startsWith("{\"format\":\"breakfast-diary\",\"formatVersion\":1,\"schemaVersion\":6,\"exportedAtEpochMs\":5000,"))
        assertTrue("\"foodName\":\"Snapshot name e-1\"" in text)
        assertTrue("\"settings\":{\"dayStartHour\":4,\"longDraft\":\"TWO_LINES\"}" in text)
        assertTrue("\"snapshotCarbohydrates\":null" in text)
    }

    /** A new schema version or table must reach the file too: this fails until [DIARY_SCHEMA_VERSION] and [DiaryRows] follow. */
    @Test fun theFileFollowsTheLatestSchemaAndCoversEveryTable() {
        val dir = File("schemas/debug/com.iron.breakfast.data.BreakfastDatabase")
        val latest = dir.listFiles()!!.maxOf { it.nameWithoutExtension.toInt() }
        assertEquals(latest, DIARY_SCHEMA_VERSION)
        val schema = Json.parseToJsonElement(File(dir, "$latest.json").readText()).jsonObject
        val tables = schema["database"]!!.jsonObject["entities"]!!.jsonArray.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("foods", "food_aliases", "serving_conversions", "preferences", "meal_entries"), tables)
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
        refused(file().copy(formatVersion = 2))
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
