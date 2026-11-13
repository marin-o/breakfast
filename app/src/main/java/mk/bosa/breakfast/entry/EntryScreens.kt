package mk.bosa.breakfast.entry

import mk.bosa.breakfast.domain.isPositiveAmount
import mk.bosa.breakfast.domain.fieldText
import mk.bosa.breakfast.domain.fieldNumber
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.combinedClickable
import mk.bosa.breakfast.ui.components.ValueField
import mk.bosa.breakfast.ui.components.MealField
import mk.bosa.breakfast.ui.components.DayPickerDialog
import mk.bosa.breakfast.ui.components.DecimalField
import mk.bosa.breakfast.ui.components.SubScreen
import mk.bosa.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import mk.bosa.breakfast.domain.positiveAmountOf
import mk.bosa.breakfast.domain.parseDecimal
import mk.bosa.breakfast.domain.macroTotalsOf
import mk.bosa.breakfast.ui.screens.macroStats
import mk.bosa.breakfast.ui.screens.kcalLabel
import mk.bosa.breakfast.domain.editableNumber
import mk.bosa.breakfast.ui.components.DayTotal
import mk.bosa.breakfast.ui.shortDateLabel
import mk.bosa.breakfast.ui.components.MacroLine
import mk.bosa.breakfast.ui.components.LocalFibreShown
import mk.bosa.breakfast.domain.macroTotalOf
import mk.bosa.breakfast.ui.macroLineParts
import mk.bosa.breakfast.ui.clockLabel
import mk.bosa.breakfast.ui.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.app.DiarySession
import mk.bosa.breakfast.app.Surfaces
import mk.bosa.breakfast.today.DayState
import mk.bosa.breakfast.today.UndoController
import mk.bosa.breakfast.domain.MealEntry
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.ui.label
import mk.bosa.breakfast.ui.pretty
import mk.bosa.breakfast.ui.components.foodText
import mk.bosa.breakfast.ui.components.AddCircle
import mk.bosa.breakfast.ui.components.DestructiveButton
import mk.bosa.breakfast.ui.components.Field
import mk.bosa.breakfast.ui.components.focusOnOpen
import mk.bosa.breakfast.ui.components.Hairline
import mk.bosa.breakfast.ui.components.PrimaryButton
import mk.bosa.breakfast.ui.components.SecondaryButton
import mk.bosa.breakfast.ui.components.SectionHeading
import mk.bosa.breakfast.ui.components.Sheet
import mk.bosa.breakfast.ui.components.TertiaryButton
import mk.bosa.breakfast.ui.components.ButtonKind
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors
import java.util.Locale
import mk.bosa.breakfast.ui.screens.quantityRateDisplay
import mk.bosa.breakfast.ui.screens.labelFor
import mk.bosa.breakfast.domain.Measure
import mk.bosa.breakfast.domain.amountTextAfterSwitch
import mk.bosa.breakfast.domain.measures
import mk.bosa.breakfast.ui.components.MeasureChoices
import mk.bosa.breakfast.ui.screens.plainLine
import mk.bosa.breakfast.ui.screens.formatKcal
import mk.bosa.breakfast.ui.screens.entrySourceLabel
import mk.bosa.breakfast.ui.screens.sourceLabel
import mk.bosa.breakfast.domain.isRecipeId


@Composable
internal fun EntryDetailScreen(entryEdit: EntryEdit, session: DiarySession, surfaces: Surfaces, undo: UndoController, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    val food = session.foods.firstOrNull { it.id == entry.foodId }
    val quantity = quantityRateDisplay(entry, food)
    SubScreen("Entry", entryEdit::closeDetail, subtitle = "${entry.mealSlot.label()}, ${shortDateLabel(entry.dayKey)}, ${clockLabel(entry.savedAtEpochMs)}") {
        Text(foodText(entry.foodName), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 22.dp))
        Text(quantity.plainLine(), color = colors.soft, style = BreakfastType.Secondary)
        DayTotal(
            formatKcal(entry.nutritionSnapshot.calories),
            macroStats(
                entry.nutritionSnapshot.calories, macroTotalsOf(listOf(entry.nutritionSnapshot)),
                fibre = macroTotalOf(listOf(entry.nutritionSnapshot.fibre)), showFibre = LocalFibreShown.current,
            ),
            Modifier.padding(top = 22.dp),
            caption = "kilocalories",
        )
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EntryQuickEditSheet(entryEdit: EntryEdit, session: DiarySession, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    // Saveable: a dark-mode or font-size change recreates the activity and must not reset what was typed.
    var amount by rememberSaveable(entry.id) { mutableStateOf(editableNumber(entry.enteredAmount)) }
    var unit by rememberSaveable(entry.id) { mutableStateOf(entry.enteredUnit) }
    var servingName by rememberSaveable(entry.id) { mutableStateOf(entry.measure().name) }
    val measure = Measure(unit, servingName)
    val food = entryEdit.foodOf(entry.foodId)
    val preview = fieldNumber(amount, entry.enteredAmount)?.takeIf(::isPositiveAmount)?.let { entryEdit.preview(entry, entry.foodId, it, unit, servingName) }
    val changed = entryDraftNutritionChanged(entry, amount, measure, entry.foodId)
    // A save error stays until the amount or unit changes.
    LaunchedEffect(amount, unit, servingName) { entryEdit.inputChanged() }
    val keyboard = LocalSoftwareKeyboardController.current
    Sheet(onDismissRequest = { entryEdit.cancel() }, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(foodText(entry.foodName), style = BreakfastType.Section, color = colors.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AddCircle("Decrease amount", { amount = adjustAmount(amount, -1.0) }, icon = Icons.Outlined.Remove)
                DecimalField(
                    "Amount", amount, { amount = it }, Modifier.weight(1f),
                    labelVisible = false, amountStyle = true,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); entryEdit.saveQuick("${fieldText(amount.trim(), entry.enteredAmount)} ${measure.label}") }),
                    fieldModifier = Modifier.focusOnOpen(),
                )
                AddCircle("Increase amount", { amount = adjustAmount(amount, 1.0) })
            }
            MeasureChoices(food, measure, {
                amount = food.amountTextAfterSwitch(amount, entry.enteredAmount, measure, it)
                unit = it.unit
                servingName = it.name
            }, fallback = listOf(entry.measure()))
            Hairline(Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (preview != null) {
                    val baseUnit = food?.nutrition?.unit ?: entry.resolvedBaseUnit.takeIf { it != MeasureUnit.UNKNOWN } ?: unit
                    val base = "${preview.resolvedBaseAmount.pretty()} ${baseUnit.labelFor(preview.resolvedBaseAmount)}"
                    val shownBase = if (preview.approximate && food != null && unit != food.nutrition.unit) "≈ $base" else base
                    Text(shownBase, color = colors.ink, style = BreakfastType.BodyMedium)
                    Text(kcalLabel(preview.nutrition.calories), color = colors.ink, style = BreakfastType.BodyMedium)
                } else {
                    Text(POSITIVE_AMOUNT_MESSAGE, color = colors.soft, style = BreakfastType.Secondary)
                }
                if (changed) Text("was ${formatKcal(entry.nutritionSnapshot.calories)}", color = colors.soft, style = BreakfastType.Secondary)
            }
            PrimaryButton("Save", { entryEdit.saveQuick("${fieldText(amount.trim(), entry.enteredAmount)} ${measure.label}") }, enabled = positiveAmountOf(amount) != null)
            entryEdit.error?.let { Text(it, color = colors.bad, style = BreakfastType.Meta) }
        }
    }
}

private fun adjustAmount(value: String, delta: Double): String = (parseDecimal(value)?.plus(delta)?.coerceAtLeast(0.1) ?: 1.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.ROOT, it) }
internal fun entryDraftNutritionChanged(entry: MealEntry, amount: String, measure: Measure, foodId: String?): Boolean = foodId != entry.foodId || fieldNumber(amount, entry.enteredAmount)?.let { it != entry.enteredAmount } != false || measure != entry.measure()

@Composable
internal fun EntryFullEditScreen(entryEdit: EntryEdit, session: DiarySession, dayState: DayState, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    val selectedFood = entryEdit.editingFood() ?: entryEdit.foodOf(entry.foodId)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    var amount by rememberSaveable(entry.id) { mutableStateOf(editableNumber(entry.enteredAmount)) }
    var unit by rememberSaveable(entry.id) { mutableStateOf(entry.enteredUnit) }
    var servingName by rememberSaveable(entry.id) { mutableStateOf(entry.measure().name) }
    val measure = Measure(unit, servingName)
    var meal by rememberSaveable(entry.id) { mutableStateOf(entry.mealSlot) }
    var dayKey by rememberSaveable(entry.id) { mutableStateOf(entry.dayKey) }
    var datePickerOpen by rememberSaveable(entry.id) { mutableStateOf(false) }
    val units = selectedFood?.measures().orEmpty().ifEmpty { listOf(entry.measure()) }
    // With the entry's food deleted and no other chosen, the entry itself: its amount in its own unit, scaled.
    val foodId = selectedFood?.id ?: entry.foodId
    val preview = fieldNumber(amount, entry.enteredAmount)?.takeIf(::isPositiveAmount)?.let { entryEdit.preview(entry, foodId, it, unit, servingName) }
    LaunchedEffect(selectedFood?.id) {
        if (measure !in units) units.first().let {
            amount = selectedFood.amountTextAfterSwitch(amount, entry.enteredAmount, measure, it)
            unit = it.unit
            servingName = it.name
        }
    }
    // A save error stays until something being edited changes.
    LaunchedEffect(selectedFood?.id, amount, unit, servingName, meal, dayKey) { entryEdit.inputChanged() }
    SubScreen("Edit entry", { entryEdit.cancel() }) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Field("Food", selectedFood?.name ?: entry.foodName, {}, Modifier.weight(1f), readOnly = true)
                SecondaryButton("Replace", entryEdit::openFoodPicker, modifier = Modifier.widthIn(min = 96.dp * fontScale), fillWidth = false)
            }
            DecimalField("Amount", amount, { amount = it }, Modifier.widthIn(min = 140.dp))
            MeasureChoices(selectedFood, measure, {
                amount = selectedFood.amountTextAfterSwitch(amount, entry.enteredAmount, measure, it)
                unit = it.unit
                servingName = it.name
            }, fallback = units)
            MealField(meal, { meal = it })
            ValueField("Date", shortDateLabel(dayKey), { datePickerOpen = true }, Icons.Default.CalendarMonth, "Change date")
        }
        val shown = preview?.nutrition ?: entry.nutritionSnapshot
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.End) {
            Text(kcalLabel(shown.calories), style = BreakfastType.Section, color = colors.ink)
            MacroLine(macroLineParts(shown, fibre = LocalFibreShown.current), BreakfastType.Secondary, alignment = Alignment.End)
        }
        PrimaryButton("Save", { entryEdit.saveFull(foodId, fieldText(amount, entry.enteredAmount), unit, meal, dayKey, servingName) }, enabled = !session.busy)
        entryEdit.error?.let { Text(it, Modifier.padding(top = 12.dp), color = colors.bad, style = BreakfastType.Meta) }
    }
    if (datePickerOpen) DayPickerDialog(dayKey, dayState.todayKey, onDismiss = { datePickerOpen = false }, onPick = { dayKey = it; datePickerOpen = false })
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

/**
 * What a diary row does, on Daily and a meal: a tap [onOpen]s it, a double tap edits the amount, a long press the food
 * and amount, and TalkBack has them all with Delete as actions.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun Modifier.entryActions(entry: MealEntry, entryEdit: EntryEdit, undo: UndoController, onOpen: () -> Unit): Modifier =
    semantics {
        onClick(label = "Open details for ${entry.foodName}") { entryEdit.openDetail(entry); true }
        customActions = listOf(
            CustomAccessibilityAction("Quick edit amount") { entryEdit.beginQuick(entry); true },
            CustomAccessibilityAction("Edit food and amount") { entryEdit.beginFull(entry); true },
            CustomAccessibilityAction("Delete ${entry.foodName}") { undo.delete(entry); true },
        )
    }.combinedClickable(onClick = onOpen, onDoubleClick = { entryEdit.beginQuick(entry) }, onLongClick = { entryEdit.beginFull(entry) })
