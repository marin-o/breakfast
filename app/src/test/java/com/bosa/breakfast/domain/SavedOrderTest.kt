package com.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Entries saved together keep their typed order: each gets its own saved time, in order. */
class SavedOrderTest {
    @Test fun entriesSavedTogetherSortInTheirTypedOrder() {
        val now = 1_000_000L
        val times = (0 until 4).map { orderedSavedAt(now, it) }
        assertEquals(times.sorted(), times)
        assertEquals(times.toSet().size, times.size)
        assertEquals(now, times.first())
    }
}
