package com.bosa.breakfast.app

import com.bosa.breakfast.today.UndoToasts
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import com.bosa.breakfast.ui.theme.ComposerHeight
import androidx.compose.ui.graphics.graphicsLayer
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.library.LibrarySearchField
import com.bosa.breakfast.ui.components.BottomNav
import com.bosa.breakfast.ui.components.Composer
import com.bosa.breakfast.ui.components.ComposerFade
import com.bosa.breakfast.ui.components.ComposerText
import com.bosa.breakfast.ui.components.NavTab
import com.bosa.breakfast.ui.components.ToastFloorMark
import com.bosa.breakfast.ui.components.ToastReveal
import com.bosa.breakfast.ui.components.closingToast
import com.bosa.breakfast.ui.components.inert
import com.bosa.breakfast.ui.components.rememberMotion
import com.bosa.breakfast.ui.label

/** What the one ink bar does on a base screen: opens the Log page (for [Log.meal]; none on Daily), or searches the Library. */
internal sealed interface BarMode {
    data class Log(val meal: MealSlot?) : BarMode
    data object Search : BarMode
}

/** The bar's mode on this screen; null on Recipes, which has no bar. */
internal fun BaseScreen.barMode(meal: MealSlot?): BarMode? = when (this) {
    BaseScreen.DAILY -> BarMode.Log(null)
    BaseScreen.MEAL -> BarMode.Log(meal)
    BaseScreen.LIBRARY, BaseScreen.MEAL_LIBRARY -> BarMode.Search
    BaseScreen.RECIPES -> null
}

/** The tab this screen is in the bottom bar; null on a meal and a meal's Library, which show the bar without the tabs. */
internal fun BaseScreen.navTab(): NavTab? = when (this) {
    BaseScreen.DAILY -> NavTab.MEALS
    BaseScreen.LIBRARY -> NavTab.LIBRARY
    BaseScreen.RECIPES -> NavTab.RECIPES
    BaseScreen.MEAL, BaseScreen.MEAL_LIBRARY -> null
}

/** The last value something showed, so it can keep drawing it while it fades. */
private class Kept<T>(var value: T)

/**
 * The one bottom area of the base screens, drawn once over whichever shows, outside their change: the toasts, the
 * ink bar and the tabs never leave or come back on a tab change. Only what differs changes in place: the bar's text
 * crosses between the Log opener and the Library's search ([Motion.nav]), the tabs' line slides, the tabs fold away
 * under a meal, and on Recipes the bar fades where it stands so the tabs do not move.
 */
@Composable
internal fun BaseBottomArea(vm: BreakfastViewModel, screen: BaseScreen, meal: MealSlot?, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    val undo = vm.undo
    val surfaces = vm.surfaces
    val mode = screen.barMode(meal)
    val keptMode = remember { Kept<BarMode>(BarMode.Log(null)) }
    if (mode != null) keptMode.value = mode
    val barMode = keptMode.value
    val tab = screen.navTab()
    val keptTab = remember { Kept(NavTab.MEALS) }
    if (tab != null) keptTab.value = tab
    // The area rides above the keyboard (also while it closes after the search), clear of a side display cutout.
    ComposerFade(modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).imePadding()) {
        val logScreen = screen == BaseScreen.DAILY || screen == BaseScreen.MEAL
        ToastFloorMark()
        UndoToasts(undo, shown = logScreen)
        ToastReveal(vm.confirmations.message, vm.confirmations.slot)
        val reminder = screen == BaseScreen.DAILY && vm.reminders.shownOnDaily(covered = surfaces.baseCovered())
        if (reminder) LaunchedEffect(Unit) { vm.reminders.onScreenNow() }
        ToastReveal("Export your diary.".takeIf { reminder }, remember(vm) { closingToast(vm.reminders::hide) }, "Export", vm.reminders::export)
        val barAlpha by animateFloatAsState(if (mode != null) 1f else 0f, motion.nav(), label = "bar")
        // The bar's place keeps its height on Recipes, so the tabs and "Logged!" do not move.
        Box(Modifier.fillMaxWidth().heightIn(min = ComposerHeight)) {
            // Fading out on Recipes it is not read or tapped; once gone, taps reach what lies under its place.
            if (mode != null || barAlpha > 0f) Composer(vm.scan::begin, Modifier.graphicsLayer { alpha = barAlpha }.then(if (mode == null) Modifier.inert() else Modifier)) {
                Crossfade(barMode, Modifier.weight(1f), animationSpec = motion.nav(), label = "bar text") { shown ->
                    // The text on its way out is not read or tapped.
                    Box(if (shown != barMode) Modifier.inert() else Modifier) {
                        when (shown) {
                            BarMode.Search -> LibrarySearchField(vm.library, vm.resolve, Modifier.fillMaxWidth())
                            is BarMode.Log -> {
                                val draft = vm.log.draft.isNotBlank()
                                ComposerText(
                                    placeholder = if (draft) vm.log.draft else shown.meal?.let { "Add to ${it.label()}" } ?: "Log food",
                                    onClick = { vm.log.open(shown.meal) },
                                    modifier = Modifier.fillMaxWidth(),
                                    draft = draft,
                                    longDraft = vm.settings.longDraft,
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(tab != null, enter = motion.rowIn(), exit = motion.rowOut()) {
            val selected = keptTab.value
            BottomNav(
                selected = selected,
                onMeals = when (selected) {
                    NavTab.RECIPES -> surfaces::closeRecipes
                    else -> ({ surfaces.closeMeal(); surfaces.closeLibrary() })
                },
                onLibrary = when (selected) {
                    NavTab.MEALS -> ({ surfaces.closeMeal(); surfaces.openLibrary() })
                    NavTab.LIBRARY -> ({})
                    NavTab.RECIPES -> surfaces::openLibrary
                },
                onRecipes = when (selected) {
                    NavTab.MEALS -> ({ surfaces.closeMeal(); surfaces.openRecipes() })
                    NavTab.LIBRARY -> surfaces::openRecipes
                    NavTab.RECIPES -> ({})
                },
                onSettings = surfaces::openSettings,
                settingsOpen = surfaces.settingsOpen,
                onProfile = surfaces::openProfile,
                profileOpen = surfaces.profileOpen,
            )
        }
    }
}
