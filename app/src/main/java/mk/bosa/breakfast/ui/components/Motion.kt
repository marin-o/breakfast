package mk.bosa.breakfast.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Screens over the base glide in this long (DESIGN.md, Motion), and out in [SCREEN_OUT_MS]. */
internal const val SCREEN_IN_MS = 220
internal const val SCREEN_OUT_MS = 160
/** The toasts and "Not right?". */
internal const val FEEDBACK_MS = 180
/** Rows fading in and collapsing out. */
internal const val LIST_MS = 200
/** The day's content after a day swipe, an arrow, Today or the date picker. */
internal const val DAY_MS = 220
/** A swiped row settling open or shut. */
internal const val SWIPE_MS = 180
/** The base screens changing (a tab, a meal, a meal's Library): the new one in, the old one out in [BASE_OUT_MS]. */
internal const val BASE_IN_MS = 240
internal const val BASE_OUT_MS = 200
/** The bottom bar's highlight moving to another item. */
internal const val NAV_MS = 220
/** A Settings page changing within Settings. */
internal const val PAGE_MS = 220

/** How far a screen and the day's content slide as they come and go. */
internal val ScreenSlide = 24.dp
/** How far the toasts and "Not right?" rise as they appear. */
internal val FeedbackSlide = 8.dp

/**
 * The Quiet motion (DESIGN.md, Motion): short, standard easing, no bounce, nothing waits for it. The one rule for
 * reduced motion lives here: when [reduced] (Settings, Motion is Reduced, or the phone's animations are removed) every
 * spec is [snap] and every transition is none, so each change is instant.
 */
@Immutable
internal class Motion(val reduced: Boolean, private val density: Density) {
    fun <T> screenIn(): FiniteAnimationSpec<T> = spec(SCREEN_IN_MS)
    fun <T> screenOut(): FiniteAnimationSpec<T> = spec(SCREEN_OUT_MS)
    fun <T> day(): FiniteAnimationSpec<T> = spec(DAY_MS)
    fun <T> swipe(): FiniteAnimationSpec<T> = spec(SWIPE_MS)
    fun <T> nav(): FiniteAnimationSpec<T> = spec(NAV_MS)

    /**
     * One change of the base screen, as a whole: the new one fades in, the old one fades out, both in place, so the
     * bottom bars they share cross where they stand. Its body moves by [baseBodyIn] and [baseBodyOut].
     */
    fun baseChange(): ContentTransform =
        if (reduced) instant()
        else ContentTransform(
            fadeIn(tween(BASE_IN_MS, easing = FastOutSlowInEasing)),
            fadeOut(tween(BASE_OUT_MS, easing = FastOutSlowInEasing)),
            sizeTransform = null,
        )

    /** A change that takes no time: the reduced form of [baseChange] and [pageChange]. */
    private fun instant() = ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

    /** The new base screen's body: from the side its tab lies on, or a rise into a deeper screen ([BaseMove]). */
    fun baseBodyIn(move: BaseMove): EnterTransition {
        if (reduced) return EnterTransition.None
        val slide = with(density) { ScreenSlide.roundToPx() }
        val spec = tween<androidx.compose.ui.unit.IntOffset>(BASE_IN_MS, easing = FastOutSlowInEasing)
        return when (move) {
            BaseMove.FORWARD -> slideInHorizontally(spec) { slide }
            BaseMove.BACK -> slideInHorizontally(spec) { -slide }
            BaseMove.RISE -> slideInVertically(spec) { slide }
            BaseMove.FALL, BaseMove.NONE -> EnterTransition.None
        }
    }

    /** The old base screen's body: the reverse of the new one's, so a screen left the way it came goes back that way. */
    fun baseBodyOut(move: BaseMove): ExitTransition {
        if (reduced) return ExitTransition.None
        val slide = with(density) { ScreenSlide.roundToPx() }
        val spec = tween<androidx.compose.ui.unit.IntOffset>(BASE_OUT_MS, easing = FastOutSlowInEasing)
        return when (move) {
            BaseMove.FORWARD -> slideOutHorizontally(spec) { -slide }
            BaseMove.BACK -> slideOutHorizontally(spec) { slide }
            BaseMove.FALL -> slideOutVertically(spec) { slide }
            BaseMove.RISE, BaseMove.NONE -> ExitTransition.None
        }
    }

    /** A Settings page: a deeper page in from the end, a shallower one (Back) from the start, each with a fade. */
    fun pageChange(deeper: Boolean): ContentTransform {
        if (reduced) return instant()
        val slide = with(density) { ScreenSlide.roundToPx() }
        val sign = if (deeper) 1 else -1
        val inSpec = tween<Float>(PAGE_MS, easing = FastOutSlowInEasing)
        val outSpec = tween<Float>(SCREEN_OUT_MS, easing = FastOutSlowInEasing)
        val move = tween<androidx.compose.ui.unit.IntOffset>(PAGE_MS, easing = FastOutSlowInEasing)
        return ContentTransform(
            fadeIn(inSpec) + slideInHorizontally(move) { sign * slide },
            fadeOut(outSpec) + slideOutHorizontally(move) { -sign * slide },
            sizeTransform = null,
        )
    }

    /** For `Modifier.animateItem` in lazy lists: null (no animation) when reduced. */
    fun <T> listItem(): FiniteAnimationSpec<T>? = if (reduced) null else tween(LIST_MS, easing = FastOutSlowInEasing)

    /** [ScreenSlide] in pixels. */
    val screenSlidePx: Float get() = with(density) { ScreenSlide.toPx() }

    /** A toast or "Not right?" appearing: a fade and an [FeedbackSlide] rise; with [resize] its space opens too. */
    fun feedbackIn(resize: Boolean = false): EnterTransition {
        if (reduced) return EnterTransition.None
        val rise = with(density) { FeedbackSlide.roundToPx() }
        val enter = fadeIn(tween(FEEDBACK_MS, easing = FastOutSlowInEasing)) + slideInVertically(tween(FEEDBACK_MS, easing = FastOutSlowInEasing)) { rise }
        return if (resize) enter + expandVertically(tween(FEEDBACK_MS, easing = FastOutSlowInEasing)) else enter
    }

    fun feedbackOut(resize: Boolean = false): ExitTransition {
        if (reduced) return ExitTransition.None
        val rise = with(density) { FeedbackSlide.roundToPx() }
        val exit = fadeOut(tween(FEEDBACK_MS, easing = FastOutSlowInEasing)) + slideOutVertically(tween(FEEDBACK_MS, easing = FastOutSlowInEasing)) { rise }
        return if (resize) exit + shrinkVertically(tween(FEEDBACK_MS, easing = FastOutSlowInEasing)) else exit
    }

    /** A scrim behind something lifted: it only fades, in the time of what it lies behind ([feedbackIn]). */
    fun scrimIn(): EnterTransition = if (reduced) EnterTransition.None else fadeIn(tween(FEEDBACK_MS, easing = FastOutSlowInEasing))
    fun scrimOut(): ExitTransition = if (reduced) ExitTransition.None else fadeOut(tween(FEEDBACK_MS, easing = FastOutSlowInEasing))

    /** A row of a list that is not lazy: it fades in as its space opens, and fades as it collapses out. */
    fun rowIn(): EnterTransition = if (reduced) EnterTransition.None else fadeIn(tween(LIST_MS, easing = FastOutSlowInEasing)) + expandVertically(tween(LIST_MS, easing = FastOutSlowInEasing))
    fun rowOut(): ExitTransition = if (reduced) ExitTransition.None else fadeOut(tween(LIST_MS, easing = FastOutSlowInEasing)) + shrinkVertically(tween(LIST_MS, easing = FastOutSlowInEasing))

    private fun <T> spec(millis: Int): FiniteAnimationSpec<T> = if (reduced) snap() else tween(millis, easing = FastOutSlowInEasing)
}

/**
 * How the base screen's body moves when it changes ([Motion.baseBodyIn]): [FORWARD] to a tab further right, [BACK] to
 * one further left, [RISE] into a screen opened from one (a meal, a meal's Library), [FALL] back out of it.
 */
internal enum class BaseMove { FORWARD, BACK, RISE, FALL, NONE }

/**
 * The modifier a base screen puts on its body (its list), so it moves as [Motion.baseBodyIn] says while the screen
 * around it, the bottom bar included, only fades. None outside a base screen change.
 */
internal val LocalScreenBody = compositionLocalOf<Modifier> { Modifier }

/** The [Motion] for this composition: reduced by [rememberMotionReduced]. */
@Composable
internal fun rememberMotion(): Motion {
    val reduced = rememberMotionReduced()
    val density = LocalDensity.current
    return remember(reduced, density) { Motion(reduced, density) }
}

/**
 * Something on its way out: it takes no tap and TalkBack does not see it. A closed toast, a removed row or a
 * leaving "Not right?" can still be drawn while it fades, but it can no longer be used. Only presses and releases are
 * taken (a tap needs both), so a drag that starts on it still scrolls the list around it.
 */
internal fun Modifier.inert(): Modifier = this
    .clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                if (it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed()) it.consume()
            }
        }
    }

/** Holds the last value shown, so content keeps drawing it while it leaves. */
private class Last<T : Any> { var value: T? = null }

/**
 * Shows [content] for [value] while it is not null, gliding in and out ([Motion.feedbackIn]); while it leaves it draws
 * the last value and is [inert]. With [resize] the space it takes opens and closes with it (inside lists).
 */
@Composable
internal fun <T : Any> Reveal(value: T?, modifier: Modifier = Modifier, resize: Boolean = false, content: @Composable (T) -> Unit) {
    val motion = rememberMotion()
    val last = remember { Last<T>() }
    if (value != null) last.value = value
    AnimatedVisibility(
        visible = value != null,
        modifier = if (value == null) modifier.inert() else modifier,
        enter = motion.feedbackIn(resize),
        exit = motion.feedbackOut(resize),
    ) { last.value?.let { content(it) } }
}

/** One row of [AnimatedRows]: [present] is false while it collapses out. */
internal class ShownRow<T>(val key: Any, val item: T, val present: Boolean, val entering: Boolean)

/**
 * The rows of a list as drawn: the current items in order, with a removed row kept where it was until it has
 * collapsed ([drop]). The first list, and every list after [AnimatedRows]' reset, shows as it is, without motion.
 */
internal class RowList<T> {
    private var drawn: List<ShownRow<T>>? = null
    private val gone = HashSet<Any>()
    private var version by mutableIntStateOf(0)

    fun update(items: List<T>, keyOf: (T) -> Any, animate: Boolean): List<ShownRow<T>> {
        version
        val before = drawn
        val rows = if (before == null || !animate) items.map { ShownRow(keyOf(it), it, present = true, entering = false) }
        else mergeRows(before, items, keyOf).filterNot { !it.present && it.key in gone }
        gone.clear()
        drawn = rows
        return rows
    }

    /** A removed row has collapsed: it is no longer drawn. */
    fun drop(key: Any) { if (gone.add(key)) version++ }
}

/**
 * [items] in order, each new one entering; a row of [before] no longer in [items] stays, not present, after the row
 * that came before it.
 */
internal fun <T> mergeRows(before: List<ShownRow<T>>, items: List<T>, keyOf: (T) -> Any): List<ShownRow<T>> {
    val known = before.associateBy { it.key }
    val current = items.map { keyOf(it) }.toSet()
    val result = ArrayList<ShownRow<T>>(items.size + before.size)
    val emitted = HashSet<Any>()
    var next = 0
    fun emitUpTo(key: Any) {
        while (next < items.size) {
            val item = items[next++]
            val k = keyOf(item)
            if (emitted.add(k)) result += ShownRow(k, item, present = true, entering = known[k]?.present != true)
            if (k == key) return
        }
    }
    for (row in before) {
        if (row.key in current) { if (row.key !in emitted) emitUpTo(row.key) }
        else result += ShownRow(row.key, row.item, present = false, entering = false)
    }
    while (next < items.size) {
        val item = items[next++]
        val k = keyOf(item)
        if (emitted.add(k)) result += ShownRow(k, item, present = true, entering = known[k]?.present != true)
    }
    return result
}

/**
 * The rows of a list that is not lazy ([Motion.rowIn]): a new row fades in as its space opens, a removed row fades as
 * it collapses and is [inert] while it does. A new [reset] (another day) shows its rows at once.
 */
@Composable
internal fun <T> AnimatedRows(items: List<T>, keyOf: (T) -> Any, reset: Any? = null, row: @Composable (T) -> Unit) {
    val motion = rememberMotion()
    val list = remember(reset) { RowList<T>() }
    for (shown in list.update(items, keyOf, animate = !motion.reduced)) key(shown.key) {
        val state = remember { MutableTransitionState(!shown.entering).apply { targetState = true } }
        SideEffect { state.targetState = shown.present }
        if (!shown.present && state.isIdle && !state.currentState) SideEffect { list.drop(shown.key) }
        AnimatedVisibility(state, if (shown.present) Modifier else Modifier.inert(), enter = motion.rowIn(), exit = motion.rowOut()) { row(shown.item) }
    }
}
