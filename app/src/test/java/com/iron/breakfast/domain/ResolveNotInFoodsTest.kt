package com.iron.breakfast.domain

import com.iron.breakfast.log.rowStillInDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A "Not in your foods" part resolved through the library: its text is rewritten to the picked food, and the picked food is pinned. */
class ResolveNotInFoodsTest {
    private val picked = FoodRecord(
        id = "off-pesto-ml", name = "Branded pesto", aliases = emptyList(), category = "Open Food Facts",
        origin = FoodOrigin.GENERIC,
        nutrition = NutritionBasis(100.0, MeasureUnit.MILLILITER, NutritionValues(400.0, 5.0, 8.0, 38.0)),
        defaultAmount = 100.0,
        defaultUnit = MeasureUnit.MILLILITER,
    )
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))

    @Test fun aPickedPartKeepsItsTypedAmount() {
        assertEquals("Branded pesto 200ml", planner.pickedText("kiselo mleko 200ml", picked))
    }

    @Test fun aPickedPartWithoutAnAmountIsJustTheFoodName() {
        assertEquals("Branded pesto", planner.pickedText("kiselo mleko", picked))
    }

    @Test fun aLeadingAmountIsKeptAfterTheFoodName() {
        assertEquals("Branded pesto 200ml", planner.pickedText("200ml kiselo mleko", picked))
    }

    @Test fun theLibraryOpensWithTheFoodTextOnly() {
        assertEquals("kiselo mleko", planner.libraryQueryFor("kiselo mleko 200g"))
    }

    @Test fun onlyTheMiddlePartOfThreeIsRewritten() {
        val input = "2 eggs, kiselo mleko 200ml, banana"
        val middle = planner.segments(input)[1]
        assertEquals("kiselo mleko 200ml", middle.text)
        val rewritten = replaceDraftPart(input, middle.start, middle.end, planner.pickedText(middle.text, picked))
        assertEquals("2 eggs, Branded pesto 200ml, banana", rewritten)
    }

    @Test fun aPinnedPartResolvesToThePickedFood() {
        val pinned = LogPlanner(FoodMatcher(FoodCatalog.fixtures), pins = mapOf("Branded pesto 200ml" to picked))
        val entry = pinned.plan("Branded pesto 200ml").entries.single()
        assertEquals("off-pesto-ml", entry.food.id)
        assertEquals(200.0, entry.amount, 0.0)
        assertEquals(MeasureUnit.MILLILITER, entry.unit)
    }

    @Test fun aPinOverridesWhatTheTextWouldOtherwiseMatch() {
        val pinned = LogPlanner(FoodMatcher(FoodCatalog.fixtures), pins = mapOf("kiselo mleko 200ml" to picked))
        assertEquals("off-pesto-ml", pinned.plan("kiselo mleko 200ml").entries.single().food.id)
        assertTrue(planner.plan("kiselo mleko 200ml").entries.none { it.food.id == "off-pesto-ml" })
    }

    /** MOB-025 (A4-10): a row from the draft before a keystroke is not resolved against the text typed since. */
    @Test fun aRowWhoseSpanNoLongerHoldsItsTextIsIgnored() {
        val shown = "2 eggs, zzqx 100g"
        val row = planner.draft(shown, typing = false).rows.filterIsInstance<DraftRow.Unresolved>().single()
        assertTrue(rowStillInDraft(shown, row))
        // One more letter typed before the next draft was ready: the same span now holds "zzqxa 100".
        assertFalse(rowStillInDraft("2 eggs, zzqxa 100g", row))
        assertFalse(rowStillInDraft("2 eggs", row))
        assertFalse(rowStillInDraft(shown, DraftRow.Unresolved("zzqx 100g")))
    }

    @Test fun anUnresolvedRowCarriesItsSpanInTheInput() {
        val input = "2 eggs, zzqx 100g"
        val row = planner.draft(input, typing = false).rows.filterIsInstance<DraftRow.Unresolved>().single()
        assertNotNull(row)
        assertEquals("zzqx 100g", input.substring(row.start, row.end))
    }
}
