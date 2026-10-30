package com.iron.breakfast.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * At most [max] takers in any rolling [windowMs]: one more waits its turn rather than fails. Takers go one at a time, a
 * cancel stops the wait, and a taker that fails later has still used its slot; nothing here retries.
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
            while (taken.isNotEmpty() && now - taken.first() >= windowMs) taken.removeFirst()
            if (taken.size < max) { taken.addLast(now); return@withLock }
            sleep(taken.first() + windowMs - now)
        }
    }
}
