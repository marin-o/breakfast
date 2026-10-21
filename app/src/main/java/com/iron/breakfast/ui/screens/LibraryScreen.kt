package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.MealChoices
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.InkBar
import com.iron.breakfast.ui.components.ScanBarcodeIcon
import com.iron.breakfast.ui.components.SearchField
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.Sheet
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.BottomNav
import com.iron.breakfast.ui.theme.LocalBreakfastColors

@Composable
internal fun LibraryScreen(vm: BreakfastViewModel, modifier: Modifier) {
    val colors = LocalBreakfastColors.current
    val density = LocalDensity.current
    var menuOpen by remember { mutableStateOf(false) }
    val archived = vm.libraryArchivedOnly
    val root = vm.slot == null
    val title = if (archived) "Archived" else if (root) "Library" else "Add to ${vm.slot!!.label()}"
    val subtitle = when {
        archived -> ""
        root -> "${vm.libraryFoods.size} foods, by last logged"
        else -> entryDateLabel(vm.selectedDayKey)
    }
    BackHandler(enabled = menuOpen) { menuOpen = false }
    LazyColumn(modifier.fillMaxSize().background(colors.bg).padding(horizontal = Gutter)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (archived || !root) IconButton(onClick = { if (archived) vm.openLibrary() else vm.closeMeal() }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.ink)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(title, color = colors.ink, style = BreakfastType.Section)
                    if (!archived) Text(subtitle, color = colors.soft, style = BreakfastType.Meta)
                }
                if (root && !archived) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.MoreVert, "Library menu", tint = colors.ink) }
                        if (menuOpen) Popup(
                            alignment = Alignment.TopEnd,
                            offset = with(density) { androidx.compose.ui.unit.IntOffset(0, 48.dp.roundToPx()) },
                            onDismissRequest = { menuOpen = false },
                            properties = PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true),
                        ) {
                            // The one shadow in the app: the overflow menu popover.
                            Column(Modifier.widthIn(min = 220.dp).shadow(6.dp, SecondaryShape).background(colors.bg).border(1.dp, colors.line, SecondaryShape).padding(vertical = 2.dp)) {
                                TertiaryButton("Archived foods", { menuOpen = false; vm.openArchivedLibrary() }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else Spacer(Modifier.size(44.dp))
            }
            Hairline()
            if (archived) {
                Text("${vm.libraryFoods.size} foods", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(vertical = 8.dp))
                Hairline()
            }
        }
        items(vm.libraryFoods, key = { it.id }) { food -> LibraryFoodRow(vm, food, root, archived) }
        item {
            Hairline(Modifier.padding(top = 8.dp))
            if (archived) Text("Past entries keep their numbers.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun LibraryFoodRow(vm: BreakfastViewModel, food: FoodRecord, root: Boolean, archived: Boolean) {
    val colors = LocalBreakfastColors.current
    val last = vm.lastLoggedEntry(food)
    val repeatAmount = last?.enteredAmount ?: food.defaultAmount
    val repeatUnit = last?.enteredUnit ?: food.defaultUnit
    val expanded = root && !archived && vm.expandedLibraryFoodId == food.id
    val target = vm.slot
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FoodRow(
                name = food.name,
                quantityLine = if (archived) "Archived" else if (last == null) "${repeatAmount.pretty()} ${repeatUnit.displayLabel()}" else "last ${repeatAmount.pretty()} ${repeatUnit.displayLabel()}",
                calories = last?.let { receiptKcal(it.nutritionSnapshot.calories) } ?: defaultServingCalories(food),
                modifier = Modifier.weight(1f),
                onClick = { vm.openLibraryFood(food) },
            )
            if (!archived) AddCircle(
                contentDescription = if (target == null) "Add ${food.name} to a meal" else "Add ${food.name} to ${target.label()}",
                onClick = { if (root) vm.toggleLibraryFoodPicker(food) else vm.logLibraryFood(food, target!!, repeatAmount, repeatUnit) },
                enabled = !food.archived,
                icon = if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
            )
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Add ${repeatAmount.pretty()} ${repeatUnit.displayLabel()} to", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(bottom = 6.dp))
                MealChoices(selected = null, onSelect = { meal -> vm.logLibraryFood(food, meal, repeatAmount, repeatUnit); vm.collapseLibraryFoodPicker() }, singleRow = true)
            }
        }
    }
}

private fun defaultServingCalories(food: FoodRecord): String = receiptKcal(com.iron.breakfast.defaultServingCalculation(food).nutrition.calories)

/**
 * The Library's bottom bar: the Composer look (ink bar, "Search foods") over the existing text field.
 * It filters the list through [BreakfastViewModel.submitLibraryInput] and does not open the Log page.
 */
@Composable
internal fun LibraryComposer(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val inMeal = vm.slot != null
    Column(Modifier.fillMaxWidth().background(colors.bg).then(if (inMeal) Modifier else Modifier.navigationBarsPadding()).padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InkBar(if (inMeal) Modifier.navigationBarsPadding() else Modifier) {
            BasicTextField(
                value = vm.input,
                onValueChange = vm::onInput,
                singleLine = true,
                textStyle = BreakfastType.Body.copy(color = colors.bg),
                cursorBrush = SolidColor(colors.bg),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submitLibraryInput() }, onDone = { vm.submitLibraryInput() }),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { vm.submitLibraryInput(); true } else false }
                    .semantics { contentDescription = "Search foods" },
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 22.dp), contentAlignment = Alignment.CenterStart) {
                        if (vm.input.isEmpty()) Text("Search foods", color = colors.bg.copy(alpha = 0.6f), style = BreakfastType.Body)
                        inner()
                    }
                },
            )
            if (vm.input.isNotEmpty()) IconButton(onClick = { vm.onInput("") }, modifier = Modifier.size(IconTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = colors.bg, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = vm::beginScan, modifier = Modifier.size(IconTarget)) {
                Icon(ScanBarcodeIcon, contentDescription = "Scan barcode", tint = colors.bg)
            }
        }
        if (!inMeal) BottomNav(selectedToday = false, onToday = { vm.closeMeal(); vm.closeLibrary() }, onLibrary = {})
    }
}

private fun servingDisplayName(conversion: ServingConversion): String = conversion.label?.takeIf { it.isNotBlank() } ?: conversion.unit.displayLabel()

/** The serving's name with its count: "1 tbsp", "1 bottle". A printed name that already starts with a count is kept as typed. */
internal fun servingRowName(conversion: ServingConversion): String {
    val name = servingDisplayName(conversion)
    return if (name.firstOrNull()?.isDigit() == true) name else "1 $name"
}

@Composable private fun ServingValue(conversion: ServingConversion, food: FoodRecord) {
    val value = "${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}"
    Text(if (conversion.approximate) "≈$value" else value, color = LocalBreakfastColors.current.soft, style = BreakfastType.Body)
}

@Composable
internal fun LibraryFoodDetailDialog(vm: BreakfastViewModel, food: FoodRecord) {
    val colors = LocalBreakfastColors.current
    var amountSheetOpen by remember(food.id) { mutableStateOf(false) }
    var mealMenuOpen by remember(food.id) { mutableStateOf(false) }
    var targetMeal by remember(food.id) { mutableStateOf(vm.slot ?: MealSlot.LUNCH) }
    val servings = food.householdServings()
    BackHandler { vm.closeLibraryFood() }
    Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp)) {
        TopBar(
            title = "Food",
            onBack = vm::closeLibraryFood,
            modifier = Modifier.padding(top = 8.dp),
            trailing = { if (!food.archived) TertiaryButton("Edit", { vm.closeLibraryFood(); vm.openLibraryEdit(food) }) },
        )
        Hairline(Modifier.padding(top = 8.dp))
        Text(foodText(food.name), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 8.dp))
        if (food.archived) Text("Archived", color = colors.soft, style = BreakfastType.Secondary)
        food.brand?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.soft, style = BreakfastType.Secondary) }
        Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
        food.barcode?.let { Text("Barcode $it", color = colors.soft, style = BreakfastType.Secondary) }
        Hairline(Modifier.padding(top = 16.dp))
        SectionHeading(basisLabel(food.nutrition.amount, food.nutrition.unit), Modifier.padding(top = 16.dp))
        LibraryFactRow("Energy", receiptKcal(food.nutrition.values.calories), "kcal")
        LibraryFactRow("Fat", food.nutrition.values.fat?.pretty() ?: "—", "g")
        LibraryFactRow("Carbohydrate", food.nutrition.values.carbohydrates?.pretty() ?: "—", "g")
        LibraryFactRow("Protein", food.nutrition.values.protein?.pretty() ?: "—", "g")
        if (servings.isNotEmpty()) {
            Hairline(Modifier.padding(top = 16.dp))
            SectionHeading("Servings", Modifier.padding(top = 16.dp))
            servings.forEach { conversion ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(servingRowName(conversion), color = colors.ink, style = BreakfastType.Body)
                    ServingValue(conversion, food)
                }
            }
        }
        if (food.aliases.isNotEmpty()) {
            Hairline(Modifier.padding(top = 16.dp))
            SectionHeading("Aliases", Modifier.padding(top = 16.dp))
            food.aliases.forEach { Text(it, color = colors.ink, style = BreakfastType.Body, modifier = Modifier.padding(top = 4.dp)) }
        }
        Hairline(Modifier.padding(top = 16.dp))
        if (food.archived) {
            PrimaryButton("Restore", onClick = { vm.restoreLibraryFood(food) }, Modifier.padding(top = 24.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(targetMeal.label(), { mealMenuOpen = true }, Modifier.widthIn(min = 128.dp), fillWidth = false, trailing = Icons.Outlined.KeyboardArrowDown)
                PrimaryButton("Log to ${targetMeal.label()}", onClick = { amountSheetOpen = true }, Modifier.weight(1f))
            }
            if (mealMenuOpen) MealChoices(selected = targetMeal, onSelect = { targetMeal = it; mealMenuOpen = false }, modifier = Modifier.padding(top = 8.dp))
            if (contributionAvailable(food)) SecondaryButton("Contribute to Open Food Facts", { vm.closeLibraryFood(); vm.openContributionForFood(food) }, Modifier.padding(top = 16.dp))
            if (food.origin == FoodOrigin.CUSTOM) DestructiveButton("Archive food", { vm.archiveLibraryFood(food) }, Modifier.fillMaxWidth().padding(top = 8.dp), outlined = true)
        }
    }
    if (amountSheetOpen) LibraryAmountSheet(vm, food, targetMeal) { amountSheetOpen = false }
}

@Composable
private fun LibraryFactRow(label: String, number: String, unit: String) {
    val colors = LocalBreakfastColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    if (isLargeFont(fontScale)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(label, color = colors.ink, style = BreakfastType.Body)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                Text(number, color = colors.ink, style = BreakfastType.BodyMedium)
                Text(" $unit", color = colors.soft, style = BreakfastType.Meta, maxLines = 1)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, color = colors.ink, style = BreakfastType.Body)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(number, color = colors.ink, style = BreakfastType.BodyMedium)
                Text(" $unit", color = colors.soft, style = BreakfastType.Meta, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun FocusedFoodAddDialog(vm: BreakfastViewModel, food: FoodRecord) {
    val target = vm.slot ?: return
    LibraryAmountSheet(vm, food, target, vm::closeLibraryFood)
}

@Composable
internal fun FoodBarcodeField(vm: BreakfastViewModel, label: String = "Barcode") {
    val draft = vm.draft
    val colors = LocalBreakfastColors.current
    Field(
        label, draft.barcode, { draft.barcode = it.asciiDigits() }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailing = {
            if (draft.barcode.isNotBlank()) IconButton(onClick = { draft.barcode = "" }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Close, "Clear barcode", tint = colors.soft) }
            IconButton(onClick = vm::beginBarcodeRescan, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.QrCodeScanner, "Scan barcode", tint = colors.ink) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFoodEditScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val food = vm.foods.firstOrNull { it.id == vm.editingLibraryFoodId } ?: return
    val draft = vm.draft
    BackHandler { vm.closeCustom() }
    Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TopBar("Edit food", vm::closeCustom, Modifier.padding(top = 8.dp))
        Field("Name", draft.name, { draft.name = it })
        Field("Brand", draft.brand, { draft.brand = it })
        FoodBarcodeField(vm)
        Text("Source: ${entrySourceLabel(food)}. Local edits stay on this device.", color = colors.soft, style = BreakfastType.Secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("kcal", draft.calories, { draft.calories = it }, Modifier.weight(1f))
            Field("Protein, g", draft.protein, { draft.protein = it }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Carbs, g", draft.carbs, { draft.carbs = it }, Modifier.weight(1f))
            Field("Fat, g", draft.fat, { draft.fat = it }, Modifier.weight(1f))
        }
        SectionHeading("Servings")
        draft.servings.forEach { conversion ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${servingRowName(conversion)} = ${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}", Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
                AddCircle("Remove serving", { draft.servings = draft.servings - conversion }, icon = Icons.Outlined.Close)
            }
        }
        if (draft.servingFormOpen) {
            val amountError = draft.servingAmount.toDoubleOrNull()?.let { !it.isFinite() || it <= 0.0 } ?: true
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // "1 [name] = [amount]", then the unit choices, as drawn.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text("1", color = colors.ink, style = BreakfastType.Body, modifier = Modifier.padding(top = 22.dp))
                    Field("Name", draft.servingLabel, { draft.servingLabel = it }, Modifier.weight(1f))
                    Text("=", color = colors.ink, style = BreakfastType.Body, modifier = Modifier.padding(top = 22.dp))
                    Field(
                        "Amount", draft.servingAmount, { draft.servingAmount = it.asciiDigits(allowDecimalPoint = true) }, Modifier.weight(1f),
                        error = amountError,
                        errorMessage = if (amountError) "Enter more than 0 ${draft.servingUnit.label}." else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MeasureUnit.GRAM, MeasureUnit.MILLILITER).forEach { unit ->
                        ChoiceOption(unit.displayLabel(), draft.servingUnit == unit, { draft.servingUnit = unit }, Modifier.widthIn(min = 72.dp))
                    }
                }
                SecondaryButton("Add serving", vm::commitDraftServing)
                TertiaryButton("Cancel", draft::cancelServing, Modifier.align(Alignment.CenterHorizontally), soft = true)
            }
        } else {
            TertiaryButton("Add serving", {
                draft.servingUnit = food.nutrition.unit.takeIf { it == MeasureUnit.GRAM || it == MeasureUnit.MILLILITER } ?: MeasureUnit.GRAM
                draft.servingAmount = "0"
                draft.servingLabel = ""
                draft.servingFormOpen = true
            })
        }
        SectionHeading("Aliases")
        draft.aliases.forEach { alias ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(alias, Modifier.weight(1f), color = colors.ink, style = BreakfastType.Body)
                AddCircle("Remove alias $alias", { draft.aliases = draft.aliases - alias }, icon = Icons.Outlined.Close)
            }
        }
        if (draft.aliasFieldOpen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(
                    "New alias", draft.aliasInput, { draft.aliasInput = it }, Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { draft.commitAlias() }),
                )
                SecondaryButton("Add", { draft.commitAlias() }, fillWidth = false)
            }
        } else {
            TertiaryButton("Add alias", { draft.aliasFieldOpen = true })
        }
        PrimaryButton("Save", vm::saveCustom, Modifier.padding(top = 8.dp))
        if (food.origin == FoodOrigin.CUSTOM && !food.archived) DestructiveButton("Archive food", { vm.archiveLibraryFood(food) }, Modifier.fillMaxWidth(), outlined = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryAmountSheet(vm: BreakfastViewModel, food: FoodRecord, target: MealSlot, onDismiss: () -> Unit) {
    val colors = LocalBreakfastColors.current
    var amountText by remember(food.id, target) { mutableStateOf((vm.lastLoggedEntry(food)?.enteredAmount ?: food.defaultAmount).pretty()) }
    var unit by remember(food.id, target) { mutableStateOf(vm.lastLoggedEntry(food)?.enteredUnit ?: food.defaultUnit) }
    var chosenMeal by remember(food.id, target) { mutableStateOf(target) }
    val units = (listOf(food.defaultUnit, food.nutrition.unit) + food.conversions.map { it.unit }).filter { it != MeasureUnit.UNKNOWN }.distinct()
    val calc = amountText.toDoubleOrNull()?.takeIf { it > 0 }?.let { NutritionCalculator().calculate(food, it, unit) }
    Sheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(foodText(food.name), color = colors.ink, style = BreakfastType.Section)
            Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Amount", amountText, { amountText = it.asciiDigits(allowDecimalPoint = true) }, Modifier.widthIn(min = 96.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(Modifier.weight(1f).padding(top = 22.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEach { candidate -> ChoiceOption(candidate.displayLabel(), unit == candidate, { unit = candidate }) }
                }
            }
            Text("Meal", color = colors.soft, style = BreakfastType.Meta)
            MealChoices(selected = chosenMeal, onSelect = { chosenMeal = it }, singleRow = true)
            Hairline()
            Text(calc?.let { "${receiptKcal(it.nutrition.calories)} kcal" } ?: "Enter a positive amount", color = colors.ink, style = BreakfastType.Section)
            Text(calc?.let { "P ${it.nutrition.protein?.pretty() ?: "—"}  C ${it.nutrition.carbohydrates?.pretty() ?: "—"}  F ${it.nutrition.fat?.pretty() ?: "—"}" } ?: "", color = colors.soft, style = BreakfastType.Secondary)
            PrimaryButton(
                "Add to ${chosenMeal.label()}",
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0 && calc?.supported == true) { vm.logLibraryFood(food, chosenMeal, amount, unit); onDismiss() } else vm.noticeForInvalidLibraryAmount()
                },
                enabled = !food.archived && calc?.supported == true,
            )
        }
    }
}
