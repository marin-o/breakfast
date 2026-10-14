package com.iron.breakfast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.EntryEditMode
import com.iron.breakfast.QuickAddPreview
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.macro
import com.iron.breakfast.pretty
import com.iron.breakfast.label
import com.iron.breakfast.ui.components.ReceiptBottomTray
import com.iron.breakfast.ui.components.ReceiptChoiceChip
import com.iron.breakfast.ui.components.ReceiptEntryRow
import com.iron.breakfast.ui.components.ReceiptQuickAddField
import com.iron.breakfast.ui.components.ReceiptSticker
import com.iron.breakfast.ui.components.ReceiptSnackbar
import com.iron.breakfast.ui.components.ReceiptTicket
import com.iron.breakfast.ui.components.ReceiptTokenVisualTransformation
import com.iron.breakfast.ui.theme.Ground
import com.iron.breakfast.ui.theme.Ink
import com.iron.breakfast.ui.theme.OnGround
import com.iron.breakfast.ui.theme.OnGroundSoft
import com.iron.breakfast.ui.theme.Paper
import com.iron.breakfast.ui.theme.PaperFresh
import com.iron.breakfast.ui.theme.PenBlue
import com.iron.breakfast.ui.theme.PenOnDark
import com.iron.breakfast.ui.theme.PenOnDarkInk
import com.iron.breakfast.ui.theme.PenTonal
import com.iron.breakfast.ui.theme.PenTonalInk
import com.iron.breakfast.ui.theme.PrintRed
import com.iron.breakfast.ui.theme.Tray
import com.iron.breakfast.ui.theme.receiptHeadingStyle
import kotlin.math.roundToInt

@Composable
internal fun MealScreen(vm: BreakfastViewModel, modifier: Modifier) {
    val target = vm.slot ?: return
    val entries = vm.entries.filter { it.dayKey == vm.selectedDayKey && it.mealSlot == target }
    val foods = vm.foods.associateBy { it.id }
    val total = entries.fold(NutritionValues.ZERO) { a, e -> a + e.nutritionSnapshot }
    val (_, subtitle) = dayTitle(vm.selectedDayKey)

    LazyColumn(
        modifier.fillMaxSize().background(Ground),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::closeMeal) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    val headingStyle = receiptHeadingStyle(target.label())
                    Text(target.label(), style = headingStyle, color = OnGround)
                    Text(subtitle, style = MaterialTheme.typography.titleSmall, color = OnGroundSoft)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(receiptKcal(total.calories), style = MaterialTheme.typography.titleLarge, color = OnGround, fontWeight = FontWeight.Bold)
                    Text(" kcal", style = MaterialTheme.typography.titleSmall, color = OnGroundSoft)
                }
            }
        }
        item {
            ReceiptTicket(total = receiptKcal(total.calories), time = earliestMealTime(entries), mealName = "") {
                entries.forEachIndexed { index, entry -> EntryRow(entry, foods[entry.foodId], vm, index == 0) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.openFocusedLibrary(target) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add food", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = vm::beginScan, modifier = Modifier.heightIn(min = 48.dp)) { Text("Scan") }
            }
        }
        item { vm.pendingContribution?.let { com.iron.breakfast.ContributionAction(vm) } }
    }
}

@Composable
internal fun MealComposer(vm: BreakfastViewModel, target: MealSlot) {
    val previews = vm.quickAddPreviews()
    val suggestions = vm.quickAddSuggestions()
    Column(Modifier.fillMaxWidth().background(Tray).navigationBarsPadding()) {
        vm.undoEntry?.let { deleted ->
            ReceiptSnackbar(
                message = "${deleted.foodName} deleted",
                actionLabel = "Undo",
                onAction = vm::undo,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        ReceiptBottomTray(
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (previews.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            previews.forEach { preview -> MealPendingSticker(preview) }
                        }
                    }
                    if (suggestions.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.forEachIndexed { index, ranked ->
                                if (index == 0) {
                                    com.iron.breakfast.ui.components.ReceiptTonalChip(
                                        vm.quickAddSuggestionLabel(ranked.food),
                                        { vm.chooseQuickSuggestion(ranked.food) },
                                    )
                                } else {
                                    ReceiptChoiceChip(ranked.food.name, false, { vm.chooseQuickSuggestion(ranked.food) })
                                }
                            }
                        }
                    }
                    ReceiptQuickAddField(
                        value = vm.input,
                        onValueChange = vm::onInput,
                        onLog = vm::submit,
                        onImeDone = vm::submit,
                        placeholder = "Add to ${target.label()}",
                        expanded = vm.input.contains(',') || vm.input.length > 28,
                        visualTransformation = ReceiptTokenVisualTransformation(tokenRangesForInput(vm.input, vm::isRecognizedFood)),
                    )
                }
            },
            nav = {},
        )
    }
}

@Composable
private fun MealPendingSticker(preview: QuickAddPreview) {
    ReceiptSticker(
        name = preview.food.name,
        value = receiptKcal(preview.calculation.nutrition.calories),
        detail = "${preview.amount.pretty()} ${preview.unit.displayLabel()}",
        fields = listOf("${preview.calculation.resolvedBaseAmount.pretty()} ${preview.food.nutrition.unit.displayLabel()}", sourceLetter(preview.food)),
        approximateDetail = preview.calculation.approximate,
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(entry: MealEntry, food: FoodRecord?, vm: BreakfastViewModel, first: Boolean) {
    var revealOffset by remember(entry.id) { mutableFloatStateOf(0f) }
    var rowHeight by remember(entry.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    val deleteWidth = with(LocalDensity.current) { 96.dp.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    val quantity = quantityRateDisplay(entry, food)
    Box(Modifier.fillMaxWidth().background(Paper).onSizeChanged { rowHeight = it.height }) {
        if (revealOffset < 0f) Row(
            Modifier.align(Alignment.CenterEnd).width(96.dp).height(if (rowHeight == 0) 56.dp else with(density) { rowHeight.toDp() }).background(PrintRed).clickable(onClickLabel = "Delete ${entry.foodName}", role = Role.Button) { vm.delete(entry); revealOffset = 0f }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = OnGround)
            Text("Delete", color = OnGround, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
        }
        ReceiptEntryRow(
            name = entry.foodName,
            quantityLine = quantity.line,
            approximate = quantity.approximateLabel,
            calories = receiptKcal(entry.nutritionSnapshot.calories),
            source = sourceLetter(food),
            first = first,
            fresh = vm.feedbackOpen(entry),
            modifier = Modifier
                .offset { IntOffset(revealOffset.roundToInt(), 0) }
                .semantics {
                    onClick(label = "Open details for ${entry.foodName}") { vm.openEntryDetail(entry); true }
                    customActions = listOf(
                        CustomAccessibilityAction("Quick edit amount") { vm.beginQuickEdit(entry); true },
                        CustomAccessibilityAction("Edit food and amount") { vm.beginFullEdit(entry); true },
                        CustomAccessibilityAction("Delete ${entry.foodName}") { vm.delete(entry); true },
                    )
                }
                .combinedClickable(
                    onClick = { vm.openEntryDetail(entry) },
                    onDoubleClick = { vm.beginQuickEdit(entry) },
                    onLongClick = { vm.beginFullEdit(entry) },
                )
                .pointerInput(entry.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dx = 0f
                        var dy = 0f
                        var horizontal = false
                        var vertical = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val deltaX = change.position.x - change.previousPosition.x
                            val deltaY = change.position.y - change.previousPosition.y
                            dx += deltaX
                            dy += deltaY
                            if (!horizontal && !vertical && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                                horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                                vertical = !horizontal
                            }
                            if (horizontal && dx < 0f) {
                                revealOffset = (revealOffset + deltaX).coerceIn(-deleteWidth, 0f)
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                        if (horizontal) revealOffset = if (revealOffset <= -deleteWidth * .65f) -deleteWidth else 0f
                    }
                },
        )
    }
}
