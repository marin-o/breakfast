package mk.bosa.breakfast.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.servingNamed
import mk.bosa.breakfast.search.USDA_MAX_RESULTS
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * The exact shipped file on a device, under the phone's own SQLite. It unpacks to a folder of its own in the cache,
 * deleted afterwards, never the app's `no_backup/usda`.
 */
@RunWith(AndroidJUnit4::class)
class UsdaCatalogueReaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "usda-test-${UUID.randomUUID()}")
    private val readers = mutableListOf<UsdaCatalogueReader>()
    private var assetOpens = 0

    private fun reader(open: () -> InputStream = { assetOpens++; context.assets.open(USDA_ASSET_PATH) }) =
        UsdaCatalogueReader(directory, open).also { readers += it }

    private val coatedBreast = 2705975L

    @After fun tearDown() {
        readers.forEach { it.close() }
        directory.deleteRecursively()
    }

    private fun search(text: String) = runBlocking { reader().search(text) }

    private fun openUnpacked(): SQLiteDatabase {
        assertTrue(search("egg").isNotEmpty())
        val file = directory.listFiles()!!.single()
        // Writable, on this test's own copy: integrity_check on an FTS4 table asks to write, though the app only reads.
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    }

    @Test fun theFileHoldsWhatTheImporterCounted() = openUnpacked().use { db ->
        assertEquals(USDA_CATALOGUE_VERSION, db.version)
        fun count(sql: String) = db.rawQuery(sql, null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(5431, count("SELECT count(*) FROM food WHERE release = 'FNDDS 2021-2023'"))
        assertEquals(1814, count("SELECT count(*) FROM food WHERE release = 'SR Legacy 2018-04'"))
        assertEquals(19319, count("SELECT count(*) FROM portion"))
        assertEquals(0, count("SELECT count(*) FROM portion WHERE grams <= 0"))
        assertEquals(1, count("SELECT count(*) FROM alias"))
        assertEquals(160, count("SELECT count(*) FROM translation"))
        assertEquals(76, count("SELECT count(*) FROM food WHERE bump > 0"))
        db.rawQuery("PRAGMA integrity_check", null).use {
            it.moveToFirst()
            assertEquals("ok", it.getString(0))
        }
    }

    @Test fun aFoodMapsWithItsValuesAndPortions() {
        val food = reader().food(coatedBreast)!!
        assertEquals("fndds_$coatedBreast", food.id)
        assertEquals(219.0, food.nutrition.values.calories, 0.0)
        assertEquals(24.6, food.nutrition.values.protein!!, 0.0)
        assertEquals(9.99, food.nutrition.values.fat!!, 0.0)
        assertEquals(6.01, food.nutrition.values.carbohydrates!!, 0.0)
        assertEquals(361.35, food.nutrition.values.calories * 165.0 / 100.0, 1e-9)
        val serving = food.servingNamed("large breast")!!
        assertEquals(MeasureUnit.GRAM, serving.unit)
        assertEquals(185.0, serving.baseUnitsPerServing, 0.0)
        assertTrue(serving.approximate)
    }

    @Test fun bothWordingsAnswerWithTheCoatedBreast() {
        assertTrue(search("chicken breast coated").any { it.id == "fndds_$coatedBreast" })
        assertTrue(search("breaded chicken breast").any { it.id == "fndds_$coatedBreast" })
        assertTrue(search("breaded chicken breasts").any { it.id == "fndds_$coatedBreast" })
    }

    @Test fun anAdditionalDescriptionFindsItsFood() {
        assertTrue(reader().rankedCandidates("sauteed with coating").any { it.fdcId == coatedBreast })
    }

    @Test fun anApostropheFindsTheWordAsPrinted() {
        assertTrue(reader().rankedCandidates("farmer's").any { it.name == "Cottage cheese, farmer's" })
    }

    @Test fun eggDoesNotFindEggplantFirst() {
        val first = search("egg").first()
        assertFalse(first.name, first.name.contains("eggplant", ignoreCase = true))
    }

    @Test fun aSearchAnswersAtMostTheLimit() {
        assertEquals(USDA_MAX_RESULTS, search("chicken").size)
    }

    @Test fun aCyrillicWordInTheTableFindsItsFoods() {
        val first = search("јајце").first()
        assertEquals("Egg, whole, raw", first.name)
        assertTrue(search("пилешки гради").any { it.name.contains("chicken", ignoreCase = true) })
    }

    @Test fun aCyrillicWordFindsEachOfItsEnglishWords() {
        assertTrue(search("пита").any { it.name.contains("pie", ignoreCase = true) })
        assertTrue(reader().rankedCandidates("пита").any { it.name == "Bread, pita" })
        assertTrue(reader().rankedCandidates("печено").any { it.name.contains("baked", ignoreCase = true) })
        assertTrue(reader().rankedCandidates("печени").any { it.name.contains("roasted", ignoreCase = true) })
        assertTrue(search("црн дроб").any { it.name.contains("liver", ignoreCase = true) })
    }

    @Test fun aCyrillicWordOutsideTheTableOrAMixedWordFindsNothing() {
        assertEquals(emptyList<Any>(), search("чорба"))
        assertEquals(emptyList<Any>(), search("egg чорба"))
        assertEquals(emptyList<Any>(), search("eгg"))
    }

    @Test fun aLocalDishFindsItsUsdaFoodsInBothScripts() {
        listOf("sarma", "сарма").forEach { typed ->
            val found = search(typed).map { it.name }
            assertTrue(found.toString(), found.any { it.startsWith("Stuffed cabbage") })
            assertTrue(found.toString(), found.any { it.contains("grape leaves", ignoreCase = true) })
        }
        assertTrue(search("долма").any { it.name.contains("grape leaves", ignoreCase = true) })
    }

    @Test fun aSingularFindsThePlainFoodPrintedInThePlural() {
        assertEquals("Tomatoes, raw", search("tomato").first().name)
        assertEquals("Peppers, raw, NFS", search("pepper").first().name)
        assertTrue(search("strawberry").any { it.name == "Strawberries, raw" })
        assertTrue(search("cherry").any { it.name == "Cherries, raw" })
    }

    @Test fun aHomeStapleIsFoundFirstInEnglishAndMacedonian() {
        listOf(
            "flour" to "Wheat flour, white, all-purpose, enriched, bleached", "брашно" to "Wheat flour, white, all-purpose, enriched, bleached",
            "corn flour" to "Corn flour, whole-grain, yellow", "пченкарно брашно" to "Corn flour, whole-grain, yellow",
            "cornmeal" to "Cornmeal, whole-grain, yellow", "salt" to "Salt, table", "сол" to "Salt, table",
            "baking powder" to "Leavening agents, baking powder, double-acting, sodium aluminum sulfate",
            "прашок за пециво" to "Leavening agents, baking powder, double-acting, sodium aluminum sulfate",
            "baking soda" to "Leavening agents, baking soda", "сода бикарбона" to "Leavening agents, baking soda",
            "semolina" to "Semolina, unenriched", "гриз" to "Semolina, unenriched",
        ).forEach { (typed, name) -> assertEquals(typed, name, search(typed).first().name) }
    }

    @Test fun anSrLegacyFoodSaysSoAndKeepsTheFnddsId() {
        val salt = search("salt").first()
        assertEquals("SR Legacy 2018-04", salt.sourceRelease)
        assertTrue(salt.id.startsWith("fndds_"))
        assertEquals("FNDDS 2021-2023", reader().food(coatedBreast)!!.sourceRelease)
    }

    @Test fun aBumpPutsTheCommonFoodFirst() {
        assertEquals("Chicken breast, grilled without sauce, skin not eaten", search("chicken").first().name)
        assertEquals("Egg, whole, raw", search("egg").first().name)
    }

    @Test fun rawFtsSyntaxIsNeverSyntax() {
        val reader = reader()
        listOf("egg OR milk", "egg NOT milk", "\"egg", "egg*", "food_fts:egg", "NEAR(egg milk)", "(", "*", "'; DROP TABLE food; --").forEach { typed ->
            runBlocking { reader.search(typed) }
        }
        assertTrue(runBlocking { reader.search("egg") }.isNotEmpty())
    }

    @Test fun theSecondStartDoesNotUnpackAgain() {
        val first = reader()
        assertTrue(runBlocking { first.search("egg") }.isNotEmpty())
        runBlocking { first.search("milk") }
        assertEquals(1, assetOpens)
        first.close()
        assertTrue(runBlocking { reader().search("egg") }.isNotEmpty())
        assertEquals(1, assetOpens)
    }

    @Test fun aFileThatCannotBeUnpackedMeansNoResultsAndNoFile() {
        val reader = reader { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        assertEquals(emptyList<Any>(), runBlocking { reader.search("egg") })
        assertEquals(0, directory.list().orEmpty().size)
    }

    @Test fun aFileThatCannotBeOpenedMeansNoResults() {
        val gzipOfNonsense = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.GZIPOutputStream(out).use { it.write(ByteArray(8192) { 7 }) }
        }.toByteArray()
        assertEquals(emptyList<Any>(), runBlocking { reader { ByteArrayInputStream(gzipOfNonsense) }.search("egg") })
    }
}
