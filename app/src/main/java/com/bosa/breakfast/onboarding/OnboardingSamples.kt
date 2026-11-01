package com.bosa.breakfast.onboarding

import com.bosa.breakfast.domain.SegmentResolution
import com.bosa.breakfast.domain.RankedFood
import com.bosa.breakfast.domain.FoodOrigin
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.NutritionBasis
import com.bosa.breakfast.domain.ServingConversion
import com.bosa.breakfast.domain.NutritionCalculator
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.DraftRow
import com.bosa.breakfast.domain.LogDraftView
import com.bosa.breakfast.domain.NutritionValues
import com.bosa.breakfast.domain.ProfileValues
import com.bosa.breakfast.domain.macroTotalsOf
import com.bosa.breakfast.ui.components.MacroStat
import com.bosa.breakfast.ui.screens.macroStats

/** A page's words; its example is drawn by [OnboardingExample]. With [pointsAtExample] the words sit under the example, an arrow from them to it. */
internal data class OnboardingPage(val title: String, val body: String, val pointsAtExample: Boolean = false)

internal val ONBOARDING_PAGES = listOf(
    OnboardingPage("Type what you ate.", "Your entry is just a comma separated list."),
    OnboardingPage("The green one is what the app would log.", "Partial searches also work.", pointsAtExample = true),
    OnboardingPage("Not in your foods?", "Tap Find matches to look it up on Open Food Facts."),
    OnboardingPage("We remember what you pick.", "You can also scan barcodes."),
    OnboardingPage("Your goals.", "Set up your goals in Profile."),
)

private val SAMPLE_DAY = NutritionValues(1240.0, 86.0, 140.0, 38.0)

/** The sample day's kcal, for the example over [sampleMacroStats]. */
internal val SAMPLE_DAY_KCAL: Double get() = SAMPLE_DAY.calories

/** A fixed day against fixed targets, through the rule Meals uses: nothing of the user's is read. */
internal fun sampleMacroStats(): List<MacroStat> = macroStats(
    SAMPLE_DAY.calories, macroTotalsOf(listOf(SAMPLE_DAY)), ProfileValues(proteinTarget = 120.0, carbsTarget = 180.0, fatTarget = 60.0),
)

internal const val SAMPLE_LINE = "2 eggs, 100g oats"
internal const val SAMPLE_PARTIAL = "ban"
internal const val SAMPLE_UNKNOWN = "100 g kefyr"

private val calculator = NutritionCalculator()

private fun sampleFood(id: String, name: String, values: NutritionValues, unit: MeasureUnit, amount: Double, conversions: List<ServingConversion> = emptyList()) = FoodRecord(
    id = id, name = name, aliases = emptyList(), category = "sample", origin = FoodOrigin.GENERIC,
    nutrition = NutritionBasis(100.0, MeasureUnit.GRAM, values), conversions = conversions, defaultAmount = amount, defaultUnit = unit,
)

// The examples' foods, with fixed values: nothing of the user's or of a database is read.
private val SAMPLE_EGG = sampleFood(
    "egg", "Egg", NutritionValues(143.0, 12.6, 0.7, 9.5), MeasureUnit.PIECE, 1.0,
    listOf(ServingConversion(MeasureUnit.PIECE, 50.0, "large egg reference", true)),
)
private val SAMPLE_OATS = sampleFood("rolled_oats", "Rolled oats", NutritionValues(389.0, 16.9, 66.3, 6.9), MeasureUnit.GRAM, 40.0)
private val SAMPLE_BANANA = sampleFood(
    "banana", "Banana", NutritionValues(89.0, 1.1, 22.8, 0.3), MeasureUnit.PIECE, 1.0,
    listOf(ServingConversion(MeasureUnit.PIECE, 118.0, "medium banana reference", true)),
)

/** A part of [food], through the real calculator. `OnboardingSamplesTest` pins it to what the planner gives. */
private fun sampleEntry(raw: String, food: FoodRecord, amount: Double, unit: MeasureUnit): DraftRow.Entry {
    return DraftRow.Entry(SegmentResolution(raw, emptyList(), food, amount, unit, calculator.calculate(food, amount, unit)))
}

private fun viewOf(rows: List<DraftRow>) = LogDraftView(rows, emptyList(), choiceHint = false, preselected = false, activePhrase = "", logged = emptyList())

/** The rows for [SAMPLE_LINE]. */
internal fun sampleLineView(): LogDraftView = viewOf(
    listOf(sampleEntry("2 eggs", SAMPLE_EGG, 2.0, MeasureUnit.PIECE), sampleEntry("100g oats", SAMPLE_OATS, 100.0, MeasureUnit.GRAM)),
)

/** The green chip for [SAMPLE_PARTIAL]. */
internal fun samplePartialView(): LogDraftView {
    val banana = SAMPLE_BANANA
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
