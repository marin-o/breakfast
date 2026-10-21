package com.iron.breakfast.ui.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.ContributionAction
import com.iron.breakfast.EntryEditMode
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.label
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.Composer
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.Snackbar
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.Gutter
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import kotlin.math.roundToInt

@Composable
internal fun MealScreen(vm: BreakfastViewModel, modifier: Modifier) {
    val target = vm.slot ?: return
    val entries = vm.entries.filter { it.dayKey == vm.selectedDayKey && it.mealSlot == target }
    val foods = vm.foods.associateBy { it.id }
    val total = entries.fold(NutritionValues.ZERO) { sum, entry -> sum + entry.nutritionSnapshot }
    val colors = LocalBreakfastColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            // Room for the composer over the fade at the bottom.
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 260.dp),
        ) {
            item {
                TopBar(
                    title = target.label(),
                    onBack = vm::closeMeal,
                    modifier = Modifier.padding(top = 8.dp),
                    subtitle = shortDateLabel(vm.selectedDayKey),
                    trailing = {
                        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.Bottom) {
                            Text(formatKcal(total.calories), color = colors.ink, style = BreakfastType.Title)
                            Text(" kcal", color = colors.soft, style = BreakfastType.Secondary)
                        }
                    },
                )
                Hairline(Modifier.padding(top = 14.dp))
            }
            items(entries) { entry ->
                Column(Modifier.fillMaxWidth()) {
                    EntryRow(entry, foods[entry.foodId], vm)
                    // Same correction block as Daily, for a doubtful match from the Log page.
                    if (vm.feedbackOpen(entry)) CorrectionStrip(vm, entry)
                }
            }
            item {
                val addFoodModifier = Modifier.widthIn(min = 128.dp * fontScale)
                val scanModifier = Modifier.widthIn(min = 80.dp * fontScale)
                if (isLargeFont(fontScale)) {
                    Column(Modifier.fillMaxWidth().padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Add food", { vm.openFocusedLibrary(target) }, addFoodModifier, fillWidth = false)
                        SecondaryButton("Scan", vm::beginScan, scanModifier, fillWidth = false)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Add food", { vm.openFocusedLibrary(target) }, addFoodModifier, fillWidth = false)
                        SecondaryButton("Scan", vm::beginScan, scanModifier, fillWidth = false)
                    }
                }
                vm.pendingContribution?.let { ContributionAction(vm) }
            }
        }
        // Hidden during an entry edit, which owns the input and its own sheet.
        if (vm.entryEditMode == EntryEditMode.NONE) MealComposer(vm, target, Modifier.align(Alignment.BottomCenter))
    }
}

/** Ink composer that opens the Log page with this meal preset, over the fade. Shows the logged-batch and delete snackbars. */
@Composable
internal fun MealComposer(vm: BreakfastViewModel, target: MealSlot, modifier: Modifier = Modifier) {
    val draft = vm.input.isNotBlank()
    ComposerFade(modifier) {
        vm.loggedBatchMessage?.let { Snackbar(it, "Undo", vm::undoLoggedBatch, Modifier.padding(bottom = 8.dp)) }
        vm.undoEntry?.let { deleted -> Snackbar("${deleted.foodName} deleted", "Undo", vm::undo, Modifier.padding(bottom = 8.dp)) }
        Composer(
            placeholder = if (draft) vm.input else "Add to ${target.label()}",
            onClick = { vm.openLog(target) },
            onScan = vm::beginScan,
            draft = draft,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(entry: MealEntry, food: FoodRecord?, vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    var revealOffset by remember(entry.id) { mutableFloatStateOf(0f) }
    var rowHeight by remember(entry.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    val deleteWidth = with(LocalDensity.current) { 96.dp.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    val quantity = quantityRateDisplay(entry, food, includeRateUnit = false, asciiMultiply = true)
    Box(Modifier.fillMaxWidth().background(colors.bg).onSizeChanged { rowHeight = it.height }) {
        // Swipe reveals the delete panel; the panel is the only place `bad` fills a surface.
        if (revealOffset < 0f) Row(
            Modifier.align(Alignment.CenterEnd).width(96.dp).height(if (rowHeight == 0) 56.dp else with(density) { rowHeight.toDp() }).background(colors.bad).clickable(onClickLabel = "Delete ${entry.foodName}", role = Role.Button) { vm.delete(entry); revealOffset = 0f },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Delete", color = colors.cameraMark, style = BreakfastType.Button)
        }
        FoodRow(
            name = entry.foodName,
            quantityLine = quantity.plainLine(),
            calories = formatKcal(entry.nutritionSnapshot.calories),
            modifier = Modifier.offset { IntOffset(revealOffset.roundToInt(), 0) }.semantics {
                onClick(label = "Open details for ${entry.foodName}") { vm.openEntryDetail(entry); true }
                customActions = listOf(
                    CustomAccessibilityAction("Quick edit amount") { vm.beginQuickEdit(entry); true },
                    CustomAccessibilityAction("Edit food and amount") { vm.beginFullEdit(entry); true },
                    CustomAccessibilityAction("Delete ${entry.foodName}") { vm.delete(entry); true },
                )
            }.combinedClickable(onClick = { vm.openEntryDetail(entry) }, onDoubleClick = { vm.beginQuickEdit(entry) }, onLongClick = { vm.beginFullEdit(entry) }).pointerInput(entry.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    var horizontal = false
                    var vertical = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val deltaX = change.position.x - change.previousPosition.x
                        val deltaY = change.position.y - change.previousPosition.y
                        dx += deltaX
                        dy += deltaY
                        if (!horizontal && !vertical && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) { horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy); vertical = !horizontal }
                        if (horizontal && dx < 0f) { revealOffset = (revealOffset + deltaX).coerceIn(-deleteWidth, 0f); change.consume() }
                        if (!change.pressed) break
                    }
                    if (horizontal) revealOffset = if (revealOffset <= -deleteWidth * .65f) -deleteWidth else 0f
                }
            },
        )
    }
}
