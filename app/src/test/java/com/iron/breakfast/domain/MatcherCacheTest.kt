package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** MOB-028: the matcher is built once per foods list object, so a write that keeps the list keeps the matcher. */
class MatcherCacheTest {
    @Test fun theSameFoodsListKeepsTheMatcherAndANewOneRebuildsIt() {
        var builds = 0
        val cache = MatcherCache { builds++; FoodMatcher(it) }
        val foods = TestFoods.fixtures.toList()
        val first = cache.matcherFor(foods)
        assertSame(first, cache.matcherFor(foods))
        assertEquals(1, builds)

        val edited = foods.toList()
        assertNotSame(first, cache.matcherFor(edited))
        assertEquals(2, builds)
    }

    @Test fun invalidateRebuildsEvenForTheSameList() {
        val cache = MatcherCache()
        val foods = TestFoods.fixtures
        val first = cache.matcherFor(foods)
        cache.invalidate()
        assertNotSame(first, cache.matcherFor(foods))
    }
}
