package com.iron.breakfast.entry

import com.iron.breakfast.domain.isPositiveAmount
import com.iron.breakfast.domain.fieldText
import com.iron.breakfast.domain.fieldNumber
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.combinedClickable
import com.iron.breakfast.ui.components.ValueField
import com.iron.breakfast.ui.components.MealField
import com.iron.breakfast.ui.components.DayPickerDialog
import com.iron.breakfast.ui.components.DecimalField
import com.iron.breakfast.ui.components.SubScreen
import com.iron.breakfast.domain.POSITIVE_AMOUNT_MESSAGE
import com.iron.breakfast.domain.positiveAmountOf
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.macroTotalsOf
import com.iron.breakfast.ui.screens.macroStats
import com.iron.breakfast.ui.screens.kcalLabel
import com.iron.breakfast.domain.editableNumber
import com.iron.breakfast.ui.components.DayTotal
import com.iron.breakfast.ui.shortDateLabel
import com.iron.breakfast.ui.macroLine
import com.iron.breakfast.ui.clockLabel
import com.iron.breakfast.ui.theme.*
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.today.DayState
import com.iron.breakfast.today.UndoController
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.AddCircle
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.DestructiveButton
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.Sheet
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import java.util.Locale
import com.iron.breakfast.ui.screens.quantityRateDisplay
import com.iron.breakfast.ui.screens.labelFor
import com.iron.breakfast.domain.Measure
import com.iron.breakfast.domain.measures
import androidx.compose.foundation.layout.FlowRow
import com.iron.breakfast.ui.screens.plainLine
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.entrySourceLabel
import com.iron.breakfast.ui.screens.sourceLabel
import com.iron.breakfast.domain.isRecipeId


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
            macroStats(entry.nutritionSnapshot.calories, macroTotalsOf(listOf(entry.nutritionSnapshot))),
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun EntryQuickEditSheet(entryEdit: EntryEdit, session: DiarySession, entry: MealEntry) {
    val colors = LocalBreakfastColors.current
    // Saveable: a dark-mode or font-size change recreates the activity and must not reset what was typed.
    var amount by rememberSaveable(entry.id) { mutableStateOf(editableNumber(entry.enteredAmount)) }
    var unit by rememberSaveable(entry.id) { mutableStateOf(entry.enteredUnit) }
    var servingName by rememberSaveable(entry.id) { mutableStateOf(entry.measure().name) }
    val measure = Measure(unit, servingName)
    val food = entryEdit.foodOf(entry.foodId)
    val units = food?.measures().orEmpty().ifEmpty { listOf(entry.measure()) }
    val preview = fieldNumber(amount, entry.enteredAmount)?.takeIf(::isPositiveAmount)?.let { entryEdit.preview(entry, entry.foodId, it, unit, servingName) }
    val changed = entryDraftNutritionChanged(entry, amount, measure, entry.foodId)
    // A save error stays until the amount or unit changes.
    LaunchedEffect(amount, unit, servingName) { entryEdit.inputChanged() }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { requester.requestFocus() }
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
                    fieldModifier = Modifier.focusRequester(requester),
                )
                AddCircle("Increase amount", { amount = adjustAmount(amount, 1.0) })
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { candidate -> ChoiceOption(candidate.label, measure == candidate, { unit = candidate.unit; servingName = candidate.name }) }
            }
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    LaunchedEffect(selectedFood?.id) { if (measure !in units) units.first().let { unit = it.unit; servingName = it.name } }
    // A save error stays until something being edited changes.
    LaunchedEffect(selectedFood?.id, amount, unit, servingName, meal, dayKey) { entryEdit.inputChanged() }
    SubScreen("Edit entry", { entryEdit.cancel() }) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Field("Food", selectedFood?.name ?: entry.foodName, {}, Modifier.weight(1f), readOnly = true)
                SecondaryButton("Replace", entryEdit::openFoodPicker, modifier = Modifier.widthIn(min = 96.dp * fontScale), fillWidth = false)
            }
            DecimalField("Amount", amount, { amount = it }, Modifier.widthIn(min = 140.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { candidate -> ChoiceOption(candidate.label, measure == candidate, { unit = candidate.unit; servingName = candidate.name }) }
            }
            MealField(meal, { meal = it })
            ValueField("Date", shortDateLabel(dayKey), { datePickerOpen = true }, Icons.Default.CalendarMonth, "Change date")
        }
        val shown = preview?.nutrition ?: entry.nutritionSnapshot
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.End) {
            Text(kcalLabel(shown.calories), style = BreakfastType.Section, color = colors.ink)
            Text(macroLine(shown), color = colors.soft, style = BreakfastType.Secondary)
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
