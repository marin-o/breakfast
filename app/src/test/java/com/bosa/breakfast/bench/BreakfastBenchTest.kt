package com.bosa.breakfast.bench

import com.bosa.breakfast.domain.FoodMatcher
import com.bosa.breakfast.domain.LogPlanner
import com.bosa.breakfast.domain.MatcherCache
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression check for MOB-028 (a, d) on the synthetic year: the per-keystroke Log draft and the matcher index
 * build. It reports numbers (stdout, prefixed "BreakfastBench") and asserts only thresholds far above them, so a
 * loaded build machine does not fail it; the device benchmark (androidTest `BreakfastBenchmark`) is the measurement.
 */
class BreakfastBenchTest {
    private val year = SyntheticYear()
    private val logging = year.foods
    private val preferences = year.preferences.associateBy { it.foodId }

    private fun report(stats: BenchStats) = println("BreakfastBench jvm ${stats.line()}")

    @Test fun perKeystrokeLogDraft() {
        val matcher = FoodMatcher(logging)
        // Warm-up: the JIT and the regexes, not the planner (each pass below starts a new one).
        keystrokeSamples(SyntheticYear.DRAFT_INPUTS, passes = 2) { LogPlanner(matcher, preferences) }
        val stats = BenchStats("draft-per-keystroke", keystrokeSamples(SyntheticYear.DRAFT_INPUTS, passes = 5) { LogPlanner(matcher, preferences) })
        report(stats)
        assertTrue("per-keystroke draft median ${stats.medianMs} ms", stats.medianMs < GENEROUS_KEYSTROKE_MS)
    }

    @Test fun matcherIndexBuild() {
        val stats = bench("matcher-build foods=${logging.size}", warmup = 3, iterations = 10) { FoodMatcher(logging) }
        report(stats)
        assertTrue("matcher build median ${stats.medianMs} ms", stats.medianMs < GENEROUS_BUILD_MS)
    }

    /**
     * The first draft after a write, through [MatcherCache] as the engine does it: an entry write now keeps the foods
     * list object (matcher kept); a food write hands over a new one (matcher rebuilt), as every write used to.
     */
    @Test fun firstDraftAfterAWrite() {
        val foods = year.foods
        val cache = MatcherCache()
        var n = 0
        val kept = bench("first-draft entry write (matcher kept)", warmup = 5, iterations = 20) {
            logDraftWork(LogPlanner(cache.matcherFor(foods), preferences), "2 eggs, ${n++} g rolled oats")
        }
        val rebuilt = bench("first-draft food write (matcher rebuilt)", warmup = 5, iterations = 20) {
            logDraftWork(LogPlanner(cache.matcherFor(foods.toList()), preferences), "2 eggs, ${n++} g rolled oats")
        }
        report(kept)
        report(rebuilt)
        assertTrue("first draft median ${kept.medianMs} ms", kept.medianMs < GENEROUS_BUILD_MS)
    }

    private companion object {
        /** Tens of times what a keystroke takes on a desktop JVM: only a pathological regression trips these. */
        const val GENEROUS_KEYSTROKE_MS = 250.0
        const val GENEROUS_BUILD_MS = 2_000.0
    }
}
