package mk.bosa.breakfast.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Open Food Facts search's rule (`SearchRules.kt`). */
class SearchRulesTest {
    @Test
    fun staleOffResponsesRequireCurrentOpenQueryAndGeneration() {
        assertTrue(shouldApplyOffSearchResult(3, 3, "oats", "oats", true))
        assertFalse(shouldApplyOffSearchResult(2, 3, "oats", "oats", true))
        assertFalse(shouldApplyOffSearchResult(3, 3, "oats", "milk", true))
        assertFalse(shouldApplyOffSearchResult(3, 3, "oats", "oats", false))
    }
}
