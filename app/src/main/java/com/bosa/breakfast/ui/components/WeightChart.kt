package com.bosa.breakfast.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.domain.WeightPoint
import com.bosa.breakfast.domain.MINUTES_PER_DAY
import com.bosa.breakfast.domain.curveMinute
import com.bosa.breakfast.domain.dayStartMinute
import com.bosa.breakfast.domain.oldestFirst
import com.bosa.breakfast.ui.pretty
import com.bosa.breakfast.ui.theme.BreakfastType
import com.bosa.breakfast.ui.theme.CurveHeight
import com.bosa.breakfast.ui.theme.Geologica
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.bosa.breakfast.ui.dayMonthLabel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** The kg axis: [min] and [max] are multiples of [step], a round number. */
internal data class KgAxis(val min: Double, val max: Double, val step: Double)

/** The chart's points, oldest first, with x in whole minutes (see [curveMinute]) and no two alike, and the ranges the axes show. */
internal data class WeightChartData(
    val xs: List<Double>,
    val ys: List<Double>,
    val minX: Double,
    val maxX: Double,
    val axis: KgAxis,
    val target: Double?,
)

/**
 * What [points] draw; null when there is nothing to draw. One point sits on its own date, with a day each side. More run
 * from the start of the first one's day to the last one, a day at least. Points in the same minute are a minute apart,
 * as the library keeps one drawing position per x.
 */
internal fun weightChartData(points: List<WeightPoint>, target: Double?, zone: ZoneId = ZoneId.systemDefault()): WeightChartData? {
    val placed = oldestFirst(points).mapNotNull { point -> curveMinute(point, zone)?.let { it to point } }.sortedBy { it.first }
    if (placed.isEmpty()) return null
    val ys = placed.map { it.second.kg }
    val axis = kgAxis(ys + listOfNotNull(target))
    placed.singleOrNull()?.let { (_, point) ->
        val x = dayStartMinute(point.dayKey) ?: return null
        return WeightChartData(listOf(x), ys, x - MINUTES_PER_DAY, x + MINUTES_PER_DAY, axis, target)
    }
    val xs = placed.map { it.first }.runningReduce { before, x -> maxOf(x, before + 1) }
    val minX = dayStart(xs.first())
    return WeightChartData(xs, ys, minX, maxOf(xs.last(), minX + MINUTES_PER_DAY), axis, target)
}

/** The date a position along the x axis falls on. */
internal fun curveDate(x: Double): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(x.toLong(), MINUTES_PER_DAY))

private fun dayStart(x: Double): Double = (curveDate(x).toEpochDay() * MINUTES_PER_DAY).toDouble()

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

/** The whole days to label, evenly spread from the first point's day to the last's, no more than [maxLabels]; with room for one, the newest. */
internal fun dateTicks(data: WeightChartData, maxLabels: Int): List<Double> {
    if (data.xs.size == 1) return data.xs
    val first = dayStart(data.xs.first())
    val days = (dayStart(data.xs.last()) - first) / MINUTES_PER_DAY
    val count = minOf(maxLabels, days.toInt() + 1)
    if (count <= 1) return listOf(dayStart(data.xs.last()))
    return List(count) { first + (it * days / (count - 1)).roundToLong() * MINUTES_PER_DAY }
}

private const val MAX_DATE_LABELS = 5

/** The most [dateTicks] whose closest two stay a label's room apart, when [labelsAcross] labels with their room fill the axis. */
internal fun dateTicksThatFit(data: WeightChartData, labelsAcross: Float): List<Double> {
    val width = data.maxX - data.minX
    for (count in minOf(MAX_DATE_LABELS, labelsAcross.toInt()) downTo 2) {
        val ticks = dateTicks(data, count)
        if (ticks.zipWithNext().all { (a, b) -> (b - a) / width * labelsAcross >= 1 }) return ticks
    }
    return dateTicks(data, 1)
}

/** A gap of half a label between dates, so they never touch at any font size. */
private const val LABEL_ROOM = 1.5f

/** Labels only [dateTicks], so dates fit the width whatever the time the points span. The first and last value make the axis keep room for half a label at each end. */
private class DateLabels(private val data: WeightChartData) : HorizontalAxis.ItemPlacer {
    private fun ticks(context: CartesianDrawingContext, maxLabelWidth: Float) =
        dateTicksThatFit(data, context.layerBounds.width / (maxLabelWidth * LABEL_ROOM))

    private val ends = if (data.xs.size == 1) data.xs else listOf(data.minX, data.maxX)

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
        // The 2dp above and below is the padding Vico 2 drew; Vico 3's default is 4dp.
        val label = rememberAxisLabelComponent(
            style = TextStyle(color = colors.soft, fontFamily = Geologica, fontSize = BreakfastType.Meta.fontSize),
            padding = Insets(horizontal = 4.dp, vertical = 2.dp),
        )
        val axisLine = rememberAxisLineComponent(fill = Fill(colors.line))
        val tick = rememberAxisTickComponent(fill = Fill(colors.line))
        val targetLine = rememberLineComponent(fill = Fill(colors.soft), thickness = 1.dp)
        val dot = rememberShapeComponent(fill = Fill(colors.ink), shape = CircleShape)
        val chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(colors.ink)),
                        stroke = LineCartesianLayer.LineStroke.Continuous(2.dp),
                        pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(dot, 6.dp)),
                    ),
                ),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(data.minX, data.maxX, data.axis.min, data.axis.max) },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = axisLine,
                label = label,
                tick = tick,
                guideline = rememberAxisGuidelineComponent(fill = Fill(colors.line)),
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
                    val withYear = curveDate(data.xs.first()).year != curveDate(data.xs.last()).year
                    CartesianValueFormatter { _, value, _ -> dayMonthLabel(curveDate(value), withYear) }
                },
            ),
            decorations = data.target?.let { kg -> listOf(remember { HorizontalLine({ kg }, targetLine) }) }.orEmpty(),
            // Real time: one step is one minute, however far apart the points are.
            getXStep = { _, _, _ -> 1.0 },
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
