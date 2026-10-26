package com.iron.breakfast.bench

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iron.breakfast.data.BreakfastDatabase
import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.data.DiaryRows
import com.iron.breakfast.data.toAliasEntities
import com.iron.breakfast.data.toConversionEntities
import com.iron.breakfast.data.toDomain
import com.iron.breakfast.data.toEntity
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.foodsAvailableForLogging
import com.iron.breakfast.domain.libraryRecencyOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * MOB-028 step 1 / MOB-H07: timings on a synthetic year ([SyntheticYear]: ~3000 entries over 365 days, the catalogue
 * plus 450 personal foods) in a throwaway in-memory database, through the real [BreakfastStore] and [BreakfastEngine].
 * The owner's diary is never opened. Each line goes to logcat (tag `BreakfastBench`) and to `am instrument -r` as an
 * INSTRUMENTATION_STATUS (code 2), and the last line of each step is in INSTRUMENTATION_RESULT.
 *
 * Runs only when asked: `-e class com.iron.breakfast.bench.BreakfastBenchmark` (or `-e bench true`), so a full
 * instrumented run skips it. The app under test is the debug build (debuggable), so numbers are pessimistic next to perf.
 */
@RunWith(AndroidJUnit4::class)
class BreakfastBenchmark {
    private lateinit var database: BreakfastDatabase
    private lateinit var store: BreakfastStore
    private lateinit var engine: BreakfastEngine
    private val year = SyntheticYear()

    @Before
    fun setUp() = runBlocking {
        assumeTrue("BreakfastBenchmark runs only with -e class ...BreakfastBenchmark or -e bench true", requested())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BreakfastDatabase::class.java).build()
        store = populated(database)
        engine = BreakfastEngine(store)
        assertEquals(year.diary.size, store.entries().size)
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        report(
            "setup foods=${store.foods().size} (custom=${year.custom.size}) entries=${store.entries().size} " +
                "days=${store.entries().map { it.dayKey }.distinct().size} preferences=${store.preferenceMap().size} " +
                "busiestDay=${year.busiestDay} debuggable=$debuggable",
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        fileDatabase?.close()
        fileDatabasePath?.let { path -> listOf("", "-wal", "-shm", "-journal").forEach { File(path + it).delete() } }
    }

    private var fileDatabase: BreakfastDatabase? = null
    private var fileDatabasePath: String? = null

    /** A store on [database], initialised and filled with the synthetic year in one import transaction. */
    private suspend fun populated(database: BreakfastDatabase): BreakfastStore = BreakfastStore(database).apply {
        initialize()
        replaceDiary(
            DiaryRows(
                foods = year.foods.map { it.toEntity() },
                aliases = year.foods.flatMap { it.toAliasEntities() },
                conversions = year.foods.flatMap { it.toConversionEntities() },
                preferences = year.preferences.map { it.toEntity() },
                entries = year.diary.map { it.toEntity() },
            )
        )
    }

    /**
     * The same year in a throwaway FILE database in the test's cache dir (a unique name, never `breakfast.db`), so a
     * write pays its real journal and fsync cost. Closed and deleted in [tearDown].
     */
    private suspend fun fileStore(): Pair<BreakfastDatabase, BreakfastStore> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = File(context.cacheDir, "bench-${UUID.randomUUID()}.db").path
        check(!path.endsWith("breakfast.db"))
        fileDatabasePath = path
        val opened = Room.databaseBuilder(context, BreakfastDatabase::class.java, path).build()
        fileDatabase = opened
        return opened to populated(opened)
    }

    /** (a) The Log page's off-thread draft for each prefix of three realistic inputs, a new planner per typed pass. */
    @Test
    fun a_perKeystrokeLogDraft() {
        val pin = year.foods.first()
        engine.planner() // The matcher is built once before the Log opens, as in the app.
        keystrokeSamples(SyntheticYear.DRAFT_INPUTS, passes = 2) { engine.planner(passPins("warm", it, pin)) }
        val all = mutableListOf<Long>()
        SyntheticYear.DRAFT_INPUTS.forEachIndexed { index, input ->
            val samples = keystrokeSamples(listOf(input), passes = PASSES) { engine.planner(passPins("in$index", it, pin)) }
            all += samples
            report(BenchStats("a draft-per-keystroke input${index + 1} (${input.length} keys)", samples))
        }
        report(BenchStats("a draft-per-keystroke all", all))
    }

    /** (b) The save path's planning for a 4-part input: a new planner (after a write) and the Log's warm one. */
    @Test
    fun b_savePathPlanning() {
        val pin = year.foods.first()
        engine.planner()
        var pass = 0
        report(bench("b save-plan new planner", warmup = WARMUP, iterations = ITERATIONS) {
            engine.planner(passPins("save", pass++, pin)).plan(SyntheticYear.SAVE_INPUT)
        })
        val warm = engine.planner()
        logDraftWork(warm, SyntheticYear.SAVE_INPUT)
        report(bench("b save-plan warm planner (after the draft)", warmup = WARMUP, iterations = ITERATIONS) {
            warm.plan(SyntheticYear.SAVE_INPUT)
        })
    }

    /** (c) A single-entry write: the transaction plus the full snapshot reload every write does now. */
    @Test
    fun c_storeWriteAndReload() = runBlocking {
        val (database, store) = fileStore()
        report("c on a file database (journal ${database.openHelper.writableDatabase.let { if (it.isWriteAheadLoggingEnabled) "WAL" else "rollback" }})")
        var next = 0
        val written = mutableListOf<String>()
        report(bench("c saveEntries(1 entry) incl. full reload", warmup = WARMUP, iterations = ITERATIONS) {
            val entry = benchEntry(next++)
            store.saveEntries(listOf(entry))
            written += entry.id
        })
        val toDelete = ArrayDeque(written)
        report(bench("c deleteEntry incl. full reload", warmup = WARMUP, iterations = ITERATIONS) {
            store.deleteEntry(toDelete.removeFirst())
        })
        // The reload's parts, read the way the store's snapshot reads them.
        report(bench("c reload part: entries all()+map", warmup = WARMUP, iterations = ITERATIONS) {
            database.mealEntryDao().all().map { it.toDomain() }
        })
        report(bench("c reload part: foods+aliases+conversions all()", warmup = WARMUP, iterations = ITERATIONS) {
            database.foodDao().all(); database.foodAliasDao().all(); database.servingConversionDao().all()
        })
    }

    /** (d) The matcher index build, and the engine path end to end: does an entry-only write rebuild it? */
    @Test
    fun d_matcherBuildAndRebuildAfterEntryWrite() = runBlocking {
        report(bench("d matcher-build foods=${foodsAvailableForLogging(store.foods()).size}", warmup = WARMUP, iterations = ITERATIONS) {
            FoodMatcher(foodsAvailableForLogging(store.foods()))
        })
        val pin = year.foods.first()
        engine.planner()
        var pass = 0
        report(bench("d first draft, no write (new planner, matcher kept)", warmup = WARMUP, iterations = ITERATIONS) {
            logDraftWork(engine.planner(passPins("nowrite", pass++, pin)), "2 eggs")
        })
        var rebuilds = 0
        var next = 1_000
        val samples = (0 until WARMUP + ITERATIONS).map {
            val matcherBefore = engine.matcherForTesting()
            store.saveEntries(listOf(benchEntry(next++)))
            // A new text each time, as the next draft after a log is: the planner's part cache cannot answer it.
            val (_, ns) = timed { logDraftWork(engine.planner(), "2 eggs, $next g rolled oats") }
            if (engine.matcherForTesting() !== matcherBefore) rebuilds++
            ns
        }.drop(WARMUP)
        report(BenchStats("d first draft after an entry-only write", samples))
        report("d entry-only write rebuilds the matcher: ${rebuilds == WARMUP + ITERATIONS} (rebuilt $rebuilds of ${WARMUP + ITERATIONS})")
    }

    /** (e) The day-total fold for the busiest day, and everything Daily computes in composition for one day. */
    @Test
    fun e_dayTotalsAndDailyData() {
        val busiest = store.entries().filter { it.dayKey == year.busiestDay }
        report(bench("e day-totals fold busiest day (${busiest.size} entries)", warmup = 20, iterations = ITERATIONS * 5) {
            dayTotalsWork(busiest)
        })
        report(bench("e daily-data one day (filters ${store.entries().size} entries, ${store.foods().size} foods)", warmup = 20, iterations = ITERATIONS * 5) {
            dailyDataWork(store.entries(), store.foods(), year.busiestDay)
        })
    }

    /** (f) The Library list for a three-letter query, as refreshLibrary computes it (filter, then recency order). */
    @Test
    fun f_libraryFoodsMatching() = runBlocking {
        for (query in listOf("chi", "дом")) {
            var shown = 0
            report(bench("f library foodsMatching(\"$query\") + recency order", warmup = 5, iterations = ITERATIONS * 2) {
                shown = libraryRecencyOrder(store.foodsMatching(query), store.entries()).size
            })
            report("f library \"$query\" shows $shown foods")
        }
    }

    private fun benchEntry(index: Int): MealEntry = year.diary[index % year.diary.size].copy(id = "bench-write-$index")

    /** Pins that never match typed text but make the engine hand out a new planner (same matcher) for each pass. */
    private fun passPins(tag: String, pass: Int, food: FoodRecord): Map<String, FoodRecord> = mapOf("\u0000$tag-$pass" to food)

    private fun report(stats: BenchStats) = report(stats.line())

    private fun report(line: String) {
        Log.i(TAG, line)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendStatus(BENCH_STATUS, Bundle().apply {
            putString(TAG, line)
            putString("stream", "$TAG $line\n")
        })
        instrumentation.addResults(Bundle().apply { putString("$TAG.${line.substringBefore(" median").take(60)}", line) })
    }

    private fun requested(): Boolean {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("bench") == "true" || args.getString("class").orEmpty().contains("BreakfastBenchmark")
    }

    private companion object {
        const val TAG = "BreakfastBench"
        /** Not a JUnit status code (1, 0, -1, -2, -3, -4): `am instrument -r` prints it as a plain status line. */
        const val BENCH_STATUS = 2
        const val PASSES = 10
        /** At least 10, so the JIT and SQLite caches are warm before the timed runs. */
        const val WARMUP = 10
        const val ITERATIONS = 20
    }
}
