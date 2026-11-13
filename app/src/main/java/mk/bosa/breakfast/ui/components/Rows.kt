package mk.bosa.breakfast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.ui.label
import mk.bosa.breakfast.ui.theme.Macro
import mk.bosa.breakfast.ui.MacroPart
import mk.bosa.breakfast.ui.theme.Mark
import mk.bosa.breakfast.ui.theme.mark
import mk.bosa.breakfast.ui.theme.PartInset
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.ui.macroText
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.HeaderHeight
import mk.bosa.breakfast.ui.theme.IconTarget
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/** A food, meal or entry line: name, soft quantity line, kcal on the right (ink 500 for totals). */
@Composable
fun FoodRow(
    name: String,
    quantityLine: String,
    calories: String,
    modifier: Modifier = Modifier,
    total: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** Room after the kcal: a meal's rows end where its add circle does ([AddCircleInset]). */
    caloriesEnd: Dp = 0.dp,
) {
    val colors = LocalBreakfastColors.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(top = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(foodText(name), color = colors.ink, style = BreakfastType.Body)
            if (quantityLine.isNotBlank()) Text(quantityLine, color = colors.soft, style = BreakfastType.Secondary)
        }
        Text(
            calories,
            color = if (total) colors.ink else colors.soft,
            style = if (total) BreakfastType.BodyMedium else BreakfastType.Body,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(end = caloriesEnd),
        )
    }
}

/** Section heading row: hairline, heading, time, total and the add circle. [emptyText] shows under an empty meal, [macros] the meal's macros under one with foods. */
@Composable
internal fun MealHeader(
    name: String,
    total: String,
    onAdd: () -> Unit,
    addDescription: String,
    modifier: Modifier = Modifier,
    time: String? = null,
    emptyText: String? = null,
    macros: List<MacroPart>? = null,
) {
    val colors = LocalBreakfastColors.current
    val timeBelow = isLargeFont(LocalDensity.current.fontScale)
    Column(modifier.fillMaxWidth().padding(top = 30.dp)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (timeBelow) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = colors.ink, style = BreakfastType.Section)
                    if (!time.isNullOrBlank()) Text(time, color = colors.soft, style = BreakfastType.Meta)
                }
            } else {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(name, color = colors.ink, style = BreakfastType.Section)
                    if (!time.isNullOrBlank()) Text(time, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(total, color = colors.ink, style = BreakfastType.Title, modifier = Modifier.padding(end = 8.dp))
            AddCircle(addDescription, onAdd)
        }
        if (emptyText != null) Text(emptyText, color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 4.dp))
        if (macros != null) MacroLine(macros, BreakfastType.Secondary, Modifier.padding(top = 4.dp))
    }
}

/** Date title with a previous/next pair; a "Go To Today" text button appears when [showToday] is set. */
@Composable
fun DayHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showToday: Boolean = false,
    onToday: () -> Unit = {},
    onTitleClick: (() -> Unit)? = null,
    /** False on today: Next is dimmed and does nothing. */
    nextEnabled: Boolean = true,
    /** What TalkBack calls the arrows. */
    previousDescription: String = "Previous day",
    nextDescription: String = "Next day",
) {
    val colors = LocalBreakfastColors.current
    Row(modifier.fillMaxWidth().heightIn(min = HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        // The title and its chevron form one click target that opens the date picker.
        Row(
            Modifier
                .weight(1f)
                .then(if (onTitleClick != null) Modifier.clickable(role = Role.Button, onClick = onTitleClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = colors.ink, style = BreakfastType.Title)
            if (onTitleClick != null) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.soft, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
        if (showToday) TertiaryButton("Go To Today", onToday)
        IconButton(onClick = onPrevious, modifier = Modifier.size(IconTarget)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = previousDescription, tint = colors.ink, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onNext, enabled = nextEnabled, modifier = Modifier.size(IconTarget)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = nextDescription, tint = colors.ink.copy(alpha = if (nextEnabled) 1f else 0.4f), modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * One line of facts: the label, with its [mark]'s dot if any (a macro's, or fibre's), then the value in ink with its [unit] in soft, or, with no
 * unit, the value in soft. [inset] sets the line in by that much, under the line before, as a label's "of which" line.
 */
@Composable
internal fun FactRow(label: String, value: String, unit: String? = null, mark: Mark? = null, inset: Dp = 0.dp) {
    val colors = LocalBreakfastColors.current
    val shownValue: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = if (unit == null) colors.soft else colors.ink, style = if (unit == null) BreakfastType.Body else BreakfastType.BodyMedium, textAlign = TextAlign.End)
            if (unit != null) Text(" $unit", color = colors.soft, style = BreakfastType.Meta, maxLines = 1)
        }
    }
    if (unit != null && isLargeFont(LocalDensity.current.fontScale.coerceAtLeast(1f))) {
        Column(Modifier.fillMaxWidth().padding(start = inset, top = 8.dp)) {
            MacroLabel(label, mark, colors.ink, BreakfastType.Body)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { shownValue() }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(start = inset, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            MacroLabel(label, mark, colors.ink, BreakfastType.Body, Modifier.weight(1f))
            shownValue()
        }
    }
}

/** A total on its own line: label and value in the title style, the value at the end, under the label when they don't fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TotalRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalBreakfastColors.current
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.ink, style = BreakfastType.Title)
        Text(value, color = colors.ink, style = BreakfastType.Title)
    }
}

/**
 * The nutrition lines of a page, in the label's order: Energy, then the macros when there are any, each a value and unit,
 * saturated fat set in under Fat and sugars under Carbohydrate, fibre before Protein and salt last, each of those four
 * only when given; [marked] gives the macros their dots, and fibre its own while fibre is shown.
 */
@Composable
internal fun NutritionFacts(
    energy: String,
    fat: Pair<String, String>? = null,
    carbs: Pair<String, String>? = null,
    protein: Pair<String, String>? = null,
    marked: Boolean = true,
    saturatedFat: Pair<String, String>? = null,
    sugars: Pair<String, String>? = null,
    fibre: Pair<String, String>? = null,
    salt: Pair<String, String>? = null,
) {
    FactRow("Energy", energy, "kcal")
    // A sub-line starts 16dp in from its parent's label text, which a dot moves along.
    val subInset = PartInset + if (marked) MacroDotSize + MacroDotGap else 0.dp
    fun mark(of: Macro) = of.mark.takeIf { marked }
    fat?.let { FactRow("Fat", it.first, it.second, mark(Macro.FAT)) }
    saturatedFat?.let { FactRow("Saturated fat", it.first, it.second, inset = subInset) }
    carbs?.let { FactRow("Carbohydrate", it.first, it.second, mark(Macro.CARBS)) }
    sugars?.let { FactRow("Sugars", it.first, it.second, inset = subInset) }
    fibre?.let { FactRow("Fibre", it.first, it.second, Mark.Fibre.takeIf { marked }) }
    protein?.let { FactRow("Protein", it.first, it.second, mark(Macro.PROTEIN)) }
    salt?.let { FactRow("Salt", it.first, it.second) }
}

/**
 * A food's [values] as its page shows them: each macro, "—" when unknown, and the other four only when known.
 */
@Composable
internal fun NutritionFacts(energy: String, values: NutritionValues, marked: Boolean = true) {
    fun grams(value: Double?) = macroText(value) to "g"
    fun known(value: Double?) = value?.let(::grams)
    NutritionFacts(
        energy,
        fat = grams(values.fat),
        carbs = grams(values.carbohydrates),
        protein = grams(values.protein),
        marked = marked,
        saturatedFat = known(values.saturatedFat),
        sugars = known(values.sugars),
        fibre = known(values.fibre),
        salt = known(values.salt),
    )
}
