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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.domain.DayBar
import com.bosa.breakfast.domain.Macro
import com.bosa.breakfast.ui.pretty
import com.bosa.breakfast.ui.theme.CurveHeight
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.bosa.breakfast.ui.theme.macroColor
import com.bosa.breakfast.ui.weekdayLabel
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import kotlin.math.ceil

/** The kcal axis: 0 to [top], in round [step]s. */
internal data class KcalAxis(val top: Double, val step: Double)

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
    val axis: KcalAxis,
    val dayKeys: List<String>,
)

private val KCAL_STEPS = listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)
private const val MOST_KCAL_STEPS = 4.0
private const val EMPTY_WEEK_KCAL = 2000.0
private const val SLOT_SHARE = 0.2

/** A round axis from zero that holds [values]; a week with nothing has a plain one, so its slots still draw. */
internal fun kcalAxis(values: List<Double>): KcalAxis {
    val high = values.maxOrNull()?.takeIf { it > 0 } ?: EMPTY_WEEK_KCAL
    val step = KCAL_STEPS.firstOrNull { high / it <= MOST_KCAL_STEPS } ?: KCAL_STEPS.last()
    return KcalAxis(ceil(high / step) * step, step)
}

/** [days] as bars; without [showGoal] (Tracking only) there is no goal line. */
internal fun macroBarsData(days: List<DayBar>, showGoal: Boolean): MacroBarsData {
    val targets = days.map { day -> day.targetKcal.takeIf { showGoal } }
    val axis = kcalAxis(days.map { it.proteinKcal + it.carbsKcal + it.fatKcal + it.unknownKcal } + targets.filterNotNull())
    return MacroBarsData(
        days.map { it.proteinKcal },
        days.map { it.carbsKcal },
        days.map { it.fatKcal },
        days.map { it.unknownKcal },
        days.map { if (it.logged) 0.0 else axis.top * SLOT_SHARE },
        targets,
        axis,
        days.map { it.dayKey },
    )
}

/** Every label a day has while "Wed" fits its width; past a plain larger text, every other day. */
internal fun dayLabelsEvery(fontScale: Float): Int = if (fontScale > 1.3f) 2 else 1

/** The goal, a line across each day at that day's target, over the bars. */
private class GoalLines(private val targets: List<Double?>, private val line: LineComponent, private val top: Double) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds: Rect = context.layerBounds
        val spacing = context.layerDimensions.xSpacing
        targets.forEachIndexed { day, target ->
            if (target == null) return@forEachIndexed
            val centre = bounds.left + context.layerDimensions.startPadding + day * spacing
            val y = bounds.bottom - (target / top).toFloat() * bounds.height
            line.drawHorizontal(context, centre - spacing / 2, centre + spacing / 2, y)
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
internal fun MacroBarsChart(days: List<DayBar>, showGoal: Boolean, modifier: Modifier = Modifier.fillMaxWidth().height(CurveHeight)) {
    val colors = LocalBreakfastColors.current
    val data = remember(days, showGoal) { macroBarsData(days, showGoal) }
    val every = dayLabelsEvery(LocalDensity.current.fontScale)
    key(data, colors, every) {
        val parts = rememberChartAxisParts()
        val goal = rememberLineComponent(fill = Fill(colors.ink), thickness = 1.5.dp)
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
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = data.axis.top) },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = parts.line,
                label = parts.label,
                tick = parts.tick,
                guideline = parts.guideline,
                itemPlacer = remember { VerticalAxis.ItemPlacer.step({ data.axis.step }, false) },
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> value.pretty() } },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = parts.line,
                label = parts.label,
                tick = parts.tick,
                guideline = null,
                itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { every }) },
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> weekdayLabel(data.dayKeys[value.toInt().coerceIn(data.dayKeys.indices)]) } },
            ),
            decorations = listOf(remember { GoalLines(data.targets, goal, data.axis.top) }),
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
        ChartHost(chart, model, modifier)
    }
}

private val BAR_WIDTH = 26.dp
private val BAR_SPACING = 12.dp
private val SEGMENT_GAP = 1.dp
