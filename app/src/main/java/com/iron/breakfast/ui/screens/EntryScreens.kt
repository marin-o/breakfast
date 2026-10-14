package com.iron.breakfast.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.label
import com.iron.breakfast.pretty
import com.iron.breakfast.ui.components.ApproxTag
import com.iron.breakfast.ui.components.ReceiptBottomSheet
import com.iron.breakfast.ui.components.ReceiptChoiceChip
import com.iron.breakfast.ui.components.ReceiptTicket
import com.iron.breakfast.ui.components.ReceiptTornEdge
import com.iron.breakfast.ui.theme.Ground
import com.iron.breakfast.ui.theme.Ink
import com.iron.breakfast.ui.theme.InkSoft
import com.iron.breakfast.ui.theme.OnGround
import com.iron.breakfast.ui.theme.Paper
import com.iron.breakfast.ui.theme.PenOnDarkInk
import com.iron.breakfast.ui.theme.PenTonal
import com.iron.breakfast.ui.theme.PenTonalInk
import com.iron.breakfast.ui.theme.PrintRed
import com.iron.breakfast.ui.theme.Rule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun EntryDetailScreen(vm: BreakfastViewModel, entry: MealEntry) {
    BackHandler { vm.closeEntryDetail() }
    val food = vm.foods.firstOrNull { it.id == entry.foodId }
    val quantity = quantityRateDisplay(entry, food)
    val loggedDate = runCatching { LocalDate.parse(entry.dayKey) }.getOrNull()
    val loggedLabel = loggedDate?.let { "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}, ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}" } ?: entry.dayKey
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::closeEntryDetail) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnGround) }
                    Text("Entry", style = MaterialTheme.typography.titleLarge, color = OnGround, fontWeight = FontWeight.Bold)
                }
                ReceiptTicket(mealName = entry.foodName, total = receiptKcal(entry.nutritionSnapshot.calories)) {
                    DetailFactRow("Amount") { if (entry.approximateConversion) ApproxTag("${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}") else Text("${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}", color = Ink) }
                    DetailFactRow("Weight") { Text(quantity.line, color = Ink) }
                    Row(Modifier.fillMaxWidth().dashedTopRule().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MacroFact("Protein", entry.nutritionSnapshot.protein)
                        MacroFact("Carbs", entry.nutritionSnapshot.carbohydrates)
                        MacroFact("Fat", entry.nutritionSnapshot.fat)
                    }
                    DetailFactRow("Source") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(food?.sourceName ?: food?.category ?: "Saved food", color = Ink); SourceBadge(sourceLetter(food)) } }
                    DetailFactRow("Match") { Text("Matched from ${entry.sourcePhrase}", color = Ink) }
                    Text("${entry.mealSlot.label()}, $loggedLabel, logged ${entry.savedAtEpochMs.formatClock()}", Modifier.padding(14.dp), color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { vm.closeEntryDetail(); vm.beginQuickEdit(entry) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) { Icon(Icons.Default.Edit, null); Text("Edit amount", Modifier.padding(start = 8.dp)) }
                OutlinedButton(onClick = { vm.closeEntryDetail(); vm.beginFullEdit(entry) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) { Text("Replace food") }
                TextButton(onClick = { vm.openLibraryFoodDetail(entry) }, Modifier.fillMaxWidth().height(48.dp)) { Text("Open in Library", color = PenOnDarkInk, fontWeight = FontWeight.Bold) }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(), color = Paper, shape = RoundedCornerShape(6.dp)) {
                TextButton(onClick = { vm.closeEntryDetail(); vm.delete(entry) }, Modifier.fillMaxWidth().height(60.dp)) { Icon(Icons.Default.Delete, null, tint = PrintRed); Text("Delete entry", Modifier.padding(start = 8.dp), color = PrintRed, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable private fun androidx.compose.foundation.layout.RowScope.MacroFact(label: String, value: Double?) { Column(Modifier.weight(1f)) { Text(label, color = InkSoft, style = MaterialTheme.typography.labelSmall); Text(value?.let { "${it.pretty()} g" } ?: "—", color = Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) } }
@Composable private fun DetailFactRow(label: String, value: @Composable () -> Unit) { Row(Modifier.fillMaxWidth().height(44.dp).dashedTopRule().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = InkSoft, style = MaterialTheme.typography.bodyMedium); value() } }
@Composable private fun SourceBadge(source: String) { Box(Modifier.size(22.dp).border(1.5.dp, InkSoft, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) { Text(source, color = InkSoft, style = MaterialTheme.typography.labelSmall) } }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EntryQuickEditSheet(vm: BreakfastViewModel, entry: MealEntry) {
    BackHandler { vm.cancelEdit() }
    var amount by remember(entry.id) { mutableStateOf(entry.enteredAmount.pretty()) }
    var unit by remember(entry.id) { mutableStateOf(entry.enteredUnit) }
    val food = vm.foods.firstOrNull { it.id == entry.foodId }
    val units = food?.let(vm::editUnits).orEmpty().ifEmpty { listOf(entry.enteredUnit) }
    val preview = amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.draftCalculation(entry.foodId, it, unit) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    ReceiptBottomSheet(onDismissRequest = vm::cancelEdit) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.foodName, style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { amount = adjustAmount(amount, -1.0) }, Modifier.size(48.dp)) { Icon(Icons.Default.Remove, "Decrease amount") }
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); vm.onInput("${amount.trim()} ${unit.displayLabel()}"); vm.submit() }), modifier = Modifier.width(160.dp).height(64.dp).focusRequester(focusRequester))
                IconButton(onClick = { amount = adjustAmount(amount, 1.0) }, Modifier.size(48.dp)) { Icon(Icons.Default.Add, "Increase amount") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { units.forEach { candidate -> ReceiptChoiceChip(candidate.displayLabel(), unit == candidate, { unit = candidate }) } }
            Text(preview?.let { "${it.resolvedBaseAmount.pretty()} ${food?.nutrition?.unit?.displayLabel() ?: unit.displayLabel()}, ${receiptKcal(it.nutrition.calories)} kcal · was ${receiptKcal(entry.nutritionSnapshot.calories)}" } ?: "Enter a positive amount · was ${receiptKcal(entry.nutritionSnapshot.calories)} kcal", color = InkSoft)
            Button(onClick = { vm.onInput("${amount.trim()} ${unit.displayLabel()}"); vm.submit() }, enabled = amount.toDoubleOrNull()?.let { it > 0.0 } == true, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) { Text("Save") }
            if (vm.editInvalid) Text(vm.mealDraftError ?: "Could not save this entry.", color = PrintRed)
        }
    }
}

private fun adjustAmount(value: String, delta: Double): String = (value.toDoubleOrNull()?.plus(delta)?.coerceAtLeast(0.1) ?: 1.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.ROOT, it) }

@Composable
internal fun EntryFullEditScreen(vm: BreakfastViewModel, entry: MealEntry) {
    BackHandler { vm.cancelEdit() }
    val selectedFood = vm.editingFood() ?: vm.foods.firstOrNull { it.id == entry.foodId }
    var amount by remember(entry.id) { mutableStateOf(entry.enteredAmount.pretty()) }
    var unit by remember(entry.id) { mutableStateOf(entry.enteredUnit) }
    var meal by remember(entry.id) { mutableStateOf(entry.mealSlot) }
    var dayKey by remember(entry.id) { mutableStateOf(entry.dayKey) }
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(dayKey) }.getOrElse { LocalDate.now() }
    val units = selectedFood?.let(vm::editUnits).orEmpty().ifEmpty { listOf(entry.enteredUnit) }
    val preview = selectedFood?.let { food -> amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.draftCalculation(food.id, it, unit) } }
    LaunchedEffect(selectedFood?.id) { if (unit !in units) unit = units.first() }
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::cancelEdit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnGround) }
                Text("Edit entry", style = MaterialTheme.typography.titleLarge, color = OnGround, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedFood?.let { vm.saveFullEdit(it.id, amount, unit, meal, dayKey) } }, enabled = !vm.busy) { Text("Save", color = PenOnDarkInk, fontWeight = FontWeight.Bold) }
            }
            Surface(Modifier.padding(horizontal = 14.dp), color = Paper, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(selectedFood?.name ?: entry.foodName, {}, readOnly = true, label = { Text("Food") }, singleLine = true, modifier = Modifier.weight(1f).height(56.dp))
                        TextButton(onClick = vm::openEditFoodPicker, Modifier.height(48.dp).background(PenTonal, RoundedCornerShape(24.dp))) { Text("Replace", color = PenTonalInk, fontWeight = FontWeight.Bold) }
                    }
                    OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(140.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { units.forEach { candidate -> ReceiptChoiceChip(candidate.displayLabel(), unit == candidate, { unit = candidate }) } }
                    Text("Meal", color = InkSoft)
                    MealSlot.entries.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { candidate -> ReceiptChoiceChip(candidate.label(), meal == candidate, { meal = candidate }, Modifier.weight(1f)) } } }
                    Text("Date", color = InkSoft)
                    OutlinedButton(onClick = { DatePickerDialog(context, { _, year, month, day -> dayKey = LocalDate.of(year, month + 1, day).toString() }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, Modifier.fillMaxWidth().height(48.dp)) { Text(date.format(DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)), Modifier.weight(1f), color = Ink); Icon(Icons.Default.CalendarMonth, "Change date") }
                }
            }
            ReceiptTornEdge(fill = Paper)
            Surface(Modifier.padding(14.dp), color = Paper, shape = RoundedCornerShape(6.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Entry", color = InkSoft); Text("${receiptKcal(preview?.nutrition?.calories ?: entry.nutritionSnapshot.calories)} kcal", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold) }
                    Text(preview?.let { "P ${it.nutrition.protein?.pretty() ?: "—"} g   C ${it.nutrition.carbohydrates?.pretty() ?: "—"} g   F ${it.nutrition.fat?.pretty() ?: "—"} g · was ${receiptKcal(entry.nutritionSnapshot.calories)} kcal" } ?: "P ${entry.nutritionSnapshot.protein?.pretty() ?: "—"} g   C ${entry.nutritionSnapshot.carbohydrates?.pretty() ?: "—"} g   F ${entry.nutritionSnapshot.fat?.pretty() ?: "—"} g", color = Ink, modifier = Modifier.padding(top = 12.dp))
                }
            }
            if (vm.editInvalid) Text(vm.mealDraftError ?: "Could not save this entry.", Modifier.padding(horizontal = 14.dp), color = PrintRed)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EntryFoodPicker(vm: BreakfastViewModel) {
    ReceiptBottomSheet(onDismissRequest = vm::closeEditFoodPicker) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Replace food", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold)
            vm.foods.filterNot { it.archived }.take(30).forEach { food -> TextButton(onClick = { vm.chooseEditFood(food) }, Modifier.fillMaxWidth().height(48.dp)) { Text(food.name, color = Ink) } }
        }
    }
}

private fun Modifier.dashedTopRule(): Modifier = drawBehind { drawLine(com.iron.breakfast.ui.theme.Rule, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))) }
private fun Long.formatClock(): String = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
