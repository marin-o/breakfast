package mk.bosa.breakfast.search

import mk.bosa.breakfast.domain.FoodRecord
import org.junit.Assert.assertEquals
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

    private fun ranked(query: String, vararg names: String) = rankOffResults(query, names.map(::product)).map { it.name }

    @Test
    fun aProductWithTheWordComesBeforeOnesWithoutIt() {
        assertEquals(
            listOf("Wheat flour sourdough bread", "Strong White Bread Flour", "Mission Carb Balance Flour Tortilla Wraps", "Flour tortillas", "White Sourdough", "Farine de ble"),
            ranked(
                "flour", "White Sourdough", "Farine de ble", "Wheat flour sourdough bread", "Strong White Bread Flour",
                "Mission Carb Balance Flour Tortilla Wraps", "Flour tortillas",
            ),
        )
    }

    @Test
    fun theOrderOffGaveStaysWithinAGroup() {
        assertEquals(listOf("Plain Flour", "Rye Flour", "Oat Bran"), ranked("flour", "Plain Flour", "Oat Bran", "Rye Flour"))
    }

    @Test
    fun aPluralAndEveryWordCountAndNothingIsDropped() {
        assertEquals(listOf("Corn Flour", "Corn Bread", "Flour Wraps"), ranked("corn flours", "Corn Bread", "Flour Wraps", "Corn Flour"))
        assertEquals(listOf("A", "B"), ranked("", "A", "B"))
    }

    @Test
    fun everyWordTogetherComesBeforeEveryWordApartBeforeSomeWords() {
        assertEquals(
            listOf("Extra Virgin Olive Oil", "Olive Oils", "Oil, Olive Pomace", "Oil Spray", "Olive Tapenade", "Butter"),
            ranked("olive oil", "Oil Spray", "Butter", "Oil, Olive Pomace", "Olive Tapenade", "Extra Virgin Olive Oil", "Olive Oils"),
        )
    }

    @Test
    fun theWordsMustBeWholeAndInTheTypedOrderToCountAsAPhrase() {
        assertEquals(
            listOf("Whole Olive Oil", "Oil of Olive", "Olives in Oil"),
            ranked("olive oil", "Oil of Olive", "Olives in Oil", "Whole Olive Oil"),
        )
    }
}
