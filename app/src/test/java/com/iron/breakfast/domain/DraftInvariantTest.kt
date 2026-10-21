package com.iron.breakfast.domain

import com.iron.breakfast.draftLogPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant: text the user typed never vanishes. For any input, every part that is not a meal word ends up as a logged
 * entry, as an unresolved row, or as the active "which one?" group. Nothing else is shown.
 */
class DraftInvariantTest {
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures + listOf(
        FoodRecord(
            id = "bufu", name = "Buckwheat", aliases = emptyList(), category = "Grain", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(340.0, 12.0, 70.0, 3.0)),
            defaultAmount = 100.0, defaultUnit = MeasureUnit.GRAM,
        ),
        FoodRecord(
            id = "macadamia", name = "Macadamia", aliases = emptyList(), category = "Nut", origin = FoodOrigin.CUSTOM,
            nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(700.0, 8.0, 14.0, 75.0)),
            defaultAmount = 30.0, defaultUnit = MeasureUnit.GRAM,
        ),
    )))

    private val messy = listOf(
        "two eggs and a bananna", "kiselo mleko 200g 1 kg rolled oats", "banana 1 egg 2", "oats 60g milk 200ml",
        "100g buckwheat 1 tsp pb", "100g buckwheat 1 tsp pb 1 banana", "Mac and cheese", "M&M", "Ben & Jerry's",
        "apple", "lunch", "lunch, 1 banana", "lunch 1 banana", "dinner, 2 eggs", "snacks, 1 egg", "1 banana,",
        "1 banana, ", "bananna", "vanana", "2 egg 1", "egg and", "egg &", "1 banana; 2 eggs", "1 banana\n2 eggs",
        "3 eggs,1 banana", "pb, a banana", "a", "an", "a warm milk", "ten grain", "2x egg", "egg x2",
        "half a tbsp pb", "milk 3.2% 1 banana", "Protein 2 bar", "crunchy peanut butter 1 tbsp", "chicken and rice",
        "rice + beans", "egg & cheese", "kiselo mleko", "1 ban", "bread and butter", "2 ban", "xyzzy 2 qwerty",
        "nonsense and more nonsense", "Домашно млеко 200 ml", "kiselo mleko 2.8%, 1 banana", "   ", "",
    )

    private fun nonMeal(input: String) = planner.segments(input)
        .map { it.text }
        .filter { ShorthandLexicon.mealSlotOf(it) == null }

    @Test fun everyTypedPartIsEntryRowOrChoice() {
        val failures = messy.mapNotNull { input ->
            val draft = planner.draft(input)
            val loggedTexts = draft.logged.map { it.raw }
            val lost = nonMeal(input).filter { part ->
                part !in loggedTexts && part !in draft.unresolved && !(part == draft.activeText && draft.choices.isNotEmpty())
            }
            if (lost.isEmpty()) null else "[$input] lost: $lost"
        }
        assertEquals(emptyList<String>(), failures)
    }

    /** A choice group with one candidate is never preselected: a single clean match is an entry row instead. */
    @Test fun noSingleCandidateChoiceIsPreselectedForAnyInput() {
        val bad = (messy + listOf("milk", "egg", "bananna", "a bananna", "an egg", "oats", "1 ban")).filter { input ->
            val draft = planner.draft(input)
            draft.choices.size == 1 && draft.preselected
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test fun twoEggsAndABannanaShowsEggsAndBananaNotNothing() {
        val draft = planner.draft("two eggs and a bananna")
        assertEquals(listOf("egg", "banana"), draft.logged.map { it.food.id })
        assertEquals(listOf(2.0, 1.0), draft.logged.map { it.amount })
        assertTrue(draft.unresolved.isEmpty())
        assertEquals(draft.logged.map { it.food.id }, draftLogPlan("two eggs and a bananna", planner).entries.map { it.food.id })
    }

    @Test fun anUnmatchedActivePartIsAnUnresolvedRow() {
        val draft = planner.draft("xyzzy 2 qwerty")
        assertEquals(listOf("xyzzy 2 qwerty"), draft.unresolved)
        assertTrue(draft.logged.isEmpty())
    }
}

class DraftRowOrderTest {
    private val planner = LogPlanner(FoodMatcher(FoodCatalog.fixtures))

    /** The rows follow the order the parts were typed: each row's text is a later part than the one before. */
    @Test fun rowsAreInTypedOrderForEveryInput() {
        val inputs = listOf(
            "kiselo mleko 200g 1 kg rolled oats", "two eggs and a bananna", "banana 1 egg 2", "oats 60g milk 200ml",
            "100g buckwheat 1 tsp pb 1 banana", "lunch, 2 eggs, banana", "xyzzy 2 qwerty, 1 banana", "2 egg 1 banana",
        )
        val failures = inputs.mapNotNull { input ->
            val parts = planner.segments(input).map { it.text }.filter { ShorthandLexicon.mealSlotOf(it) == null }
            val indexes = planner.draft(input).rows.map { row ->
                val text = if (row is DraftRow.Entry) row.resolution.raw else (row as DraftRow.Unresolved).text
                parts.indexOf(text)
            }
            if (indexes == indexes.sorted() && indexes.none { it < 0 }) null else "[$input] rows $indexes"
        }
        assertEquals(emptyList<String>(), failures)
    }
}
