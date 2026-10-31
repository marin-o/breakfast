package com.iron.breakfast.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.iron.breakfast.ui.theme.CurveHeight
import com.iron.breakfast.ui.theme.LocalBreakfastColors

/** A point of a [PointCurve], in its own units. */
internal data class CurvePoint(val x: Double, val y: Double)

/** Where a curve draws, in px: its [points] in order, and the line for its target's height. */
internal data class CurveLayout(val points: List<Offset>, val targetY: Float?)

/**
 * [points] scaled into [width] by [height], kept [inset] from the edges, with larger y higher. The y range includes
 * [target], so its line is always in view. A range of one value sits in the middle.
 */
internal fun curveLayout(points: List<CurvePoint>, target: Double?, width: Float, height: Float, inset: Float): CurveLayout {
    if (points.isEmpty()) return CurveLayout(emptyList(), null)
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val ys = points.map { it.y } + listOfNotNull(target)
    val minY = ys.min()
    val maxY = ys.max()
    fun scale(value: Double, min: Double, max: Double): Float = if (max == min) 0.5f else ((value - min) / (max - min)).toFloat()
    val innerW = width - 2 * inset
    val innerH = height - 2 * inset
    fun yOf(value: Double) = inset + innerH * (1f - scale(value, minY, maxY))
    return CurveLayout(
        points.map { Offset(inset + innerW * scale(it.x, minX, maxX), yOf(it.y)) },
        target?.let(::yOf),
    )
}

/** A line through [points] with a dot on each (one point is a dot), and [target] as a soft line across. Draws nothing without points. */
@Composable
internal fun PointCurve(points: List<CurvePoint>, target: Double?, modifier: Modifier = Modifier, height: Dp = CurveHeight) {
    val colors = LocalBreakfastColors.current
    val inOrder = remember(points) { points.sortedBy { it.x } }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val layout = curveLayout(inOrder, target, size.width, size.height, 8.dp.toPx())
        layout.targetY?.let { drawLine(colors.soft, Offset(0f, it), Offset(size.width, it), strokeWidth = 1.dp.toPx()) }
        layout.points.zipWithNext { from, to -> drawLine(colors.ink, from, to, strokeWidth = 2.dp.toPx()) }
        layout.points.forEach { drawCircle(colors.ink, radius = 3.dp.toPx(), center = it) }
    }
}
