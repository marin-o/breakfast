package com.iron.breakfast.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestSearchTest {
    private val search = LatestSearch<String>(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, Dispatchers.Unconfined)
    private val landed = mutableListOf<String>()

    @Test fun onlyTheNewestSearchLands() {
        val slow = CompletableDeferred<String>()
        search.launch({ slow.await() }, { true }) { landed += it }
        search.launch({ "new" }, { true }) { landed += it }
        slow.complete("old")
        assertEquals(listOf("new"), landed)
    }

    @Test fun anAnswerNoLongerWantedOrCancelledDoesNotLand() {
        search.launch({ "closed" }, { false }) { landed += it }
        val slow = CompletableDeferred<String>()
        search.launch({ slow.await() }, { true }) { landed += it }
        search.cancel()
        slow.complete("late")
        assertEquals(emptyList<String>(), landed)
    }
}
