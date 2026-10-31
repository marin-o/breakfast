package com.iron.breakfast.log

import com.iron.breakfast.ui.components.rememberDraftField
import com.iron.breakfast.app.rememberDraftView
import com.iron.breakfast.ui.components.DraftParts
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import com.iron.breakfast.today.UndoSnackbars
import com.iron.breakfast.today.UndoController
import com.iron.breakfast.ui.theme.SnackbarGutter
import com.iron.breakfast.ui.components.ConfirmationSnackbar
import com.iron.breakfast.app.Confirmations
import com.iron.breakfast.ui.components.ScanBarcodeIcon
import com.iron.breakfast.scan.ScanSession
import com.iron.breakfast.ui.shortDateLabel
import com.iron.breakfast.ui.countLabel
import com.iron.breakfast.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.today.DayState
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.rememberTick
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.screens.formatKcal


/**
 * The full-screen Log page: one text input, found foods, an ambiguous-word choice and the add bar.
 * It edits the Log draft ([LogComposer.draft]), which stays until it is added or cleared; leaving the page keeps it.
 */
@Composable
internal fun LogScreen(log: LogComposer, session: DiarySession, settings: SettingsState, day: DayState, scan: ScanSession, confirmations: Confirmations, undo: UndoController, unmatched: UnmatchedParts) {
    val colors = LocalBreakfastColors.current
    // One resolution pass per draft: rows, choices and the add-bar sum all come from the same planner run.
    val draft = rememberDraftView(log.draft, log.committed, log.pinsSnapshot(), session, settings, { log.draftView() }, log::computeDraftView)
    val target = log.targetMeal()
    // The bar counts what Add will save, including the active segment's preselected match.
    val logged = draft.logged
    val kcal = logged.sumOf { it.calculation.nutrition.calories }
    // Add needs text and a free write gate, and the bar's rule (a disabled Add also stops the keyboard's Done).
    val queueActive = unmatched.resolve.queueActive
    val bar = logBarOf(draft.addLogs, draft.needsMatch, draft.hasNoValue, queueActive, unqueuedNeedsMatch = draft.needsMatchParts.any { it !in unmatched.resolve.parts })
    val canAdd = log.draft.isNotBlank() && !session.busy && bar != LogBar.ADD_DISABLED && bar != LogBar.HIDDEN
    val placeholder = if (day.selectedDayKey == day.todayKey) "2 eggs, 1 banana" else "Add to ${shortDateLabel(day.selectedDayKey)}"
    val requester = remember { FocusRequester() }
    // The keyboard stays down while unmatched foods show their results, so it never covers them; a tap on the text brings it back.
    val keyboard = LocalSoftwareKeyboardController.current
    val matching = unmatched.resolve.queueActive
    LaunchedEffect(matching) { if (matching) keyboard?.hide() else requester.requestFocus() }
    val tick = rememberTick()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = HeaderHeight).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = log::close, modifier = Modifier.size(IconTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = colors.ink, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                LogMealPicker(logMealPickerLabel(log.targetMeal()), log)
            }
            Box(Modifier.size(IconTarget))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            val field = rememberDraftField(log.draft, log::onDraft)
            Row(verticalAlignment = Alignment.Top) {
            BasicTextField(
                value = field.value,
                // Add's write takes this text: nothing typed meanwhile could be kept apart from it.
                readOnly = session.busy,
                onValueChange = field.onChange,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    // Done commits the text: the last word is no longer being typed.
                    log.commit()
                    if (canAdd) { tick(); log.submit() }
                }),
                textStyle = BreakfastType.LargeInput.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier.weight(1f).padding(top = 18.dp).focusRequester(requester),
                decorationBox = { inner ->
                    Box {
                        if (log.draft.isEmpty()) Text(placeholder, color = colors.soft, style = BreakfastType.LargeInput)
                        inner()
                    }
                },
            )
            // The whole draft stays: what is scanned comes back as one more part of it.
            IconButton(onClick = { scan.begin(forLog = true) }, modifier = Modifier.padding(top = 8.dp).size(IconTarget)) {
                Icon(ScanBarcodeIcon, contentDescription = "Scan barcode", tint = colors.ink)
            }
            }
            Hairline(Modifier.padding(top = 22.dp))
            log.error?.let { Text(it, color = colors.bad, style = BreakfastType.Meta, modifier = Modifier.padding(top = 8.dp)) }
            DraftParts(draft, onChoose = log::chooseSuggestion, onResolve = log::resolveNotInFoods, queued = unmatched.resolve.parts.takeIf { unmatched.resolve.queueActive }.orEmpty(), queuedRow = rememberUnmatchedRows(unmatched))
        }
        // While its unmatched foods are worked through, the batch that just logged and each part say so here.
        Column(Modifier.fillMaxWidth().padding(horizontal = SnackbarGutter)) {
            UndoSnackbars(undo, shown = queueActive)
            ConfirmationSnackbar(confirmations.message)
        }
        // The add bar sits above whichever is taller: the keyboard or the navigation bar.
        // At large font the button takes its own full-width row so the sum keeps its width.
        val sum: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text("+${formatKcal(kcal)}", color = colors.ink, style = BreakfastType.Section)
                Text("kcal, ${countLabel(logged.size, "food", "foods")}", color = colors.soft, style = BreakfastType.Meta)
            }
        }
        val addButton: @Composable (Boolean) -> Unit = { fill ->
            PrimaryButton(
                text = when {
                    bar == LogBar.FIND_MATCHES -> "Find matches"
                    target != null -> "Add to ${target.label()}"
                    else -> "Add"
                },
                onClick = { tick(); log.submit() },
                enabled = canAdd,
                fillWidth = fill,
            )
        }
        val barInsets = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        val barModifier = barInsets.padding(horizontal = Gutter, vertical = 12.dp)
        val showsSum = bar == LogBar.ADD
        // A hidden sum keeps its place, so the bar's height never changes between its states.
        val hiddenSum = Modifier.alpha(0f).clearAndSetSemantics { }
        if (bar == LogBar.HIDDEN) {
            Box(barInsets)
        } else if (isLargeFont(LocalDensity.current.fontScale)) {
            Column(barModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sum(if (showsSum) Modifier.fillMaxWidth() else hiddenSum.fillMaxWidth())
                addButton(true)
            }
        } else if (!showsSum) {
            Box(barModifier, contentAlignment = Alignment.Center) {
                sum(hiddenSum.fillMaxWidth())
                addButton(true)
            }
        } else {
            Row(barModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                sum(Modifier.weight(1f))
                addButton(false)
            }
        }
    }
}

/** The meal button at the top of the Log page. Its menu sets the target meal; "Meal" when nothing is chosen or typed. */
@Composable
private fun LogMealPicker(label: String, log: LogComposer) {
    val colors = LocalBreakfastColors.current
    Box {
        Row(
            Modifier
                .heightIn(min = IconTarget)
                .border(1.dp, colors.line, ChoiceShape)
                .clickable(role = Role.Button, onClick = log::openMealMenu)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = colors.ink, style = BreakfastType.Button)
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = log.mealMenuOpen,
            onDismissRequest = log::dismissMealMenu,
            containerColor = colors.bg,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, colors.line),
        ) {
            MealSlot.entries.forEach { meal ->
                DropdownMenuItem(
                    text = { Text(meal.label(), color = colors.ink, style = BreakfastType.Body) },
                    onClick = { log.chooseMeal(meal) },
                )
            }
        }
    }
}
