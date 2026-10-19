@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.iron.breakfast.ui.components

import com.iron.breakfast.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.label
import com.iron.breakfast.ui.theme.ReceiptFieldPillShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.iron.breakfast.ui.theme.LocalReceiptColors
import com.iron.breakfast.ui.theme.TicketBandHeight
import com.iron.breakfast.ui.theme.TicketAddHeight
import com.iron.breakfast.ui.theme.QuickAddHeight
import com.iron.breakfast.ui.theme.ReceiptPaperShape
import com.iron.breakfast.ui.theme.ReceiptPillShape

@Composable
fun ReceiptTicket(
    mealName: String,
    total: String,
    time: String? = null,
    addFoodLabel: String? = null,
    onAddFood: (() -> Unit)? = null,
    onMealClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalReceiptColors.current
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.paper),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TicketBandHeight)
                    .background(colors.paper)
                    .clickable(enabled = onMealClick != null, role = Role.Button) { onMealClick?.invoke() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(mealName, style = MaterialTheme.typography.headlineMedium, color = colors.red, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (!time.isNullOrBlank()) Text(time, style = MaterialTheme.typography.bodySmall, color = colors.soft)
                }
                Text(total, style = MaterialTheme.typography.titleLarge, color = colors.red, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Column(content = content)
            if (addFoodLabel != null && onAddFood != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TicketAddHeight)
                        .border(2.dp, colors.ink)
                        .clickable(role = Role.Button, onClick = onAddFood)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                    Text(addFoodLabel, color = colors.ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ReceiptSlip(
    label: String,
    total: String,
    unit: String = "kcal",
    macros: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    pending: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = LocalReceiptColors.current
    Surface(modifier = modifier, color = colors.paper, shape = ReceiptPaperShape) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = colors.soft, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(total, style = MaterialTheme.typography.displayLarge, color = colors.ink)
                    pending?.let { Text(" $it", style = MaterialTheme.typography.headlineLarge, color = colors.red) }
                    Text(" $unit", style = MaterialTheme.typography.titleSmall, color = colors.soft)
                }
            }
            if (macros.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.fillMaxWidth().receiptDashedRule().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    macros.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.ink) }
                }
            }
            content?.invoke()
        }
    }
}

/** The 1.5dp dashed paper rule drawn along the top edge of a row. */
@Composable
fun Modifier.receiptDashedRule(): Modifier {
    val soft = LocalReceiptColors.current.soft
    return drawBehind {
    drawLine(
        color = soft,
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    }
}

@Composable
fun ReceiptEntryRow(
    name: String,
    quantityLine: String,
    calories: String,
    source: String,
    approximate: String? = null,
    fresh: Boolean = false,
    first: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Small grey line under the kcal figure, such as "/100 ml". */
    calorieCaption: String? = null,
    /** Daily and meal receipts place one printed rule between sections instead of row rules. */
    showRule: Boolean = true,
) {
    val colors = LocalReceiptColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val calorieWidth = 54.dp * fontScale
    Box(
        modifier
            .fillMaxWidth()
            .then(if (fresh) Modifier.background(colors.paper) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (first || !showRule) Modifier else Modifier.receiptDashedRule()),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(receiptFoodName(name), color = colors.ink, style = MaterialTheme.typography.bodyLarge)
                if (approximate != null || quantityLine.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (approximate != null) ApproxTag(approximate)
                    if (quantityLine.isNotBlank()) Text(quantityLine, color = colors.soft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
            Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
                Text(calories, color = colors.ink, style = MaterialTheme.typography.displaySmall, modifier = Modifier.width(calorieWidth), textAlign = TextAlign.End)
                if (calorieCaption != null) Text(calorieCaption, color = colors.soft, style = MaterialTheme.typography.labelSmall)
            }
            SourceBox(source, Modifier.padding(start = 10.dp, top = 1.dp).width(22.dp * fontScale))
        }
        if (fresh) Text("*", color = colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 11.dp))
    }
}

/** Spoken and written name for a source letter. */
fun sourceLabel(letter: String): String = when (letter) {
    "B" -> "Bundled"
    "P" -> "Personal"
    "O" -> "Open Food Facts"
    else -> "Unknown source"
}

@Composable
fun SourceBox(source: String, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    val description = "Source: ${sourceLabel(source).lowercase()}"
    Box(modifier.defaultMinSize(22.dp, 22.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(source, color = colors.soft, style = MaterialTheme.typography.labelSmall)
    }
}

/** Round 48dp tonal button for repeated row actions. */
@Composable
fun ReceiptTonalIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LocalReceiptColors.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp).border(2.dp, if (enabled) colors.ink else colors.soft),
    ) { Icon(icon, contentDescription = contentDescription, tint = if (enabled) colors.ink else colors.soft) }
}

/** Circular outlined barcode-scan button that sits beside a search pill. */
@Composable
fun ReceiptScanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    IconButton(onClick = onClick, modifier = modifier.size(48.dp).border(2.dp, colors.ink)) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", tint = colors.ink)
    }
}

@Composable
fun ApproxTag(text: String, modifier: Modifier = Modifier) = ReceiptRedTag("≈ $text", modifier)

/** Small print-red chip: the approximation mark and the "Required" marker share this one form. */
@Composable
fun ReceiptRedTag(text: String, modifier: Modifier = Modifier) {
    val colors = com.iron.breakfast.ui.theme.LocalReceiptColors.current
    Text(
        text = text,
        modifier = modifier.background(colors.redFill).padding(horizontal = 3.dp, vertical = 1.dp),
        color = colors.onRedFill,
        style = MaterialTheme.typography.labelSmall,
    )
}

enum class ReceiptTokenKind { Meal, Quantity, Food }

data class ReceiptTokenRange(val range: IntRange, val kind: ReceiptTokenKind)

/** Applies the locked quick-add emphasis without changing cursor offsets. */
class ReceiptTokenVisualTransformation(
    private val tokens: List<ReceiptTokenRange>,
    private val redFill: Color = Color(0xFFB8263A),
    private val onRedFill: Color = Color(0xFFEEEFEC),
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text)
        tokens.forEach { token ->
            val start = token.range.first.coerceIn(0, text.length)
            val end = (token.range.last + 1).coerceIn(start, text.length)
            if (start < end) styled.addStyle(token.kind.style(redFill, onRedFill), start, end)
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun ReceiptTokenKind.style(redFill: Color, onRedFill: Color): SpanStyle = when (this) {
    ReceiptTokenKind.Meal -> SpanStyle(color = onRedFill, background = redFill, fontWeight = FontWeight.Bold)
    ReceiptTokenKind.Quantity -> SpanStyle(fontWeight = FontWeight.Bold)
    ReceiptTokenKind.Food -> SpanStyle(textDecoration = TextDecoration.Underline)
}

/** Food names use the Macedonian shaping locale required by the receipt font. */
fun receiptFoodName(value: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(localeList = LocaleList("mk"))) { append(value) }
}

@Composable
fun ReceiptSticker(
    name: String,
    value: String,
    detail: String,
    fields: List<String> = emptyList(),
    approximateDetail: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReceiptColors.current
    Column(
        modifier
            .widthIn(max = 180.dp)
            .background(colors.paper)
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(receiptFoodName(name), color = colors.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(value, color = colors.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (approximateDetail) ApproxTag(detail) else ReceiptTag(detail)
            fields.forEach { field ->
                ReceiptTag(field)
            }
        }
    }
}

/** Small outlined grey tag: "2 tbsp" on a sticker, "from scan" on a field. */
@Composable
fun ReceiptTag(value: String, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Text(
        value,
        color = colors.soft,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.border(1.dp, colors.soft).padding(horizontal = 5.dp),
    )
}

@Composable
fun ReceiptChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (selected) Modifier.drawBehind {
                val one = 1.dp.toPx()
                val gap = 2.dp.toPx()
                drawRect(colors.ink, topLeft = androidx.compose.ui.geometry.Offset(one / 2, one / 2), size = androidx.compose.ui.geometry.Size(size.width - one, size.height - one), style = Stroke(one))
                drawRect(colors.ink, topLeft = androidx.compose.ui.geometry.Offset(gap + one / 2, gap + one / 2), size = androidx.compose.ui.geometry.Size(size.width - 2 * gap - one, size.height - 2 * gap - one), style = Stroke(one))
            } else Modifier.border(2.dp, colors.ink))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(receiptFoodName(label), color = colors.ink, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight(500), maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun ReceiptActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailingIcon: ImageVector? = null) {
    val colors = LocalReceiptColors.current
    Row(modifier.heightIn(min = 48.dp).border(2.dp, colors.ink).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(receiptFoodName(label), color = colors.ink, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        trailingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.ink) }
    }
}

@Composable
fun ReceiptTonalChip(
    label: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReceiptColors.current
    Row(modifier.heightIn(min = 48.dp).border(3.dp, colors.ink).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.ink) }
        Text(receiptFoodName(label), color = colors.ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ReceiptQuickAddField(
    value: String,
    onValueChange: (String) -> Unit,
    onLog: () -> Unit,
    placeholder: String = "2 eggs, 1 banana",
    expanded: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeDone: () -> Unit = onLog,
    logContentDescription: String = "Log",
    modifier: Modifier = Modifier,
) {
    val colors = LocalReceiptColors.current
    val stacked = LocalDensity.current.fontScale >= 1.8f
    val effectiveExpanded = expanded || stacked
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (effectiveExpanded) 76.dp else QuickAddHeight)
            .background(colors.paper)
            .border(if (focused) 3.dp else 2.dp, colors.ink)
            .padding(start = 10.dp, end = 0.dp, top = if (stacked) 8.dp else 0.dp, bottom = if (stacked) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ">",
            color = colors.ink,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            singleLine = !effectiveExpanded,
            minLines = if (effectiveExpanded) 2 else 1,
            maxLines = if (effectiveExpanded) 2 else 1,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onImeDone() }),
            decorationBox = { inner -> Box { if (value.isBlank()) Text(placeholder, color = colors.soft, style = MaterialTheme.typography.bodyMedium); inner() } },
        )
        // Keep the visual control at 46dp while giving the action the required
        // 48dp touch target.
        IconButton(onClick = onLog, enabled = value.isNotBlank(), modifier = Modifier.size(52.dp).border(2.dp, colors.ink)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = logContentDescription, tint = if (value.isBlank()) colors.soft else colors.ink)
        }
    }
}

@Composable
fun ReceiptSnackbar(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReceiptColors.current
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        color = colors.ink,
        contentColor = colors.paper,
        shape = ReceiptPaperShape,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = colors.paper, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalReceiptColors.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        shape = ReceiptPaperShape,
        containerColor = colors.paper,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        dragHandle = {
            Text("====", color = colors.ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        },
        content = content,
    )
}

@Composable
fun ReceiptBottomTray(content: @Composable () -> Unit, nav: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Column(modifier.background(colors.paper)) {
        CharacterRule('=', color = colors.ink, modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp))
        Box(Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 8.dp)) { content() }
        nav()
    }
}

@Composable
fun ReceiptNavigationBar(selectedDaily: Boolean, onDaily: () -> Unit, onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    val stacked = LocalDensity.current.fontScale >= 1.8f
    val container = modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
    val tabData = listOf("DAILY" to onDaily, "LIBRARY" to onLibrary)
    if (stacked) {
        Column(container, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tabData.forEachIndexed { index, (label, action) ->
                ReceiptNavTab(label, selected = if (index == 0) selectedDaily else !selectedDaily, action = action, modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(container, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabData.forEachIndexed { index, (label, action) ->
                ReceiptNavTab(label, selected = if (index == 0) selectedDaily else !selectedDaily, action = action, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReceiptNavTab(label: String, selected: Boolean, action: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Box(
        modifier.heightIn(min = 48.dp)
            .background(if (selected) colors.ink else colors.paper)
            .border(2.dp, colors.ink)
            .clickable(role = Role.Tab, onClick = action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) colors.paper else colors.ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}


/** Sub-screen header: arrow_back, a bold title (optionally a soft second line) and a trailing slot. */
@Composable
fun ReceiptTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backEnabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalReceiptColors.current
    Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = backEnabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title.uppercase(), color = colors.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = colors.soft, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

/** Rounded paper text field with a clear button, used for the search screens. */
@Composable
fun ReceiptSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    val colors = LocalReceiptColors.current
    Row(
        modifier
            .heightIn(min = 52.dp)
            .background(colors.paper)
            .border(2.dp, colors.ink)
            .padding(start = 11.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val strokeWidth = 2.dp.toPx()
            drawCircle(
                color = colors.ink,
                radius = 4.dp.toPx(),
                center = Offset(7.dp.toPx(), 7.dp.toPx()),
                style = Stroke(width = strokeWidth),
            )
            drawLine(
                color = colors.ink,
                start = Offset(10.dp.toPx(), 10.dp.toPx()),
                end = Offset(14.dp.toPx(), 14.dp.toPx()),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Square,
            )
        }
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.ink),
            keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }, onDone = { onSearch?.invoke() }),
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear", tint = colors.soft)
            }
        }
    }
}

/**
 * Labelled paper field. The label sits above on the dark ground (or on paper when [labelColor] says so);
 * a 2dp ring shows focus, or print-red when [error] is set. [trailing] holds a tag or a small button.
 */
@Composable
fun ReceiptField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Square 52dp receipt field with a 3dp focus or error border. */
    onPaper: Boolean = false,
    labelColor: Color? = null,
    container: Color? = null,
    outline: Color? = null,
    error: Boolean = false,
    errorMessage: String? = null,
    readOnly: Boolean = false,
    /** Hides the visible label (the semantics label stays), for a field whose meaning is clear from its neighbours. */
    labelVisible: Boolean = true,
    /** Larger centred bold amount style used by the quick-edit sheet. */
    amountStyle: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    labelTrailing: (@Composable () -> Unit)? = null,
    /** Applied to the text field itself, for a FocusRequester. */
    fieldModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalReceiptColors.current
    val fieldLabelColor = labelColor ?: colors.soft
    val fieldContainer = container ?: colors.paper
    val fieldOutline = outline ?: if (onPaper) colors.soft else null
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val ring = when {
        error -> colors.red
        focused -> colors.ink
        else -> null
    }
    Column(modifier.fillMaxWidth()) {
        if (labelVisible) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = fieldLabelColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clearAndSetSemantics { })
            labelTrailing?.invoke()
        }
        Row(
            Modifier
                .padding(top = if (labelVisible) 4.dp else 0.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(fieldContainer)
                .then(if (ring != null) Modifier.border(3.dp, ring) else Modifier.border(2.dp, fieldOutline ?: colors.ink))
                .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .then(fieldModifier)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                singleLine = singleLine,
                minLines = minLines,
                readOnly = readOnly,
                textStyle = if (amountStyle) MaterialTheme.typography.displayLarge.copy(color = colors.ink, textAlign = TextAlign.Center) else MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.ink),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 48.dp), contentAlignment = if (amountStyle) Alignment.Center else Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, color = colors.soft, style = MaterialTheme.typography.bodyMedium)
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
        if (errorMessage != null) Text(errorMessage, color = colors.red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Inverse ink primary, square secondary, and underlined tertiary receipt actions. */
@Composable
fun ReceiptPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, fillWidth: Boolean = true) {
    val colors = LocalReceiptColors.current
    Box(modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = 52.dp).then(if (enabled) Modifier.background(colors.ink) else Modifier.border(2.dp, colors.soft)).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), color = if (enabled) colors.paper else colors.soft, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun ReceiptTonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LocalReceiptColors.current
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp).border(2.dp, if (enabled) colors.ink else colors.soft).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), color = if (enabled) colors.ink else colors.soft, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun ReceiptOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fillWidth: Boolean = true, enabled: Boolean = true) {
    val colors = LocalReceiptColors.current
    Box(modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = 48.dp).border(2.dp, if (enabled) colors.ink else colors.soft).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), color = if (enabled) colors.ink else colors.soft, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** A plain paper slip: padded paper with the 6dp radius and no band. */
@Composable
fun ReceiptPlainSlip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalReceiptColors.current
    Column(modifier.fillMaxWidth().background(colors.paper, ReceiptPaperShape).padding(16.dp), content = content)
}

/** One square paper ticket without a red band for results and matches lists. */
@Composable
fun ReceiptPaperTicket(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalReceiptColors.current
    Column(modifier.fillMaxWidth().background(colors.paper), content = content)
}

/** Bold subhead row of a paper ticket ("5 results", "Local matches"). */
@Composable
fun ReceiptSubhead(text: String, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, color = colors.ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The 2x2 meal choice. Selected = tonal + 2dp primary border + check. */
@Composable
fun ReceiptMealChips(selected: MealSlot, onSelect: (MealSlot) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlot.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { meal -> ReceiptChoiceChip(meal.label(), selected == meal, { onSelect(meal) }, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) = ReceiptPrimaryButton(text, onClick, modifier, enabled)
@Composable fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) = ReceiptOutlinedButton(text, onClick, modifier, enabled = enabled)
@Composable fun ChoiceBox(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = ReceiptChoiceChip(label, selected, onClick, modifier)
@Composable fun TertiaryLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Text(text, color = colors.ink, modifier = modifier.heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center)
}
@Composable fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp).border(2.dp, colors.red).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(text.uppercase(), color = colors.red, fontWeight = FontWeight.Bold) }
}
@Composable fun CharacterRule(char: Char = '-', color: Color? = null, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Text(char.toString().repeat(60), color = color ?: if (char == '=') colors.ink else colors.soft, maxLines = 1, softWrap = false, modifier = modifier.fillMaxWidth())
}
@Composable fun ItemRow(name: String, quantityLine: String, calories: String, source: String, modifier: Modifier = Modifier, approximate: String? = null, onClick: (() -> Unit)? = null) = ReceiptEntryRow(name, quantityLine, calories, source, approximate = approximate, onClick = onClick, modifier = modifier)
@Composable fun TotalBlock(total: String, pending: String? = null, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    Row(modifier.fillMaxWidth().background(colors.ink).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("TOTAL", color = colors.paper, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displayMedium)
        Row { Text(total, color = colors.paper, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displayMedium); pending?.let { Text(" $it", color = colors.pendingOnInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displayMedium) } }
    }
}
@Composable fun ReceiptSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalReceiptColors.current
    Column(modifier.fillMaxWidth().background(colors.paper).drawBehind { drawLine(colors.ink, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 2.dp.toPx()) }.padding(top = 10.dp, start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        Text("====", color = colors.ink, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        content()
    }
}
@Composable fun PrintedProgress(label: String, detail: String, modifier: Modifier = Modifier) {
    val colors = LocalReceiptColors.current
    val transition = rememberInfiniteTransition(label = "printed-progress")
    val animatedSteps by transition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3600, easing = LinearEasing)),
        label = "printed-progress-steps",
    )
    val completedSteps = animatedSteps.toInt().coerceIn(0, 12)
    Column(
        modifier.fillMaxWidth().semantics {
            contentDescription = "${label.uppercase()} progress, $completedSteps of 12 steps"
            progressBarRangeInfo = ProgressBarRangeInfo(animatedSteps / 12f, 0f..1f, 11)
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), color = colors.ink, fontWeight = FontWeight.Bold)
        Text(detail, color = colors.soft, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().height(28.dp).border(2.dp, colors.ink).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(12) { index ->
                Box(Modifier.weight(1f).height(20.dp).background(if (index < completedSteps) colors.ink else colors.paper))
            }
        }
    }
}
