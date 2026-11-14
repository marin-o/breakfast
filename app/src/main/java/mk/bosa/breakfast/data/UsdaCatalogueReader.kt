package mk.bosa.breakfast.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.search.USDA_MAX_RESULTS
import mk.bosa.breakfast.search.UsdaCandidate
import mk.bosa.breakfast.search.UsdaCatalogue
import mk.bosa.breakfast.search.usdaQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/** The file's version: raise it whenever the file's content changes, with `CATALOGUE_VERSION` in `tools/build_usda_catalogue.py`. */
internal const val USDA_CATALOGUE_VERSION = 9

private const val USDA_FILE_NAME = "usda-v$USDA_CATALOGUE_VERSION.sqlite"

/** Built by `tools/build_usda_catalogue.py`. Not ".gz": the Android Gradle plugin unpacks those when it merges assets. */
internal const val USDA_ASSET_PATH = "usda/$USDA_FILE_NAME.gzip"

/**
 * The USDA catalogue on the device: the shipped file, unpacked once per version ([catalogueFile]) at the first search
 * and opened read-only. Its foods stay in the file until one is picked; nothing here touches the diary.
 *
 * A file that can't be unpacked or opened means no USDA results until the next start, and it isn't tried again before
 * then, so a search never unpacks. A search abandoned while it reads runs to the end of its query on [io]; its caller
 * gets the cancellation, never the result.
 */
class UsdaCatalogueReader internal constructor(
    private val directory: File,
    private val openAsset: () -> InputStream,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : UsdaCatalogue {
    constructor(context: Context) : this(
        // Not backed up: the app can always make it again from its own asset.
        File(context.noBackupFilesDir, "usda"),
        { context.applicationContext.assets.open(USDA_ASSET_PATH) },
    )

    private class Opened(val db: SQLiteDatabase, val aliases: Map<String, List<String>>, val translations: Map<String, List<String>>)

    private val lock = Any()
    private var opened: Opened? = null
    private var triedOpening = false

    override suspend fun search(text: String): List<FoodRecord> = withContext(io) {
        rankedCandidates(text).take(USDA_MAX_RESULTS).mapNotNull { food(it.fdcId) }
    }

    /** Every food [text] finds, in rank order, before the limit of a search. */
    internal fun rankedCandidates(text: String): List<UsdaCandidate> = readOrElse(emptyList()) { catalogue ->
        val query = usdaQuery(text, catalogue.aliases, catalogue.translations) ?: return@readOrElse emptyList()
        query.rank(query.matchExpressions().flatMap { candidates(catalogue.db, it) }.distinctBy { it.fdcId })
    }

    /** The food [fdcId] is, or null when the catalogue lacks it or can't be read. */
    internal fun food(fdcId: Long): FoodRecord? = readOrElse(null) { catalogue -> readRow(catalogue.db, fdcId)?.toFoodRecord() }

    /** Closes the file; the reader opens it again if asked. */
    internal fun close() = synchronized(lock) {
        opened?.db?.close()
        opened = null
        triedOpening = false
    }

    /** [read] on the open catalogue, or [absent] with no catalogue or a catalogue that fails: its results are simply absent. */
    private fun <T> readOrElse(absent: T, read: (Opened) -> T): T = try {
        open()?.let(read) ?: absent
    } catch (_: SQLiteException) {
        absent
    }

    private fun open(): Opened? = synchronized(lock) {
        if (!triedOpening) {
            triedOpening = true
            opened = try {
                val file = catalogueFile(directory, USDA_FILE_NAME, openAsset)
                // No localized collators: they would ask to write to android_metadata.
                val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
                try {
                    Opened(db, readAliases(db), readTranslations(db))
                } catch (failed: SQLiteException) {
                    db.close()
                    throw failed
                }
            } catch (_: IOException) {
                null
            } catch (_: SQLiteException) {
                null
            }
        }
        opened
    }

    private fun readAliases(db: SQLiteDatabase): Map<String, List<String>> =
        readWordLists(db, "SELECT word, target FROM alias ORDER BY word, target")

    private fun readTranslations(db: SQLiteDatabase): Map<String, List<String>> =
        readWordLists(db, "SELECT word, english FROM translation ORDER BY word, english")

    private fun readWordLists(db: SQLiteDatabase, sql: String): Map<String, List<String>> =
        db.rawQuery(sql, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
        }.groupBy({ it.first }, { it.second })

    private fun candidates(db: SQLiteDatabase, match: String): List<UsdaCandidate> = db.rawQuery(
        "SELECT fdc_id, description, bump FROM food WHERE fdc_id IN (SELECT docid FROM food_fts WHERE food_fts MATCH ?)",
        arrayOf(match),
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(UsdaCandidate(cursor.getLong(0), cursor.getString(1), cursor.getInt(2))) }
    }

    private fun readRow(db: SQLiteDatabase, fdcId: Long): UsdaFoodRow? = db.rawQuery(
        "SELECT food_code, description, additional, kcal, protein, fat, carbs, saturated_fat, sugars, fibre, salt, release FROM food WHERE fdc_id = ?",
        arrayOf(fdcId.toString()),
    ).use { cursor ->
        if (!cursor.moveToFirst()) return null
        UsdaFoodRow(
            fdcId = fdcId,
            foodCode = cursor.getString(0),
            description = cursor.getString(1),
            additional = cursor.getString(2),
            kcal = cursor.getDouble(3),
            protein = cursor.doubleOrNull(4),
            fat = cursor.doubleOrNull(5),
            carbs = cursor.doubleOrNull(6),
            saturatedFat = cursor.doubleOrNull(7),
            sugars = cursor.doubleOrNull(8),
            fibre = cursor.doubleOrNull(9),
            salt = cursor.doubleOrNull(10),
            release = cursor.getString(11),
            portions = readPortions(db, fdcId),
        )
    }

    private fun readPortions(db: SQLiteDatabase, fdcId: Long): List<UsdaPortionRow> = db.rawQuery(
        "SELECT label, grams FROM portion WHERE fdc_id = ? ORDER BY position",
        arrayOf(fdcId.toString()),
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(UsdaPortionRow(cursor.getString(0), cursor.getDouble(1))) }
    }

    private fun Cursor.doubleOrNull(column: Int): Double? = if (isNull(column)) null else getDouble(column)
}
