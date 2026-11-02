package mk.bosa.breakfast.ui.components

import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import mk.bosa.breakfast.ui.theme.LocalBreakfastColors

/**
 * Where a sequence of pages is. Pure: the owner holds it. [page] is where it is going, and all of Next and Back count from
 * there, so a second tap is not lost; [settled] is where the pager last stopped, which differs while it moves.
 */
internal data class StepPosition(val page: Int, val count: Int, val settled: Int = page) {
    val isLast: Boolean get() = page == count - 1
    val canGoBack: Boolean get() = page > 0
    /** The pager has reached the last page: what replaces Next shows only now, so a second tap cannot land on it. */
    val arrivedAtLast: Boolean get() = isLast && settled == page
    fun next(): StepPosition = if (isLast) this else copy(page = page + 1)
    fun back(): StepPosition = if (canGoBack) copy(page = page - 1) else this
    /** A restore, on a page that exists. */
    fun at(to: Int): StepPosition = to.coerceIn(0, count - 1).let { copy(page = it, settled = it) }
    /**
     * The pager stopped on [to]. A stop [byUser], or with no move going, is where it is; one on the way to a newer [page],
     * as one move replaces another, changes nothing about where it is going.
     */
    fun settledAt(to: Int, byUser: Boolean): StepPosition =
        if (byUser || page == settled) copy(page = to, settled = to) else copy(settled = to)
}

/** Whether a drag came since the pager last stopped. */
private class DragSeen { var seen = false }

/**
 * Pages side by side at [position]: where the pager stops goes to [onSettled], with whether a swipe took it there, and a
 * [position] changed elsewhere (Next, Back) moves there, at once under reduced motion. Each [content] scrolls by itself
 * if it needs to.
 */
@Composable
internal fun StepPager(position: StepPosition, onSettled: (page: Int, byUser: Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable (page: Int) -> Unit) {
    val motion = rememberMotion()
    val pager = rememberPagerState(initialPage = position.page) { position.count }
    val drag = remember { DragSeen() }
    LaunchedEffect(pager) { pager.interactionSource.interactions.collect { if (it is DragInteraction.Start) drag.seen = true } }
    LaunchedEffect(position.page) {
        // Also on a page it is passing or stopped short of: a move replaced halfway leaves the pager between pages.
        if (pager.settledPage != position.page || pager.currentPage != position.page || pager.currentPageOffsetFraction != 0f) {
            drag.seen = false
            if (motion.reduced) pager.scrollToPage(position.page) else pager.animateScrollToPage(position.page)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            onSettled(page, drag.seen)
            drag.seen = false
        }
    }
    HorizontalPager(pager, modifier, key = { it }) { content(it) }
}

/** Where a [StepPosition] is, as a row of dots: the current one ink, the others `line`. One sentence to TalkBack. */
@Composable
internal fun StepDots(position: StepPosition, modifier: Modifier = Modifier) {
    val colors = LocalBreakfastColors.current
    val description = "Page ${position.page + 1} of ${position.count}"
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        repeat(position.count) { page ->
            Box(Modifier.size(6.dp).background(if (page == position.page) colors.ink else colors.line, CircleShape))
        }
    }
}
