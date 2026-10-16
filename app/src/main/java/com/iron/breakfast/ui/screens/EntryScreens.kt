package com.iron.breakfast.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.label
import com.iron.breakfast.pretty
import com.iron.breakfast.ui.components.ApproxTag
import com.iron.breakfast.ui.components.CharacterRule
import com.iron.breakfast.ui.components.ReceiptBottomSheet
import com.iron.breakfast.ui.components.ReceiptChoiceChip
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.ReceiptField
import com.iron.breakfast.ui.components.ReceiptOutlinedButton
import com.iron.breakfast.ui.components.ReceiptPrimaryButton
import com.iron.breakfast.ui.components.ReceiptTonalIconButton
import com.iron.breakfast.ui.components.SourceBox
import com.iron.breakfast.ui.components.receiptFoodName
import com.iron.breakfast.ui.theme.LocalReceiptColors
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun EntryDetailScreen(vm: BreakfastViewModel, entry: MealEntry) {
    val colors = LocalReceiptColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BackHandler { vm.closeEntryDetail() }
    val food = vm.foods.firstOrNull { it.id == entry.foodId }
    val quantity = quantityRateDisplay(entry, food)
    val date = runCatching { LocalDate.parse(entry.dayKey).format(DateTimeFormatter.ofPattern("EEE dd.MM.yyyy", Locale.ENGLISH)) }.getOrDefault(entry.dayKey)
    Surface(Modifier.fillMaxSize(), color = colors.paper) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::closeEntryDetail) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }
                    Text("ENTRY", style = MaterialTheme.typography.headlineSmall, color = colors.ink, fontWeight = FontWeight.Bold)
                }
                Text("${entry.mealSlot.label()}  $date  ${entry.savedAtEpochMs.formatClock()}", color = colors.soft, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                CharacterRule('-', modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Text(receiptFoodName(entry.foodName), color = colors.ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(receiptKcal(entry.nutritionSnapshot.calories), color = colors.ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(54.dp * fontScale))
                    Text(sourceLetter(food), color = colors.soft, modifier = Modifier.width(22.dp * fontScale), textAlign = TextAlign.End)
                }
                if (quantity.approximateLabel != null) {
                    Row(Modifier.padding(start = 36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ApproxTag(quantity.approximateLabel)
                        Text(quantity.line, color = colors.soft)
                    }
                } else {
                    Text(quantity.line, color = colors.soft, modifier = Modifier.padding(start = 36.dp))
                }
                CharacterRule('-', modifier = Modifier.padding(top = 8.dp))
                MacroTable(entry)
                CharacterRule('=', modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", color = colors.ink, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(receiptKcal(entry.nutritionSnapshot.calories), color = colors.ink, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                }
                Text("${sourceLetter(food)}  ${entrySourceLabel(food)}", color = colors.soft, modifier = Modifier.padding(top = 4.dp))
                CharacterRule('-', modifier = Modifier.padding(top = 10.dp))
                Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceiptPrimaryButton("Edit amount", { vm.closeEntryDetail(); vm.beginQuickEdit(entry) })
                    ReceiptOutlinedButton("Replace food", { vm.closeEntryDetail(); vm.beginFullEdit(entry) })
                    TextButton(onClick = { vm.openLibraryFoodDetail(entry) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open in Library", color = colors.ink, textDecoration = TextDecoration.Underline) }
                }
                CharacterRule('-', modifier = Modifier.padding(top = 62.dp))
                DestructiveButton("Delete entry", { vm.closeEntryDetail(); vm.delete(entry) }, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun MacroTable(entry: MealEntry) {
    val colors = LocalReceiptColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val gramsWidth = 54.dp * fontScale
    val kcalWidth = 58.dp * fontScale
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth()) { Text("", Modifier.weight(1f)); Text("g", color = colors.soft, modifier = Modifier.width(gramsWidth), textAlign = TextAlign.End); Text("kcal", color = colors.soft, modifier = Modifier.width(kcalWidth), textAlign = TextAlign.End) }
        listOf("Protein" to (entry.nutritionSnapshot.protein to entry.nutritionSnapshot.protein?.times(4.0)), "Carbs" to (entry.nutritionSnapshot.carbohydrates to entry.nutritionSnapshot.carbohydrates?.times(4.0)), "Fat" to (entry.nutritionSnapshot.fat to entry.nutritionSnapshot.fat?.times(9.0))).forEach { (label, values) ->
            Row(Modifier.fillMaxWidth()) { Text(label, color = colors.ink, modifier = Modifier.weight(1f)); Text(values.first?.pretty() ?: "0", color = colors.ink, modifier = Modifier.width(gramsWidth), textAlign = TextAlign.End); Text(receiptKcal(values.second ?: 0.0), color = colors.ink, modifier = Modifier.width(kcalWidth), textAlign = TextAlign.End) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EntryQuickEditSheet(vm: BreakfastViewModel, entry: MealEntry) {
    val colors = LocalReceiptColors.current
    BackHandler { vm.cancelEdit() }
    var amount by remember(entry.id) { mutableStateOf(editAmountText(entry.enteredAmount)) }
    var unit by remember(entry.id) { mutableStateOf(entry.enteredUnit) }
    val food = vm.foods.firstOrNull { it.id == entry.foodId }
    val units = food?.let(vm::editUnits).orEmpty().ifEmpty { listOf(entry.enteredUnit) }
    val preview = amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.draftCalculation(entry.foodId, it, unit) }
    val changed = entryDraftNutritionChanged(entry, amount, unit, entry.foodId)
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { requester.requestFocus() }
    ReceiptBottomSheet(onDismissRequest = vm::cancelEdit, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(receiptFoodName(entry.foodName), style = MaterialTheme.typography.headlineSmall, color = colors.ink, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ReceiptTonalIconButton(Icons.Default.Remove, "Decrease amount", { amount = adjustAmount(amount, -1.0) })
                ReceiptField("Amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), labelVisible = false, amountStyle = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); vm.onInput("${amount.trim()} ${unit.displayLabel()}"); vm.submit() }), fieldModifier = Modifier.focusRequester(requester))
                ReceiptTonalIconButton(Icons.Default.Add, "Increase amount", { amount = adjustAmount(amount, 1.0) })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { units.forEach { candidate -> ReceiptChoiceChip(candidate.displayLabel(), unit == candidate, { unit = candidate }) } }
            CharacterRule('-', modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (preview != null) { val base = "${preview.resolvedBaseAmount.pretty()} ${food?.nutrition?.unit?.displayLabel() ?: unit.displayLabel()}"; if (preview.approximate && food != null && unit != food.nutrition.unit) ApproxTag(base) else Text(base, color = colors.ink, fontWeight = FontWeight.Bold); Text("${receiptKcal(preview.nutrition.calories)} kcal", color = colors.ink, fontWeight = FontWeight.Bold) } else Text("Enter a positive amount", color = colors.soft)
                if (changed) Text("was ${receiptKcal(entry.nutritionSnapshot.calories)}", color = colors.soft)
            }
            ReceiptPrimaryButton("Save", { vm.onInput("${amount.trim()} ${unit.displayLabel()}"); vm.submit() }, enabled = amount.toDoubleOrNull()?.let { it > 0 } == true)
            if (vm.editInvalid) Text(vm.mealDraftError ?: "Could not save this entry.", color = colors.red)
        }
    }
}

private fun editAmountText(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
private fun adjustAmount(value: String, delta: Double): String = (value.toDoubleOrNull()?.plus(delta)?.coerceAtLeast(0.1) ?: 1.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.ROOT, it) }
private fun entryDraftNutritionChanged(entry: MealEntry, amount: String, unit: MeasureUnit, foodId: String?): Boolean = foodId != entry.foodId || amount.toDoubleOrNull()?.let { it != entry.enteredAmount } != false || unit != entry.enteredUnit

@Composable
internal fun EntryFullEditScreen(vm: BreakfastViewModel, entry: MealEntry) {
    val colors = LocalReceiptColors.current
    BackHandler { vm.cancelEdit() }
    val selectedFood = vm.editingFood() ?: vm.foods.firstOrNull { it.id == entry.foodId }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    var amount by remember(entry.id) { mutableStateOf(editAmountText(entry.enteredAmount)) }
    var unit by remember(entry.id) { mutableStateOf(entry.enteredUnit) }
    var meal by remember(entry.id) { mutableStateOf(entry.mealSlot) }
    var dayKey by remember(entry.id) { mutableStateOf(entry.dayKey) }
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(dayKey) }.getOrElse { LocalDate.now() }
    val units = selectedFood?.let(vm::editUnits).orEmpty().ifEmpty { listOf(entry.enteredUnit) }
    val preview = selectedFood?.let { food -> amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.draftCalculation(food.id, it, unit) } }
    LaunchedEffect(selectedFood?.id) { if (unit !in units) unit = units.first() }
    Surface(Modifier.fillMaxSize(), color = colors.paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = vm::cancelEdit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }; Text("EDIT ENTRY", style = MaterialTheme.typography.headlineSmall, color = colors.ink, fontWeight = FontWeight.Bold) }
            CharacterRule('-', modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp))
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) { ReceiptField("Food", selectedFood?.name ?: entry.foodName, {}, Modifier.weight(1f), readOnly = true); ReceiptOutlinedButton("Replace", vm::openEditFoodPicker, fillWidth = false, modifier = Modifier.widthIn(min = 96.dp * fontScale).padding(bottom = 0.dp)) }
                ReceiptField("Amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.width(140.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { units.forEach { candidate -> ReceiptChoiceChip(candidate.displayLabel(), unit == candidate, { unit = candidate }) } }
                Text("Meal", color = colors.soft, modifier = Modifier.padding(top = 8.dp))
                MealSlot.entries.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { candidate -> ReceiptChoiceChip(candidate.label(), meal == candidate, { meal = candidate }, Modifier.weight(1f)) } } }
                Text("Date", color = colors.soft, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).border(2.dp, colors.ink).clickable(role = Role.Button) { DatePickerDialog(context, { _, year, month, day -> dayKey = LocalDate.of(year, month + 1, day).toString() }, date.year, date.monthValue - 1, date.dayOfMonth).show() }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(date.format(DateTimeFormatter.ofPattern("EEE dd.MM.yyyy", Locale.ENGLISH)), Modifier.weight(1f), color = colors.ink); Icon(Icons.Default.CalendarMonth, "Change date", tint = colors.ink) }
            }
            CharacterRule('-', modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp))
            val shown = preview?.nutrition ?: entry.nutritionSnapshot
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) { Text("${receiptKcal(shown.calories)} kcal", style = MaterialTheme.typography.displayMedium, color = colors.ink, fontWeight = FontWeight.Bold); Text("P ${shown.protein?.pretty() ?: "—"}   C ${shown.carbohydrates?.pretty() ?: "—"}   F ${shown.fat?.pretty() ?: "—"}", color = colors.soft) }
            ReceiptPrimaryButton("Save", { selectedFood?.let { vm.saveFullEdit(it.id, amount, unit, meal, dayKey) } }, modifier = Modifier.padding(horizontal = 14.dp), enabled = !vm.busy)
            if (vm.editInvalid) Text(vm.mealDraftError ?: "Could not save this entry.", Modifier.padding(14.dp), color = colors.red)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EntryFoodPicker(vm: BreakfastViewModel) {
    val colors = LocalReceiptColors.current
    ReceiptBottomSheet(onDismissRequest = vm::closeEditFoodPicker) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("REPLACE FOOD", style = MaterialTheme.typography.headlineSmall, color = colors.ink, fontWeight = FontWeight.Bold)
            vm.foods.filterNot { it.archived }.take(30).forEach { food -> TextButton(onClick = { vm.chooseEditFood(food) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(receiptFoodName(food.name), color = colors.ink) } }
        }
    }
}

private fun Long.formatClock(): String = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
