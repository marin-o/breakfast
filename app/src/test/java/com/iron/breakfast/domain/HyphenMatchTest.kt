package com.iron.breakfast.domain

import com.iron.breakfast.testFood
import com.iron.breakfast.data.foodMatchesQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A hyphen between letters is a word break in food names and what is typed, within one script (owner, 2026-10-29). */
class HyphenMatchTest {
    private fun food(id: String, name: String) = testFood(
        id, name, category = "Personal food", unit = MeasureUnit.MILLILITER, values = NutritionValues(1.0, 0.0, 0.0, 0.0),
        defaultAmount = 330.0, defaultUnit = MeasureUnit.MILLILITER, personalOverride = true,
    )

    private val zero = food("custom-zero", "Coca Cola Zero Sugar")
    private val classic = food("custom-classic", "Coca-Cola")
    private val kokaKola = food("custom-koka", "Кока-Кола")
    private val kokaSpaced = food("custom-koka-spaced", "Кока Кола Зеро")
    private val koka = food("custom-k", "Кока")
    private val cola = food("custom-c", "Cola")
    private val vitaminka = food("custom-vitaminka", "Vitaminka-Витаминка")
    private val matcher = FoodMatcher(FoodCatalog.fixtures + zero + classic + kokaKola + kokaSpaced + koka + cola + vitaminka)

    @Test fun theLibrarySearchReadsAHyphenAsABreakOnBothSides() {
        assertTrue(foodMatchesQuery(zero, "coca-cola zero sugar"))
        assertTrue(foodMatchesQuery(zero, "Coca-Cola"))
        assertTrue(foodMatchesQuery(classic, "coca cola"))
        assertTrue(foodMatchesQuery(kokaSpaced, "кока-кола"))
        assertTrue(foodMatchesQuery(kokaKola, "кока кола"))
        assertFalse(foodMatchesQuery(zero, "pepsi"))
    }

    @Test fun theLogBarAndTheResolverMatchAcrossAHyphen() {
        assertEquals("custom-zero", matcher.best("coca-cola zero sugar")!!.food.id)
        assertTrue(matcher.isExactName("coca cola"))
        assertEquals("custom-classic", matcher.best("coca cola")!!.food.id)
        assertEquals("custom-koka", matcher.best("кока кола")!!.food.id)
        assertEquals("custom-koka-spaced", matcher.best("кока-кола зеро")!!.food.id)
        val logged = LogPlanner(matcher).plan("330 ml coca-cola zero sugar").entries.single()
        assertEquals("custom-zero" to 330.0, logged.food.id to logged.amount)
    }

    /**
     * A hyphen between two scripts is a break too, as the matcher's words always split it: each word is in one script
     * and is matched within it, so "Vitaminka-Витаминка" is found typed as it is, and a word that mixes scripts with no
     * hyphen still matches nothing.
     */
    @Test fun aHyphenBetweenScriptsSplitsIntoWordsOfOneScript() {
        assertEquals("vitaminka витаминка", hyphensAsBreaks("vitaminka-витаминка"))
        assertEquals("custom-vitaminka", matcher.best("Vitaminka-Витаминка")!!.food.id)
        assertEquals("custom-vitaminka", matcher.best("vitaminka")!!.food.id)
        assertEquals("custom-vitaminka", matcher.best("витаминка")!!.food.id)
        assertTrue(foodMatchesQuery(vitaminka, "Vitaminka-Витаминка"))
        assertTrue(foodMatchesQuery(vitaminka, "vitaminka витаминка"))
        assertTrue("a mixed word with no hyphen matches nothing", matcher.suggestions("vitaminkaвитаминка").isEmpty())
        assertTrue(matcher.suggestions("vitaminкa").isEmpty())
    }

    /** A hyphen still being typed ("coca-") is a break: the search keeps finding what it found. */
    @Test fun aHyphenAtTheEndOfAQueryIsABreak() {
        assertTrue(foodMatchesQuery(classic, "coca-"))
        assertTrue(foodMatchesQuery(zero, "coca-"))
        assertTrue(foodMatchesQuery(classic, "-cola"))
        assertTrue(foodMatchesQuery(zero, "coca- cola"))
    }

    /** Quantities are not names: a hyphen by a digit stays as it is, so ranges read as before. */
    @Test fun aHyphenByADigitIsLeftAlone() {
        assertEquals("1-2 eggs", hyphensAsBreaks("1-2 eggs"))
        assertEquals("b-12", hyphensAsBreaks("b-12"))
        assertEquals("coca cola", hyphensAsBreaks("coca-cola"))
    }
}
