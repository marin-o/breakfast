package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Latin matches only Latin and Cyrillic only Cyrillic, in every tier; a word that mixes the two matches nothing. */
class MixedScriptTest {
    private val matcher = FoodMatcher(FoodCatalog.fixtures)
    private val planner = LogPlanner(matcher)

    /** "banana" with a Cyrillic а in second place, as a bilingual keyboard types it. */
    private val mixed = "bаnana"

    @Test fun scriptsAreClassifiedOnce() {
        assertEquals(TextScript.LATIN, scriptOf("banana"))
        assertEquals(TextScript.LATIN, scriptOf("ćevapi"))
        assertEquals(TextScript.CYRILLIC, scriptOf("банана"))
        assertEquals(TextScript.MIXED, scriptOf(mixed))
        assertEquals(TextScript.NONE, scriptOf("2.8%"))
    }

    @Test fun aMixedWordMatchesNothing() {
        assertTrue(matcher.suggestions(mixed).isEmpty())
        val plan = planner.plan("1 $mixed")
        assertTrue(plan.entries.isEmpty())
        assertEquals(listOf("1 $mixed"), plan.unresolved)
    }

    @Test fun aMixedFragmentSuggestsNothing() {
        assertTrue(matcher.startingWith("bаn").isEmpty())
    }

    @Test fun aLatinWordStillResolves() {
        assertEquals("banana", planner.plan("1 banana").entries.single().food.id)
    }

    @Test fun aCyrillicWordResolvesOnlyThroughCyrillicNames() {
        assertEquals("banana", planner.plan("1 банана").entries.single().food.id)
        matcher.suggestions("банана", limit = 20).forEach { ranked ->
            assertTrue(ranked.food.name, ranked.food.allNames().any { scriptOf(it) == TextScript.CYRILLIC })
        }
    }
}
