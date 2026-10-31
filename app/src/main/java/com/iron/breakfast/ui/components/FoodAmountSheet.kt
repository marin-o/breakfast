package com.iron.breakfast.ui.components

import com.iron.breakfast.domain.isPositiveAmount
import com.iron.breakfast.domain.fieldNumber
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
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.Measure
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import com.iron.breakfast.domain.measures
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.macroLine
import com.iron.breakfast.domain.editableNumber
import com.iron.breakfast.ui.screens.entrySourceLabel
import com.iron.breakfast.ui.screens.kcalLabel
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * The amount sheet: the food's name and source, the amount and what it counts (the food's [measures]: its default and
 * basis, the other of g and ml with a density, its named servings; the row wraps), the meal ([startMeal]; none for a recipe's ingredient, which has no meal), the kcal and macros it comes
 * to, and Add. The Library logs through it, a recipe is logged through it, and a recipe's ingredient is added through
 * it. Its values are saveable, so a configuration change keeps the open sheet as it was.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val measures = food.measures()
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
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                measures.forEach { candidate ->
                    ChoiceOption(candidate.label, measure == candidate, { unit = candidate.unit; servingName = candidate.name })
                }
            }
            chosenMeal?.let { meal -> MealField(meal, { chosenMeal = it }) }
            Hairline()
            Text(calc?.let { kcalLabel(it.nutrition.calories) } ?: POSITIVE_AMOUNT_MESSAGE, color = colors.ink, style = BreakfastType.Section)
            Text(calc?.let { macroLine(it.nutrition) } ?: "", color = colors.soft, style = BreakfastType.Secondary)
            PrimaryButton(
                chosenMeal?.let { "Add to ${it.label()}" } ?: "Add",
                onClick = { exactAmount?.let { onAdd(it, measure, chosenMeal) } },
                enabled = calc?.supported == true,
            )
        }
    }
}
