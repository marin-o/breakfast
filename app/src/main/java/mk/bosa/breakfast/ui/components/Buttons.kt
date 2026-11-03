package mk.bosa.breakfast.ui.components

import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.ui.theme.AddCircleSize
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.IconTarget
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors
import mk.bosa.breakfast.ui.theme.PrimaryHeight
import mk.bosa.breakfast.ui.theme.PrimaryShape
import mk.bosa.breakfast.ui.theme.SecondaryHeight
import mk.bosa.breakfast.ui.theme.SecondaryShape
import mk.bosa.breakfast.ui.theme.TertiaryHeight

enum class ButtonKind { Constructive, Neutral }

/** One filled primary per screen. Constructive (ok) adds, saves, logs or restores; Neutral (ink) covers the rest. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: ButtonKind = ButtonKind.Constructive,
    fillWidth: Boolean = true,
) {
    val colors = LocalBreakfastColors.current
    val fill = if (kind == ButtonKind.Constructive) colors.ok else colors.ink
    val content = if (kind == ButtonKind.Constructive) colors.onOk else colors.bg
    Box(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = PrimaryHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(PrimaryShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = content,
            style = BreakfastType.Button,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    /** Over the camera ground: white outline and label instead of line and ink. */
    onDark: Boolean = false,
    /** A small icon after the label, such as the chevron on a picker. */
    trailing: ImageVector? = null,
) {
    val colors = LocalBreakfastColors.current
    val ink = if (onDark) colors.cameraMark else colors.ink
    Box(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = SecondaryHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .border(1.dp, if (onDark) colors.cameraMark else colors.line, SecondaryShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, color = ink, style = BreakfastType.Button, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (trailing != null) Icon(trailing, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    soft: Boolean = false,
    /** Over the camera ground: white label. */
    onDark: Boolean = false,
) {
    val colors = LocalBreakfastColors.current
    Box(
        modifier
            .heightIn(min = TertiaryHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (onDark) colors.cameraMark else if (soft) colors.soft else colors.ink,
            style = BreakfastType.Button,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Plain `bad` text by default; [outlined] gives the full-width 1dp outline used inside sheets and confirmations. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalBreakfastColors.current
    if (outlined) {
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = PrimaryHeight)
                .alpha(if (enabled) 1f else 0.45f)
                .border(1.dp, colors.bad, PrimaryShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = colors.bad, style = BreakfastType.Button, textAlign = TextAlign.Center) }
    } else {
        Box(
            modifier
                .heightIn(min = TertiaryHeight)
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = colors.bad, style = BreakfastType.Button, textAlign = TextAlign.Center) }
    }
}

/** 32dp circle with a `+` at the start of a section, in a 44dp box; the touch area is 48dp (see [IconTarget]). */
@Composable
fun AddCircle(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = Icons.Outlined.Add,
) {
    val colors = LocalBreakfastColors.current
    Box(
        modifier
            .size(IconTarget)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(AddCircleSize).border(1.5.dp, colors.ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
        }
    }
}

/** A form's Save at its foot, in a [SubScreen]'s `bottom`. */
@Composable
internal fun SaveButton(onClick: () -> Unit, enabled: Boolean) {
    PrimaryButton("Save", onClick, Modifier.padding(top = 16.dp, bottom = 8.dp), enabled = enabled, kind = ButtonKind.Constructive)
}

/** The ways on for a food not found here: Open Food Facts, or a personal food. */
/** Whether two buttons of these natural widths go side by side in [available]. */
internal fun buttonsFitOneLine(first: Int, second: Int, gap: Int, available: Int): Boolean = first + gap + second <= available

/**
 * Two buttons that fill their width (`fillWidth`): side by side, sharing the room left evenly, and one above the other
 * when their labels don't fit on one line or when [stacked].
 */
@Composable
internal fun ButtonsSideBySide(stacked: Boolean, first: @Composable () -> Unit, second: @Composable () -> Unit, modifier: Modifier = Modifier, gap: Dp = 12.dp) {
    Layout(content = { first(); second() }, modifier = modifier) { measurables, constraints ->
        val (a, b) = measurables
        val width = constraints.maxWidth
        val space = gap.roundToPx()
        val aWidth = a.maxIntrinsicWidth(Constraints.Infinity)
        val bWidth = b.maxIntrinsicWidth(Constraints.Infinity)
        if (stacked || !buttonsFitOneLine(aWidth, bWidth, space, width)) {
            val top = a.measure(Constraints.fixedWidth(width))
            val bottom = b.measure(Constraints.fixedWidth(width))
            layout(width, top.height + space + bottom.height) {
                top.place(0, 0)
                bottom.place(0, top.height + space)
            }
        } else {
            val share = (width - aWidth - bWidth - space) / 2
            val height = maxOf(a.maxIntrinsicHeight(aWidth + share), b.maxIntrinsicHeight(bWidth + share))
            val left = a.measure(Constraints.fixed(aWidth + share, height))
            val right = b.measure(Constraints.fixed(width - space - left.width, height))
            layout(width, height) {
                left.place(0, 0)
                right.place(left.width + space, 0)
            }
        }
    }
}

@Composable
internal fun SearchOrCreateButtons(onSearch: () -> Unit, onCreate: () -> Unit) {
    PrimaryButton("Search Open Food Facts", onSearch, kind = ButtonKind.Neutral)
    SecondaryButton("Create personal food", onCreate)
}
