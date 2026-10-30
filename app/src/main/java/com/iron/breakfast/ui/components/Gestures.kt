package com.iron.breakfast.ui.components

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs
import kotlin.math.hypot

/** How a followed touch ended: how far it went sideways, and which way it locked (neither for a tap). */
internal class DragEnd(val dx: Float, val horizontal: Boolean, val vertical: Boolean)

/**
 * Follows [pointer] until it lifts, locking to a direction past [touchSlop]; [onMove] sees each move with its sideways
 * step, the sideways total and whether it has locked sideways, and consumes what it takes.
 */
internal suspend fun AwaitPointerEventScope.followDrag(
    pointer: PointerId,
    touchSlop: Float,
    onMove: (change: PointerInputChange, stepX: Float, dx: Float, horizontal: Boolean) -> Unit,
): DragEnd {
    var dx = 0f
    var dy = 0f
    var horizontal = false
    var vertical = false
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: break
        val stepX = change.position.x - change.previousPosition.x
        dx += stepX
        dy += change.position.y - change.previousPosition.y
        if (!horizontal && !vertical && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
            horizontal = abs(dx) > abs(dy)
            vertical = !horizontal
        }
        onMove(change, stepX, dx, horizontal)
        if (!change.pressed) break
    }
    return DragEnd(dx, horizontal, vertical)
}
