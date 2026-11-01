package com.iron.breakfast.today

import com.iron.breakfast.ui.components.listRowMotion
import com.iron.breakfast.entry.entryActions
import com.iron.breakfast.ui.components.followDrag
import com.iron.breakfast.ui.shortDateLabel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.iron.breakfast.ui.components.BottomAreaClearance
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.iron.breakfast.app.DiarySession
import com.iron.breakfast.app.Surfaces
import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.entry.EntryEdit
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.scan.ScanSession
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.contribution.ContributionAction
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.nutritionTotalOf
import com.iron.breakfast.ui.label
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.FoodRow
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.Motion
import com.iron.breakfast.ui.components.Reveal
import com.iron.breakfast.ui.components.rememberMotion
import com.iron.breakfast.ui.components.LocalScreenBody
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.AddCircleInset
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.Gutter
import com.iron.breakfast.ui.theme.PrimaryShape
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.theme.swipeLabelOnBad
import kotlin.math.roundToInt
import com.iron.breakfast.ui.screens.formatKcal
import com.iron.breakfast.ui.screens.entryQuantityLine

@Composable
internal fun MealScreen(
    session: DiarySession,
    day: DayState,
    settings: SettingsState,
    log: LogComposer,
    contribution: ContributionFlow,
    entryEdit: EntryEdit,
    surfaces: Surfaces,
    undo: UndoController,
    feedback: MatchFeedback,
    scan: ScanSession,
    modifier: Modifier,
    /** The meal shown: the Meal screen's own, or, while it glides out, the one it showed. */
    meal: MealSlot? = surfaces.meal,
) {
    val target = meal ?: return
    val entries = session.entries.filter { it.dayKey == day.selectedDayKey && it.mealSlot == target }
    val total = nutritionTotalOf(entries)
    val colors = LocalBreakfastColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val motion = rememberMotion()
    Box(modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().then(LocalScreenBody.current),
            // Room for the bottom area that floats over the end of the list.
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = BottomAreaClearance),
        ) {
            item {
                TopBar(
                    title = target.label(),
                    onBack = surfaces::closeMeal,
                    modifier = Modifier.padding(top = 8.dp),
                    subtitle = shortDateLabel(day.selectedDayKey),
                    trailing = {
                        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.Bottom) {
                            Text(formatKcal(total.calories), color = colors.ink, style = BreakfastType.Title)
                            Text(" kcal", color = colors.soft, style = BreakfastType.Secondary)
                        }
                    },
                )
                Hairline(Modifier.padding(top = 14.dp))
            }
            // A new or restored row fades in; a deleted one fades as the rows below close the gap.
            items(entries, key = { it.id }) { entry ->
                Column(Modifier.fillMaxWidth().then(listRowMotion(motion))) {
                    EntryRow(entry, undo, entryEdit, motion)
                    // Same correction block as Daily, for a doubtful match from the Log page.
                    Reveal(entry.takeIf { feedback.isOpen(it) }, resize = true) { CorrectionStrip(feedback, it) }
                }
            }
            item {
                val addFoodModifier = Modifier.widthIn(min = 128.dp * fontScale)
                val scanModifier = Modifier.widthIn(min = 80.dp * fontScale)
                if (isLargeFont(fontScale)) {
                    Column(Modifier.fillMaxWidth().padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Add food", { surfaces.openFocusedLibrary(target) }, addFoodModifier, fillWidth = false)
                        SecondaryButton("Scan", scan::begin, scanModifier, fillWidth = false)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Add food", { surfaces.openFocusedLibrary(target) }, addFoodModifier, fillWidth = false)
                        SecondaryButton("Scan", scan::begin, scanModifier, fillWidth = false)
                    }
                }
                if (contribution.actionShown) ContributionAction(contribution)
            }
        }
    }
}

@Composable
private fun EntryRow(entry: MealEntry, undo: UndoController, entryEdit: EntryEdit, motion: Motion) {
    val colors = LocalBreakfastColors.current
    var revealOffset by remember(entry.id) { mutableFloatStateOf(0f) }
    // The row follows the finger; on release it settles open or shut ([Motion.swipe]), and a new touch stops that.
    val scope = rememberCoroutineScope()
    var settle by remember(entry.id) { mutableStateOf<Job?>(null) }
    // The row was open when the touch began: a tap then shuts it instead of opening the details.
    var openAtDown by remember(entry.id) { mutableStateOf(false) }
    val currentMotion by rememberUpdatedState(motion)
    var rowHeight by remember(entry.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val deleteWidth = with(LocalDensity.current) { (96.dp * fontScale).toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().background(colors.bg).onSizeChanged { rowHeight = it.height }) {
        // Swipe reveals the delete panel; the panel is the only place `bad` fills a surface. It shows as wide as the
        // row has moved, so it grows with the finger; the full panel under it keeps its width, and "Delete" its line.
        // It is a rounded button inset from the row, shaped like the primary buttons.
        if (revealOffset < 0f) Box(
            Modifier.align(Alignment.CenterEnd).width(with(density) { (-revealOffset.roundToInt()).toDp() }).height(if (rowHeight == 0) 56.dp else with(density) { rowHeight.toDp() }).padding(start = 8.dp, top = 6.dp, bottom = 6.dp).clip(PrimaryShape).background(colors.bad).clickable(onClickLabel = "Delete ${entry.foodName}", role = Role.Button) { undo.delete(entry) },
        ) {
            Row(
                Modifier.wrapContentWidth(Alignment.Start, unbounded = true).widthIn(min = with(density) { deleteWidth.toDp() } - 8.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Delete", color = swipeLabelOnBad(colors), style = BreakfastType.Button, maxLines = 1, softWrap = false)
            }
        }
        FoodRow(
            name = entry.foodName,
            quantityLine = entryQuantityLine(entry),
            calories = formatKcal(entry.nutritionSnapshot.calories),
            caloriesEnd = AddCircleInset,
            modifier = Modifier.offset { IntOffset(revealOffset.roundToInt(), 0) }.entryActions(entry, entryEdit, undo) { if (!openAtDown) entryEdit.openDetail(entry) }.pointerInput(entry.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    settle?.cancel()
                    openAtDown = revealOffset < 0f
                    val end = followDrag(down.id, touchSlop) { change, stepX, dx, horizontal ->
                        // Left opens; right shuts a row that is open (a shut row ignores a drag to the right).
                        if (horizontal && (dx < 0f || revealOffset < 0f)) { revealOffset = (revealOffset + stepX).coerceIn(-deleteWidth, 0f); change.consume() }
                    }
                    // Every release ends open or shut, also a tap or a scroll that stopped a settle half way; a tap on
                    // an open row shuts it.
                    val tap = !end.horizontal && !end.vertical
                    val target = if ((tap && openAtDown) || revealOffset > -deleteWidth * .65f) 0f else -deleteWidth
                    if (revealOffset != target) settle = scope.launch { animate(revealOffset, target, animationSpec = currentMotion.swipe()) { value, _ -> revealOffset = value } }
                }
            },
        )
    }
}
