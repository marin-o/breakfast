package com.iron.breakfast.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.label
import com.iron.breakfast.pretty
import com.iron.breakfast.ui.components.ApproxTag
import com.iron.breakfast.ui.components.CharacterRule
import com.iron.breakfast.ui.components.ReceiptBottomSheet
import com.iron.breakfast.ui.components.ReceiptBottomTray
import com.iron.breakfast.ui.components.ReceiptChoiceChip
import com.iron.breakfast.ui.components.ReceiptField
import com.iron.breakfast.ui.components.ReceiptNavigationBar
import com.iron.breakfast.ui.components.ReceiptOutlinedButton
import com.iron.breakfast.ui.components.ReceiptPrimaryButton
import com.iron.breakfast.ui.components.ReceiptScanButton
import com.iron.breakfast.ui.components.ReceiptTonalChip
import com.iron.breakfast.ui.components.ReceiptTonalIconButton
import com.iron.breakfast.ui.components.receiptFoodName
import com.iron.breakfast.ui.theme.LocalReceiptColors

@Composable
internal fun LibraryScreen(vm: BreakfastViewModel, modifier: Modifier) {
    val colors = LocalReceiptColors.current
    val density = LocalDensity.current
    var menuOpen by remember { mutableStateOf(false) }
    val archived = vm.libraryArchivedOnly
    val root = vm.slot == null
    val title = if (archived) "ARCHIVED" else if (root) "LIBRARY" else "ADD TO ${vm.slot!!.name}"
    val subtitle = when {
        archived -> ""
        root -> "${vm.libraryFoods.size} foods, by last logged"
        else -> entryDateLabel(vm.selectedDayKey)
    }
    BackHandler(enabled = menuOpen) { menuOpen = false }
    LazyColumn(modifier.fillMaxSize().background(colors.paper).padding(horizontal = 14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (archived || !root) IconButton(onClick = { if (archived) vm.openLibrary() else vm.closeMeal() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }
                Column(Modifier.weight(1f), horizontalAlignment = if (root && !archived) Alignment.CenterHorizontally else Alignment.Start) {
                    Text(title, color = colors.ink, style = if (root && !archived) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!archived) Text(subtitle, color = colors.soft)
                }
                if (root && !archived) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Library menu", tint = colors.ink) }
                        if (menuOpen) Popup(
                            alignment = Alignment.TopEnd,
                            offset = with(density) { androidx.compose.ui.unit.IntOffset(0, 48.dp.roundToPx()) },
                            onDismissRequest = { menuOpen = false },
                            properties = PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true),
                        ) {
                            Column(Modifier.width(220.dp).background(colors.paper).border(2.dp, colors.ink).padding(vertical = 2.dp)) {
                                TextButton(onClick = { menuOpen = false; vm.openArchivedLibrary() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Archived foods", color = colors.ink, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                } else Spacer(Modifier.size(48.dp))
            }
            CharacterRule()
            if (archived) {
                Text("${vm.libraryFoods.size} foods", color = colors.soft, modifier = Modifier.padding(vertical = 4.dp))
                CharacterRule()
            }
            Text(if (archived) "ITEM, ARCHIVED                 KCAL" else "ITEM, LAST                       KCAL", color = colors.soft, modifier = Modifier.padding(vertical = 2.dp))
            CharacterRule()
        }
        items(vm.libraryFoods, key = { it.id }) { food -> LibraryFoodRow(vm, food, root, archived) }
        item {
            CharacterRule('=')
            if (archived) Text("Past entries keep their numbers.", color = colors.soft, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun LibraryFoodRow(vm: BreakfastViewModel, food: FoodRecord, root: Boolean, archived: Boolean) {
    val colors = LocalReceiptColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val last = vm.lastLoggedEntry(food)
    val repeatAmount = last?.enteredAmount ?: food.defaultAmount
    val repeatUnit = last?.enteredUnit ?: food.defaultUnit
    val expanded = root && !archived && vm.expandedLibraryFoodId == food.id
    val target = vm.slot
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(role = Role.Button) { vm.openLibraryFood(food) }.padding(vertical = 5.dp)) {
                Text(receiptFoodName(food.name), color = colors.ink)
                Text(if (archived) "archived" else if (last == null) "${repeatAmount.pretty()} ${repeatUnit.displayLabel()}" else "last ${repeatAmount.pretty()} ${repeatUnit.displayLabel()}", color = colors.soft)
            }
            Text(last?.let { printedFactKcal(it.nutritionSnapshot.calories) } ?: defaultServingCalories(food), color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp * fontScale), textAlign = TextAlign.End)
            Text(sourceLetter(food), color = colors.soft, modifier = Modifier.width(20.dp * fontScale), textAlign = TextAlign.End)
            if (!archived) ReceiptTonalIconButton(icon = if (expanded) Icons.Default.Close else Icons.Default.Add, contentDescription = if (target == null) "Add ${food.name} to a meal" else "Add ${food.name} to ${target.label()}", onClick = { if (root) vm.toggleLibraryFoodPicker(food) else vm.logLibraryFood(food, target!!, repeatAmount, repeatUnit) }, enabled = !food.archived, modifier = Modifier.padding(start = 8.dp))
        }
        if (expanded) {
            Column(Modifier.padding(start = 2.dp, bottom = 8.dp)) {
                Text("Add ${repeatAmount.pretty()} ${repeatUnit.displayLabel()} to", color = colors.soft, modifier = Modifier.padding(bottom = 6.dp))
                LibraryMealChoices(selected = null, onSelect = { meal -> vm.logLibraryFood(food, meal, repeatAmount, repeatUnit); vm.collapseLibraryFoodPicker() })
            }
        }
    }
}

private fun defaultServingCalories(food: FoodRecord): String = printedFactKcal(com.iron.breakfast.defaultServingCalculation(food).nutrition.calories)

@Composable
internal fun LibraryComposer(vm: BreakfastViewModel) {
    val colors = LocalReceiptColors.current
    val inMeal = vm.slot != null
    ReceiptBottomTray(modifier = if (inMeal) Modifier else Modifier.navigationBarsPadding(), content = {
        Row(Modifier.fillMaxWidth().then(if (inMeal) Modifier.navigationBarsPadding() else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f).heightIn(min = 52.dp).border(2.dp, colors.ink).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = colors.ink, modifier = Modifier.size(18.dp))
                BasicTextField(value = vm.input, onValueChange = vm::onInput, modifier = Modifier.weight(1f).padding(start = 8.dp).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { vm.submitLibraryInput(); true } else false }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { vm.submitLibraryInput() }), decorationBox = { inner -> Box { if (vm.input.isBlank()) Text("Search foods", color = colors.soft); inner() } })
            }
            ReceiptScanButton(vm::beginScan)
        }
    }, nav = { if (!inMeal) ReceiptNavigationBar(selectedDaily = false, onDaily = { vm.closeMeal(); vm.closeLibrary() }, onLibrary = {}) })
}

private fun servingDisplayName(conversion: ServingConversion): String = conversion.label?.takeIf { it.isNotBlank() } ?: conversion.unit.displayLabel()
@Composable private fun ServingValue(conversion: ServingConversion, food: FoodRecord) { val value = "${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}"; if (conversion.approximate) ApproxTag(value) else Text(value, color = LocalReceiptColors.current.ink, fontWeight = FontWeight.Bold) }

@Composable
internal fun LibraryFoodDetailDialog(vm: BreakfastViewModel, food: FoodRecord) {
    val colors = LocalReceiptColors.current
    var amountSheetOpen by remember(food.id) { mutableStateOf(false) }
    var mealMenuOpen by remember(food.id) { mutableStateOf(false) }
    var targetMeal by remember(food.id) { mutableStateOf(vm.slot ?: MealSlot.LUNCH) }
    val servings = food.householdServings()
    BackHandler { vm.closeLibraryFood() }
    Column(Modifier.fillMaxSize().background(colors.paper).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeLibraryFood) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }
            Text("FOOD", color = colors.ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!food.archived) TextButton(onClick = { vm.closeLibraryFood(); vm.openLibraryEdit(food) }, Modifier.heightIn(min = 48.dp)) { Text("EDIT", color = colors.ink, fontWeight = FontWeight.Bold) }
        }
        CharacterRule()
        if (food.archived) Text("ARCHIVED", Modifier.fillMaxWidth().background(colors.ink).padding(horizontal = 8.dp, vertical = 2.dp), color = colors.paper, fontWeight = FontWeight.Bold)
        Text(receiptFoodName(food.name), color = colors.ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        food.brand?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.soft) }
        Text("${sourceLetter(food)}  ${sourceLabelFor(food)}", color = colors.soft)
        food.barcode?.let { Text("Barcode $it", color = colors.soft) }
        CharacterRule()
        Text(basisLabel(food.nutrition.amount, food.nutrition.unit).uppercase(), color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        LibraryFactRow("Energy", printedFactKcal(food.nutrition.values.calories), "kcal")
        LibraryFactRow("Fat", food.nutrition.values.fat?.pretty() ?: "—", "g")
        LibraryFactRow("Carbohydrate", food.nutrition.values.carbohydrates?.pretty() ?: "—", "g")
        LibraryFactRow("Protein", food.nutrition.values.protein?.pretty() ?: "—", "g")
        if (servings.isNotEmpty()) { CharacterRule(); Text("SERVINGS", color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); servings.forEach { conversion -> Row { Text("1 ${servingDisplayName(conversion)} = ", color = colors.ink); ServingValue(conversion, food) } } }
        if (food.aliases.isNotEmpty()) { CharacterRule(); Text("ALIASES", color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); food.aliases.forEach { Text(it, color = colors.ink) } }
        CharacterRule('=')
        if (food.archived) ReceiptPrimaryButton("Restore", onClick = { vm.restoreLibraryFood(food) }, Modifier.padding(top = 16.dp)) else {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ReceiptPrimaryButton("Log to ${targetMeal.name}", onClick = { amountSheetOpen = true }, Modifier.weight(1f)); TextButton(onClick = { mealMenuOpen = true }, Modifier.width(128.dp).heightIn(min = 52.dp).border(2.dp, colors.ink)) { Text("${targetMeal.name} v", color = colors.ink, fontWeight = FontWeight.Bold) } }
            if (mealMenuOpen) LibraryMealChoices(selected = targetMeal, onSelect = { targetMeal = it; mealMenuOpen = false })
            if (contributionAvailable(food)) TextButton(onClick = { vm.closeLibraryFood(); vm.openContributionForFood(food) }, Modifier.fillMaxWidth().padding(top = 22.dp).heightIn(min = 52.dp).border(2.dp, colors.ink)) { Text("CONTRIBUTE TO OPEN FOOD FACTS", color = colors.ink, fontWeight = FontWeight.Bold) }
            if (food.origin == FoodOrigin.CUSTOM) TextButton(onClick = { vm.archiveLibraryFood(food) }, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp).border(2.dp, colors.red)) { Text("ARCHIVE FOOD", color = colors.red, fontWeight = FontWeight.Bold) }
        }
    }
    if (amountSheetOpen) LibraryAmountSheet(vm, food, targetMeal) { amountSheetOpen = false }
}

@Composable
private fun LibraryFactRow(label: String, number: String, unit: String) {
    val colors = LocalReceiptColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val numberWidth = 60.dp * fontScale
    val unitWidth = 50.dp * fontScale
    if (fontScale >= 1.75f) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = colors.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(number, color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(numberWidth), textAlign = TextAlign.End)
                Text(unit, color = colors.soft, maxLines = 1, modifier = Modifier.width(unitWidth).padding(start = 10.dp))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.ink, modifier = Modifier.weight(1f))
            Text(number, color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(numberWidth), textAlign = TextAlign.End)
            Text(unit, color = colors.soft, maxLines = 1, modifier = Modifier.width(unitWidth).padding(start = 10.dp))
        }
    }
}

@Composable
private fun LibraryMealChoices(selected: MealSlot?, onSelect: (MealSlot) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        MealSlot.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { meal -> ReceiptChoiceChip(meal.name, selected == meal, { onSelect(meal) }, Modifier.weight(1f)) }
            }
        }
    }
}
@Composable internal fun FocusedFoodAddDialog(vm: BreakfastViewModel, food: FoodRecord) { val target = vm.slot ?: return; LibraryAmountSheet(vm, food, target, vm::closeLibraryFood) }

@Composable
internal fun FoodBarcodeField(vm: BreakfastViewModel, label: String = "Barcode") {
    val draft = vm.draft
    ReceiptField(label, draft.barcode, { draft.barcode = it.asciiDigits() }, onPaper = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), trailing = { if (draft.barcode.isNotBlank()) ReceiptTonalIconButton(Icons.Default.Close, "Clear barcode", { draft.barcode = "" }); ReceiptTonalIconButton(Icons.Default.QrCodeScanner, "Scan barcode", vm::beginBarcodeRescan) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFoodEditScreen(vm: BreakfastViewModel) {
    val colors = LocalReceiptColors.current
    val food = vm.foods.firstOrNull { it.id == vm.editingLibraryFoodId } ?: return
    val draft = vm.draft
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BackHandler { vm.closeCustom() }
    Column(Modifier.fillMaxSize().background(colors.paper).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = vm::closeCustom) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }; Text("EDIT FOOD", color = colors.ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) }
        CharacterRule(); ReceiptField("Name", draft.name, { draft.name = it }, onPaper = true); ReceiptField("Brand", draft.brand, { draft.brand = it }, onPaper = true, modifier = Modifier.padding(top = 12.dp)); FoodBarcodeField(vm)
        Text("Source: ${sourceLetter(food)}  ${sourceLabelFor(food)}", color = colors.soft, modifier = Modifier.padding(top = 12.dp)); Text("Local edits stay on this device", color = colors.soft); CharacterRule(); Text(basisLabel(food.nutrition.amount, food.nutrition.unit).uppercase(), color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ReceiptField("kcal", draft.calories, { draft.calories = it }, Modifier.weight(1f), onPaper = true); ReceiptField("Protein, g", draft.protein, { draft.protein = it }, Modifier.weight(1f), onPaper = true) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ReceiptField("Carbs, g", draft.carbs, { draft.carbs = it }, Modifier.weight(1f), onPaper = true); ReceiptField("Fat, g", draft.fat, { draft.fat = it }, Modifier.weight(1f), onPaper = true) }
        CharacterRule(); Text("SERVINGS", color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); draft.servings.forEach { conversion -> Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) { Text("1 ${servingDisplayName(conversion)} = ${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}", Modifier.weight(1f), color = colors.ink); ReceiptTonalIconButton(Icons.Default.Close, "Remove serving", { draft.servings = draft.servings - conversion }) } }
        if (draft.servingFormOpen) {
            val amountError = draft.servingAmount.toDoubleOrNull()?.let { !it.isFinite() || it <= 0.0 } ?: true
            Column(
                Modifier.fillMaxWidth().drawBehind {
                    val stroke = 2.dp.toPx()
                    drawRect(colors.ink, style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
                }.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("NEW SERVING", color = colors.ink, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1", color = colors.ink)
                    ReceiptField("Name", draft.servingLabel, { draft.servingLabel = it }, Modifier.weight(1f), onPaper = true)
                }
                Text("=", color = colors.ink, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    ReceiptField("Amount", draft.servingAmount, { draft.servingAmount = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f), onPaper = true, error = amountError, errorMessage = if (amountError) "Enter more than 0 ${draft.servingUnit.label}." else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(Modifier.width(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(MeasureUnit.GRAM, MeasureUnit.MILLILITER).forEach { unit -> ReceiptChoiceChip(unit.displayLabel(), draft.servingUnit == unit, { draft.servingUnit = unit }, Modifier.width(56.dp)) } }
                }
                Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReceiptOutlinedButton("ADD SERVING", vm::commitDraftServing, Modifier.widthIn(min = 150.dp * fontScale).padding(horizontal = 2.dp), fillWidth = false)
                    TextButton(onClick = draft::cancelServing, Modifier.heightIn(min = 48.dp)) { Text("CANCEL", color = colors.ink, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline) }
                }
            }
        } else ReceiptOutlinedButton("+ Add serving", { draft.servingUnit = food.nutrition.unit.takeIf { it == MeasureUnit.GRAM || it == MeasureUnit.MILLILITER } ?: MeasureUnit.GRAM; draft.servingAmount = "0"; draft.servingLabel = ""; draft.servingFormOpen = true }, Modifier.widthIn(min = 150.dp * fontScale), fillWidth = false)
        CharacterRule(); Text("ALIASES", color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); draft.aliases.forEach { alias -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).border(2.dp, colors.ink), verticalAlignment = Alignment.CenterVertically) { Text(alias, Modifier.weight(1f).padding(start = 10.dp), color = colors.ink); ReceiptTonalIconButton(Icons.Default.Close, "Remove alias $alias", { draft.aliases = draft.aliases - alias }) } }
        if (draft.aliasFieldOpen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ReceiptField("New alias", draft.aliasInput, { draft.aliasInput = it }, Modifier.weight(1f), onPaper = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { draft.commitAlias() }))
                ReceiptTonalChip("ADD", { draft.commitAlias() })
            }
        } else TextButton(onClick = { draft.aliasFieldOpen = true }, Modifier.heightIn(min = 48.dp).border(2.dp, colors.ink)) { Text("+ ADD ALIAS", color = colors.ink, fontWeight = FontWeight.Bold) }
        ReceiptPrimaryButton("Save", vm::saveCustom, Modifier.padding(top = 22.dp)); CharacterRule(); if (food.origin == FoodOrigin.CUSTOM && !food.archived) TextButton(onClick = { vm.archiveLibraryFood(food) }, Modifier.fillMaxWidth().heightIn(min = 52.dp).border(2.dp, colors.red)) { Text("ARCHIVE FOOD", color = colors.red, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryAmountSheet(vm: BreakfastViewModel, food: FoodRecord, target: MealSlot, onDismiss: () -> Unit) {
    val colors = LocalReceiptColors.current
    var amountText by remember(food.id, target) { mutableStateOf((vm.lastLoggedEntry(food)?.enteredAmount ?: food.defaultAmount).pretty()) }
    var unit by remember(food.id, target) { mutableStateOf(vm.lastLoggedEntry(food)?.enteredUnit ?: food.defaultUnit) }
    var chosenMeal by remember(food.id, target) { mutableStateOf(target) }
    val units = (listOf(food.defaultUnit, food.nutrition.unit) + food.conversions.map { it.unit }).filter { it != MeasureUnit.UNKNOWN }.distinct()
    val calc = amountText.toDoubleOrNull()?.takeIf { it > 0 }?.let { NutritionCalculator().calculate(food, it, unit) }
    ReceiptBottomSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) { Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(receiptFoodName(food.name), color = colors.ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("${sourceLetter(food)}  ${sourceLabelFor(food)}", color = colors.soft); Text("Amount", color = colors.soft)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ReceiptField("Amount", amountText, { amountText = it.asciiDigits(allowDecimalPoint = true) }, Modifier.width(96.dp), onPaper = true, labelVisible = false, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { units.forEach { candidate -> ReceiptChoiceChip(candidate.displayLabel(), unit == candidate, { unit = candidate }) } } }
        Text("Meal", color = colors.soft); LibraryMealChoices(selected = chosenMeal, onSelect = { chosenMeal = it }); CharacterRule(); Text(calc?.let { "${receiptKcal(it.nutrition.calories)} kcal" } ?: "Enter a positive amount", color = colors.ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(calc?.let { "P ${it.nutrition.protein?.pretty() ?: "—"}  C ${it.nutrition.carbohydrates?.pretty() ?: "—"}  F ${it.nutrition.fat?.pretty() ?: "—"}" } ?: "", color = colors.soft)
        ReceiptPrimaryButton("Add to ${chosenMeal.name}", onClick = { val amount = amountText.toDoubleOrNull(); if (amount != null && amount > 0 && calc?.supported == true) { vm.logLibraryFood(food, chosenMeal, amount, unit); onDismiss() } else vm.noticeForInvalidLibraryAmount() }, enabled = !food.archived && calc?.supported == true)
    } }
}

private fun sourceLabelFor(food: FoodRecord): String = when (sourceLetter(food)) { "B" -> "Bundled food"; "P" -> "Personal"; "O" -> "Open Food Facts"; else -> "Food" }
