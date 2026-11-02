package mk.bosa.breakfast.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import mk.bosa.breakfast.ui.theme.BreakfastType
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors
import mk.bosa.breakfast.ui.theme.ToastGutter
import mk.bosa.breakfast.ui.theme.ToastShape
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Which way a released [Toast] leaves. */
internal enum class ToastExit { LEFT, RIGHT, DOWN }

/** How far the finger holds a toast below its place: anywhere below it, never above it. Sideways it follows freely. */
internal fun toastDragY(dy: Float): Float = max(dy, 0f)

/**
 * Where a toast released at ([x], [y]) with velocity ([vx], [vy]) goes; null springs it back. A fling decides first, by
 * its stronger axis (a fling mainly up dismisses nothing), then how far it was dragged past [sideways] or [down].
 */
internal fun toastExit(x: Float, y: Float, vx: Float, vy: Float, sideways: Float, down: Float, fling: Float): ToastExit? = when {
    vy <= -fling && abs(vy) > abs(vx) -> null
    abs(vx) >= fling && abs(vx) >= vy -> if (vx < 0f) ToastExit.LEFT else ToastExit.RIGHT
    vy >= fling -> ToastExit.DOWN
    abs(x) >= sideways && abs(x) >= y -> if (x < 0f) ToastExit.LEFT else ToastExit.RIGHT
    y >= down -> ToastExit.DOWN
    else -> null
}

/** How far one show of a toast is out of its place; each holds its own, so stacked ones swipe independently. */
@Stable
internal class ToastSwipe(val showId: Long) {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    /** Released to leave, or closed by its owner: it takes no new gesture. */
    var leaving by mutableStateOf(false)
}

/** The swipe of the show on screen: a new show, or the same toast shown again after it closed, starts in its place. */
internal class ToastShowing(showId: Long) {
    private var closed = false
    private var swipe = ToastSwipe(showId)

    fun swipeFor(message: String?, showId: Long): ToastSwipe {
        if (message == null) {
            closed = true
        } else if (closed || showId != swipe.showId) {
            swipe = ToastSwipe(showId)
            closed = false
        }
        return swipe
    }
}

/**
 * The one ink toast: [message] (as many lines as it needs), an optional action in `bg` 500 on the right, and with an
 * [owner] a swipe left, right or down that takes it away for good. It takes touches only on itself; what lies around it
 * stays usable. How long it stays is its owner's ([ToastSlot]).
 */
@Composable
internal fun Toast(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    owner: ToastOwner? = null,
    swipe: ToastSwipe = remember { ToastSwipe(owner?.showId ?: 0L) },
) {
    val colors = LocalBreakfastColors.current
    Row(
        modifier
            .then(if (owner != null) Modifier.toastSwipe(swipe, owner).semantics { dismiss { owner.close(swipe.showId); true } } else Modifier)
            .fillMaxWidth()
            .graphicsLayer {
                val at = swipe.offset.value
                translationX = at.x
                translationY = at.y
                if (size.width > 0f && size.height > 0f) {
                    alpha = 1f - max(abs(at.x) / size.width, at.y / (size.height * TOAST_DOWN_EXIT)).coerceIn(0f, 1f)
                }
            }
            .heightIn(min = 48.dp)
            .clip(ToastShape)
            .background(colors.ink)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = colors.bg, style = BreakfastType.Secondary, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = colors.bg,
                style = BreakfastType.Button,
                modifier = Modifier.clickable(enabled = !swipe.leaving, role = Role.Button, onClick = onAction).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * Follows the finger, by [toastDragY] downwards; on release it leaves by [toastExit] and then closes in its [owner], or
 * settles back. It sits outside the toast's movement, so the finger is read in still coordinates. A press alone takes
 * nothing, so the action still taps; a drag consumes its moves, which cancels that tap. With reduced motion a release is
 * instant.
 *
 * Its time stands still from the press until the release. Another show takes over by a new [swipe], which ends this
 * gesture with nothing closed. A release that leaves always closes its own show, even if the toast leaves the screen on
 * the way out; the owner ignores it once a newer show is there.
 */
@Composable
private fun Modifier.toastSwipe(swipe: ToastSwipe, owner: ToastOwner): Modifier {
    val motion by rememberUpdatedState(rememberMotion())
    return pointerInput(swipe, owner) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (swipe.leaving) return@awaitEachGesture
                owner.hold(swipe.showId, true)
                var leaves = false
                try {
                    val velocity = VelocityTracker().apply { addPointerInputChange(down) }
                    val start = swipe.offset.value
                    var dx = 0f
                    var dy = 0f
                    var moving = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPointerInputChange(change)
                        if (!change.pressed) break
                        val step = change.positionChangeIgnoreConsumed()
                        dx += step.x
                        dy += step.y
                        if (!moving && hypot(dx, toastDragY(dy)) > viewConfiguration.touchSlop) moving = true
                        if (moving) {
                            change.consume()
                            val at = Offset(start.x + dx, toastDragY(start.y + dy))
                            launch(start = CoroutineStart.UNDISPATCHED) { swipe.offset.snapTo(at) }
                        }
                    }
                    if (moving) {
                        val at = swipe.offset.value
                        val speed = velocity.calculateVelocity()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val exit = toastExit(at.x, at.y, speed.x, speed.y, width * TOAST_SIDEWAYS_SHARE, height * TOAST_DOWN_SHARE, TOAST_FLING_DP_PER_S.dp.toPx())
                        val away = when (exit) {
                            ToastExit.LEFT -> Offset(-width * TOAST_SIDE_EXIT, at.y)
                            ToastExit.RIGHT -> Offset(width * TOAST_SIDE_EXIT, at.y)
                            ToastExit.DOWN -> Offset(at.x, height * TOAST_DOWN_EXIT)
                            null -> Offset.Zero
                        }
                        if (exit != null) {
                            leaves = true
                            swipe.leaving = true
                        }
                        // Undispatched: past this line the finally below runs, however the animation ends.
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                swipe.offset.animateTo(away, motion.swipe())
                            } finally {
                                if (exit != null) owner.close(swipe.showId)
                            }
                        }
                    }
                } finally {
                    if (!leaves) owner.hold(swipe.showId, false)
                }
            }
        }
    }
}

/**
 * A [Toast] for [message] while it is not null, gliding in and out ([Reveal]), with the 8dp under it. [modifier] places
 * the whole reveal (an align in a Box must sit on it); with [resize] its space opens and closes with it. Each show of
 * [owner] starts in its place. TalkBack reads each one from the root (`Announcements`).
 */
@Composable
internal fun ToastReveal(
    message: String?,
    owner: ToastOwner,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    resize: Boolean = false,
) {
    val showing = remember { ToastShowing(owner.showId) }
    val swipe = showing.swipeFor(message, owner.showId)
    if (message == null) SideEffect { swipe.leaving = true }
    Reveal(message, modifier, resize) { Toast(it, actionLabel, onAction, Modifier.padding(bottom = 8.dp), owner, swipe) }
}

/**
 * Where the app's one notice is drawn: in one place, over every screen ([NoticeToastHost]), above the bottom of the
 * surface on top ([ToastFloorMark]); while a [Sheet] is open, in the sheet, as a sheet has a window of its own over the
 * app's.
 */
internal class ToastSeat(val slot: ToastSlot<String>, val actionLabel: String, val onAction: () -> Unit) {
    var sheetsOpen by mutableIntStateOf(0)
    private val floors = mutableStateMapOf<Any, Float>()

    fun floorY(floor: Any): Float? = floors[floor]

    fun setFloor(floor: Any, y: Float?) {
        if (y == null) floors.remove(floor) else floors[floor] = y
    }
}

/** The [ToastSeat] of the app; provided at the root (none in the debug gallery). */
internal val LocalToastSeat = staticCompositionLocalOf<ToastSeat?> { null }

/** The surface being drawn, whose bottom a [ToastFloorMark] marks; provided at the root. */
internal val LocalToastFloor = staticCompositionLocalOf<Any?> { null }

/** Marks the top of a surface's bottom area: a notice stacks above this line and rides with the keyboard. */
@Composable
internal fun ToastFloorMark() {
    val seat = LocalToastSeat.current ?: return
    val floor = LocalToastFloor.current ?: return
    Spacer(Modifier.fillMaxWidth().onGloballyPositioned { seat.setFloor(floor, it.positionInRoot().y) })
    DisposableEffect(seat, floor) { onDispose { seat.setFloor(floor, null) } }
}

/** A screen's own toasts ([content], if any) in the toasts' gutter, with its [ToastFloorMark] over them. */
@Composable
internal fun ToastStack(content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ToastGutter)) {
        ToastFloorMark()
        content()
    }
}

/** The notice, over everything the root draws: on [floor]'s mark, else above the keyboard or the system bars. */
@Composable
internal fun NoticeToastHost(seat: ToastSeat, floor: Any?) {
    if (seat.sheetsOpen > 0) return
    val y = floor?.let(seat::floorY)
    Box(
        Modifier
            .fillMaxSize()
            .then(if (y == null) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(0, (y?.roundToInt() ?: constraints.maxHeight) - placeable.height)
                }
            }
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = ToastGutter),
    ) { NoticeToast(seat) }
}

/** The notice inside a [Sheet], at its top: the content moves over to make room and the keyboard cannot cover it. */
@Composable
internal fun SheetNoticeToast() {
    val seat = LocalToastSeat.current ?: return
    DisposableEffect(seat) {
        seat.sheetsOpen++
        onDispose { seat.sheetsOpen-- }
    }
    NoticeToast(seat, Modifier.zIndex(1f), resize = true)
}

@Composable
private fun NoticeToast(seat: ToastSeat, modifier: Modifier = Modifier, resize: Boolean = false) {
    ToastReveal(seat.slot.shown, seat.slot, seat.actionLabel, seat.onAction, modifier, resize)
}

/** Past this share of its width a dragged toast leaves sideways. */
private const val TOAST_SIDEWAYS_SHARE = 0.35f
/** Past this share of its height a dragged toast leaves downwards. */
private const val TOAST_DOWN_SHARE = 0.75f
private const val TOAST_FLING_DP_PER_S = 800
/** How far it travels as it leaves sideways, in its widths: past the edge of the screen. */
private const val TOAST_SIDE_EXIT = 1.2f
/** How far it travels as it leaves downwards, in its heights; it has faded out by there. */
private const val TOAST_DOWN_EXIT = 3f
