package com.bosa.breakfast.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bosa.breakfast.domain.MINUTES_PER_DAY
import com.bosa.breakfast.domain.WeightSeries
import com.bosa.breakfast.domain.curveMinute
import com.bosa.breakfast.ui.pretty
import com.bosa.breakfast.ui.theme.BreakfastType
import com.bosa.breakfast.ui.theme.CurveHeight
import com.bosa.breakfast.ui.theme.Geologica
import com.bosa.breakfast.ui.theme.LocalBreakfastColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
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
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
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
import com.bosa.breakfast.ui.monthLabel
import com.bosa.breakfast.settings.DisplayDecimal
import com.bosa.breakfast.settings.markDecimals
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** A chart's value axis: [min] and [max] are multiples of [step], a round number. */
internal data class RoundAxis(val min: Double, val max: Double, val step: Double) {
    val stepCount: Double get() = (max - min) / step
}

/** One line of the trend, oldest first, x as in [WeightChartData]. */
internal data class TrendLine(val xs: List<Double>, val ys: List<Double>)

/** The chart's dots, oldest first, with x in whole minutes (see [curveMinute]) and no two alike, its [trend], and the ranges the axes show. */
internal data class WeightChartData(
    val xs: List<Double>,
    val ys: List<Double>,
    val minX: Double,
    val maxX: Double,
    val axis: RoundAxis,
    val target: Double?,
    val trend: List<TrendLine> = emptyList(),
)

/** Each x whole, and after the one before: the library keeps one drawing position per x, and a weekly average falls between minutes. */
private fun wholeMinutes(minutes: List<Double>): List<Double> =
    minutes.map { Math.round(it).toDouble() }.runningReduce { before, x -> maxOf(x, before + 1) }

/**
 * What [series] draws; null when there is nothing to draw. One dot sits on its own date, with a day each side. More run
 * from the start of the first one's day to the last one, a day at least.
 */
internal fun weightChartData(series: WeightSeries, target: Double?): WeightChartData? {
    if (series.dots.isEmpty()) return null
    val ys = series.dots.map { it.kg }
    val trend = series.trend.map { line -> TrendLine(wholeMinutes(line.map { it.minute }), line.map { it.kg }) }
    val axis = weightAxis(ys + trend.flatMap { it.ys } + listOfNotNull(target))
    series.dots.singleOrNull()?.let { dot ->
        val x = dayStart(dot.minute)
        return WeightChartData(listOf(x), ys, x - MINUTES_PER_DAY, x + MINUTES_PER_DAY, axis, target)
    }
    val xs = wholeMinutes(series.dots.map { it.minute })
    val minX = dayStart(xs.first())
    val last = maxOf(xs.last(), trend.maxOfOrNull { it.xs.last() } ?: xs.last())
    return WeightChartData(xs, ys, minX, maxOf(last, minX + MINUTES_PER_DAY), axis, target, trend)
}

/** The date a position along the x axis falls on. */
internal fun curveDate(x: Double): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(x.toLong(), MINUTES_PER_DAY))

private fun dayStart(x: Double): Double = (curveDate(x).toEpochDay() * MINUTES_PER_DAY).toDouble()

/**
 * The axis from the smallest of [steps] that holds [low] to [high] in no more than [most] steps, never below zero: with
 * [clear] to spare at each end, or if only that lets this step fit, with the values on the ends themselves.
 */
internal fun roundAxis(low: Double, high: Double, steps: List<Double>, most: Int, clear: Double = 0.0): RoundAxis {
    val from = (low - clear).coerceAtLeast(0.0)
    val to = high + clear
    fun axis(step: Double, start: Double, end: Double) = RoundAxis(floor(start / step) * step, ceil(end / step) * step, step)
    return steps.firstNotNullOfOrNull { step ->
        listOf(axis(step, from, to), axis(step, low, high)).firstOrNull { it.max > it.min && it.stepCount <= most }
    } ?: axis(steps.last(), from, to)
}

private val KG_STEPS = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
private const val MOST_KG_STEPS = 5

/** The kg axis: 5% of the span to spare at each end; equal values take 1 kg each side. */
internal fun weightAxis(values: List<Double>): RoundAxis {
    val low = values.min()
    val high = values.max()
    return roundAxis(low, high, KG_STEPS, MOST_KG_STEPS, clear = if (high == low) 1.0 else (high - low) * 0.05)
}

/** What TalkBack reads for the curve, with a point for the mark: "From 86.0 to 84.4 kg", or "86.0 kg" for one dot. */
internal fun weightChartDescription(data: WeightChartData): String {
    fun kg(value: Double) = "%.1f".format(Locale.ROOT, value)
    return if (data.ys.size == 1) "${kg(data.ys.single())} kg" else "From ${kg(data.ys.first())} to ${kg(data.ys.last())} kg"
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

/** The most [dateTicks], up to [maxLabels], whose closest two stay a label's room apart, when [labelsAcross] labels with their room fill the axis. */
internal fun dateTicksThatFit(data: WeightChartData, labelsAcross: Float, maxLabels: Int = MAX_DATE_LABELS): List<Double> {
    val width = data.maxX - data.minX
    for (count in minOf(maxLabels, labelsAcross.toInt()) downTo 2) {
        val ticks = dateTicks(data, count)
        if (ticks.zipWithNext().all { (a, b) -> (b - a) / width * labelsAcross >= 1 }) return ticks
    }
    return dateTicks(data, 1)
}

/** With this many days or more in view the axis names months, as the mock draws it. */
internal const val MONTH_LABELS_FROM_DAYS = 60L

/** The first of each month from [minX] to [maxX], every second, third... as [labelsAcross] needs. */
internal fun monthTicks(minX: Double, maxX: Double, labelsAcross: Float): List<Double> {
    val months = generateSequence(curveDate(minX).withDayOfMonth(1)) { it.plusMonths(1) }
        .takeWhile { it.toEpochDay() * MINUTES_PER_DAY <= maxX }
        .map { (it.toEpochDay() * MINUTES_PER_DAY).toDouble() }
        .filter { it >= minX }
        .toList()
    if (months.isEmpty()) return months
    val every = ceil(months.size / labelsAcross.coerceAtLeast(1f).toDouble()).toInt().coerceAtLeast(1)
    return months.filterIndexed { index, _ -> index % every == 0 }
}

/**
 * The whole days to label between [from] and [to], the view of a zoomed chart: every day, or every second, third... as
 * [labelsAcross] in the view needs. They fall on multiples of that step, so a label stays on its day as the chart is dragged.
 */
internal fun visibleDateTicks(from: Double, to: Double, labelsAcross: Float): List<Double> {
    val days = (to - from) / MINUTES_PER_DAY
    val every = ceil(days / labelsAcross.coerceAtLeast(1f)).toLong().coerceIn(1L, floor(days).toLong().coerceAtLeast(1L))
    val size = (every * MINUTES_PER_DAY).toDouble()
    return generateSequence(ceil(from / size) * size) { it + size }.takeWhile { it <= to }.toList()
}

/** A gap of half a label between dates, so they never touch at any font size. */
private const val LABEL_ROOM = 1.5f

/** Past this much of the full width, against the view, the chart counts as zoomed. */
private const val ZOOMED_FROM = 1.01f

/** What the ticks are worked out from. */
private data class TicksAt(val start: Double, val end: Double, val spacing: Float, val width: Float, val labelWidth: Float)

/**
 * Labels only what fits the width, whatever the time the points span and however far the lifted chart is zoomed: months with
 * two months or more in view, else [dateTicks], or [visibleDateTicks] when zoomed. The first and last value make the axis keep
 * room for half a label at each end. A drag asks for these on every frame, so the last answer is kept for as long as it holds.
 */
private class DateLabels(private val data: WeightChartData) : HorizontalAxis.ItemPlacer {
    /** Set as the ticks are placed, so the labels name what the ticks are. */
    var monthly = false
        private set

    private var ticksAt: TicksAt? = null
    private var kept = emptyList<Double>()

    private fun ticks(context: CartesianDrawingContext, visible: ClosedFloatingPointRange<Double>, maxLabelWidth: Float): List<Double> {
        val at = TicksAt(visible.start, visible.endInclusive, context.layerDimensions.xSpacing, context.layerBounds.width, maxLabelWidth)
        if (at != ticksAt) {
            kept = workedOut(at).inView(visible)
            ticksAt = at
        }
        return kept
    }

    private fun workedOut(at: TicksAt): List<Double> {
        monthly = at.width / at.spacing >= MONTH_LABELS_FROM_DAYS * MINUTES_PER_DAY
        val fullWidth = (data.maxX - data.minX).toFloat() * at.spacing
        val labelRoom = at.labelWidth * LABEL_ROOM
        return when {
            monthly -> monthTicks(data.minX, data.maxX, fullWidth / labelRoom)
            fullWidth > at.width * ZOOMED_FROM -> visibleDateTicks(maxOf(at.start, data.minX), minOf(at.end, data.maxX), at.width / labelRoom)
            else -> dateTicksThatFit(data, fullWidth / labelRoom)
        }
    }

    /** A day's slack, so a rounding at the edge never loses the last label. */
    private fun List<Double>.inView(range: ClosedFloatingPointRange<Double>) =
        filter { it >= range.start - MINUTES_PER_DAY && it <= range.endInclusive + MINUTES_PER_DAY }

    private val ends = if (data.xs.size == 1) data.xs else listOf(data.minX, data.maxX)

    override fun getShiftExtremeLines(context: CartesianDrawingContext) = false
    override fun getFirstLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float) = ends.first()
    override fun getLastLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float) = ends.last()
    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ) = ticks(context, visibleXRange, maxLabelWidth)
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
    ) = ticks(context, visibleXRange, maxLabelWidth)
    override fun getStartLayerMargin(context: CartesianMeasuringContext, layerDimensions: CartesianLayerDimensions, tickThickness: Float, maxLabelWidth: Float) = 0f
    override fun getEndLayerMargin(context: CartesianMeasuringContext, layerDimensions: CartesianLayerDimensions, tickThickness: Float, maxLabelWidth: Float) = 0f
}

/** What both charts' axes are made of, in one look. */
private class ChartAxisParts(val label: TextComponent, val line: LineComponent, val tick: LineComponent, val guideline: LineComponent)

@Composable
private fun rememberChartAxisParts(): ChartAxisParts {
    val colors = LocalBreakfastColors.current
    return ChartAxisParts(
        // The 2dp above and below is the padding Vico 2 drew; Vico 3's default is 4dp.
        label = rememberAxisLabelComponent(
            style = TextStyle(color = colors.soft, fontFamily = Geologica, fontSize = BreakfastType.Meta.fontSize),
            padding = Insets(horizontal = 4.dp, vertical = 2.dp),
        ),
        line = rememberAxisLineComponent(fill = Fill(colors.line)),
        tick = rememberAxisTickComponent(fill = Fill(colors.line)),
        guideline = rememberAxisGuidelineComponent(fill = Fill(colors.line)),
    )
}

/** A chart's two axes: values up the side, and the x axis along the bottom. */
internal class ChartAxes(val start: VerticalAxis<Axis.Position.Vertical.Start>, val bottom: HorizontalAxis<Axis.Position.Horizontal.Bottom>)

/** The axes either chart has: [step] apart up the side, and [placer] and [formatter] saying what the bottom labels. */
@Composable
internal fun rememberChartAxes(step: Double, placer: HorizontalAxis.ItemPlacer, formatter: CartesianValueFormatter): ChartAxes {
    val parts = rememberChartAxisParts()
    return ChartAxes(
        VerticalAxis.rememberStart(
            line = parts.line,
            label = parts.label,
            tick = parts.tick,
            guideline = parts.guideline,
            itemPlacer = remember(step) { VerticalAxis.ItemPlacer.step({ step }, false) },
            valueFormatter = remember { CartesianValueFormatter { _, value, _ -> value.pretty() } },
        ),
        HorizontalAxis.rememberBottom(
            line = parts.line,
            label = parts.label,
            tick = parts.tick,
            guideline = null,
            itemPlacer = placer,
            valueFormatter = formatter,
        ),
    )
}

/** A chart's height: its labels grow with the font, so the plot grows with them and they keep fitting. */
@Composable
internal fun curveHeight(): Dp = CurveHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)

/**
 * The one way either chart is hosted, fitted to its width. Unless [zoomInViewX] is given it takes no touch at all: a surface
 * above it is hit first and does nothing, so every drag and pinch goes on to the page around it. With it, the chart drags and
 * pinches, as far in as that many x units in view. TalkBack reads [description] for the whole chart.
 */
@Composable
internal fun ChartHost(chart: CartesianChart, model: CartesianChartModel, modifier: Modifier, zoomInViewX: Double? = null, description: String? = null) {
    val moves = zoomInViewX != null
    // The library starts its zoom state again for a new Zoom instance, so a recomposition must not make one.
    val maxZoom = remember(zoomInViewX) { if (zoomInViewX != null) Zoom.max(Zoom.x(zoomInViewX), Zoom.Content) else Zoom.Content }
    Box(if (description != null) modifier.clearAndSetSemantics { contentDescription = description } else modifier) {
        CartesianChartHost(
            chart,
            model,
            Modifier.fillMaxSize(),
            scrollState = rememberVicoScrollState(scrollEnabled = moves),
            zoomState = rememberVicoZoomState(
                zoomEnabled = moves,
                initialZoom = Zoom.Content,
                minZoom = Zoom.Content,
                maxZoom = maxZoom,
            ),
        )
        if (!moves) Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

private const val LIFTED_VIEW_DAYS = 7.0
private const val LIFTED_ZOOM_AT_LEAST = 3.0

/**
 * How little of the lifted chart's [span] of x a pinch can leave in view: a week. A chart that short would have no zoom to
 * give, and so no drag, so it can always go in three times, down to a day.
 */
internal fun liftedViewMinutes(span: Double): Double =
    minOf(LIFTED_VIEW_DAYS * MINUTES_PER_DAY, maxOf(span / LIFTED_ZOOM_AT_LEAST, MINUTES_PER_DAY.toDouble()))

/**
 * The weight curve: [series]' faint dots, its bold trend, [target] as a soft line across, dates along the bottom spaced by
 * real time and kg up the side. Draws nothing without dots. It takes no touch unless [lifted]. Macro colours stay out.
 * This and [MacroBarsChart] are the only files that know the chart library.
 */
@Composable
internal fun WeightChart(series: WeightSeries, target: Double?, modifier: Modifier = Modifier.fillMaxWidth().height(curveHeight()), lifted: Boolean = false) {
    val data = remember(series, target) { weightChartData(series, target) } ?: return
    val colors = LocalBreakfastColors.current
    // The library keeps much of what it is first given: a new set of points or a new theme starts it again.
    key(data, colors) {
        val targetLine = rememberLineComponent(fill = Fill(colors.soft), thickness = 1.dp)
        val dot = rememberShapeComponent(fill = Fill(colors.soft.copy(alpha = FAINT_DOT_ALPHA)), shape = CircleShape)
        val dots = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            stroke = LineCartesianLayer.LineStroke.Continuous(0.dp),
            pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(dot, 4.dp)),
        )
        val trend = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(colors.ink)),
            stroke = LineCartesianLayer.LineStroke.Continuous(2.dp),
        )
        val labels = remember { DateLabels(data) }
        val axes = rememberChartAxes(
            data.axis.step,
            labels,
            remember {
                val withYear = curveDate(data.xs.first()).year != curveDate(data.xs.last()).year
                CartesianValueFormatter { _, value, _ ->
                    if (labels.monthly) monthLabel(curveDate(value), withYear) else dayMonthLabel(curveDate(value), withYear)
                }
            },
        )
        val chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(listOf(dots) + data.trend.map { trend }),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(data.minX, data.maxX, data.axis.min, data.axis.max) },
            ),
            startAxis = axes.start,
            bottomAxis = axes.bottom,
            decorations = data.target?.let { kg -> listOf(remember { HorizontalLine({ kg }, targetLine) }) }.orEmpty(),
            // Real time: one step is one minute, however far apart the points are.
            getXStep = { _, _, _ -> 1.0 },
        )
        val model = remember {
            CartesianChartModel(
                LineCartesianLayerModel.build {
                    series(data.xs, data.ys)
                    data.trend.forEach { series(it.xs, it.ys) }
                },
            )
        }
        ChartHost(
            chart, model, modifier,
            zoomInViewX = if (lifted) liftedViewMinutes(data.maxX - data.minX) else null,
            description = markDecimals(weightChartDescription(data), DisplayDecimal.mark),
        )
    }
}

/** The raw weigh-ins sit behind the trend. */
private const val FAINT_DOT_ALPHA = 0.55f
