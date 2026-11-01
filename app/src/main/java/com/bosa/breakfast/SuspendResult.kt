package com.bosa.breakfast

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for suspend work (MOB-027): a failure becomes a [Result], but a [CancellationException] is rethrown,
 * so a cancelled coroutine is never reported as a failed save. Nothing chained after it runs on a cancel; the scope
 * that was cancelled (the ViewModel's) is gone with its busy flag and notices. The store's user-confirmed writes are
 * NonCancellable once started, so a cancel never leaves a write half done either.
 */
internal suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}
