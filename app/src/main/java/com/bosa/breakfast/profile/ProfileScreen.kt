package com.bosa.breakfast.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.domain.AGE_RANGE_MESSAGE
import com.bosa.breakfast.domain.Activity
import com.bosa.breakfast.domain.Goal
import com.bosa.breakfast.domain.HEIGHT_RANGE_MESSAGE
import com.bosa.breakfast.domain.KCAL_RANGE_MESSAGE
import com.bosa.breakfast.domain.LoseWarning
import com.bosa.breakfast.domain.MACRO_TARGET_RANGE_MESSAGE
import com.bosa.breakfast.domain.PACE_CHOICES
import com.bosa.breakfast.domain.PERCENT_RANGE_MESSAGE
import com.bosa.breakfast.domain.Sex
import com.bosa.breakfast.domain.WEIGHT_RANGE_MESSAGE
import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.WeightRange
import com.bosa.breakfast.domain.ageTextValid
import com.bosa.breakfast.domain.editableNumber
import com.bosa.breakfast.domain.gramsText
import com.bosa.breakfast.domain.heightTextValid
import com.bosa.breakfast.domain.kcalTargetTextValid
import com.bosa.breakfast.domain.lowestLine
import com.bosa.breakfast.domain.macroPercentTextValid
import com.bosa.breakfast.domain.macroTargetTextValid
import com.bosa.breakfast.domain.paceTextValid
import com.bosa.breakfast.domain.paceShown
import com.bosa.breakfast.domain.percentShown
import com.bosa.breakfast.domain.percentsShown
import com.bosa.breakfast.domain.positiveAmountOf
import com.bosa.breakfast.domain.weightTextValid
import com.bosa.breakfast.settings.DisplayDecimal
import com.bosa.breakfast.settings.markDecimals
import com.bosa.breakfast.ui.MacroPart
import com.bosa.breakfast.ui.components.AddCircle
import com.bosa.breakfast.ui.components.CheckboxRow
import com.bosa.breakfast.ui.components.ChoiceGrid
import com.bosa.breakfast.ui.components.DayHeader
import com.bosa.breakfast.ui.components.DecimalField
import com.bosa.breakfast.ui.components.FactRow
import com.bosa.breakfast.ui.components.Field
import com.bosa.breakfast.ui.components.FormFields
import com.bosa.breakfast.ui.components.Hairline
import com.bosa.breakfast.ui.components.MacroBarsChart
import com.bosa.breakfast.ui.components.MacroLine
import com.bosa.breakfast.ui.components.NutrientField
import com.bosa.breakfast.ui.components.NutrientFields
import com.bosa.breakfast.ui.components.Reveal
import com.bosa.breakfast.ui.components.SaveButton
import com.bosa.breakfast.ui.components.StepPager
import com.bosa.breakfast.ui.components.SubScreen
import com.bosa.breakfast.ui.components.TabWords
import com.bosa.breakfast.ui.components.TotalRow
import com.bosa.breakfast.ui.components.WeightChart
import com.bosa.breakfast.ui.components.curveHeight
import com.bosa.breakfast.ui.components.inert
import com.bosa.breakfast.ui.components.isLargeFont
import com.bosa.breakfast.ui.components.rememberMotion
import com.bosa.breakfast.ui.countLabel
import com.bosa.breakfast.ui.pretty
import com.bosa.breakfast.ui.theme.BreakfastType
import com.bosa.breakfast.ui.theme.Gutter
import com.bosa.breakfast.ui.theme.IconTarget
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.bosa.breakfast.ui.theme.Macro
import com.bosa.breakfast.ui.theme.PrimaryShape
import com.bosa.breakfast.ui.weekLabel
import kotlin.math.roundToInt

private const val CUSTOM = "Custom"

private val SEX_CHOICES = listOf(Sex.FEMALE to "Female", Sex.MALE to "Male", Sex.NOT_SAYING to "Not saying")
private val ACTIVITY_CHOICES = listOf(Activity.INACTIVE to "Inactive", Activity.LOW_ACTIVE to "Low active", Activity.ACTIVE to "Active", Activity.VERY_ACTIVE to "Very active")
private val GOAL_CHOICES = listOf(Goal.LOSE to "Lose", Goal.MAINTAIN to "Maintain", Goal.GAIN to "Gain")
private val RANGE_CHOICES = listOf(WeightRange.ONE_MONTH to "1M", WeightRange.THREE_MONTHS to "3M", WeightRange.SIX_MONTHS to "6M", WeightRange.ONE_YEAR to "1Y", WeightRange.ALL to "All")
private val MACRO_NAMES = listOf(Macro.PROTEIN to "Protein", Macro.CARBS to "Carbs", Macro.FAT to "Fat")

/** The three swipeable pages under the tappable words, and the lifted chart over everything. */
@Composable
internal fun ProfileScreen(profile: ProfileState) {
    val motion = rememberMotion()
    val pages = profile.pages
    // Out here, as a page leaves composition when it is swiped away.
    val profileScroll = rememberScrollState()
    val weightList = rememberLazyListState()
    val nutritionScroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        SubScreen(
            "Profile", profile::leave,
            modifier = if (profile.chartLifted) Modifier.clearAndSetSemantics { } else Modifier,
            scroll = false,
            titleContent = { TabWords(pages.map { it.word }, profile.position.page, { profile.goToPage(pages[it]) }) },
            bottom = {
                val saving = profile.page == ProfilePage.PROFILE
                AnimatedVisibility(saving, if (saving) Modifier else Modifier.inert(), enter = motion.rowIn(), exit = motion.rowOut()) {
                    SaveButton(profile::save, profile.canSave)
                }
            },
        ) {
            StepPager(profile.position, profile::pageSettled, Modifier.weight(1f).fillMaxWidth()) { index ->
                when (pages.getOrNull(index)) {
                    ProfilePage.PROFILE -> ProfilePageFields(profile, profileScroll)
                    ProfilePage.WEIGHT -> WeightPage(profile, weightList)
                    ProfilePage.NUTRITION -> NutritionPage(profile, nutritionScroll)
                    null -> Unit
                }
            }
        }
        LiftedChart(profile)
    }
}

@Composable
private fun ProfilePageFields(profile: ProfileState, scroll: ScrollState) {
    val draft = profile.draft
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 12.dp)) {
        FormFields {
            Field("Name", draft.name, { profile.edit(draft.copy(name = it)) })
            NutrientFields(
                listOf(
                    rangeField("Height, cm", draft.height, heightTextValid(draft.height), HEIGHT_RANGE_MESSAGE) { profile.edit(draft.copy(height = it)) },
                    rangeField("Age", draft.age, ageTextValid(draft.age), AGE_RANGE_MESSAGE) { profile.edit(draft.copy(age = it)) },
                ),
                gap = 8.dp, oneColumnWhenLabelsWrap = true,
            )
            NutrientFields(
                listOf(
                    rangeField("Current weight, kg", draft.current, weightTextValid(draft.current), WEIGHT_RANGE_MESSAGE) { profile.edit(draft.copy(current = it)) },
                    rangeField("Target weight, kg", draft.target, weightTextValid(draft.target), WEIGHT_RANGE_MESSAGE) { profile.edit(draft.copy(target = it)) },
                ),
                gap = 8.dp, oneColumnWhenLabelsWrap = true,
            )
            if (profile.curveOffered) CheckboxRow("Add to curve", profile.addToCurve, profile::toggleAddToCurve)
            CheckboxRow("Several weights a day", draft.severalWeights, { profile.edit(draft.copy(severalWeights = !draft.severalWeights)) })
            // No sex selected counts as Not saying.
            Choices("Sex", SEX_CHOICES, draft.sex ?: Sex.NOT_SAYING) { profile.edit(draft.copy(sex = it)) }
            Choices("Activity", ACTIVITY_CHOICES, draft.activity, columns = 2) { profile.edit(draft.copy(activity = it)) }
            CheckboxRow("Tracking only", draft.trackingOnly, { profile.edit(draft.copy(trackingOnly = !draft.trackingOnly)) })
            if (!draft.trackingOnly) {
                GoalSection(profile)
                KcalField(profile)
                MacroFields(profile)
            }
        }
    }
}

/** Daily burn, the goal, its warnings and the pace: only with a body the burn can be worked out from. */
@Composable
private fun GoalSection(profile: ProfileState) {
    val burn = profile.burn ?: return
    val draft = profile.draft
    Column {
        TotalRow("Daily burn", "${burn.kcal.roundToInt()} kcal")
        if (burn.loggingMismatch) SoftLine("Your logging and weight don't match. Check that your meals are all logged.", Modifier.padding(top = 4.dp))
    }
    Choices("Goal", GOAL_CHOICES, draft.goal, onSelect = profile::chooseGoal)
    profile.loseWarnings.forEach { warning ->
        Text(
            when (warning) {
                LoseWarning.UNDERWEIGHT -> "Your weight is already below the healthy range, and losing more could harm you. You deserve support with this, and a doctor or dietitian can help. If food or your weight has been on your mind a lot, you don't have to deal with it alone."
                LoseWarning.UNDER_18 -> "You're still growing, and your body needs enough food for that. Please talk to a doctor or a parent you trust before trying to lose weight."
            },
            color = LocalBreakfastColors.current.ink,
            style = BreakfastType.Body,
        )
    }
    if (draft.paceShown) PaceControls(profile)
    profile.goalResult?.let(::lowestLine)?.let { SoftLine(markDecimals(it, DisplayDecimal.mark)) }
    if (draft.goal != null) {
        SoftLine("Daily burn is an estimate, so your real pace may differ.")
        SoftLine("We'll adjust daily kcal a little if your weight isn't keeping to this pace.")
    }
}

@Composable
private fun PaceControls(profile: ProfileState) {
    val draft = profile.draft
    val numbers = PACE_CHOICES.map { editableNumber(it) }
    ChoiceGrid(
        options = numbers + CUSTOM,
        text = { markDecimals(it, DisplayDecimal.mark) },
        selected = if (draft.paceCustom) CUSTOM else editableNumber(positiveAmountOf(draft.pace)),
        onSelect = { if (it == CUSTOM) profile.choosePace(draft.pace, custom = true) else profile.choosePace(it, custom = false) },
        label = "Kg a week",
    )
    if (draft.paceCustom) {
        DecimalField("Kg a week", draft.pace, { profile.choosePace(it, custom = true) }, error = !paceTextValid(draft.pace))
    }
    if (profile.paceTooFast) SoftLine("This is faster than 1% of your weight a week.")
}

@Composable
private fun KcalField(profile: ProfileState) {
    val draft = profile.draft
    val valid = kcalTargetTextValid(draft.kcal)
    DecimalField("Daily kcal", draft.kcal, profile::editKcal, error = !valid, errorMessage = KCAL_RANGE_MESSAGE.takeUnless { valid })
}

/** Each macro's % beside its g, the dot on the first; with no valid Daily kcal, only the g. */
@Composable
private fun MacroFields(profile: ProfileState) {
    val draft = profile.draft
    val large = isLargeFont(LocalDensity.current.fontScale)
    if (!draft.percentsShown) {
        NutrientFields(MACRO_NAMES.map { (macro, name) -> gramsField(profile, macro, name, dotted = true) }, gap = 12.dp, columns = if (large) 1 else 3, oneColumnWhenLabelsWrap = true)
        return
    }
    NutrientFields(
        MACRO_NAMES.flatMap { (macro, name) -> listOf(percentField(profile, macro, name), gramsField(profile, macro, name, dotted = false)) },
        gap = 8.dp,
        columns = if (large) 1 else 2,
        oneColumnWhenLabelsWrap = true,
    )
}

private fun percentField(profile: ProfileState, macro: Macro, name: String): NutrientField {
    val shown = if (profile.clearedPercent == macro) "" else profile.draft.percentShown(macro)
    val valid = macroPercentTextValid(shown)
    return NutrientField("$name, %", shown, { profile.editMacro(macro, it, byPercent = true) }, error = !valid, errorMessage = PERCENT_RANGE_MESSAGE.takeUnless { valid }, macro = macro)
}

private fun gramsField(profile: ProfileState, macro: Macro, name: String, dotted: Boolean): NutrientField {
    val grams = profile.draft.gramsText(macro)
    val valid = macroTargetTextValid(grams)
    return NutrientField(
        "$name, g", grams, { profile.editMacro(macro, it, byPercent = false) },
        error = !valid, errorMessage = MACRO_TARGET_RANGE_MESSAGE.takeUnless { valid }, macro = macro.takeIf { dotted },
    )
}

private fun rangeField(label: String, value: String, valid: Boolean, message: String, onValue: (String) -> Unit) =
    NutrientField(label, value, onValue, error = !valid, errorMessage = message.takeUnless { valid })

@Composable
private fun <T> Choices(label: String?, choices: List<Pair<T, String>>, selected: T?, columns: Int = choices.size, onSelect: (T) -> Unit) {
    val names = choices.toMap()
    ChoiceGrid(choices.map { it.first }, { names.getValue(it) }, selected, onSelect, columns, label)
}

@Composable
private fun SoftLine(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalBreakfastColors.current.soft, style = BreakfastType.Secondary, modifier = modifier)
}

@Composable
private fun WeightPage(profile: ProfileState, list: LazyListState) {
    val points = profile.points
    LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp)) {
        item(key = "ranges") { Choices(null, RANGE_CHOICES, profile.weightRange, onSelect = profile::chooseRange) }
        if (profile.series.dots.isNotEmpty()) item(key = "chart") {
            Box(Modifier.padding(top = 16.dp)) {
                WeightChart(profile.series, profile.values.targetWeight)
                ChartCornerIcon(Icons.Outlined.Fullscreen, "Maximise chart", profile::liftChart)
            }
        }
        if (profile.projectionText.isNotEmpty()) item(key = "projection") {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.projectionText.forEach { line ->
                    Text(markDecimals(line, DisplayDecimal.mark), color = LocalBreakfastColors.current.ink, style = BreakfastType.Body)
                }
            }
        }
        if (points.isNotEmpty()) item(key = "line") { Hairline(Modifier.padding(top = 16.dp, bottom = 8.dp)) }
        items(points, key = { it.id }) { point -> PointRow(point, profile) }
    }
}

@Composable
private fun PointRow(point: WeightPoint, profile: ProfileState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // FactRow keeps 8dp above itself; the same below centres its text on the circle.
        Box(Modifier.weight(1f).padding(bottom = 8.dp)) { FactRow(profile.pointLabel(point), point.kg.pretty(), "kg") }
        AddCircle("Delete ${profile.pointSummary(point)}", { profile.askDelete(point) }, icon = Icons.Outlined.Close)
    }
}

/** The chart's one control, at its top right, on the page and lifted. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChartCornerIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.align(Alignment.TopEnd).size(IconTarget)) {
        Icon(icon, contentDescription = description, tint = LocalBreakfastColors.current.ink, modifier = Modifier.size(22.dp))
    }
}

private val LiftedSideGap = 8.dp
private val LiftedPadding = 8.dp

/**
 * The chart over everything, drag and pinch working: the scrim behind, the app's one shadow, the width less 8dp each side and
 * the page chart's aspect ratio. A tap on the scrim closes it, as Back and its close icon do. The scrim's empty semantics node
 * says nothing to TalkBack and covers all beneath it, the page and the screens under the page.
 */
@Composable
private fun LiftedChart(profile: ProfileState) {
    val motion = rememberMotion()
    val colors = LocalBreakfastColors.current
    val lifted = profile.chartLifted
    AnimatedVisibility(lifted, Modifier.fillMaxSize(), enter = motion.scrimIn(), exit = motion.scrimOut()) {
        Box(Modifier.fillMaxSize().background(colors.scrim).semantics(mergeDescendants = true) {}.pointerInput(Unit) { detectTapGestures { profile.closeChart() } })
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = maxWidth - LiftedSideGap * 2
        val height = (curveHeight() * (width / (maxWidth - Gutter * 2))).coerceAtMost(maxHeight - LiftedSideGap * 2)
        Reveal(Unit.takeIf { lifted }) {
            // Its own margin takes touches too, so only the scrim closes it.
            Box(Modifier.width(width).height(height).shadow(16.dp, PrimaryShape).clip(PrimaryShape).background(colors.bg).pointerInput(Unit) {}.padding(LiftedPadding)) {
                WeightChart(profile.series, profile.values.targetWeight, Modifier.fillMaxSize(), lifted = true)
                ChartCornerIcon(Icons.Outlined.Close, "Close", profile::closeChart)
            }
        }
    }
}

@Composable
private fun NutritionPage(profile: ProfileState, scroll: ScrollState) {
    val colors = LocalBreakfastColors.current
    val week = profile.week
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 12.dp)) {
        DayHeader(
            weekLabel(week.start, week.end), profile::previousWeek, profile::nextWeek,
            nextEnabled = profile.hasNextWeek,
            previousDescription = "Previous week",
            nextDescription = "Next week",
        )
        MacroBarsChart(week.days, showGoal = !profile.draft.trackingOnly, Modifier.fillMaxWidth().padding(top = 8.dp).height(curveHeight()))
        week.averageKcal?.let { kcal -> TotalRow("Daily average", "${kcal.roundToInt()} kcal", Modifier.padding(top = 16.dp)) }
        if (week.loggedDays > 0) {
            MacroLine(
                listOf(
                    MacroPart("Protein ${week.averageProtein?.roundToInt() ?: 0} g", Macro.PROTEIN),
                    MacroPart("Carbs ${week.averageCarbs?.roundToInt() ?: 0} g", Macro.CARBS),
                    MacroPart("Fat ${week.averageFat?.roundToInt() ?: 0} g", Macro.FAT),
                ),
                BreakfastType.Secondary,
                Modifier.padding(top = 8.dp),
                color = colors.ink,
            )
        }
        SoftLine(countLabel(week.loggedDays, "day logged", "days logged"), Modifier.padding(top = 8.dp))
    }
}
