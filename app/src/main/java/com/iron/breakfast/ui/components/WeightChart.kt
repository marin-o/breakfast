package com.iron.breakfast.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.unit.dp
import com.iron.breakfast.domain.WeightPoint
import com.iron.breakfast.domain.curveX
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.CurveHeight
import com.iron.breakfast.ui.theme.Geologica
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.iron.breakfast.ui.dayMonthLabel
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** The kg axis: [min] and [max] are multiples of [step], a round number. */
internal data class KgAxis(val min: Double, val max: Double, val step: Double)

/** The chart's points, oldest first, with x in days since the epoch, and the ranges the axes show. */
internal data class WeightChartData(
    val xs: List<Double>,
    val ys: List<Double>,
    val minX: Double,
    val maxX: Double,
    val axis: KgAxis,
    val target: Double?,
)

/** What [points] draw; null when there is nothing to draw. One point takes a day each side, so it sits in the middle. */
internal fun weightChartData(points: List<WeightPoint>, target: Double?): WeightChartData? {
    val dated = points.mapNotNull { point -> curveX(point.dayKey)?.let { it to point.kg } }.sortedBy { it.first }
    if (dated.isEmpty()) return null
    val xs = dated.map { it.first }
    val ys = dated.map { it.second }
    val margin = if (xs.size == 1) 1.0 else 0.0
    return WeightChartData(xs, ys, xs.first() - margin, xs.last() + margin, kgAxis(ys + listOfNotNull(target)), target)
}

private val ROUND_STEPS = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
private const val MAX_STEPS = 4.0

/** A round axis with [values] clear of its ends. Equal values take 1 kg each side. */
internal fun kgAxis(values: List<Double>): KgAxis {
    val low = values.min()
    val high = values.max()
    val clear = if (high == low) 1.0 else (high - low) * 0.05
    val from = (low - clear).coerceAtLeast(0.0)
    val to = high + clear
    val step = ROUND_STEPS.firstOrNull { (to - from) / it <= MAX_STEPS } ?: ROUND_STEPS.last()
    return KgAxis(floor(from / step) * step, ceil(to / step) * step, step)
}

/** The whole days to label, evenly spread with both ends, no more than [maxLabels]; with room for one, the newest. */
internal fun dateTicks(data: WeightChartData, maxLabels: Int): List<Double> {
    if (data.xs.size == 1) return data.xs
    val first = data.xs.first()
    val span = data.xs.last() - first
    val count = minOf(maxLabels, span.toInt() + 1)
    if (count <= 1) return listOf(data.xs.last())
    return List(count) { first + (it * span / (count - 1)).roundToLong() }
}

private const val MAX_DATE_LABELS = 5

/** A gap of half a label between dates, so they never touch at any font size. */
private const val LABEL_ROOM = 1.5f

/** Labels only [dateTicks], so dates fit the width whatever the time the points span. The first and last value make the axis keep room for half a label at each end. */
private class DateLabels(private val data: WeightChartData) : HorizontalAxis.ItemPlacer {
    private fun ticks(context: CartesianDrawingContext, maxLabelWidth: Float) =
        dateTicks(data, minOf(MAX_DATE_LABELS, (context.layerBounds.width() / (maxLabelWidth * LABEL_ROOM)).toInt()))

    private val ends = listOf(data.xs.first(), data.xs.last()).distinct()

    override fun getShiftExtremeLines(context: CartesianDrawingContext) = false
    override fun getFirstLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float) = ends.first()
    override fun getLastLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float) = ends.last()
    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ) = ticks(context, maxLabelWidth)
    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ) = ends
    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ) = ends
    override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ) = ticks(context, maxLabelWidth)
    override fun getStartLayerMargin(context: CartesianMeasuringContext, layerDimensions: CartesianLayerDimensions, tickThickness: Float, maxLabelWidth: Float) = 0f
    override fun getEndLayerMargin(context: CartesianMeasuringContext, layerDimensions: CartesianLayerDimensions, tickThickness: Float, maxLabelWidth: Float) = 0f
}

/**
 * The weight curve, in a line with a dot on each of [points], [target] as a soft line across, dates along the bottom
 * spaced by real time and kg up the side. Draws nothing without points. It neither scrolls nor zooms, so a drag across it
 * stays the page's. The only file that knows the chart library.
 */
@Composable
internal fun WeightChart(points: List<WeightPoint>, target: Double?, modifier: Modifier = Modifier) {
    val data = remember(points, target) { weightChartData(points, target) } ?: return
    val colors = LocalBreakfastColors.current
    // The library keeps much of what it is first given: a new set of points or a new theme starts it again.
    key(data, colors) {
        val typeface by LocalFontFamilyResolver.current.resolveAsTypeface(Geologica)
        val label = rememberAxisLabelComponent(color = colors.soft, typeface = typeface, textSize = BreakfastType.Meta.fontSize)
        val axisLine = rememberAxisLineComponent(fill = fill(colors.line))
        val tick = rememberAxisTickComponent(fill = fill(colors.line))
        val targetLine = rememberLineComponent(fill = fill(colors.soft), thickness = 1.dp)
        val dot = rememberShapeComponent(fill = fill(colors.ink), shape = CorneredShape.Pill)
        val chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(colors.ink)),
                        stroke = LineCartesianLayer.LineStroke.continuous(2.dp),
                        pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.point(dot, 6.dp)),
                    ),
                ),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(data.minX, data.maxX, data.axis.min, data.axis.max) },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = axisLine,
                label = label,
                tick = tick,
                guideline = rememberAxisGuidelineComponent(fill = fill(colors.line)),
                itemPlacer = remember { VerticalAxis.ItemPlacer.step({ data.axis.step }, false) },
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> "${value.pretty()} kg" } },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = axisLine,
                label = label,
                tick = tick,
                guideline = null,
                itemPlacer = remember { DateLabels(data) },
                valueFormatter = remember {
                    val first = LocalDate.ofEpochDay(data.xs.first().toLong())
                    val last = LocalDate.ofEpochDay(data.xs.last().toLong())
                    CartesianValueFormatter { _, value, _ -> dayMonthLabel(LocalDate.ofEpochDay(value.toLong()), withYear = first.year != last.year) }
                },
            ),
            decorations = data.target?.let { kg -> listOf(remember { HorizontalLine({ kg }, targetLine) }) }.orEmpty(),
            // Real time: one step is one day, however far apart the points are.
            getXStep = { 1.0 },
        )
        val model = remember { CartesianChartModel(LineCartesianLayerModel.build { series(data.xs, data.ys) }) }
        CartesianChartHost(
            chart,
            model,
            modifier.fillMaxWidth().height(CurveHeight),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content, minZoom = Zoom.Content, maxZoom = Zoom.Content),
        )
    }
}
