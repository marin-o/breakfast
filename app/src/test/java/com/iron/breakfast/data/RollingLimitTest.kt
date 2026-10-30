package com.iron.breakfast.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RollingLimitTest {
    private var now = 0L
    private val slept = mutableListOf<Long>()
    private val limit = RollingLimit(OFF_SEARCHES_PER_MINUTE, 60_000L, { now }, { slept += it; now += it })

    @Test fun tenGoAtOnceAndTheEleventhWaitsForTheFirstToAge() = runBlocking {
        repeat(10) { now += 100; limit.take() }
        assertEquals(emptyList<Long>(), slept)
        limit.take()
        assertEquals(listOf(59_100L), slept)
    }

    @Test fun aFailedPrimaryWithItsFallbackUsesOneSlot() = runBlocking {
        var opened = 0
        val client = OpenFoodFactsClient(
            connectionFactory = { opened++; null },
            searchLimit = RollingLimit(1, 60_000L, { now }, { slept += it; now += it }),
        )
        client.search("polenta")
        assertEquals("primary and fallback", 2, opened)
        assertEquals(emptyList<Long>(), slept)
    }
}
