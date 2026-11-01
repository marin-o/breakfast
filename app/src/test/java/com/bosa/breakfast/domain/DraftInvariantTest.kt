package com.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant: text the user typed never vanishes. For any input, every part that is not a meal word ends up as a logged
 * entry, as an unresolved row, or as the active "which one?" group. Nothing else is shown.
 */
class DraftInvariantTest {
    companion object {
        /** Messy inputs, shared with the add-bar invariant in [com.bosa.breakfast.AddBarMatchesAddTest]. */
        internal val MESSY = listOf(
            "two eggs and a bananna", "kiselo mleko 200g 1 kg rolled oats", "banana 1 egg 2", "oats 60g milk 200ml",
            "100g buckwheat 1 tsp pb", "100g buckwheat 1 tsp pb 1 banana", "Mac and cheese", "M&M", "Ben & Jerry's",
            "apple", "lunch", "lunch, 1 banana", "lunch 1 banana", "dinner, 2 eggs", "snacks, 1 egg", "1 banana,",
            "1 banana, ", "bananna", "vanana", "2 egg 1", "egg and", "egg &", "1 banana; 2 eggs", "1 banana\n2 eggs",
            "3 eggs,1 banana", "pb, a banana", "a", "an", "a warm milk", "ten grain", "2x egg", "egg x2",
            "half a tbsp pb", "milk 3.2% 1 banana", "Protein 2 bar", "crunchy peanut butter 1 tbsp", "chicken and rice",
            "rice + beans", "egg & cheese", "kiselo mleko", "1 ban", "bread and butter", "2 ban", "xyzzy 2 qwerty",
            "nonsense and more nonsense", "Домашно млеко 200 ml", "kiselo mleko 2.8%, 1 banana", "   ", "",
            "mleko200g", "banana2", "oats60g", "млеко200г", "банана2", "mleko0,5l", "млеко0,5л", "oats60g milk",
            "oats60g banana2", "2 eggs, oats60g", "банана2g", "mleko200г", "Vitamin B12 tablet", "xyzzy200g", "cow milk3.2", "млеко2.8", "full fat milk3.2",
        )

        /** The planner the messy inputs are read with: the fixtures plus two custom foods. */
        internal fun messyPlanner(countTyping: Boolean = true) = LogPlanner(FoodMatcher(TestFoods.fixtures + listOf(
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
        )), countTyping = countTyping)
    }

    private val planner = messyPlanner()
    private val messy = MESSY

    private fun nonMeal(input: String) = planner.segments(input)
        .map { it.text }
        .filter { ShorthandLexicon.mealSlotOf(it) == null }

    /**
     * Every typed part ends up as a logged entry, a Not-in-your-foods row, a choice group, or, only for the part being
     * typed, a suggestion group or nothing. Rows follow the typed order, and the first choice or suggestion shows
     * selected exactly when Add saves the part being typed.
     */
    @Test fun everyTypedPartIsEntryRowChoiceOrActiveSuggestion() {
        val failures = messy.flatMap { input ->
            listOf(true, false).mapNotNull { typing ->
                val draft = planner.draft(input, typing)
                val loggedTexts = draft.logged.map { it.raw }
                val parts = nonMeal(input)
                val activeOwns = { part: String -> part == draft.activeText }
                val lost = parts.filter { part ->
                    part !in loggedTexts && part !in draft.unresolved &&
                        !(activeOwns(part) && (draft.choices.isNotEmpty() || draft.suggestions.isNotEmpty() || typing))
                }
                val rowTexts = draft.rows.map { if (it is DraftRow.Entry) it.resolution.raw else (it as DraftRow.Unresolved).text }
                val orderOk = rowTexts.fold(-1 to true) { (last, ok), text ->
                    val index = parts.indexOf(text).takeIf { it >= 0 }
                    if (index == null) (last to ok) else (index to (ok && index > last))
                }.second
                when {
                    lost.isNotEmpty() -> "[$input typing=$typing] lost: $lost"
                    !orderOk -> "[$input typing=$typing] rows out of typed order: $rowTexts"
                    // Whatever Add saves of the part being typed shows selected; nothing else does.
                    draft.preselected != (draft.counted != null && draft.choices.isNotEmpty()) ->
                        "[$input typing=$typing] preselected=${draft.preselected} counted=${draft.counted?.food?.id}"
                    else -> null
                }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    /** Each typed part renders exactly once: as an entry row, a Not-in-your-foods row, or a choice or suggestion group. */
    @Test fun eachTypedPartRendersExactlyOnce() {
        val inputs = messy + listOf("milk", "кисело млеко", "milk, 1 banana", "oats", "banana")
        val failures = inputs.flatMap { input ->
            listOf(true, false).mapNotNull { typing ->
                val draft = planner.draft(input, typing)
                val parts = nonMeal(input)
                val rendered = draft.rows.map { if (it is DraftRow.Entry) it.resolution.raw else (it as DraftRow.Unresolved).text } +
                    listOfNotNull(draft.activeText.takeIf { draft.choices.isNotEmpty() || draft.suggestions.isNotEmpty() })
                val problems = parts.distinct().mapNotNull { part ->
                    val typed = parts.count { it == part }
                    val shown = rendered.count { it == part }
                    // The part being typed may show nothing at all (no food starts with it); nothing else may.
                    val mayBeEmpty = part == draft.activeText && shown == 0 && typed == 1
                    if (shown == typed || mayBeEmpty) null else "\"$part\" shown $shown, typed $typed"
                }
                if (problems.isEmpty()) null else "[$input typing=$typing] $problems"
            }
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
        val draft = planner.draft("xyzzy 2 qwerty", typing = false)
        assertEquals(listOf("xyzzy 2 qwerty"), draft.unresolved)
        assertTrue(draft.logged.isEmpty())
    }
}
