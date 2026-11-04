package mk.bosa.breakfast.library

import mk.bosa.breakfast.ui.components.PageSection
import mk.bosa.breakfast.ui.components.FoodRowWithAdd
import mk.bosa.breakfast.ui.components.listRowMotion
import mk.bosa.breakfast.ui.components.TabList
import mk.bosa.breakfast.ui.screens.defaultServingCalories
import mk.bosa.breakfast.domain.presetMeal
import mk.bosa.breakfast.ui.components.FoodAmountSheet
import mk.bosa.breakfast.ui.components.barPlaceholder
import mk.bosa.breakfast.ui.components.SearchText
import mk.bosa.breakfast.ui.components.NutritionFacts
import mk.bosa.breakfast.ui.components.ItemPage
import mk.bosa.breakfast.ui.screens.previewQuantityLine
import mk.bosa.breakfast.contribution.contributionAvailable
import mk.bosa.breakfast.ui.shortDateLabel
import mk.bosa.breakfast.ui.countLabel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import mk.bosa.breakfast.ui.theme.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import mk.bosa.breakfast.app.Surfaces
import mk.bosa.breakfast.contribution.ContributionFlow
import mk.bosa.breakfast.food.FoodForm
import mk.bosa.breakfast.log.UsdaRows
import mk.bosa.breakfast.log.rememberUsdaRows
import mk.bosa.breakfast.resolve.ResolveFlow
import mk.bosa.breakfast.scan.ProductReview
import mk.bosa.breakfast.search.UsdaPartSearch
import mk.bosa.breakfast.settings.SettingsState
import mk.bosa.breakfast.today.DayState
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MealSlot
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.ServingConversion
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.domain.displayLabel
import mk.bosa.breakfast.ui.label
import mk.bosa.breakfast.contribution.contributionShown
import mk.bosa.breakfast.ui.pretty
import mk.bosa.breakfast.ui.components.isLargeFont
import mk.bosa.breakfast.ui.components.DestructiveButton
import mk.bosa.breakfast.ui.components.Hairline
import mk.bosa.breakfast.ui.components.MealChoices
import mk.bosa.breakfast.ui.components.PrimaryButton
import mk.bosa.breakfast.ui.components.SecondaryButton
import mk.bosa.breakfast.ui.components.TertiaryButton
import mk.bosa.breakfast.ui.components.rememberMotion
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors
import mk.bosa.breakfast.ui.screens.formatKcal
import mk.bosa.breakfast.ui.screens.entrySourceLabel
import mk.bosa.breakfast.ui.screens.basisLabel


@Composable
internal fun LibraryScreen(
    library: LibraryState,
    resolve: ResolveFlow,
    day: DayState,
    surfaces: Surfaces,
    foodForm: FoodForm,
    automaticMeal: () -> MealSlot?,
    modifier: Modifier,
    /** The USDA rows under the search, and the review a tap on one opens. */
    usda: UsdaPartSearch,
    review: ProductReview,
    /** The meal it adds to: the Library's own, or, while it glides out, the one it added to as it showed. */
    target: MealSlot? = library.target,
) {
    val focus = LocalFocusManager.current
    val motion = rememberMotion()
    val usdaRows = rememberUsdaRows(usda, library.query, except = library.foods)
    val root = target == null
    // A part being resolved is searched for, not added to a meal the user did not choose here.
    val title = if (resolve.resolvingPart) "Search" else if (root) "Library" else "Add to ${target!!.label()}"
    val subtitle = if (root) "${countLabel(library.foods.size, "food", "foods")}, by last logged" else shortDateLabel(day.selectedDayKey)
    // The list rides above the keyboard while the search has it.
    TabList(
        title, subtitle, library.foods.isNotEmpty(), modifier,
        onBack = if (root) null else surfaces::exitLibrary,
        bodyModifier = Modifier.imePadding(),
        trailing = { if (root) TertiaryButton("New food", { foodForm.open() }) },
    ) {
        // Logged foods go to the top.
        items(library.foods, key = { it.id }) { food ->
            LibraryFoodRow(library, automaticMeal, food, root, listRowMotion(motion))
        }
        // The search's keyboard goes first: a sheet opened while it closes is left misplaced, with no scrim.
        item { UsdaRows(usdaRows, library.query) { focus.clearFocus(); review.choose(it, typed = library.typedPart()) } }
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
        FoodRowWithAdd(
            name = food.name,
            quantityLine = if (last == null) repeatLine else "last $repeatLine",
            calories = last?.let { formatKcal(it.nutritionSnapshot.calories) } ?: defaultServingCalories(food),
            onClick = { library.openFood(food) },
            addDescription = if (target == null) "Add ${food.name} to a meal" else "Add ${food.name} to ${target.label()}",
            onAdd = { if (root) library.toggleFoodPicker(food) else library.log(food, target!!, repeatAmount, repeatUnit, repeatName) },
            addIcon = if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
        )
        if (expanded) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Add $repeatLine to", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(bottom = 6.dp))
                // The Meal setting's meal shows selected (none for Ask): one tap logs it, or another meal is tapped.
                MealChoices(selected = automaticMeal(), onSelect = { meal -> library.log(food, meal, repeatAmount, repeatUnit, repeatName); library.collapseFoodPicker() }, singleRow = true)
            }
        }
    }
}

/**
 * The Library's search, in the one ink bar of the bottom area (`app/BaseBottomArea.kt`): the text field and its
 * Clear. It filters the list through [LibraryState.submitInput] and does not open the Log page.
 */
@Composable
internal fun LibrarySearchField(library: LibraryState, resolve: ResolveFlow, modifier: Modifier = Modifier) {
    val colors = LocalBreakfastColors.current
    // While resolving, a cleared search lands here: the bar takes the keyboard so typing goes on.
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val resolveCleared = resolve.resolvingPart && library.query.isEmpty()
    LaunchedEffect(resolveCleared) { if (resolveCleared) { runCatching { focus.requestFocus() }; keyboard?.show() } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SearchText(
            library.input, library::onInput, { library.onInput("") }, "Search foods", library::submitInput,
            textColor = colors.bg, placeholderColor = colors.bg.barPlaceholder, clearTint = colors.bg,
            fieldModifier = Modifier
                .focusRequester(focus)
                .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { library.submitInput(); true } else false },
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
    var targetMeal by rememberSaveable(food.id) { mutableStateOf(presetMeal(surfaces.mealOnScreen(), automaticMeal())) }
    val servings = food.householdServings()
    ItemPage("Food", library::closeFood, food.name, { library.closeFood(); foodForm.openEdit(food) }) {
        food.brand?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.soft, style = BreakfastType.Secondary) }
        Text(entrySourceLabel(food), color = colors.soft, style = BreakfastType.Secondary)
        food.barcode?.let { Text("Barcode $it", color = colors.soft, style = BreakfastType.Secondary) }
        PageSection(basisLabel(food.nutrition.amount, food.nutrition.unit))
        NutritionFacts(formatKcal(food.nutrition.values.calories), food.nutrition.values)
        if (servings.isNotEmpty()) {
            PageSection("Servings")
            servings.forEach { conversion ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(servingRowName(conversion), color = colors.ink, style = BreakfastType.Body)
                    ServingValue(conversion, food)
                }
            }
        }
        if (food.aliases.isNotEmpty()) {
            PageSection("Aliases")
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
        DestructiveButton("Delete food", { library.askDelete(food) }, Modifier.fillMaxWidth().padding(top = 8.dp), outlined = true)
    }
    if (amountSheetOpen) LibraryAmountSheet(library, food, targetMeal) { amountSheetOpen = false }
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
        onDismiss = onDismiss,
    )
}
