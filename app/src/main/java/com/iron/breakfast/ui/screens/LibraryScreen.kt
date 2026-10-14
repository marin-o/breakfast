package com.iron.breakfast.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import com.iron.breakfast.ui.components.ReceiptActionChip
import com.iron.breakfast.ui.components.ReceiptBottomSheet
import com.iron.breakfast.ui.components.ReceiptBottomTray
import com.iron.breakfast.ui.components.ReceiptChoiceChip
import com.iron.breakfast.ui.components.ReceiptMealChips
import com.iron.breakfast.ui.components.ReceiptNavigationBar
import com.iron.breakfast.ui.components.ReceiptScanButton
import com.iron.breakfast.ui.components.ReceiptTonalChip
import com.iron.breakfast.ui.components.ReceiptTonalIconButton
import com.iron.breakfast.ui.components.ReceiptTornEdge
import com.iron.breakfast.ui.components.SourceBox
import com.iron.breakfast.ui.components.receiptDashedRule
import com.iron.breakfast.ui.components.sourceLabel
import com.iron.breakfast.ui.theme.BoxGrey
import com.iron.breakfast.ui.theme.Ground
import com.iron.breakfast.ui.theme.Ink
import com.iron.breakfast.ui.theme.InkSoft
import com.iron.breakfast.ui.theme.OnGround
import com.iron.breakfast.ui.theme.OnGroundSoft
import com.iron.breakfast.ui.theme.Paper
import com.iron.breakfast.ui.theme.PenOnDarkInk
import com.iron.breakfast.ui.theme.PenTonal
import com.iron.breakfast.ui.theme.PenTonalInk
import com.iron.breakfast.ui.theme.PrintRed
import com.iron.breakfast.ui.theme.Rule
import com.iron.breakfast.ui.theme.receiptHeadingStyle
import java.time.LocalDate
import java.util.Locale

@Composable internal fun LibraryScreen(vm: BreakfastViewModel, modifier: Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val root = vm.slot == null
    val firstId = vm.libraryFoods.firstOrNull()?.id
    LazyColumn(modifier.fillMaxSize().background(Ground).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            if (root) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Library", style = receiptHeadingStyle("Library"), color = OnGround, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Library menu", tint = OnGround) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(if (vm.libraryIncludeArchived) "Hide archived foods" else "Archived foods") }, onClick = { vm.toggleLibraryArchived(); menuOpen = false })
                            DropdownMenuItem(text = { Text("Create personal food") }, onClick = { menuOpen = false; vm.openCustom() })
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::closeMeal) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnGround) }
                    Column(Modifier.weight(1f)) {
                        Text("Add to ${vm.slot!!.label()}", color = OnGround, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(runCatching { LocalDate.parse(vm.selectedDayKey).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)) }.getOrDefault(vm.selectedDayKey), color = OnGroundSoft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Surface(Modifier.fillMaxWidth(), color = Paper, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("By last logged", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(foodCountLabel(vm.libraryFoods.size), color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (vm.libraryFoods.isEmpty()) item { Surface(Modifier.fillMaxWidth(), color = Paper) { Text("No local foods match this search.", Modifier.padding(14.dp), color = InkSoft) } }
        items(vm.libraryFoods, key = { it.id }) { food -> LibraryFoodRow(vm, food, root, first = food.id == firstId) }
        if (vm.libraryFoods.isNotEmpty()) item { ReceiptTornEdge(fill = Paper) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun LibraryFoodRow(vm: BreakfastViewModel, food: FoodRecord, root: Boolean, first: Boolean) {
    val last = vm.lastLoggedEntry(food)
    val repeatAmount = last?.enteredAmount ?: food.defaultAmount
    val repeatUnit = last?.enteredUnit ?: food.defaultUnit
    val expanded = root && vm.expandedLibraryFoodId == food.id
    val target = vm.slot
    Surface(Modifier.fillMaxWidth(), color = Paper) {
        Column {
            Row(Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.receiptDashedRule()).padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) { vm.openLibraryFood(food) }.padding(vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                    Text(food.name, color = Ink, style = MaterialTheme.typography.bodyLarge)
                    val detail = "${repeatAmount.pretty()} ${repeatUnit.displayLabel()}, ${if (last == null) "${defaultServingCalories(food)} kcal" else "last ${last.nutritionSnapshot.calories.pretty()} kcal"}"
                    Text(if (food.archived) "Archived, $detail" else detail, color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
                SourceBox(sourceLetter(food), Modifier.padding(horizontal = 10.dp))
                ReceiptTonalIconButton(
                    icon = if (expanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = when {
                        expanded -> "Close meal choices"
                        target != null -> "Add ${food.name} to ${target.label()}"
                        else -> "Add ${food.name} to a meal"
                    },
                    onClick = { if (root) vm.toggleLibraryFoodPicker(food) else vm.logLibraryFood(food, target!!, repeatAmount, repeatUnit) },
                    enabled = !food.archived,
                )
            }
            if (expanded) {
                FlowRow(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlot.entries.forEach { meal -> ReceiptTonalChip(meal.label(), { vm.logLibraryFood(food, meal, repeatAmount, repeatUnit); vm.collapseLibraryFoodPicker() }) }
                }
            }
        }
    }
}

private fun defaultServingCalories(food: FoodRecord): String =
    com.iron.breakfast.defaultServingCalculation(food).nutrition.calories.pretty()

/** Search pill, scan button and (outside a meal) the navigation bar, as drawn in the locked Library tray. */
@Composable internal fun LibraryComposer(vm: BreakfastViewModel) {
    val inMeal = vm.slot != null
    ReceiptBottomTray(
        content = {
            Row(Modifier.fillMaxWidth().then(if (inMeal) Modifier.navigationBarsPadding() else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).heightIn(min = 54.dp).background(Paper, RoundedCornerShape(27.dp)).padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = InkSoft, modifier = Modifier.size(20.dp))
                    BasicTextField(
                        value = vm.input,
                        onValueChange = vm::onInput,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                            .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { vm.submitLibraryInput(); true } else false },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.submitLibraryInput() }),
                        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (vm.input.isBlank()) Text("Search foods", color = InkSoft, style = MaterialTheme.typography.bodyMedium); inner() } },
                    )
                }
                ReceiptScanButton(vm::beginScan)
            }
        },
        nav = {
            if (!inMeal) ReceiptNavigationBar(selectedDaily = false, onDaily = { vm.closeMeal(); vm.closeLibrary() }, onLibrary = {})
        },
    )
}

@Composable private fun ArchivedTag() {
    Text("Archived", color = InkSoft, style = MaterialTheme.typography.labelSmall, modifier = Modifier.border(1.5.dp, BoxGrey, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** One serving line: "1 carton" with its base amount, or the red approximation chip when the conversion is a guess. */
@Composable private fun ServingValue(conversion: ServingConversion, food: FoodRecord) {
    val value = "${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}"
    if (conversion.approximate) ApproxTag(value) else Text(value, color = Ink, fontWeight = FontWeight.Bold)
}

@Composable internal fun LibraryFoodDetailDialog(vm: BreakfastViewModel, food: FoodRecord) {
    var amountSheetOpen by remember(food.id) { mutableStateOf(false) }
    var mealMenuOpen by remember(food.id) { mutableStateOf(false) }
    var targetMeal by remember(food.id) { mutableStateOf(vm.slot ?: MealSlot.LUNCH) }
    val letter = sourceLetter(food)
    val servings = food.householdServings()
    BackHandler { vm.closeLibraryFood() }
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::closeLibraryFood) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnGround) }
                Text("Food", color = OnGround, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.closeLibraryFood(); vm.openLibraryEdit(food) }, Modifier.heightIn(min = 48.dp)) { Text("Edit", color = PenOnDarkInk, fontWeight = FontWeight.Bold) }
            }
            Surface(Modifier.fillMaxWidth(), color = Paper, shape = RoundedCornerShape(6.dp)) {
                Column {
                    Text(food.name, Modifier.fillMaxWidth().background(PrintRed).padding(14.dp), color = OnGround, fontWeight = FontWeight.Bold)
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(listOfNotNull(food.brand, sourceLabel(letter)).joinToString(", "), color = Ink, modifier = Modifier.weight(1f))
                            if (food.archived) { ArchivedTag(); Spacer(Modifier.width(8.dp)) }
                            SourceBox(letter)
                        }
                        food.barcode?.let { Text("Barcode  $it", color = InkSoft, style = MaterialTheme.typography.bodySmall) }
                    }
                    LibrarySubhead(basisLabel(food.nutrition.amount, food.nutrition.unit))
                    LibraryFactRow("Energy", "${food.nutrition.values.calories.pretty()} kcal")
                    LibraryFactRow("Fat", "${food.nutrition.values.fat?.pretty() ?: "—"} g")
                    LibraryFactRow("Carbohydrate", "${food.nutrition.values.carbohydrates?.pretty() ?: "—"} g")
                    LibraryFactRow("Protein", "${food.nutrition.values.protein?.pretty() ?: "—"} g")
                    if (servings.isNotEmpty()) {
                        LibrarySubhead("Servings")
                        servings.forEach { conversion -> LibraryFactRow("1 ${conversion.unit.displayLabel()}") { ServingValue(conversion, food) } }
                    }
                    if (food.aliases.isNotEmpty()) {
                        LibrarySubhead("Aliases")
                        food.aliases.forEachIndexed { index, alias -> Text(alias, Modifier.fillMaxWidth().then(if (index == 0) Modifier else Modifier.receiptDashedRule()).padding(horizontal = 14.dp, vertical = 10.dp), color = Ink) }
                    }
                }
            }
            val logDescription = if (food.archived) "Log unavailable: this food is archived" else "Log to ${targetMeal.label()}"
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                // The amount sheet carries the single solid action while it is open.
                Button(
                    onClick = { amountSheetOpen = true },
                    enabled = !food.archived,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = logDescription },
                    shape = RoundedCornerShape(24.dp),
                    colors = if (amountSheetOpen) ButtonDefaults.buttonColors(containerColor = PenTonal, contentColor = PenTonalInk) else ButtonDefaults.buttonColors(),
                ) { Text("Log to ${targetMeal.label()}") }
                Box {
                    ReceiptActionChip(targetMeal.label(), { mealMenuOpen = true }, trailingIcon = Icons.Default.ArrowDropDown)
                    DropdownMenu(expanded = mealMenuOpen, onDismissRequest = { mealMenuOpen = false }) {
                        MealSlot.entries.forEach { meal -> DropdownMenuItem(text = { Text(meal.label()) }, onClick = { targetMeal = meal; mealMenuOpen = false }) }
                    }
                }
            }
            if (contributionAvailable(food)) OutlinedButton(onClick = { vm.closeLibraryFood(); vm.openContributionForFood(food) }, Modifier.fillMaxWidth().padding(top = 42.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) { Text("Contribute to Open Food Facts") }
            if (food.origin == FoodOrigin.CUSTOM && !food.archived) TextButton(onClick = { vm.archiveLibraryFood(food) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Archive personal food", color = PrintRed) }
        }
    }
    if (amountSheetOpen) LibraryAmountSheet(vm, food, targetMeal, onDismiss = { amountSheetOpen = false })
}

@Composable private fun LibrarySubhead(text: String) { Text(text, Modifier.fillMaxWidth().receiptDashedRule().padding(horizontal = 14.dp, vertical = 12.dp), color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

@Composable private fun LibraryFactRow(label: String, value: String) = LibraryFactRow(label) { Text(value, color = Ink, fontWeight = FontWeight.Bold) }

@Composable private fun LibraryFactRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().receiptDashedRule().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = Ink); value() }
}

@Composable internal fun FocusedFoodAddDialog(vm: BreakfastViewModel, food: FoodRecord) {
    val target = vm.slot ?: return
    LibraryAmountSheet(vm, food, target, onDismiss = vm::closeLibraryFood)
}

/** The barcode field shared by both personal-food forms; typing keeps ASCII digits and rescanning fills this same draft. */
@Composable internal fun FoodBarcodeField(vm: BreakfastViewModel, label: String = "Barcode") {
    val draft = vm.draft
    OutlinedTextField(
        value = draft.barcode,
        onValueChange = { draft.barcode = it.asciiDigits() },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 4.dp)) {
                if (draft.barcode.isNotBlank()) ReceiptTonalIconButton(Icons.Default.Close, "Clear barcode", { draft.barcode = "" })
                ReceiptTonalIconButton(Icons.Default.QrCodeScanner, "Scan barcode", vm::beginBarcodeRescan)
            }
        },
    )
}

@Composable private fun AliasChip(alias: String, onRemove: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).border(1.5.dp, Rule, RoundedCornerShape(24.dp)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(alias, color = PenTonalInk, style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onRemove, Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Remove alias $alias", tint = PenTonalInk, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LibraryFoodEditScreen(vm: BreakfastViewModel) {
    val food = vm.foods.firstOrNull { it.id == vm.editingLibraryFoodId } ?: return
    val draft = vm.draft
    val letter = sourceLetter(food)
    val servingUnits = listOf(MeasureUnit.TABLESPOON, MeasureUnit.TEASPOON, MeasureUnit.PIECE).filter { it != food.nutrition.unit }
    BackHandler { vm.closeCustom() }
    Surface(Modifier.fillMaxSize(), color = Ground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::closeCustom) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnGround) }
                Text("Edit food", color = OnGround, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::saveCustom, Modifier.heightIn(min = 48.dp)) { Text("Save", color = PenOnDarkInk, fontWeight = FontWeight.Bold) }
            }
            Surface(Modifier.fillMaxWidth(), color = Paper, shape = RoundedCornerShape(6.dp)) {
                Column {
                    Text(food.name, Modifier.fillMaxWidth().background(PrintRed).padding(14.dp), color = OnGround, fontWeight = FontWeight.Bold)
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(draft.name, { draft.name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(draft.brand, { draft.brand = it }, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        FoodBarcodeField(vm)
                        Text("Source", color = InkSoft, style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(sourceLabel(letter), color = Ink); SourceBox(letter) }
                        if (letter == "O") Text("Local edits stay on this device", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    }
                    LibrarySubhead(basisLabel(food.nutrition.amount, food.nutrition.unit))
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(draft.calories, { draft.calories = it }, label = { Text("kcal (required)") }, modifier = Modifier.weight(1f), singleLine = true); OutlinedTextField(draft.protein, { draft.protein = it }, label = { Text("Protein, g") }, modifier = Modifier.weight(1f), singleLine = true) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(draft.carbs, { draft.carbs = it }, label = { Text("Carbs, g") }, modifier = Modifier.weight(1f), singleLine = true); OutlinedTextField(draft.fat, { draft.fat = it }, label = { Text("Fat, g") }, modifier = Modifier.weight(1f), singleLine = true) }
                    }
                    LibrarySubhead("Servings")
                    draft.servings.forEach { conversion ->
                        Row(Modifier.fillMaxWidth().receiptDashedRule().padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("1 ${conversion.unit.displayLabel()}" + if (conversion.approximate) "" else " = ${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}", color = Ink)
                                if (conversion.approximate) ApproxTag("${conversion.baseUnitsPerServing.pretty()} ${food.nutrition.unit.label}")
                            }
                            ReceiptTonalIconButton(Icons.Default.Close, "Remove 1 ${conversion.unit.displayLabel()} serving", { draft.servings = draft.servings - conversion })
                        }
                    }
                    Column(Modifier.fillMaxWidth().receiptDashedRule().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (draft.servingFormOpen) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { servingUnits.forEach { unit -> ReceiptChoiceChip(unit.displayLabel(), draft.servingUnit == unit, { draft.servingUnit = unit }) } }
                            OutlinedTextField(draft.servingAmount, { draft.servingAmount = it.asciiDigits(allowDecimalPoint = true) }, label = { Text("1 ${draft.servingUnit.displayLabel()} = how many ${food.nutrition.unit.label}") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReceiptTonalChip("Add", vm::commitDraftServing)
                                TextButton(onClick = { draft.servingFormOpen = false }, Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = InkSoft) }
                            }
                        } else ReceiptTonalChip("Add serving", { draft.servingUnit = servingUnits.firstOrNull() ?: MeasureUnit.PIECE; draft.servingFormOpen = true }, Icons.Default.Add, Modifier.fillMaxWidth())
                    }
                    LibrarySubhead("Aliases")
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            draft.aliases.forEach { alias -> AliasChip(alias) { draft.aliases = draft.aliases - alias } }
                            if (!draft.aliasFieldOpen) ReceiptTonalChip("Add alias", { draft.aliasFieldOpen = true }, Icons.Default.Add)
                        }
                        if (draft.aliasFieldOpen) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(draft.aliasInput, { draft.aliasInput = it }, label = { Text("New alias") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { draft.commitAlias() }))
                            ReceiptTonalChip("Add", { draft.commitAlias() })
                        }
                    }
                }
            }
            ReceiptTornEdge(fill = Paper)
            if (food.origin == FoodOrigin.CUSTOM && !food.archived) TextButton(onClick = { vm.archiveLibraryFood(food) }, Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 56.dp).background(Paper, RoundedCornerShape(6.dp))) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = PrintRed, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Archive food", color = PrintRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun LibraryAmountSheet(vm: BreakfastViewModel, food: FoodRecord, target: MealSlot, onDismiss: () -> Unit) {
    var amountText by remember(food.id, target) { mutableStateOf((vm.lastLoggedEntry(food)?.enteredAmount ?: food.defaultAmount).pretty()) }
    var unit by remember(food.id, target) { mutableStateOf(vm.lastLoggedEntry(food)?.enteredUnit ?: food.defaultUnit) }
    var chosenMeal by remember(food.id, target) { mutableStateOf(target) }
    val units = (listOf(food.defaultUnit, food.nutrition.unit) + food.conversions.map { it.unit }).filter { it != MeasureUnit.UNKNOWN }.distinct()
    val calc = amountText.toDoubleOrNull()?.takeIf { it > 0 }?.let { NutritionCalculator().calculate(food, it, unit) }
    val letter = sourceLetter(food)
    ReceiptBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(food.name, color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(sourceLabel(letter), color = InkSoft, style = MaterialTheme.typography.bodySmall)
                SourceBox(letter, Modifier.padding(start = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(amountText, { amountText = it.asciiDigits(allowDecimalPoint = true) }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(112.dp))
                FlowRow(Modifier.weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { units.forEach { candidate -> ReceiptChoiceChip(candidate.displayLabel(), unit == candidate, { unit = candidate }) } }
            }
            Text("Meal", color = InkSoft, style = MaterialTheme.typography.labelMedium)
            ReceiptMealChips(chosenMeal, { chosenMeal = it })
            Text(calc?.let { "${receiptKcal(it.nutrition.calories)} kcal, P ${it.nutrition.protein?.pretty() ?: "—"} g, C ${it.nutrition.carbohydrates?.pretty() ?: "—"} g, F ${it.nutrition.fat?.pretty() ?: "—"} g" } ?: "Enter a positive amount", Modifier.fillMaxWidth().receiptDashedRule().padding(top = 12.dp), color = InkSoft)
            Button(onClick = { val amount = amountText.toDoubleOrNull(); if (amount != null && amount > 0.0 && calc?.supported == true) { vm.logLibraryFood(food, chosenMeal, amount, unit); onDismiss() } else vm.noticeForInvalidLibraryAmount() }, enabled = !food.archived && calc?.supported == true, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) { Text("Add to ${chosenMeal.label()}") }
        }
    }
}
