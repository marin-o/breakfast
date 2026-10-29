package com.iron.breakfast.entry

import com.iron.breakfast.ui.theme.*
import android.app.DatePickerDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.today.DayState
import com.iron.breakfast.today.UndoController
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.decimalInput
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.MacroTrack
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.Sheet
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.iron.breakfast.ui.screens.quantityRateDisplay
import com.iron.breakfast.today.shortDateLabel
import com.iron.breakfast.ui.screens.plainLine
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.entrySourceLabel
import com.iron.breakfast.ui.screens.sourceLabel
import com.iron.breakfast.domain.isRecipeId
import com.iron.breakfast.ui.screens.datePickerMaxMs


@Composable
internal fun EntryDetailScreen(entryEdit: EntryEdit, session: DiarySession, surfaces: Surfaces, undo: UndoController, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    val food = session.foods.firstOrNull { it.id == entry.foodId }
    val quantity = quantityRateDisplay(entry, food)
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp)) {
                TopBar("Entry", entryEdit::closeDetail, subtitle = "${entry.mealSlot.label()}, ${shortDateLabel(entry.dayKey)}, ${entry.savedAtEpochMs.formatClock()}")
                Text(foodText(entry.foodName), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 22.dp))
                Text(quantity.plainLine(), color = colors.soft, style = BreakfastType.Secondary)
                Text(formatKcal(entry.nutritionSnapshot.calories), color = colors.ink, style = BreakfastType.Hero, modifier = Modifier.padding(top = 22.dp))
                Text("kilocalories", color = colors.soft, style = BreakfastType.Secondary)
                MacroTable(entry, Modifier.padding(top = 22.dp))
                // A recipe is the user's own, as a personal food is.
                Text(if (isRecipeId(entry.foodId)) sourceLabel("P") else entrySourceLabel(food), color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 22.dp))
                Hairline(Modifier.padding(top = 30.dp))
                Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Its food deleted, an entry's amount still changes by its own numbers; not when they cannot scale.
                    if (entryEdit.amountEditable(entry)) PrimaryButton("Edit amount", { entryEdit.closeDetail(); entryEdit.beginQuick(entry) }, kind = ButtonKind.Neutral)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton("Replace food", { entryEdit.closeDetail(); entryEdit.beginFull(entry) }, Modifier.weight(1f), fillWidth = false)
                        SecondaryButton("Open in Library", { surfaces.openLibraryFoodDetail(entry) }, Modifier.weight(1f), fillWidth = false)
                    }
                }
                DestructiveButton("Delete entry", { entryEdit.closeDetail(); undo.delete(entry) }, Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun MacroTable(entry: MealEntry, modifier: Modifier = Modifier) {
    val colors = LocalBreakfastColors.current
    val nutrition = entry.nutritionSnapshot
    val total = nutrition.calories
    fun share(kcal: Double?): Float = if (total > 0.0 && kcal != null) (kcal / total).toFloat() else 0f
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                Triple("Protein", nutrition.protein, share(nutrition.protein?.times(4.0))),
                Triple("Carbs", nutrition.carbohydrates, share(nutrition.carbohydrates?.times(4.0))),
                Triple("Fat", nutrition.fat, share(nutrition.fat?.times(9.0))),
            ).forEach { (label, grams, fraction) ->
                Column(Modifier.weight(1f)) {
                    MacroTrack(fraction)
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Text(grams?.pretty() ?: "0", color = colors.ink, style = BreakfastType.MacroValue)
                        Text(" g", color = colors.soft, style = BreakfastType.Secondary)
                    }
                    Text(label, color = colors.soft, style = BreakfastType.Meta)
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EntryQuickEditSheet(entryEdit: EntryEdit, session: DiarySession, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    // Saveable: a dark-mode or font-size change recreates the activity and must not reset what was typed.
    var amount by rememberSaveable(entry.id) { mutableStateOf(editAmountText(entry.enteredAmount)) }
    var unit by rememberSaveable(entry.id) { mutableStateOf(entry.enteredUnit) }
    val food = session.foods.firstOrNull { it.id == entry.foodId }
    val units = food?.let(::entryUnits).orEmpty().ifEmpty { listOf(entry.enteredUnit) }
    val preview = amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { entryEdit.preview(entry, entry.foodId, it, unit) }
    val changed = entryDraftNutritionChanged(entry, amount, unit, entry.foodId)
    // A save error stays until the amount or unit changes.
    LaunchedEffect(amount, unit) { entryEdit.inputChanged() }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { requester.requestFocus() }
    Sheet(onDismissRequest = { entryEdit.cancel() }, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(foodText(entry.foodName), style = BreakfastType.Section, color = colors.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AddCircle("Decrease amount", { amount = adjustAmount(amount, -1.0) }, icon = Icons.Outlined.Remove)
                Field(
                    "Amount", amount, { amount = it.decimalInput() }, Modifier.weight(1f),
                    labelVisible = false, amountStyle = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); entryEdit.saveQuick("${amount.trim()} ${unit.displayLabel()}") }),
                    fieldModifier = Modifier.focusRequester(requester),
                )
                AddCircle("Increase amount", { amount = adjustAmount(amount, 1.0) })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { candidate -> ChoiceOption(candidate.displayLabel(), unit == candidate, { unit = candidate }) }
            }
            Hairline(Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (preview != null) {
                    val baseUnit = food?.nutrition?.unit ?: entry.resolvedBaseUnit.takeIf { it != MeasureUnit.UNKNOWN } ?: unit
                    val base = "${preview.resolvedBaseAmount.pretty()} ${baseUnit.displayLabel()}"
                    val shownBase = if (preview.approximate && food != null && unit != food.nutrition.unit) "≈ $base" else base
                    Text(shownBase, color = colors.ink, style = BreakfastType.BodyMedium)
                    Text("${formatKcal(preview.nutrition.calories)} kcal", color = colors.ink, style = BreakfastType.BodyMedium)
                } else {
                    Text("Enter a positive amount", color = colors.soft, style = BreakfastType.Secondary)
                }
                if (changed) Text("was ${formatKcal(entry.nutritionSnapshot.calories)}", color = colors.soft, style = BreakfastType.Secondary)
            }
            PrimaryButton("Save", { entryEdit.saveQuick("${amount.trim()} ${unit.displayLabel()}") }, enabled = amount.toDoubleOrNull()?.let { it > 0 } == true)
            entryEdit.error?.let { Text(it, color = colors.bad, style = BreakfastType.Meta) }
        }
    }
}

private fun editAmountText(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
private fun adjustAmount(value: String, delta: Double): String = (value.toDoubleOrNull()?.plus(delta)?.coerceAtLeast(0.1) ?: 1.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.ROOT, it) }
private fun entryDraftNutritionChanged(entry: MealEntry, amount: String, unit: MeasureUnit, foodId: String?): Boolean = foodId != entry.foodId || amount.toDoubleOrNull()?.let { it != entry.enteredAmount } != false || unit != entry.enteredUnit

@Composable
internal fun EntryFullEditScreen(entryEdit: EntryEdit, session: DiarySession, dayState: DayState, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    val selectedFood = entryEdit.editingFood() ?: session.foods.firstOrNull { it.id == entry.foodId }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    var amount by rememberSaveable(entry.id) { mutableStateOf(editAmountText(entry.enteredAmount)) }
    var unit by rememberSaveable(entry.id) { mutableStateOf(entry.enteredUnit) }
    var meal by rememberSaveable(entry.id) { mutableStateOf(entry.mealSlot) }
    var dayKey by rememberSaveable(entry.id) { mutableStateOf(entry.dayKey) }
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(dayKey) }.getOrElse { LocalDate.now() }
    val units = selectedFood?.let(::entryUnits).orEmpty().ifEmpty { listOf(entry.enteredUnit) }
    // With the entry's food deleted and no other chosen, the entry itself: its amount in its own unit, scaled.
    val foodId = selectedFood?.id ?: entry.foodId
    val preview = amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { entryEdit.preview(entry, foodId, it, unit) }
    LaunchedEffect(selectedFood?.id) { if (unit !in units) unit = units.first() }
    // A save error stays until something being edited changes.
    LaunchedEffect(selectedFood?.id, amount, unit, meal, dayKey) { entryEdit.inputChanged() }
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            TopBar("Edit entry", { entryEdit.cancel() }, Modifier.padding(horizontal = Gutter, vertical = 8.dp))
            Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    Field("Food", selectedFood?.name ?: entry.foodName, {}, Modifier.weight(1f), readOnly = true)
                    SecondaryButton("Replace", entryEdit::openFoodPicker, modifier = Modifier.widthIn(min = 96.dp * fontScale), fillWidth = false)
                }
                Field("Amount", amount, { amount = it.decimalInput() }, Modifier.widthIn(min = 140.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEach { candidate -> ChoiceOption(candidate.displayLabel(), unit == candidate, { unit = candidate }) }
                }
                SectionHeading("Meal")
                MealSlot.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { candidate -> ChoiceOption(candidate.label(), meal == candidate, { meal = candidate }, Modifier.weight(1f)) }
                    }
                }
                Text("Date", color = colors.soft, style = BreakfastType.Meta)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = FieldMinHeight)
                        .border(1.dp, colors.line, FieldShape)
                        .clickable(role = Role.Button) { DatePickerDialog(context, { _, year, month, day -> dayKey = LocalDate.of(year, month + 1, day).toString() }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = datePickerMaxMs(dayState.todayKey, java.time.ZoneId.systemDefault()) }.show() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE dd.MM.yyyy", Locale.ENGLISH)), Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
                    Icon(Icons.Default.CalendarMonth, "Change date", tint = colors.ink)
                }
            }
            val shown = preview?.nutrition ?: entry.nutritionSnapshot
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 24.dp), horizontalAlignment = Alignment.End) {
                Text("${formatKcal(shown.calories)} kcal", style = BreakfastType.Section, color = colors.ink)
                Text("P ${shown.protein?.pretty() ?: "—"}   C ${shown.carbohydrates?.pretty() ?: "—"}   F ${shown.fat?.pretty() ?: "—"}", color = colors.soft, style = BreakfastType.Secondary)
            }
            PrimaryButton("Save", { entryEdit.saveFull(foodId, amount, unit, meal, dayKey) }, Modifier.padding(horizontal = Gutter), enabled = !session.busy)
            entryEdit.error?.let { Text(it, Modifier.padding(Gutter), color = colors.bad, style = BreakfastType.Meta) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EntryFoodPicker(entryEdit: EntryEdit, session: DiarySession) {
    Sheet(onDismissRequest = entryEdit::closeFoodPicker) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeading("Replace food", Modifier.padding(bottom = 8.dp))
            session.foods.take(30).forEach { food ->
                TertiaryButton(food.name, { entryEdit.chooseFood(food) }, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun Long.formatClock(): String = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
