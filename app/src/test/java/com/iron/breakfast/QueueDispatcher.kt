package com.iron.breakfast

import kotlinx.coroutines.CoroutineDispatcher
import java.lang.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * An IO dispatcher the test drives: work sent to it waits until [runAll], so a test can act while a request is "in
 * flight". With an unconfined caller, the caller's code after `withContext` runs inside [runAll].
 */
internal class QueueDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()
    val pending: Int get() = queue.size

    override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }

    fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
}
