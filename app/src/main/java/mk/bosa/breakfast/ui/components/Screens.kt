package mk.bosa.breakfast.ui.components

import mk.bosa.breakfast.ui.theme.Gutter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.ui.label
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.HeaderHeight
import mk.bosa.breakfast.ui.theme.IconTarget
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors
import mk.bosa.breakfast.ui.theme.SheetShape

/** Material's own thresholds for a sheet, which it keeps internal. */
private val SheetPositionalThreshold = 56.dp
private val SheetVelocityThreshold = 125.dp

/** Bottom sheet on `bg` with a 36x5 `line` grabber and 24dp padding over a `scrim`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalBreakfastColors.current
    // Material's own slide up is the sheet's motion (this is rememberModalBottomSheetState with its start chosen). With
    // reduced motion the sheet starts where it would stop, so it is simply there (a swipe or a tap outside still lets
    // Material slide it away). The start is read once: a sheet already open stays as it is when Motion changes.
    val reduced = rememberMotionReduced()
    val density = LocalDensity.current
    val positionalThreshold = { with(density) { SheetPositionalThreshold.toPx() } }
    val velocityThreshold = { with(density) { SheetVelocityThreshold.toPx() } }
    val sheetState = rememberSaveable(skipPartiallyExpanded, saver = SheetState.Saver(skipPartiallyExpanded, positionalThreshold, velocityThreshold, { true }, false)) {
        val start = if (!reduced) SheetValue.Hidden else if (skipPartiallyExpanded) SheetValue.Expanded else SheetValue.PartiallyExpanded
        SheetState(skipPartiallyExpanded, positionalThreshold, velocityThreshold, start, { true }, false)
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = colors.bg,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 36.dp, height = 5.dp).background(colors.line, CircleShape))
        },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            SheetNoticeToast()
            // The content keeps what the notice leaves, however tall it is.
            Column(Modifier.weight(1f, fill = false), content = content)
        }
    }
}

/**
 * 2dp track with a 2dp ink bar sweeping across it, and a soft caption. No spinners. [onDark] draws it in white over the
 * camera ground. With reduced motion the bar stays still in the middle of the track: the same look, no sweep.
 */
@Composable
fun LinearProgress(caption: String, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val colors = LocalBreakfastColors.current
    val track = if (onDark) colors.cameraMark.copy(alpha = 0.24f) else colors.line
    val bar = if (onDark) colors.cameraMark else colors.ink
    val captionColor = if (onDark) colors.cameraMark.copy(alpha = 0.7f) else colors.soft
    val phase = if (rememberMotionReduced()) STILL_PROGRESS_PHASE else {
        val transition = rememberInfiniteTransition(label = "linear-progress")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
            label = "linear-progress-phase",
        ).value
    }
    Column(modifier.fillMaxWidth().semantics { contentDescription = caption }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val mid = size.height / 2
            drawLine(track, Offset(0f, mid), Offset(size.width, mid), strokeWidth = size.height)
            val barWidth = size.width * 0.25f
            val start = (size.width + barWidth) * phase - barWidth
            drawLine(bar, Offset(start, mid), Offset(start + barWidth, mid), strokeWidth = size.height)
        }
        Text(caption, color = captionColor, style = BreakfastType.Secondary)
    }
}

/** The sweep's phase that puts the bar in the middle of the track, where it stays when motion is reduced. */
private const val STILL_PROGRESS_PHASE = 0.5f

/**
 * A screen's header: back arrow (none on a tab, [onBack] null, whose title then starts at the gutter), a title
 * (optionally a soft second line) and a trailing slot.
 */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    /** Drawn in place of the title, such as [TabWords]. */
    titleContent: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBreakfastColors.current
    Row(modifier.fillMaxWidth().heightIn(min = HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(IconTarget)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.ink, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = if (onBack != null) 4.dp else 0.dp)) {
            if (titleContent != null) titleContent() else Text(title, color = colors.ink, style = BreakfastType.Title)
            if (subtitle != null) Text(subtitle, color = colors.soft, style = BreakfastType.Meta)
        }
        trailing()
    }
}

/**
 * The shell of a full screen over the base: `bg`, the safe area, the gutter and the [TopBar] 8dp down. With [scroll] the
 * header and [content] scroll together; without it [content] places its own scrolling part. [bottom] stays under them,
 * above the keyboard, with a notice's toast directly above it ([ToastStack]), and [toasts] are the screen's own there.
 */
@Composable
fun SubScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    scroll: Boolean = true,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    toasts: (@Composable ColumnScope.() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = LocalBreakfastColors.current.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier
                    .weight(1f)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp) else Modifier)
                    .padding(horizontal = Gutter),
            ) {
                TopBar(title, onBack, Modifier.padding(top = 8.dp), subtitle = subtitle, trailing = trailing, titleContent = titleContent)
                content()
            }
            if (toasts != null || bottom != null) ToastStack(toasts ?: {})
            if (bottom != null) Column(Modifier.fillMaxWidth().padding(horizontal = Gutter), content = bottom)
        }
    }
}

/** The next section of a food's or recipe's page: a line, then its heading. */
@Composable
internal fun PageSection(title: String) {
    Hairline(Modifier.padding(top = 16.dp))
    SectionHeading(title, Modifier.padding(top = 16.dp))
}

/** A food's or recipe's page: the [SubScreen] with Edit in its bar, a hairline, then [name]; [content] follows. */
@Composable
internal fun ItemPage(title: String, onBack: () -> Unit, name: String, onEdit: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    SubScreen(title, onBack, trailing = { if (onEdit != null) TertiaryButton("Edit", onEdit) }) {
        Hairline(Modifier.padding(top = 8.dp))
        Text(foodText(name), color = LocalBreakfastColors.current.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 8.dp))
        content()
    }
}

/**
 * A centred status under a header: the title, soft [lines], then its [actions], [top] down as each mock draws it.
 */
@Composable
internal fun StatusBlock(title: String, lines: List<String>, top: Dp, actions: @Composable ColumnScope.() -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxWidth().padding(top = top), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = colors.ink, style = BreakfastType.Title, modifier = Modifier.semantics { heading() })
        lines.forEachIndexed { index, line ->
            Text(line, color = colors.soft, style = BreakfastType.Secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = if (index == 0) 8.dp else 4.dp).widthIn(max = 290.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
    }
}

/** A two-way question over the screen: [confirm] or [dismiss]; [text] says what it does. */
@Composable
internal fun ConfirmDialog(confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String = "Cancel", text: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text(dismiss) } },
        text = text?.let { { Text(it) } },
    )
}
