package mk.bosa.breakfast.ui.components

import androidx.compose.material.icons.outlined.Person
import mk.bosa.breakfast.ui.theme.ToastGutter
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.ui.label
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.ComposerHeight
import mk.bosa.breakfast.ui.theme.ComposerShape
import mk.bosa.breakfast.ui.theme.IconTarget
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors
import mk.bosa.breakfast.ui.theme.TertiaryHeight

/** The bottom padding a base screen's list keeps, so its last row clears the [ComposerFade] floating over it. */
val BottomAreaClearance = 260.dp

/**
 * A `bg` fade with 16dp gutters, above the system navigation bar: the one bottom area of the base screens (Daily,
 * Meal, Library, Recipes; `app/BaseBottomArea.kt`), drawn once over whichever shows, so the bar and the tabs never
 * move between them. The list behind it keeps [BottomAreaClearance] at its end.
 */
@Composable
fun ComposerFade(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to colors.bg.copy(alpha = 0f), 0.22f to colors.bg))
            .navigationBarsPadding()
            .padding(start = ToastGutter, end = ToastGutter, top = 12.dp),
        content = content,
    )
}

/**
 * The base screens' ink bar (`app/BaseBottomArea.kt`): [text] (the Log opener, [ComposerText], or the Library's search)
 * then the scan icon.
 */
@Composable
fun Composer(onScan: () -> Unit, modifier: Modifier = Modifier, text: @Composable RowScope.() -> Unit) {
    InkBar(modifier) {
        text()
        IconButton(onClick = onScan, modifier = Modifier.size(IconTarget)) {
            Icon(ScanBarcodeIcon, contentDescription = "Scan barcode", tint = LocalBreakfastColors.current.bg)
        }
    }
}

/**
 * The [Composer]'s Log opener: the placeholder, or the typed draft at full strength ([draft]) cut as [longDraft] says;
 * a tap opens the Log page.
 */
@Composable
fun ComposerText(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier, draft: Boolean = false, longDraft: LongDraft = LongDraft.START) {
    val colors = LocalBreakfastColors.current
    val text = placeholder.replace('\n', ' ')
    val color = if (draft) colors.bg else colors.bg.barPlaceholder
    val textModifier = modifier.clickable(role = Role.Button, onClick = onClick)
    if (draft && longDraft == LongDraft.END) {
        EndOfText(text, color, textModifier)
    } else {
        Text(
            text,
            color = color,
            style = BreakfastType.Body,
            maxLines = if (draft && longDraft == LongDraft.TWO_LINES) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = textModifier,
        )
    }
}

/** One line of [text] that keeps its end: cut at the start with "…" when it is wider than the space. */
@Composable
private fun EndOfText(text: String, color: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = BreakfastType.Body
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val fitted = remember(text, width, measurer) {
            if (!constraints.hasBoundedWidth) text
            else startEllipsized(text) { candidate -> measurer.measure(candidate, style, maxLines = 1, softWrap = false).size.width <= width }
        }
        // TalkBack reads the whole draft, not the shortened line.
        Text(
            fitted, color = color, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = Modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        )
    }
}

/**
 * The ink bar shared by [Composer] and the Library search: an ink fill, radius 16, min height 52.
 * Its content sits in a row with 16dp start padding and [endPadding] at the end.
 */
@Composable
fun InkBar(modifier: Modifier = Modifier, endPadding: Dp = 4.dp, content: @Composable RowScope.() -> Unit) {
    val colors = LocalBreakfastColors.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ComposerHeight)
            .clip(ComposerShape)
            .background(colors.ink)
            .padding(start = 16.dp, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The bottom bar's tabs, in their order. */
enum class NavTab(val label: String) { MEALS("Meals"), LIBRARY("Library"), RECIPES("Recipes") }

/** The Settings gear as an item of the bar: after the tabs. */
private const val GEAR = 3
/** The Profile icon as an item of the bar: before the tabs. */
private const val PROFILE = 4

/**
 * The text tabs ([NavTab], in order), centred with a 40dp gap, the Profile icon at the left end and the Settings icon at
 * the right end. The active item (an icon while [profileOpen] or [settingsOpen]) is ink, the others soft, and a 2dp ink line sits under it; when it changes, the line
 * slides to the new item and the emphasis crosses over ([Motion.nav]; at once with reduced motion).
 */
@Composable
fun BottomNav(
    selected: NavTab,
    onMeals: () -> Unit,
    onLibrary: () -> Unit,
    onRecipes: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    settingsOpen: Boolean = false,
    profileOpen: Boolean = false,
) {
    val colors = LocalBreakfastColors.current
    val motion = rememberMotion()
    val active = if (settingsOpen) GEAR else if (profileOpen) PROFILE else selected.ordinal
    // Where each item sits in the bar, in px: the line goes under the one that is active.
    val spans = remember { NavSpans() }
    val start = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = spans.of(active)
    LaunchedEffect(active, target) {
        val (toStart, toWidth) = target ?: return@LaunchedEffect
        if (!placed) {
            // The first frame: right under the item; from then on the line slides.
            start.snapTo(toStart)
            width.snapTo(toWidth)
            placed = true
        }
        launch { start.animateTo(toStart, motion.nav()) }
        width.animateTo(toWidth, motion.nav())
    }
    val lineColor = colors.ink
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { spans.bar = it; spans.measure() }
            .drawBehind {
                if (placed && width.value > 0f) {
                    val y = size.height - 8.dp.toPx()
                    drawLine(lineColor, Offset(start.value, y), Offset(start.value + width.value, y), strokeWidth = 2.dp.toPx())
                }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fun Modifier.span(item: Int) = onGloballyPositioned { spans.items[item] = it; spans.measure() }
        // Equal sides keep the tabs centred. The icons keep their size: at a large font it is the gaps between the tabs that give.
        NavIconItem(Icons.Outlined.Person, "Profile", active == PROFILE, motion, Modifier.span(PROFILE), onProfile)
        Row(Modifier.weight(1f).selectableGroup(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            NavTab.entries.forEachIndexed { index, tab ->
                if (index > 0) Spacer(Modifier.weight(1f, fill = false).widthIn(max = 40.dp).fillMaxWidth())
                NavTabButton(tab.label, selected = index == active, motion = motion, labelModifier = Modifier.span(index), onClick = when (tab) {
                    NavTab.MEALS -> onMeals
                    NavTab.LIBRARY -> onLibrary
                    NavTab.RECIPES -> onRecipes
                })
            }
        }
        NavIconItem(Icons.Outlined.Settings, "Settings", active == GEAR, motion, Modifier.span(GEAR), onSettings)
    }
}

/** One end of the bar: an icon, ink while active and soft otherwise. */
@Composable
private fun NavIconItem(icon: ImageVector, description: String, active: Boolean, motion: Motion, iconModifier: Modifier, onClick: () -> Unit) {
    val colors = LocalBreakfastColors.current
    val tint by animateColorAsState(if (active) colors.ink else colors.soft, motion.nav(), label = description)
    IconButton(onClick = onClick, modifier = Modifier.size(IconTarget)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp).then(iconModifier))
    }
}

/**
 * Where the bar's items sit, measured in the bar itself: whichever of the bar and an item is placed last measures them
 * all, so no inset between the bar and the window counts. A span is written only when it moved, so the bar riding the
 * keyboard (each frame of its animation) does not draw it again.
 */
private class NavSpans {
    var bar: LayoutCoordinates? = null
    val items = arrayOfNulls<LayoutCoordinates>(PROFILE + 1)
    private val spans = mutableStateMapOf<Int, Pair<Float, Float>>()

    fun of(item: Int): Pair<Float, Float>? = spans[item]

    fun measure() {
        val bar = bar?.takeIf { it.isAttached } ?: return
        items.forEachIndexed { item, coords ->
            if (coords == null || !coords.isAttached) return@forEachIndexed
            val span = bar.localPositionOf(coords, Offset.Zero).x to coords.size.width.toFloat()
            if (spans[item] != span) spans[item] = span
        }
    }
}

@Composable
private fun NavTabButton(label: String, selected: Boolean, motion: Motion, labelModifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalBreakfastColors.current
    val color by animateColorAsState(if (selected) colors.ink else colors.soft, motion.nav(), label = "tab")
    Box(
        Modifier
            .heightIn(min = TertiaryHeight)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, style = BreakfastType.SecondaryMedium, modifier = labelModifier)
    }
}

/** Tappable words in the bottom bar's tab style, for a screen's [TopBar]; they wrap at a large font. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabWords(words: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        words.forEachIndexed { index, word -> NavTabButton(word, index == selected, motion, onClick = { onSelect(index) }) }
    }
}
