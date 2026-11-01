package com.bosa.breakfast.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A search that may be typed over: the newest one runs, and only its answer lands, while it is [launch]'s `wanted`. */
internal class LatestSearch<T>(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val main: CoroutineDispatcher,
) {
    private var job: Job? = null
    private var generation = 0L

    /** Stops the search running; its answer no longer applies. */
    fun cancel() {
        job?.cancel()
        generation++
    }

    fun launch(load: suspend () -> T, wanted: () -> Boolean, land: (T) -> Unit) {
        cancel()
        val mine = generation
        job = scope.launch(io) {
            val answer = load()
            withContext(main) { if (mine == generation && wanted()) land(answer) }
        }
    }
}
