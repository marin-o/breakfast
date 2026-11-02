package mk.bosa.breakfast.ui.components

import mk.bosa.breakfast.domain.isPositiveAmount
import mk.bosa.breakfast.domain.fieldNumber
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionCalculator
import mk.bosa.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import mk.bosa.breakfast.domain.measures
import mk.bosa.breakfast.ui.label
import mk.bosa.breakfast.ui.macroLineParts
import mk.bosa.breakfast.domain.editableNumber
import mk.bosa.breakfast.ui.screens.entrySourceLabel
import mk.bosa.breakfast.ui.screens.kcalLabel
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/** What an amount counts, as a wrapping row of chips: the food's [measures]. [detail] is a chip's second line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeasureChoices(food: FoodRecord, selected: Measure?, onSelect: (Measure) -> Unit, modifier: Modifier = Modifier, detail: (Measure) -> String? = { null }) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        food.measures().forEach { candidate ->
            ChoiceOption(candidate.label, selected == candidate, { onSelect(candidate) }, detail = detail(candidate))
        }
    }
}

/**
 * The amount sheet: the food's name and source, the amount and what it counts (the food's [measures]: its default and
 * basis, the other of g and ml with a density, its named servings; the row wraps), the meal ([startMeal]; none for a recipe's ingredient, which has no meal), the kcal and macros it comes
 * to, and Add. The Library logs through it, a recipe is logged through it, and a recipe's ingredient is added through
 * it. Its values are saveable, so a configuration change keeps the open sheet as it was.
 */
@Composable
internal fun FoodAmountSheet(
    food: FoodRecord,
    startAmount: Double,
    startMeasure: Measure,
    startMeal: MealSlot?,
    onAdd: (amount: Double, measure: Measure, meal: MealSlot?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBreakfastColors.current
    var amountText by rememberSaveable(food.id, startMeal) { mutableStateOf(editableNumber(startAmount)) }
    // The unit and a serving's name, saved apart (a Measure is not a Bundle value).
    var unit by rememberSaveable(food.id, startMeal) { mutableStateOf(startMeasure.unit) }
    var servingName by rememberSaveable(food.id, startMeal) { mutableStateOf(startMeasure.name.takeIf { startMeasure.unit == MeasureUnit.SERVING }) }
    val measure = Measure(unit, servingName)
    var chosenMeal by rememberSaveable(food.id, startMeal) { mutableStateOf(startMeal) }
    val exactAmount = fieldNumber(amountText, startAmount)?.takeIf(::isPositiveAmount)
    val calc = exactAmount?.let { NutritionCalculator().calculate(food, it, unit, servingName) }
    Sheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(foodText(food.name), color = colors.ink, style = BreakfastType.Section)
            Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
            // The field fills its row, so the units take their own line under it, as on the entry's quick edit.
            DecimalField("Amount", amountText, { amountText = it })
            MeasureChoices(food, measure, { unit = it.unit; servingName = it.name })
            chosenMeal?.let { meal -> MealField(meal, { chosenMeal = it }) }
            Hairline()
            Text(calc?.let { kcalLabel(it.nutrition.calories) } ?: POSITIVE_AMOUNT_MESSAGE, color = colors.ink, style = BreakfastType.Section)
            // No amount, no macros: the empty line keeps the sheet's height.
            if (calc != null) MacroLine(macroLineParts(calc.nutrition), BreakfastType.Secondary) else Text("", style = BreakfastType.Secondary)
            PrimaryButton(
                chosenMeal?.let { "Add to ${it.label()}" } ?: "Add",
                onClick = { exactAmount?.let { onAdd(it, measure, chosenMeal) } },
                enabled = calc?.supported == true,
            )
        }
    }
}
