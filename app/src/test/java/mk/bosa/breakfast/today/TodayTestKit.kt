package mk.bosa.breakfast.today

import mk.bosa.breakfast.app.DiaryChange
import mk.bosa.breakfast.app.DiarySession
import mk.bosa.breakfast.app.DiarySource
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.domain.Recipe
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal fun entry(id: String, approximate: Boolean = false, food: String = "Egg") =
    MealEntry(id, "f-$id", food, 1.0, MeasureUnit.PIECE, 50.0, NutritionValues(70.0, 6.0, 0.5, 5.0), "1 piece", approximate, 0L)

/**
 * A clock the test moves, and the one sleep an owner's timer waits in. [advance] moves the clock and wakes that
 * sleep; with [Dispatchers.Unconfined] the woken timer runs at once, up to its next sleep.
 */
internal class FakeTime(var now: Long = 1_000L) {
    private var waiting: CancellableContinuation<Unit>? = null
    /** Every duration a timer asked to sleep, in order. */
    val sleeps = mutableListOf<Long>()
    val clock: () -> Long = { now }
    val sleep: suspend (Long) -> Unit = { ms -> sleeps += ms; suspendCancellableCoroutine<Unit> { waiting = it } }
    val sleeping: Boolean get() = waiting?.isActive == true

    fun advance(ms: Long) {
        now += ms
        waiting?.let { waiting = null; if (it.isActive) it.resume(Unit) }
    }
}

/** An open [DiarySession] over rows the test sets, on an unconfined scope, recording what it publishes. */
internal class OpenDiary {
    var rows: List<MealEntry> = emptyList()
    var foods: List<FoodRecord> = emptyList()
    var recipes: List<Recipe> = emptyList()
    var entryReads = 0
    val published = mutableListOf<DiaryChange>()
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    val session = DiarySession(
        source = object : DiarySource {
            override suspend fun open() {}
            override fun foods(): List<FoodRecord> = foods
            override fun entries(): List<MealEntry> { entryReads++; return rows }
            override fun recipes(): List<Recipe> = recipes
            override suspend fun reloadIfStale() = false
        },
        scope = scope,
        onOpenFailed = { throw it },
    )

    init {
        session.open {}
        session.onChange { published += it }
        entryReads = 0
    }

    /** A committed write that publishes [change], as a feature's write would. */
    fun commit(change: DiaryChange) { checkNotNull(session.write(change) {}) }
}
