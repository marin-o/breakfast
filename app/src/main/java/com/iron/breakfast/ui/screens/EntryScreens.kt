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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.iron.breakfast.ui.components.ReceiptField
import com.iron.breakfast.ui.components.ReceiptOutlinedButton
import com.iron.breakfast.ui.components.ReceiptPrimaryButton
import com.iron.breakfast.ui.components.ReceiptTonalIconButton
import com.iron.breakfast.ui.components.SourceBox
import com.iron.breakfast.ui.components.receiptDashedRule
import com.iron.breakfast.ui.components.ReceiptTicket
import com.iron.breakfast.ui.theme.ReceiptPaperShape
import com.iron.breakfast.ui.theme.Sticker
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import com.iron.breakfast.ui.components.ReceiptTornEdge
import com.iron.breakfast.ui.theme.Ground
import com.iron.breakfast.ui.theme.Ink
import com.iron.breakfast.ui.theme.InkSoft
import com.iron.breakfast.ui.theme.OnGround
import com.iron.breakfast.ui.theme.Paper
import com.iron.breakfast.ui.theme.PenBlue
import com.iron.breakfast.ui.theme.PenOnDarkInk
import com.iron.breakfast.ui.theme.PenTonal
import com.iron.breakfast.ui.theme.PenTonalInk
import com.iron.breakfast.ui.theme.PrintRed
import com.iron.breakfast.ui.theme.Rule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.math.BigDecimal
import java.util.Locale

@Composable
internal fun EntryDetailScreen(vm: BreakfastViewModel, entry: MealEntry) {
    BackHandler { vm.closeEntryDetail() }
    val food = vm.foods.firstOrNull { it.id == entry.foodId }
    val quantity = quantityRateDisplay(entry, food)
    val loggedLabel = entryDateLabel(entry.dayKey)
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::closeEntryDetail) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnGround) }
                    Text("Entry", style = MaterialTheme.typography.titleLarge, color = OnGround, fontWeight = FontWeight.Bold)
                }
                ReceiptTicket(mealName = entry.foodName, total = "${receiptKcal(entry.nutritionSnapshot.calories)} kcal") {
                    DetailFactRow("Amount") { if (entry.approximateConversion) ApproxTag("${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}") else Text("${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}", color = Ink) }
                    DetailFactRow("Weight") { Text(quantity.line, color = Ink) }
                    Row(Modifier.fillMaxWidth().dashedTopRule().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactMacroFact("P", entry.nutritionSnapshot.protein)
                        CompactMacroFact("C", entry.nutritionSnapshot.carbohydrates)
                        CompactMacroFact("F", entry.nutritionSnapshot.fat)
                    }
                    DetailFactRow("Source") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(entrySourceLabel(food), color = Ink); SourceBox(sourceLetter(food)) } }
                    matchedPhraseLabel(entry.sourcePhrase, entry.foodName)?.let { label -> DetailFactRow("Match") { Text(label, color = Ink) } }
                    Text("${entry.mealSlot.label()}, $loggedLabel, logged ${entry.savedAtEpochMs.formatClock()}", Modifier.padding(14.dp), color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { vm.closeEntryDetail(); vm.beginQuickEdit(entry) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) { Icon(Icons.Default.Edit, null); Text("Edit amount", Modifier.padding(start = 8.dp)) }
                ReceiptOutlinedButton("Replace food", { vm.closeEntryDetail(); vm.beginFullEdit(entry) })
                TextButton(onClick = { vm.openLibraryFoodDetail(entry) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open in Library", color = PenOnDarkInk, fontWeight = FontWeight.Bold) }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp).navigationBarsPadding().padding(bottom = 8.dp), color = Paper, shape = RoundedCornerShape(6.dp)) {
                TextButton(onClick = { vm.closeEntryDetail(); vm.delete(entry) }, Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Icon(Icons.Default.Delete, null, tint = PrintRed); Text("Delete entry", Modifier.padding(start = 8.dp), color = PrintRed, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable private fun androidx.compose.foundation.layout.RowScope.CompactMacroFact(label: String, value: Double?) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = InkSoft, style = MaterialTheme.typography.labelSmall)
        Text(value?.let { "${it.pretty()} g" } ?: "—", color = Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun DetailFactRow(label: String, value: @Composable () -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).dashedTopRule().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = InkSoft, style = MaterialTheme.typography.bodyMedium); value() } }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EntryQuickEditSheet(vm: BreakfastViewModel, entry: MealEntry) {
    BackHandler { vm.cancelEdit() }
    var amount by remember(entry.id) { mutableStateOf(editAmountText(entry.enteredAmount)) }
    var unit by remember(entry.id) { mutableStateOf(entry.enteredUnit) }
    val food = vm.foods.firstOrNull { it.id == entry.foodId }
    val units = food?.let(vm::editUnits).orEmpty().ifEmpty { listOf(entry.enteredUnit) }
    val preview = amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.draftCalculation(entry.foodId, it, unit) }
    val draftChanged = entryDraftNutritionChanged(entry, amount, unit, entry.foodId)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    ReceiptBottomSheet(onDismissRequest = vm::cancelEdit, skipPartiallyExpanded = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(entry.foodName, style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ReceiptTonalIconButton(Icons.Default.Remove, "Decrease amount", { amount = adjustAmount(amount, -1.0) })
                ReceiptField(
                    label = "Amount",
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.weight(1f),
                    onPaper = true,
                    labelVisible = false,
                    amountStyle = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); vm.onInput("${amount.trim()} ${unit.displayLabel()}"); vm.submit() }),
                    fieldModifier = Modifier.focusRequester(focusRequester),
                )
                ReceiptTonalIconButton(Icons.Default.Add, "Increase amount", { amount = adjustAmount(amount, 1.0) })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { units.forEach { candidate -> ReceiptChoiceChip(candidate.displayLabel(), unit == candidate, { unit = candidate }) } }
            Row(Modifier.fillMaxWidth().receiptDashedRule().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (preview != null) {
                    val baseText = "${preview.resolvedBaseAmount.pretty()} ${food?.nutrition?.unit?.displayLabel() ?: unit.displayLabel()}"
                    if (preview.approximate && food != null && unit != food.nutrition.unit) ApproxTag(baseText) else Text(baseText, color = Ink, fontWeight = FontWeight.Bold)
                    Text(", ${receiptKcal(preview.nutrition.calories)} kcal", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                } else Text("Enter a positive amount", color = InkSoft, modifier = Modifier.weight(1f))
                if (draftChanged) Text("was ${receiptKcal(entry.nutritionSnapshot.calories)}", color = InkSoft, style = MaterialTheme.typography.bodySmall)
            }
            ReceiptPrimaryButton("Save", { vm.onInput("${amount.trim()} ${unit.displayLabel()}"); vm.submit() }, enabled = amount.toDoubleOrNull()?.let { it > 0.0 } == true)
            if (vm.editInvalid) Text(vm.mealDraftError ?: "Could not save this entry.", color = PrintRed)
        }
    }
}

private fun editAmountText(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private fun adjustAmount(value: String, delta: Double): String = (value.toDoubleOrNull()?.plus(delta)?.coerceAtLeast(0.1) ?: 1.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.ROOT, it) }

private fun entryDraftNutritionChanged(entry: MealEntry, amount: String, unit: MeasureUnit, foodId: String?): Boolean =
    foodId != entry.foodId || amount.toDoubleOrNull()?.let { it != entry.enteredAmount } != false || unit != entry.enteredUnit

@Composable
internal fun EntryFullEditScreen(vm: BreakfastViewModel, entry: MealEntry) {
    BackHandler { vm.cancelEdit() }
    val selectedFood = vm.editingFood() ?: vm.foods.firstOrNull { it.id == entry.foodId }
    var amount by remember(entry.id) { mutableStateOf(editAmountText(entry.enteredAmount)) }
    var unit by remember(entry.id) { mutableStateOf(entry.enteredUnit) }
    var meal by remember(entry.id) { mutableStateOf(entry.mealSlot) }
    var dayKey by remember(entry.id) { mutableStateOf(entry.dayKey) }
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(dayKey) }.getOrElse { LocalDate.now() }
    val units = selectedFood?.let(vm::editUnits).orEmpty().ifEmpty { listOf(entry.enteredUnit) }
    val preview = selectedFood?.let { food -> amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.draftCalculation(food.id, it, unit) } }
    val draftChanged = entryDraftNutritionChanged(entry, amount, unit, selectedFood?.id)
    LaunchedEffect(selectedFood?.id) { if (unit !in units) unit = units.first() }
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::cancelEdit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnGround) }
                Text("Edit entry", style = MaterialTheme.typography.titleLarge, color = OnGround, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedFood?.let { vm.saveFullEdit(it.id, amount, unit, meal, dayKey) } }, enabled = !vm.busy) { Text("Save", color = PenOnDarkInk, fontWeight = FontWeight.Bold) }
            }
            Surface(Modifier.padding(horizontal = 14.dp), color = Paper, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReceiptField("Food", selectedFood?.name ?: entry.foodName, {}, Modifier.weight(1f), onPaper = true, readOnly = true)
                        TextButton(onClick = vm::openEditFoodPicker, Modifier.padding(top = 22.dp).heightIn(min = 48.dp).background(PenTonal, RoundedCornerShape(24.dp))) { Text("Replace", color = PenTonalInk, fontWeight = FontWeight.Bold) }
                    }
                    ReceiptField("Amount", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.width(140.dp), onPaper = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { units.forEach { candidate -> ReceiptChoiceChip(candidate.displayLabel(), unit == candidate, { unit = candidate }) } }
                    Text("Meal", color = InkSoft)
                    MealSlot.entries.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { candidate -> ReceiptChoiceChip(candidate.label(), meal == candidate, { meal = candidate }, Modifier.weight(1f)) } } }
                    Text("Date", color = InkSoft)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp).background(Sticker, ReceiptPaperShape).border(1.5.dp, Rule, ReceiptPaperShape)
                            .clickable(role = Role.Button) { DatePickerDialog(context, { _, year, month, day -> dayKey = LocalDate.of(year, month + 1, day).toString() }, date.year, date.monthValue - 1, date.dayOfMonth).show() }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(date.format(DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)), Modifier.weight(1f), color = Ink); Icon(Icons.Default.CalendarMonth, "Change date", tint = PenBlue) }
                }
            }
            ReceiptTornEdge(fill = Paper, Modifier.padding(horizontal = 14.dp))
            Surface(Modifier.padding(14.dp), color = Paper, shape = RoundedCornerShape(6.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Entry", color = InkSoft); Text("${receiptKcal(preview?.nutrition?.calories ?: entry.nutritionSnapshot.calories)} kcal", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold) }
                    val shown = preview?.nutrition ?: entry.nutritionSnapshot
                    Text("P ${shown.protein?.pretty() ?: "—"} g   C ${shown.carbohydrates?.pretty() ?: "—"} g   F ${shown.fat?.pretty() ?: "—"} g", color = Ink, modifier = Modifier.padding(top = 12.dp))
                    if (draftChanged) Text("was ${receiptKcal(entry.nutritionSnapshot.calories)} kcal", color = InkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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
            vm.foods.filterNot { it.archived }.take(30).forEach { food -> TextButton(onClick = { vm.chooseEditFood(food) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(food.name, color = Ink) } }
        }
    }
}

private fun Modifier.dashedTopRule(): Modifier = drawBehind { drawLine(com.iron.breakfast.ui.theme.Rule, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))) }
private fun Long.formatClock(): String = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
