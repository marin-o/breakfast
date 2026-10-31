package com.iron.breakfast.onboarding

import com.iron.breakfast.domain.SegmentResolution
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.LogDraftView
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.ProfileValues
import com.iron.breakfast.domain.macroTotalsOf
import com.iron.breakfast.ui.components.MacroStat
import com.iron.breakfast.ui.screens.macroStats

/** A page's words; its example is drawn by [OnboardingExample]. */
internal data class OnboardingPage(val title: String, val body: String)

internal val ONBOARDING_PAGES = listOf(
    OnboardingPage("Type what you ate.", "One line, like a text. Commas split foods."),
    OnboardingPage("The green one gets logged.", "Half a word is enough. Add logs the green match, no extra tap."),
    OnboardingPage("Not in your foods?", "Find matches looks it up on Open Food Facts. Tap a result and carry on."),
    OnboardingPage("Your foods learn.", "Scan a barcode once and it's yours. Build recipes in Recipes and log them the same way."),
    OnboardingPage("Make it yours.", "Set a name, macro targets and your weight, or don't."),
)

private val SAMPLE_DAY = NutritionValues(1240.0, 86.0, 140.0, 38.0)

/** The sample day's kcal, for the example over [sampleMacroStats]. */
internal val SAMPLE_DAY_KCAL: Double get() = SAMPLE_DAY.calories

/** A fixed day against fixed targets, through the rule Meals uses: nothing of the user's is read. */
internal fun sampleMacroStats(): List<MacroStat> = macroStats(
    SAMPLE_DAY.calories, macroTotalsOf(listOf(SAMPLE_DAY)), ProfileValues(proteinTarget = 120.0, carbsTarget = 180.0, fatTarget = 60.0),
)

internal const val SAMPLE_LINE = "2 eggs, 100 g oats"
internal const val SAMPLE_PARTIAL = "ban"
internal const val SAMPLE_UNKNOWN = "100 g kefyr"

private val calculator = NutritionCalculator()
private fun catalogueFood(id: String) = FoodCatalog.fixtures.first { it.id == id }

/** A part of the catalogue's [foodId], through the real calculator. `OnboardingSamplesTest` pins it to what the planner gives. */
private fun sampleEntry(raw: String, foodId: String, amount: Double, unit: MeasureUnit): DraftRow.Entry {
    val food = catalogueFood(foodId)
    return DraftRow.Entry(SegmentResolution(raw, emptyList(), food, amount, unit, calculator.calculate(food, amount, unit)))
}

private fun viewOf(rows: List<DraftRow>) = LogDraftView(rows, emptyList(), choiceHint = false, preselected = false, activePhrase = "", logged = emptyList())

/** The rows for [SAMPLE_LINE]. */
internal fun sampleLineView(): LogDraftView = viewOf(
    listOf(sampleEntry("2 eggs", "egg", 2.0, MeasureUnit.PIECE), sampleEntry("100 g oats", "rolled_oats", 100.0, MeasureUnit.GRAM)),
)

/** The green chip for [SAMPLE_PARTIAL]. */
internal fun samplePartialView(): LogDraftView {
    val banana = catalogueFood("banana")
    return viewOf(emptyList()).copy(
        choices = listOf(RankedFood(banana, 1.0, emptyList())),
        activePhrase = SAMPLE_PARTIAL,
        preselected = true,
        firstIsAddPick = true,
        choiceCalculations = listOf(calculator.calculate(banana, banana.defaultAmount, banana.defaultUnit)),
    )
}

/** The row for [SAMPLE_UNKNOWN]. */
internal fun sampleUnknownView(): LogDraftView = viewOf(listOf(DraftRow.Unresolved(SAMPLE_UNKNOWN)))
