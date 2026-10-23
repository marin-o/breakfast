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

    /** A food may have aliases in either script, but a Cyrillic-named food never gets a Latin alias (Домашно млеко). */
    @Test fun noLatinAliasOnACyrillicNamedFood() {
        val offenders = FoodCatalog.fixtures.filter { isCyrillic(it.name) }.flatMap { food ->
            food.aliases.filterNot { isCyrillic(it) }.map { "${food.name} / $it" }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun cyrillicInputFindsItsCyrillicAliasAndLatinNameStillFindsLatin() {
        assertEquals("banana", matcher.best("банана")?.food?.id)
        assertEquals("egg", matcher.best("јајца")?.food?.id)
        assertEquals("banana", matcher.best("banana")?.food?.id)
    }

    @Test fun cyrillicButFindsChickenThigh() {
        assertEquals("usda_2646171_chicken_thigh_raw", planner.resolve("пилешки бут")?.food?.id)
        assertTrue(matcher.startingWith("бут").any { it.food.id == "usda_2646171_chicken_thigh_raw" })
    }

    @Test fun latinBuDoesNotFindChickenThigh() {
        assertFalse(matcher.suggestions("bu").any { it.food.id == "usda_2646171_chicken_thigh_raw" })
        assertFalse(planner.draft("2 eggs bu").suggestions.any { it.food.id == "usda_2646171_chicken_thigh_raw" })
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
