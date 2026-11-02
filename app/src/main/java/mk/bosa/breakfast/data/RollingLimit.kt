package mk.bosa.breakfast.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * At most [max] takers in any rolling [windowMs]: one more waits its turn rather than fails. Takers go one at a time, a
 * cancel stops the wait, and a taker that fails later has still used its slot; nothing here retries. [tryTake] is the way
 * in that never waits.
 */
class RollingLimit(
    private val max: Int,
    private val windowMs: Long,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private val taken = ArrayDeque<Long>()

    suspend fun take() = mutex.withLock {
        while (true) {
            val now = nowMs()
            if (takeAt(now)) return@withLock
            sleep(taken.first() + windowMs - now)
        }
    }

    /** A slot if one is free now; false when none is, or when another taker is waiting for one (it holds the lock). */
    fun tryTake(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            return takeAt(nowMs())
        } finally {
            mutex.unlock()
        }
    }

    private fun takeAt(now: Long): Boolean {
        while (taken.isNotEmpty() && now - taken.first() >= windowMs) taken.removeFirst()
        if (taken.size >= max) return false
        taken.addLast(now)
        return true
    }
}
