package mk.bosa.breakfast.bench

import java.util.Locale

/** Timings of one measured step, in nanoseconds; [line] is the one-line report. */
class BenchStats(val name: String, samplesNs: List<Long>) {
    private val sorted = samplesNs.sorted()
    val count: Int = sorted.size
    val medianNs: Long = percentileOf(sorted, 0.50)
    val p90Ns: Long = percentileOf(sorted, 0.90)
    val maxNs: Long = sorted.lastOrNull() ?: 0L

    val medianMs: Double get() = medianNs / 1e6
    val p90Ms: Double get() = p90Ns / 1e6

    fun line(): String = String.format(
        Locale.ROOT, "%s median=%.3fms p90=%.3fms max=%.3fms n=%d", name, medianMs, p90Ms, maxNs / 1e6, count,
    )
}

/** The nearest-rank [fraction] percentile of [sorted] (ascending); 0 for no samples. */
fun percentileOf(sorted: List<Long>, fraction: Double): Long {
    if (sorted.isEmpty()) return 0L
    val rank = kotlin.math.ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

/** Runs [block] [warmup] times untimed, then [iterations] times timed with [System.nanoTime]. */
inline fun bench(name: String, warmup: Int, iterations: Int, block: () -> Unit): BenchStats {
    repeat(warmup) { block() }
    val samples = LongArray(iterations)
    for (i in 0 until iterations) {
        val start = System.nanoTime()
        block()
        samples[i] = System.nanoTime() - start
    }
    return BenchStats(name, samples.toList())
}

/** Times one call of [block]: its result and how long it took. */
inline fun <T> timed(block: () -> T): Pair<T, Long> {
    val start = System.nanoTime()
    val result = block()
    return result to (System.nanoTime() - start)
}
