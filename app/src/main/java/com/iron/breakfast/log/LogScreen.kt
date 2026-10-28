package com.iron.breakfast.log

import com.iron.breakfast.ui.theme.*
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.iron.breakfast.app.BreakfastViewModel
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.ui.label
import com.iron.breakfast.logMealPickerLabel
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.rememberTick
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.today.shortDateLabel
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.choiceKcalDetail
import com.iron.breakfast.ui.screens.foodCountLabel


/**
 * The full-screen Log page: one text input, found foods, an ambiguous-word choice and the add bar.
 * It edits the Log draft ([LogComposer.draft]), which stays until it is added or cleared; leaving the page keeps it.
 */
@Composable
internal fun LogScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    // One resolution pass per draft: rows, choices and the add-bar sum all come from the same planner run.
    // It runs off the main thread so typing never waits on matching; the last draft shows until the next is ready.
    var draft by remember { mutableStateOf(vm.log.draftView()) }
    val pins = vm.log.pinsSnapshot()
    LaunchedEffect(vm.log.draft, vm.log.committed, pins, vm.foods, vm.ready, vm.settings.countTyping, vm.settings.learnFromPicks) {
        val input = vm.log.draft
        val committed = vm.log.committed
        draft = withContext(Dispatchers.Default) { vm.log.draftView(input, committed, pins) }
    }
    val suggestions = draft.choices
    val target = vm.log.targetMeal()
    // The bar counts what Add will save, including the active segment's preselected match.
    val logged = draft.logged
    val kcal = logged.sumOf { it.calculation.nutrition.calories }
    // Any draft can be added: unresolved segments open the resolve-unknown flow, as before.
    val canAdd = vm.log.draft.isNotBlank() && !vm.busy
    val placeholder = if (vm.selectedDayKey == vm.todayKey) "2 eggs, 1 banana" else "Add to ${shortDateLabel(vm.selectedDayKey)}"
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    val tick = rememberTick()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = HeaderHeight).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm.log::close, modifier = Modifier.size(IconTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = colors.ink, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                LogMealPicker(logMealPickerLabel(vm.log.targetMeal()), vm)
            }
            Box(Modifier.size(IconTarget))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            // The field keeps its own caret. A change made from outside (a tapped choice) moves the caret to the end.
            var field by remember { mutableStateOf(inputFieldFor(vm.log.draft)) }
            LaunchedEffect(vm.log.draft) { field = syncedInputField(field, vm.log.draft) }
            BasicTextField(
                value = field,
                // One logical line: Done submits, so a newline is never typed.
                onValueChange = { next ->
                    val text = next.text.replace("\n", " ")
                    field = next.copy(text = text)
                    vm.log.onDraft(text)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    // Done commits the text: the last word is no longer being typed.
                    vm.log.commit()
                    if (canAdd) { tick(); vm.log.submit() }
                }),
                textStyle = BreakfastType.LargeInput.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).focusRequester(requester),
                decorationBox = { inner ->
                    Box {
                        if (vm.log.draft.isEmpty()) Text(placeholder, color = colors.soft, style = BreakfastType.LargeInput)
                        inner()
                    }
                },
            )
            Hairline(Modifier.padding(top = 22.dp))
            vm.log.error?.let { Text(it, color = colors.bad, style = BreakfastType.Meta, modifier = Modifier.padding(top = 8.dp)) }
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
                        onClick = { vm.log.resolveNotInFoods(row) },
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
                            onClick = { vm.log.chooseSuggestion(ranked.food) },
                            modifier = Modifier.widthIn(min = 168.dp),
                            detail = draft.choiceCalculations.getOrNull(index)?.let(::choiceKcalDetail),
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
                onClick = { tick(); vm.log.submit() },
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
                .clickable(role = Role.Button, onClick = vm.log::openMealMenu)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = colors.ink, style = BreakfastType.Button)
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = vm.log.mealMenuOpen,
            onDismissRequest = vm.log::dismissMealMenu,
            containerColor = colors.bg,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, colors.line),
        ) {
            MealSlot.entries.forEach { meal ->
                DropdownMenuItem(
                    text = { Text(meal.label(), color = colors.ink, style = BreakfastType.Body) },
                    onClick = { vm.log.chooseMeal(meal) },
                )
            }
        }
    }
}

/**
 * The field after the text changed from outside the field (a tapped suggestion or choice): the caret goes to the end
 * of the new text. When the text is what the field already shows (the user typed it), the field keeps its caret.
 */
internal fun syncedInputField(field: TextFieldValue, text: String): TextFieldValue =
    if (field.text == text) field else inputFieldFor(text)

/** The Log field for [text] with the caret at the end: a page that opens again starts there, not at the start. */
internal fun inputFieldFor(text: String): TextFieldValue = TextFieldValue(text, TextRange(text.length))

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
