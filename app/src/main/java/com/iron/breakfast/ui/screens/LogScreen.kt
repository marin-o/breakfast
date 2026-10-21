package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.label
import com.iron.breakfast.logMealPickerLabel
import com.iron.breakfast.pretty
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/**
 * The full-screen Log page: one text input, found foods, an ambiguous-word choice and the add bar.
 * It reuses the view model's quick-add logic; the draft lives in `vm.input` until it is added or the page is left.
 */
@Composable
internal fun LogScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    BackHandler(onBack = vm::closeLog)
    // One resolution pass per draft: rows, choices and the add-bar sum all come from the same planner run.
    val draft = remember(vm.input, vm.inputCommitted, vm.foods, vm.ready) { vm.logDraftView() }
    val suggestions = draft.choices
    val target = vm.logTargetMeal()
    // The bar counts what Add will save, including the active segment's preselected match.
    val logged = draft.logged
    val kcal = logged.sumOf { it.calculation.nutrition.calories }
    // Any draft can be added: unresolved segments open the resolve-unknown flow, as before.
    val canAdd = vm.input.isNotBlank() && !vm.busy
    val placeholder = if (vm.selectedDayKey == dayKeyForToday()) "2 eggs, 1 banana" else "Add to ${shortDateLabel(vm.selectedDayKey)}"
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = HeaderHeight).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeLog, modifier = Modifier.size(IconTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = colors.ink, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                LogMealPicker(logMealPickerLabel(vm.logTargetMeal()), vm)
            }
            Box(Modifier.size(IconTarget))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            BasicTextField(
                value = vm.input,
                // One logical line: Done submits, so a newline is never typed.
                onValueChange = { vm.onInput(it.replace("\n", " ")) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    // Done commits the text: the last word is no longer being typed.
                    vm.commitInput()
                    if (canAdd) vm.submit()
                }),
                textStyle = BreakfastType.LargeInput.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).focusRequester(requester),
                decorationBox = { inner ->
                    Box {
                        if (vm.input.isEmpty()) Text(placeholder, color = colors.soft, style = BreakfastType.LargeInput)
                        inner()
                    }
                },
            )
            Hairline(Modifier.padding(top = 22.dp))
            vm.mealDraftError?.let { Text(it, color = colors.bad, style = BreakfastType.Meta, modifier = Modifier.padding(top = 8.dp)) }
            // Rows in typed order. Typed text that matches no food stays visible, so it is never lost when Add is pressed.
            draft.rows.forEach { row ->
                when (row) {
                    is DraftRow.Entry -> FoodRow(
                        name = row.resolution.food.name,
                        quantityLine = previewQuantityLine(row.resolution.amount, row.resolution.unit, row.resolution.calculation.approximate),
                        calories = formatKcal(row.resolution.calculation.nutrition.calories),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    is DraftRow.Unresolved -> FoodRow(
                        name = row.text,
                        quantityLine = "Not in your foods",
                        calories = "",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Text(foodText(draft.activePhrase), color = colors.ink, style = BreakfastType.Body, modifier = Modifier.weight(1f))
                    if (draft.choiceHint) Text("which one?", color = colors.soft, style = BreakfastType.Meta)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEachIndexed { index, ranked ->
                        ChoiceOption(
                            label = ranked.food.name,
                            selected = index == 0 && draft.preselected,
                            onClick = { vm.chooseQuickSuggestion(ranked.food) },
                            modifier = Modifier.widthIn(min = 168.dp),
                            detail = "${formatKcal(vm.quickAddSuggestionCalories(ranked.food))} kcal",
                        )
                    }
                }
            }
        }
        // The add bar sits above whichever is taller: the keyboard or the navigation bar.
        // At large font the button takes its own full-width row so the sum keeps its width.
        val sum: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text("+${formatKcal(kcal)}", color = colors.ink, style = BreakfastType.Section)
                Text("kcal, ${foodCountLabel(logged.size)}", color = colors.soft, style = BreakfastType.Meta)
            }
        }
        val addButton: @Composable (Boolean) -> Unit = { fill ->
            PrimaryButton(
                text = if (target != null) "Add to ${target.label()}" else "Add",
                onClick = vm::submit,
                enabled = canAdd,
                fillWidth = fill,
            )
        }
        val barModifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = Gutter, vertical = 12.dp)
        if (isLargeFont(LocalDensity.current.fontScale)) {
            Column(barModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sum(Modifier.fillMaxWidth())
                addButton(true)
            }
        } else {
            Row(barModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                sum(Modifier.weight(1f))
                addButton(false)
            }
        }
    }
}

/** The meal button at the top of the Log page. Its menu sets the target meal; "Meal" when nothing is chosen or typed. */
@Composable
private fun LogMealPicker(label: String, vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    Box {
        Row(
            Modifier
                .heightIn(min = IconTarget)
                .border(1.dp, colors.line, ChoiceShape)
                .clickable(role = Role.Button, onClick = vm::openLogMealMenu)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = colors.ink, style = BreakfastType.Button)
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = vm.logMealMenuOpen,
            onDismissRequest = vm::dismissLogMealMenu,
            containerColor = colors.bg,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, colors.line),
        ) {
            MealSlot.entries.forEach { meal ->
                DropdownMenuItem(
                    text = { Text(meal.label(), color = colors.ink, style = BreakfastType.Body) },
                    onClick = { vm.chooseLogMeal(meal) },
                )
            }
        }
    }
}

/**
 * The quantity line of a Log row. Counted pieces read as the user typed them (`2 pieces`, `1 piece`), never with ≈.
 * Other units show ≈ only when the amount was converted (1 tbsp as grams).
 */
internal fun previewQuantityLine(amount: Double, unit: com.iron.breakfast.domain.MeasureUnit, approximate: Boolean): String =
    if (unit == com.iron.breakfast.domain.MeasureUnit.PIECE) {
        "${amount.pretty()} ${if (amount == 1.0) "piece" else "pieces"}"
    } else {
        "${if (approximate) "≈" else ""}${amount.pretty()} ${unit.displayLabel()}"
    }
