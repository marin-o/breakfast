package mk.bosa.breakfast

import kotlinx.coroutines.CoroutineDispatcher
import java.lang.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * An IO dispatcher the test drives: work sent to it waits until [runAll], so a test can act while a request is "in
 * flight". With an unconfined caller, the caller's code after `withContext` runs inside [runAll]. While not
 * [holding], work runs at once in the caller, as on an unconfined dispatcher.
 */
internal class QueueDispatcher(var holding: Boolean = true) : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()
    val pending: Int get() = queue.size

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = holding

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (holding) queue.addLast(block) else block.run()
    }

    fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
}
