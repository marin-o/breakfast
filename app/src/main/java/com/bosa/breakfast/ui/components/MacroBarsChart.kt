package com.bosa.breakfast.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.domain.DayBar
import com.bosa.breakfast.domain.Macro
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.bosa.breakfast.ui.theme.macroColor
import com.bosa.breakfast.ui.weekdayLabel
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * What a week's bars draw, one value a day in each list, in kcal: the segments from the bottom up, and [slot] for an unlogged
 * day's empty outline, which is a share of the axis, not a value.
 */
internal data class MacroBarsData(
    val protein: List<Double>,
    val carbs: List<Double>,
    val fat: List<Double>,
    val unknown: List<Double>,
    val slot: List<Double>,
    val targets: List<Double?>,
    val axis: RoundAxis,
    val dayKeys: List<String>,
)

private val KCAL_STEPS = listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)
private const val MOST_KCAL_STEPS = 4
private const val EMPTY_WEEK_KCAL = 2000.0
private const val SLOT_SHARE = 0.2

/** [days] as bars; without [showGoal] (Tracking only) there is no goal line. */
internal fun macroBarsData(days: List<DayBar>, showGoal: Boolean): MacroBarsData {
    val targets = days.map { day -> day.targetKcal.takeIf { showGoal } }
    val highest = (days.map { it.proteinKcal + it.carbsKcal + it.fatKcal + it.unknownKcal } + targets.filterNotNull()).maxOrNull()
    // A week with nothing has a plain axis, so its slots still draw.
    val axis = roundAxis(0.0, highest?.takeIf { it > 0 } ?: EMPTY_WEEK_KCAL, KCAL_STEPS, MOST_KCAL_STEPS)
    return MacroBarsData(
        days.map { it.proteinKcal },
        days.map { it.carbsKcal },
        days.map { it.fatKcal },
        days.map { it.unknownKcal },
        days.map { if (it.logged) 0.0 else axis.max * SLOT_SHARE },
        targets,
        axis,
        days.map { it.dayKey },
    )
}

/** What TalkBack reads for a week: "Mon 2100 kcal, goal 2347; Fri not logged". Without [showGoal] (Tracking only) no goal. */
internal fun macroBarsDescription(days: List<DayBar>, showGoal: Boolean): String = days.joinToString("; ") { day ->
    val name = weekdayLabel(day.dayKey)
    val goal = day.targetKcal?.takeIf { showGoal }?.let { ", goal ${it.roundToInt()}" }.orEmpty()
    if (day.logged) "$name ${day.kcal.roundToInt()} kcal$goal" else "$name not logged"
}

/** How many days apart the labels go, when a label measures [labelWidth] (its padding the gap) and days are [daySpacing] apart. */
internal fun dayLabelsEvery(daySpacing: Float, labelWidth: Float): Int =
    if (daySpacing > 0) ceil(labelWidth / daySpacing).toInt().coerceAtLeast(1) else 1

/** The weekdays that fit, by the widest of them as measured; all else as the library's aligned labels. */
private class WeekdayLabels(private val base: HorizontalAxis.ItemPlacer = HorizontalAxis.ItemPlacer.aligned()) : HorizontalAxis.ItemPlacer by base {
    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ) = (fullXRange.start.toInt()..fullXRange.endInclusive.toInt()).map { it.toDouble() }

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        val every = dayLabelsEvery(context.layerDimensions.xSpacing, maxLabelWidth)
        return base.getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth).filter { it.toInt() % every == 0 }
    }
}

/** The goal, a line across each day at that day's target, over the bars, on a [halo] so it shows on bars of its own ink. */
private class GoalLines(private val targets: List<Double?>, private val line: LineComponent, private val halo: LineComponent, private val top: Double) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds: Rect = context.layerBounds
        val spacing = context.layerDimensions.xSpacing
        targets.forEachIndexed { day, target ->
            if (target == null) return@forEachIndexed
            val centre = bounds.left + context.layerDimensions.startPadding + day * spacing
            val y = (bounds.bottom - (target / top).toFloat() * bounds.height).coerceIn(bounds.top, bounds.bottom)
            val start = (centre - spacing / 2).coerceAtLeast(bounds.left)
            val end = (centre + spacing / 2).coerceAtMost(bounds.right)
            halo.drawHorizontal(context, start, end, y)
            line.drawHorizontal(context, start, end, y)
        }
    }
}

/**
 * A week of stacked bars ([days], Monday first): protein, carbs and fat in their macro colours, 1dp apart, so the Monochrome
 * palette's three inks stay apart by the gap alone, and the kcal no macro accounts for in soft on top. An unlogged day is an
 * empty outline. The goal line is ink and never turns red. It takes no touch. Only this file and `WeightChart.kt` know the
 * chart library.
 */
@Composable
internal fun MacroBarsChart(days: List<DayBar>, showGoal: Boolean, modifier: Modifier = Modifier.fillMaxWidth().height(curveHeight())) {
    val colors = LocalBreakfastColors.current
    val data = remember(days, showGoal) { macroBarsData(days, showGoal) }
    key(data, colors) {
        val goal = rememberLineComponent(fill = Fill(colors.ink), thickness = GOAL_THICKNESS)
        val goalHalo = rememberLineComponent(fill = Fill(colors.bg), thickness = GOAL_THICKNESS + GOAL_HALO * 2)
        val axes = rememberChartAxes(
            data.axis.step,
            remember { WeekdayLabels() },
            remember { CartesianValueFormatter { _, value, _ -> weekdayLabel(data.dayKeys[value.toInt().coerceIn(data.dayKeys.indices)]) } },
        )
        val segment = { color: Color ->
            LineComponent(fill = Fill(color), thickness = BAR_WIDTH, shape = RectangleShape, margins = Insets(top = SEGMENT_GAP))
        }
        val columns = remember {
            ColumnCartesianLayer.ColumnProvider.series(
                segment(colors.macroColor(Macro.PROTEIN)),
                segment(colors.macroColor(Macro.CARBS)),
                segment(colors.macroColor(Macro.FAT)),
                segment(colors.soft),
                LineComponent(fill = Fill(Color.Transparent), thickness = BAR_WIDTH, shape = RectangleShape, strokeFill = Fill(colors.line), strokeThickness = 1.dp),
            )
        }
        val chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = columns,
                columnCollectionSpacing = BAR_SPACING,
                mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = data.axis.max) },
            ),
            startAxis = axes.start,
            bottomAxis = axes.bottom,
            decorations = listOf(remember { GoalLines(data.targets, goal, goalHalo, data.axis.max) }),
        )
        val model = remember {
            CartesianChartModel(
                ColumnCartesianLayerModel.build {
                    series(data.protein)
                    series(data.carbs)
                    series(data.fat)
                    series(data.unknown)
                    series(data.slot)
                },
            )
        }
        ChartHost(chart, model, modifier, description = remember(days, showGoal) { macroBarsDescription(days, showGoal) })
    }
}

private val BAR_WIDTH = 26.dp
private val BAR_SPACING = 12.dp
private val SEGMENT_GAP = 1.dp
private val GOAL_THICKNESS = 1.5.dp
private val GOAL_HALO = 1.dp
