package com.iron.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scripts never cross, aliases share their name's script, and a food's category is never matched. */
class ScriptAndCategoryTest {
    private val matcher = FoodMatcher(FoodCatalog.fixtures)
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))
    private val homeMilk = "custom_home_milk"

    private fun isCyrillic(text: String) = text.any { it in '\u0400'..'\u04FF' }

    @Test fun everyAliasIsInTheScriptOfItsName() {
        val offenders = FoodCatalog.fixtures.flatMap { food ->
            food.aliases.filter { isCyrillic(it) != isCyrillic(food.name) }.map { "${food.name} / $it" }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun milkDoesNotOfferHomeMilk() {
        assertFalse(matcher.suggestions("milk").any { it.food.id == homeMilk })
        assertFalse(planner.draft("milk").suggestions.any { it.food.id == homeMilk })
        assertFalse(planner.resolve("milk")?.ranked.orEmpty().any { it.food.id == homeMilk })
    }

    @Test fun kiseloMlekoDoesNotOfferHomeMilk() {
        assertFalse(planner.resolve("kiselo mleko")?.ranked.orEmpty().any { it.food.id == homeMilk })
        assertFalse(planner.draft("kiselo mleko", typing = false).choices.any { it.food.id == homeMilk })
    }

    @Test fun cyrillicMlekoOffersHomeMilk() {
        assertTrue(planner.resolve("млеко")?.ranked.orEmpty().any { it.food.id == homeMilk })
        assertTrue(matcher.suggestions("млеко").any { it.food.id == homeMilk })
    }

    @Test fun aCategoryWordIsNeverMatched() {
        val zebra = FoodRecord(
            id = "apple", name = "Apple", aliases = emptyList(), category = "Zebra stripes", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(1.0, MeasureUnit.PIECE, NutritionValues(60.0, 0.3, 15.0, 0.2)),
            defaultAmount = 1.0, defaultUnit = MeasureUnit.PIECE,
        )
        val only = FoodMatcher(listOf(zebra))
        assertTrue(only.suggestions("zebra").isEmpty())
        assertTrue(only.suggestions("stripes").isEmpty())
        assertEquals("apple", only.best("apple")?.food?.id)
    }
}
