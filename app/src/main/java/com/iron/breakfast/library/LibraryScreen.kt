package com.iron.breakfast.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.iron.breakfast.ui.theme.*
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.iron.breakfast.app.Confirmations
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.resolve.ResolveFlow
import com.iron.breakfast.scan.ScanSession
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.today.DayState
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.ServingConversion
import com.iron.breakfast.domain.Measure
import com.iron.breakfast.domain.measures
import com.iron.breakfast.log.previewQuantityLine
import androidx.compose.foundation.layout.FlowRow
import com.iron.breakfast.domain.decimalInput
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.ui.label
import com.iron.breakfast.contribution.contributionShown
import com.iron.breakfast.ui.plain
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.MealChoices
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.InkBar
import com.iron.breakfast.ui.components.ScanBarcodeIcon
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.Sheet
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.BottomNav
import com.iron.breakfast.ui.components.NavTab
import com.iron.breakfast.ui.components.ConfirmationSnackbar
import com.iron.breakfast.ui.components.rememberMotion
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.screens.entryDateLabel
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.entrySourceLabel
import com.iron.breakfast.ui.screens.basisLabel
import com.iron.breakfast.ui.screens.contributionAvailable


@Composable
internal fun LibraryScreen(
    library: LibraryState,
    resolve: ResolveFlow,
    day: DayState,
    surfaces: Surfaces,
    foodForm: FoodForm,
    confirmations: Confirmations,
    automaticMeal: () -> MealSlot?,
    modifier: Modifier,
) {
    val colors = LocalBreakfastColors.current
    val motion = rememberMotion()
    val root = library.target == null
    // A part being resolved is searched for, not added to a meal the user did not choose here.
    val title = if (resolve.resolvingPart) "Search" else if (root) "Library" else "Add to ${library.target!!.label()}"
    val subtitle = if (root) "${library.foods.size} foods, by last logged" else entryDateLabel(day.selectedDayKey)
    Box(modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!root) IconButton(onClick = surfaces::exitLibrary, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.ink)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(title, color = colors.ink, style = BreakfastType.Section)
                        Text(subtitle, color = colors.soft, style = BreakfastType.Meta)
                    }
                    if (root) TertiaryButton("New food", { foodForm.open() }) else Spacer(Modifier.size(44.dp))
                }
                Hairline()
            }
            // Rows fade in and out as the list changes, and move to their new place (logged foods go to the top).
            items(library.foods, key = { it.id }) { food ->
                LibraryFoodRow(library, automaticMeal, food, root, Modifier.animateItem(fadeInSpec = motion.listItem(), placementSpec = motion.listItem(), fadeOutSpec = motion.listItem()))
            }
            // The list's closing line, only under rows: an empty list keeps just the header's line.
            if (library.foods.isNotEmpty()) item {
                Hairline(Modifier.padding(top = 8.dp))
            }
        }
        // "Logged!" floats over the end of the list, just above the bar, as on Daily and Meal: nothing moves.
        ConfirmationSnackbar(confirmations.message, Modifier.align(Alignment.BottomCenter).padding(horizontal = Gutter))
    }
}

@Composable
private fun LibraryFoodRow(library: LibraryState, automaticMeal: () -> MealSlot?, food: FoodRecord, root: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalBreakfastColors.current
    val last = library.lastLoggedEntry(food)
    val repeatAmount = last?.enteredAmount ?: food.defaultAmount
    val repeatUnit = last?.enteredUnit ?: food.defaultUnit
    // A serving logged last is repeated by its name ("2 slices"); a converted unit ("kg") reads as typed.
    val repeatLabel = last?.enteredUnitLabel
    val repeatName = repeatLabel.takeIf { repeatUnit == MeasureUnit.SERVING }
    val repeatLine = previewQuantityLine(repeatAmount, repeatUnit, approximate = false, label = repeatLabel)
    val expanded = root && library.expandedFoodId == food.id
    val target = library.target
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FoodRow(
                name = food.name,
                quantityLine = if (last == null) repeatLine else "last $repeatLine",
                calories = last?.let { formatKcal(it.nutritionSnapshot.calories) } ?: defaultServingCalories(food),
                modifier = Modifier.weight(1f),
                onClick = { library.openFood(food) },
            )
            AddCircle(
                contentDescription = if (target == null) "Add ${food.name} to a meal" else "Add ${food.name} to ${target.label()}",
                onClick = { if (root) library.toggleFoodPicker(food) else library.log(food, target!!, repeatAmount, repeatUnit, repeatName) },
                icon = if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
            )
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Add $repeatLine to", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(bottom = 6.dp))
                // The Meal setting's meal shows selected (none for Ask): one tap logs it, or another meal is tapped.
                MealChoices(selected = automaticMeal(), onSelect = { meal -> library.log(food, meal, repeatAmount, repeatUnit, repeatName); library.collapseFoodPicker() }, singleRow = true)
            }
        }
    }
}

internal fun defaultServingCalories(food: FoodRecord): String = formatKcal(com.iron.breakfast.domain.defaultServingCalculation(food).nutrition.calories)

/**
 * The Library's bottom bar: the Composer look (ink bar, "Search foods") over the existing text field.
 * It filters the list through [LibraryState.submitInput] and does not open the Log page.
 */
@Composable
internal fun LibraryComposer(library: LibraryState, resolve: ResolveFlow, scan: ScanSession, surfaces: Surfaces) {
    val colors = LocalBreakfastColors.current
    val inMeal = library.target != null
    // While resolving, a cleared search lands here: the bar takes the keyboard so typing goes on.
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val resolveCleared = resolve.resolvingPart && library.query.isEmpty()
    LaunchedEffect(resolveCleared) { if (resolveCleared) { runCatching { focus.requestFocus() }; keyboard?.show() } }
    Column(Modifier.fillMaxWidth().background(colors.bg).then(if (inMeal) Modifier else Modifier.navigationBarsPadding()).padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InkBar(if (inMeal) Modifier.navigationBarsPadding() else Modifier) {
            BasicTextField(
                value = library.input,
                onValueChange = library::onInput,
                singleLine = true,
                textStyle = BreakfastType.Body.copy(color = colors.bg),
                cursorBrush = SolidColor(colors.bg),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { library.submitInput() }, onDone = { library.submitInput() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { library.submitInput(); true } else false }
                    .semantics { contentDescription = "Search foods" },
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 22.dp), contentAlignment = Alignment.CenterStart) {
                        if (library.input.isEmpty()) Text("Search foods", color = colors.bg.copy(alpha = 0.6f), style = BreakfastType.Body)
                        inner()
                    }
                },
            )
            if (library.input.isNotEmpty()) IconButton(onClick = { library.onInput("") }, modifier = Modifier.size(IconTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = colors.bg, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = scan::begin, modifier = Modifier.size(IconTarget)) {
                Icon(ScanBarcodeIcon, contentDescription = "Scan barcode", tint = colors.bg)
            }
        }
        if (!inMeal) BottomNav(
            selected = NavTab.LIBRARY,
            onMeals = { surfaces.closeMeal(); surfaces.closeLibrary() },
            onLibrary = {},
            onRecipes = surfaces::openRecipes,
            onSettings = surfaces::openSettings,
        )
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
internal fun LibraryFoodDetailDialog(
    library: LibraryState,
    surfaces: Surfaces,
    foodForm: FoodForm,
    contribution: ContributionFlow,
    settings: SettingsState,
    automaticMeal: () -> MealSlot?,
    food: FoodRecord,
) {
    val colors = LocalBreakfastColors.current
    // Saveable, with the amount sheet's own values, so a configuration change keeps the open sheet as it was.
    var amountSheetOpen by rememberSaveable(food.id) { mutableStateOf(false) }
    var mealMenuOpen by rememberSaveable(food.id) { mutableStateOf(false) }
    // Starts on the meal it was opened from, else the Meal setting's meal (By time, Last used), else Lunch.
    var targetMeal by rememberSaveable(food.id) { mutableStateOf(libraryPresetMeal(surfaces.mealOnScreen(), automaticMeal())) }
    val servings = food.householdServings()
    Column(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp)) {
        TopBar(
            title = "Food",
            onBack = library::closeFood,
            modifier = Modifier.padding(top = 8.dp),
            trailing = { TertiaryButton("Edit", { library.closeFood(); foodForm.openEdit(food) }) },
        )
        Hairline(Modifier.padding(top = 8.dp))
        Text(foodText(food.name), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 8.dp))
        food.brand?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.soft, style = BreakfastType.Secondary) }
        Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
        food.barcode?.let { Text("Barcode $it", color = colors.soft, style = BreakfastType.Secondary) }
        Hairline(Modifier.padding(top = 16.dp))
        SectionHeading(basisLabel(food.nutrition.amount, food.nutrition.unit), Modifier.padding(top = 16.dp))
        LibraryFactRow("Energy", formatKcal(food.nutrition.values.calories), "kcal")
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
        // Large font stacks the meal picker over the Log button, both full width.
        val mealPicker: @Composable (Modifier, Boolean) -> Unit = { modifier, fill ->
            SecondaryButton(targetMeal.label(), { mealMenuOpen = true }, modifier, fillWidth = fill, trailing = Icons.Outlined.KeyboardArrowDown)
        }
        val logButton: @Composable (Modifier) -> Unit = { modifier ->
            PrimaryButton("Log to ${targetMeal.label()}", onClick = { amountSheetOpen = true }, modifier)
        }
        if (isLargeFont(LocalDensity.current.fontScale)) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mealPicker(Modifier.fillMaxWidth(), true)
                logButton(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                mealPicker(Modifier.widthIn(min = 128.dp), false)
                logButton(Modifier.weight(1f))
            }
        }
        if (mealMenuOpen) MealChoices(selected = targetMeal, onSelect = { targetMeal = it; mealMenuOpen = false }, modifier = Modifier.padding(top = 8.dp))
        if (contributionShown(settings.showContribute, contributionAvailable(food))) SecondaryButton("Contribute to Open Food Facts", { library.closeFood(); contribution.openForFood(food) }, Modifier.padding(top = 16.dp))
        if (food.origin == FoodOrigin.CUSTOM) DestructiveButton("Delete food", { library.askDelete(food) }, Modifier.fillMaxWidth().padding(top = 8.dp), outlined = true)
    }
    if (amountSheetOpen) LibraryAmountSheet(library, food, targetMeal) { amountSheetOpen = false }
}

/** One nutrition line of a food or recipe page: the label, then the value in ink and its unit in soft. */
@Composable
internal fun LibraryFactRow(label: String, number: String, unit: String) {
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
internal fun FocusedFoodAddDialog(library: LibraryState, surfaces: Surfaces, food: FoodRecord) {
    val target = surfaces.mealOnScreen() ?: return
    LibraryAmountSheet(library, food, target, library::closeFood)
}

@Composable
private fun LibraryAmountSheet(library: LibraryState, food: FoodRecord, target: MealSlot, onDismiss: () -> Unit) {
    val last = library.lastLoggedEntry(food)
    FoodAmountSheet(
        food = food,
        startAmount = last?.enteredAmount ?: food.defaultAmount,
        startMeasure = last?.let { Measure(it.enteredUnit, it.enteredUnitLabel.takeIf { _ -> it.enteredUnit == MeasureUnit.SERVING }) } ?: Measure(food.defaultUnit),
        startMeal = target,
        onAdd = { amount, measure, meal -> library.log(food, meal!!, amount, measure.unit, measure.name); onDismiss() },
        onInvalid = library::invalidAmount,
        onDismiss = onDismiss,
    )
}

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
    onInvalid: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBreakfastColors.current
    var amountText by rememberSaveable(food.id, startMeal) { mutableStateOf(startAmount.plain()) }
    val measures = food.measures()
    // The unit and a serving's name, saved apart (a Measure is not a Bundle value).
    var unit by rememberSaveable(food.id, startMeal) { mutableStateOf(startMeasure.unit) }
    var servingName by rememberSaveable(food.id, startMeal) { mutableStateOf(startMeasure.name.takeIf { startMeasure.unit == MeasureUnit.SERVING }) }
    val measure = Measure(unit, servingName)
    var chosenMeal by rememberSaveable(food.id, startMeal) { mutableStateOf(startMeal) }
    val calc = amountText.toDoubleOrNull()?.takeIf { it > 0 }?.let { NutritionCalculator().calculate(food, it, unit, servingName) }
    Sheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(foodText(food.name), color = colors.ink, style = BreakfastType.Section)
            Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
            // The field fills its row, so the units take their own line under it, as on the entry's quick edit.
            Field("Amount", amountText, { amountText = it.decimalInput() }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                measures.forEach { candidate ->
                    ChoiceOption(candidate.label, measure == candidate, { unit = candidate.unit; servingName = candidate.name })
                }
            }
            chosenMeal?.let { meal ->
                Text("Meal", color = colors.soft, style = BreakfastType.Meta)
                MealChoices(selected = meal, onSelect = { chosenMeal = it }, singleRow = true)
            }
            Hairline()
            Text(calc?.let { "${formatKcal(it.nutrition.calories)} kcal" } ?: "Enter a positive amount", color = colors.ink, style = BreakfastType.Section)
            Text(calc?.let { "P ${it.nutrition.protein?.pretty() ?: "—"}  C ${it.nutrition.carbohydrates?.pretty() ?: "—"}  F ${it.nutrition.fat?.pretty() ?: "—"}" } ?: "", color = colors.soft, style = BreakfastType.Secondary)
            PrimaryButton(
                chosenMeal?.let { "Add to ${it.label()}" } ?: "Add",
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0 && calc?.supported == true) onAdd(amount, measure, chosenMeal) else onInvalid()
                },
                enabled = calc?.supported == true,
            )
        }
    }
}
