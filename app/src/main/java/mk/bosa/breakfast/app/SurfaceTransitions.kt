package mk.bosa.breakfast.app

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import mk.bosa.breakfast.ui.components.Motion
import mk.bosa.breakfast.ui.components.BaseMove
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/** The key a surface is drawn under: the three base screens are one ([Surface.DAILY]), whichever shows. */
internal val Surface.slot: Surface get() = if (base) Surface.DAILY else this

/**
 * The full screens drawn over the base in the app's own window: they glide in and out (DESIGN.md, Motion). Dialogs and
 * sheets have their own windows and keep their own motion; the scanner's camera view does not fade, and the scan
 * backdrop has its own bracket slide.
 */
internal val Surface.glides: Boolean get() = when (this) {
    Surface.LOG, Surface.ENTRY_FULL_EDIT, Surface.LIBRARY_FOOD, Surface.RECIPE, Surface.RECIPE_FORM, Surface.RECIPE_SEARCH, Surface.FOOD_FORM, Surface.SETTINGS, Surface.PROFILE, Surface.ONBOARDING, Surface.OFF_SEARCH,
    Surface.RESOLVER, Surface.LIBRARY_NO_MATCH, Surface.CONTRIBUTION_REVIEW, Surface.CONTRIBUTION_SIGN_IN, Surface.CONTRIBUTION_FAILURE,
    Surface.ENTRY_DETAIL -> true
    else -> false
}

/**
 * The base screen on show, as its motion reads it: its tab in the bottom bar's order ([tab], the gear last) and how deep
 * it lies under that tab ([depth]: a meal is opened from Meals, a meal's Library from the meal).
 */
internal enum class BaseScreen(val tab: Int, val depth: Int) {
    DAILY(0, 0), MEAL(0, 1), MEAL_LIBRARY(0, 2), LIBRARY(1, 0), RECIPES(2, 0);

    companion object {
        /** The base [surface] on show; the Library is a meal's while it adds to one ([libraryForMeal]). */
        fun of(surface: Surface, libraryForMeal: Boolean): BaseScreen = when (surface) {
            Surface.MEAL -> MEAL
            Surface.LIBRARY -> if (libraryForMeal) MEAL_LIBRARY else LIBRARY
            Surface.RECIPES -> RECIPES
            else -> DAILY
        }
    }
}

/**
 * How the body moves from [from] to [to]: to another tab, toward the side the new tab lies on (from a meal too); under
 * one tab, a rise into a deeper screen and a fall back out of it.
 */
internal fun baseMove(from: BaseScreen, to: BaseScreen): BaseMove = when {
    to.tab > from.tab -> BaseMove.FORWARD
    to.tab < from.tab -> BaseMove.BACK
    to.depth > from.depth -> BaseMove.RISE
    to.depth < from.depth -> BaseMove.FALL
    else -> BaseMove.NONE
}

/** Whether the root records [slot]'s drawing, so it can still be drawn after it closed: the gliding screens and the base. */
internal val Surface.recorded: Boolean get() = glides || this == Surface.DAILY

/**
 * What one change of [Surfaces.shown] moves, by slot: [entering] screens glide in, [leaving] screens glide out, and
 * [held] stays drawn, still, under a screen gliding in (the base under the entry detail, which is drawn alone). The
 * scanner is drawn alone and comes and goes without motion; what it covered comes back as it was.
 */
internal data class SurfaceChange(val entering: Set<Surface>, val leaving: Set<Surface>, val held: Set<Surface>)

internal fun surfaceChange(before: List<Surface>, now: List<Surface>): SurfaceChange {
    if (Surface.SCANNER in before || Surface.SCANNER in now) return SurfaceChange(emptySet(), emptySet(), emptySet())
    val was = before.map { it.slot }.toSet()
    val shows = now.map { it.slot }.toSet()
    val entering = (shows - was).filter { it.glides }.toSet()
    val gone = was - shows
    val held = gone.filter { slot -> slot == Surface.DAILY && entering.any { it.ordinal > slot.ordinal } }.toSet()
    return SurfaceChange(entering, gone.filter { it.glides }.toSet(), held)
}

/** A shown slot's drawing and its glide (0 out of sight, 1 in place). */
internal class LiveSlot(val layer: GraphicsLayer?, val progress: Animatable<Float, AnimationVector1D>)

/**
 * A closed screen, drawn from its last frame while it glides out; or, [held], still while the screens in [under]
 * glide in over it.
 */
internal class Ghost(val slot: Surface, val layer: GraphicsLayer, val progress: Animatable<Float, AnimationVector1D>, val held: Boolean, val under: Set<Surface>)

/**
 * The motion of the root's surfaces, beside [Surfaces], which stays the one model of what is open: this only
 * remembers what was drawn last, so a screen that closed can be drawn a little longer as a picture of its last frame
 * (a [Ghost]: no composition, no touch, no Back, no TalkBack). Back and the top check act on the new top at once.
 */
@Stable
internal class SurfaceTransitions(private val graphics: GraphicsContext) {
    /** A ghost's picture keeps the layers it was drawn with; API 28 draws through layers that cannot be kept. */
    private val ghostsWork = Build.VERSION.SDK_INT != Build.VERSION_CODES.P
    private var drawn: List<Surface>? = null
    private val live = HashMap<Surface, LiveSlot>()
    private val ghosts = ArrayList<Ghost>()
    private var version by mutableIntStateOf(0)
    private var entering: Set<Surface> = emptySet()
    /** Where a screen that opens again while its ghost fades starts its glide: where the ghost was. */
    private val resumeAt = HashMap<Surface, Float>()

    /** What to draw, bottom first: each shown surface and each ghost, in [Surface] order. */
    fun frame(shown: List<Surface>, motion: Motion): List<Any> {
        version
        val before = drawn
        drawn = shown
        val change = if (before == null || motion.reduced) SurfaceChange(emptySet(), emptySet(), emptySet()) else surfaceChange(before, shown)
        entering = change.entering
        if (ghostsWork) for (slot in change.leaving + change.held) {
            val closed = live[slot] ?: continue
            closed.layer?.let { ghosts += Ghost(slot, it, closed.progress, held = slot in change.held, under = change.entering) }
        }
        // A screen that opens again, or reduced motion, ends its ghost; the screen goes on from where the ghost was.
        val slots = shown.map { it.slot }.toSet()
        resumeAt.clear()
        ghosts.removeAll { ghost ->
            (motion.reduced || ghost.slot in slots).also { ends -> if (ends && !ghost.held && ghost.slot in entering) resumeAt[ghost.slot] = ghost.progress.value }
        }
        return (shown + ghosts).sortedBy { if (it is Ghost) it.slot.ordinal else (it as Surface).slot.ordinal }
    }

    /** The state of [slot] as it is first drawn: a screen opening now starts out of sight. */
    fun open(slot: Surface): LiveSlot {
        val progress = Animatable(if (slot in entering) resumeAt[slot] ?: 0f else 1f)
        return LiveSlot(if (slot.recorded) graphics.createGraphicsLayer() else null, progress).also { live[slot] = it }
    }

    /** [slot] is no longer drawn: its layer goes, unless a ghost now draws it. */
    fun closed(slot: Surface, state: LiveSlot) {
        if (live[slot] === state) live.remove(slot)
        val layer = state.layer ?: return
        if (ghosts.none { it.layer === layer }) graphics.releaseGraphicsLayer(layer)
    }

    /** Every screen in [slots] that is still shown has finished gliding in. */
    fun cameIn(slots: Set<Surface>): Boolean = slots.all { slot -> live[slot]?.progress?.let { it.value >= 1f } ?: true }

    fun ended(ghost: Ghost) { if (ghosts.remove(ghost)) version++ }

    fun release(ghost: Ghost) { graphics.releaseGraphicsLayer(ghost.layer) }
}

@Composable
internal fun rememberSurfaceTransitions(): SurfaceTransitions {
    val graphics = LocalGraphicsContext.current
    return remember(graphics) { SurfaceTransitions(graphics) }
}

/**
 * A shown surface. A gliding screen comes in with a fade and a [Motion.screenSlidePx] rise; it and the base are
 * recorded as they draw (not with reduced motion), so they can leave as a [Ghost]. Everything else is drawn as it is.
 */
@Composable
internal fun ShownSurface(transitions: SurfaceTransitions, slot: Surface, motion: Motion, content: @Composable () -> Unit) {
    val state = remember { transitions.open(slot) }
    DisposableEffect(state) { onDispose { transitions.closed(slot, state) } }
    val layer = state.layer
    if (layer == null) { content(); return }
    LaunchedEffect(state) { state.progress.animateTo(1f, motion.screenIn()) }
    // The same constraints as at the root, so the screen lays out exactly as it did without the box.
    Box(
        Modifier
            .graphicsLayer {
                val p = state.progress.value
                alpha = p
                translationY = (1f - p) * motion.screenSlidePx
            }
            .drawWithContent {
                if (motion.reduced) drawContent()
                else {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
            },
        propagateMinConstraints = true,
    ) { content() }
}

/** A closed screen's last frame, gliding out, or held still until the screens over it have come in; then it goes. */
@Composable
internal fun GhostSurface(transitions: SurfaceTransitions, ghost: Ghost, motion: Motion) {
    DisposableEffect(ghost) { onDispose { transitions.release(ghost) } }
    LaunchedEffect(ghost) {
        if (ghost.held) snapshotFlow { transitions.cameIn(ghost.under) }.first { it }
        else ghost.progress.animateTo(0f, motion.screenOut())
        transitions.ended(ghost)
    }
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = if (ghost.held) 1f else ghost.progress.value
                alpha = p
                translationY = (1f - p) * motion.screenSlidePx
            }
            .drawBehind { drawLayer(ghost.layer) },
    )
}
