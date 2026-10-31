package com.iron.breakfast.app

import com.iron.breakfast.ui.shortDateLabel
import com.iron.breakfast.ui.pretty
import com.iron.breakfast.profile.ProfileScreen
import com.iron.breakfast.log.UnmatchedParts
import com.iron.breakfast.ui.components.ConfirmDialog
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.ui.components.inert
import com.iron.breakfast.ui.components.LocalScreenBody
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.iron.breakfast.transfer.DiaryPicker
import com.iron.breakfast.data.diaryFileName
import com.iron.breakfast.transfer.DIARY_FILE_TYPES
import com.iron.breakfast.scan.ScannerScreen
import com.iron.breakfast.scan.ScanReviewBackdrop
import com.iron.breakfast.entry.EntryDetailScreen
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.library.LibraryScreen
import com.iron.breakfast.today.DailyScreen
import com.iron.breakfast.today.MealScreen
import com.iron.breakfast.log.LogScreen
import com.iron.breakfast.entry.EntryQuickEditSheet
import com.iron.breakfast.entry.EntryFullEditScreen
import com.iron.breakfast.entry.EntryFoodPicker
import com.iron.breakfast.library.FocusedFoodAddDialog
import com.iron.breakfast.library.LibraryFoodDetailDialog
import com.iron.breakfast.library.MixedMealPickerDialog
import com.iron.breakfast.library.LibraryBatchMealPickerDialog
import com.iron.breakfast.food.EditFoodScreen
import com.iron.breakfast.food.CreateFoodScreen
import com.iron.breakfast.settings.SettingsScreen
import com.iron.breakfast.search.LibraryNoMatchScreen
import com.iron.breakfast.search.OffSearchScreen
import com.iron.breakfast.resolve.ResolveUnknownScreen
import com.iron.breakfast.scan.ProductReviewSheet
import com.iron.breakfast.contribution.ContributionReviewScreen
import com.iron.breakfast.contribution.ContributionSignInScreen
import com.iron.breakfast.contribution.ContributionFailureScreen
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.recipes.IngredientAmountSheet
import com.iron.breakfast.recipes.RecipeFormScreen
import com.iron.breakfast.recipes.RecipeIngredientSearch
import com.iron.breakfast.recipes.RecipeLogSheet
import com.iron.breakfast.recipes.RecipePageScreen
import com.iron.breakfast.recipes.RecipesScreen
import com.iron.breakfast.ui.components.rememberMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronApp(vm: BreakfastViewModel = viewModel()) {
    // Registered first and always, so a picker result delivered after the activity was recreated still reaches the VM.
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm.transfer::export) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm.transfer::import) }
    LaunchedEffect(vm.transfer.pickerRequest) {
        val picker = vm.transfer.pickerRequest ?: return@LaunchedEffect
        vm.transfer.pickerLaunched()
        // No document app at all: nothing was created, so there is nothing to clean up.
        runCatching {
            when (picker) {
                DiaryPicker.EXPORT -> exportPicker.launch(diaryFileName(LocalDate.now()))
                DiaryPicker.IMPORT -> importPicker.launch(DIARY_FILE_TYPES)
            }
        }.onFailure { Log.e(LOG_TAG, "No document picker", it) }
    }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); vm.reloadToday() } }
    // Back from the background: a user following today opens on the new today.
    val activity = LocalContext.current as? android.app.Activity
    // A recreation (dark mode, resize, locale) stops the old activity, but the user never left.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (stopMeansLeaving(activity?.isChangingConfigurations == true)) vm.onAppStopped() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onAppStarted() }
    // TalkBack reads each "Logged!" and each new Undo offer once, a repeat too, in order; a recreation reads nothing.
    val view = LocalView.current
    LaunchedEffect(vm.announcements.serial) { vm.announcements.take().forEach(view::announceForAccessibility) }
    // What is open and in which order (see [Surfaces]): drawn bottom first, each surface keyed so it keeps its state
    // while others open and close over or under it. The base screen is one Scaffold whichever of the three it shows.
    // A screen that just closed may still be drawn as a picture while it glides out (a [Ghost]); it is not open.
    val shown = vm.surfaces.shown()
    val motion = rememberMotion()
    val transitions = rememberSurfaceTransitions()
    for (drawn in transitions.frame(shown, motion)) key(if (drawn is Surface) drawn.slot else drawn) {
        if (drawn is Surface) ShownSurface(transitions, drawn.slot, motion) { DrawSurface(vm, drawn) }
        else GhostSurface(transitions, drawn as Ghost, motion)
    }
    // System Back goes to the topmost surface that takes it; a dialog or sheet takes it in its own window first.
    BackHandler(enabled = vm.surfaces.takesBack(shown)) { vm.surfaces.back() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawSurface(vm: BreakfastViewModel, surface: Surface) {
    when (surface) {
        Surface.DAILY, Surface.MEAL, Surface.LIBRARY, Surface.RECIPES -> BaseScreens(vm, surface)
        Surface.SCAN_REVIEW_BACKDROP -> ScanReviewBackdrop(vm.review::close)
        // Drawn over the screen beneath, which keeps its scroll position. Dialogs and sheets after it render above it.
        Surface.LOG -> LogScreen(vm.log, vm.session, vm.settings, vm.day, vm.scan, vm.confirmations, vm.undo, UnmatchedParts(vm.resolve, vm.offParts, vm.search, vm.foodForm, vm.review, vm.log))
        Surface.ENTRY_QUICK_EDIT -> vm.entryEdit.editingEntry()?.let { EntryQuickEditSheet(vm.entryEdit, vm.session, it) }
        Surface.ENTRY_FULL_EDIT -> vm.entryEdit.editingEntry()?.let { EntryFullEditScreen(vm.entryEdit, vm.session, vm.day, it) }
        Surface.ENTRY_FOOD_PICKER -> EntryFoodPicker(vm.entryEdit, vm.session)
        Surface.LIBRARY_FOOD_ADD -> vm.library.selectedFood?.let { FocusedFoodAddDialog(vm.library, vm.surfaces, it) }
        Surface.LIBRARY_FOOD -> vm.library.selectedFood?.let { LibraryFoodDetailDialog(vm.library, vm.surfaces, vm.foodForm, vm.contribution, vm.settings, vm::automaticMealNow, it) }
        Surface.RECIPE -> vm.recipes.page?.let { RecipePageScreen(vm.recipes, it, onEdit = vm.recipeForm::openEdit) }
        Surface.RECIPE_LOG -> vm.recipes.logging?.let { RecipeLogSheet(vm.recipes, vm.surfaces, vm::automaticMealNow, it) }
        Surface.RECIPE_FORM -> RecipeFormScreen(vm.recipeForm, vm.recipes, vm.session, vm.settings)
        Surface.RECIPE_SEARCH -> RecipeIngredientSearch(vm.recipeForm)
        Surface.RECIPE_INGREDIENT_ADD -> vm.recipeForm.adding?.let { IngredientAmountSheet(vm.recipeForm, it) }
        Surface.DISCARD_RECIPE -> ConfirmDialog("Discard", vm.recipeForm::discard, vm.recipeForm::keepEditing, dismiss = "Keep editing")
        // As Delete food: for good, and past entries keep their own name and numbers.
        Surface.DELETE_RECIPE -> ConfirmDialog("Delete recipe", vm.recipes::confirmDelete, vm.recipes::cancelDelete, text = "Past entries keep their numbers.")
        Surface.MIXED_MEAL_PICKER -> MixedMealPickerDialog(vm.library)
        Surface.BATCH_MEAL_PICKER -> LibraryBatchMealPickerDialog(vm.library)
        Surface.FOOD_FORM -> if (vm.foodForm.editingFoodId != null) EditFoodScreen(vm.foodForm, vm.session, vm.library) else CreateFoodScreen(vm.foodForm, vm.session)
        Surface.PROFILE -> ProfileScreen(vm.profile)
        Surface.DISCARD_PROFILE -> ConfirmDialog("Discard", vm.profile::discard, vm.profile::keepEditing, dismiss = "Keep editing")
        Surface.DELETE_WEIGHT_POINT -> vm.profile.deleteRequest?.let { point ->
            ConfirmDialog("Delete", vm.profile::confirmDelete, vm.profile::cancelDelete, text = "${shortDateLabel(point.dayKey)}, ${point.kg.pretty()} kg")
        }
        Surface.SETTINGS -> SettingsScreen(vm.settings, vm.surfaces, vm.transfer, vm.session, vm::licenceText)
        // Deleting is for good: past entries keep their own name and numbers.
        Surface.DELETE_FOOD -> ConfirmDialog("Delete food", vm.library::confirmDelete, vm.library::cancelDelete, text = "Past entries keep their numbers.")
        Surface.DISCARD_FOOD -> ConfirmDialog("Discard", vm.foodForm::discard, vm.foodForm::keepEditing, dismiss = "Keep editing")
        Surface.LIBRARY_NO_MATCH -> LibraryNoMatchScreen(vm.library, vm.surfaces, vm.search, vm.foodForm)
        Surface.OFF_SEARCH -> OffSearchScreen(vm.search, vm.foodForm, vm.resolve)
        Surface.RESOLVER -> ResolveUnknownScreen(vm.resolve, vm.undo, vm.search, vm.foodForm, vm.confirmations)
        Surface.PRODUCT_REVIEW -> vm.review.product?.let { ProductReviewSheet(vm.review, it) }
        Surface.CONTRIBUTION_REVIEW -> vm.contribution.pending?.let { ContributionReviewScreen(vm.contribution, it) }
        Surface.CONTRIBUTION_SIGN_IN -> ContributionSignInScreen(vm.contribution)
        Surface.CONTRIBUTION_FAILURE -> vm.contribution.failure?.let { ContributionFailureScreen(vm.contribution, it) }
        Surface.NOTICE -> vm.notices.message?.let { message -> AlertDialog(onDismissRequest = vm.notices::dismiss, confirmButton = { TextButton(vm.notices::dismiss) { Text("OK") } }, text = { Text(message) }) }
        Surface.REPLACE_DIARY -> ConfirmDialog("Replace diary", vm.transfer::confirmImport, vm.transfer::cancelImport, text = "This replaces entries and foods, not settings.")
        Surface.DELETE_ALL -> AlertDialog(
            onDismissRequest = vm.transfer::cancelDeleteAll,
            confirmButton = { TextButton(vm.transfer::confirmDeleteAll) { Text("Delete all") } },
            dismissButton = { TextButton(vm.transfer::cancelDeleteAll) { Text("Cancel") } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("This deletes all entries and foods.")
                    ChoiceOption("Also settings and learned picks", vm.transfer.deleteAllAlsoSettings, vm.transfer::toggleDeleteAllAlsoSettings)
                }
            },
        )
        Surface.STORAGE_UNAVAILABLE -> AlertDialog(onDismissRequest = {}, confirmButton = { TextButton(vm::retryStorage) { Text("Try again") } }, title = { Text("Storage unavailable") }, text = { Text("Storage could not be opened.") })
        Surface.ENTRY_DETAIL -> vm.entryEdit.selectedEntry?.let { EntryDetailScreen(vm.entryEdit, vm.session, vm.surfaces, vm.undo, it) }
        Surface.SCANNER -> ScannerScreen(vm.scan, vm.settings.flashlightAtStart)
    }
}

/**
 * The base screen, whichever shows (Daily, a meal, the Library, Recipes), and its changes: the new one fades in over the
 * old one and its body moves by [baseMove] (from the side a tab lies on, a rise into a meal or a meal's Library, the
 * reverse back). The screen on its way out is [inert]: no tap, no TalkBack, and Back already acts on the new one.
 * The bottom area ([BaseBottomArea]: the snackbars, the ink bar, the tabs) is drawn once over them, outside the change,
 * so it never leaves or comes back; hidden during an entry edit, which owns the input and its own sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaseScreens(vm: BreakfastViewModel, surface: Surface) {
    val motion = rememberMotion()
    val target = vm.library.target
    val screen = BaseScreen.of(surface, libraryForMeal = target != null)
    // What a meal's screen is about goes with it, so one on its way out still draws its own meal. Only those two carry
    // a meal: the others stay the same screen (scroll and all) while the meal behind them changes, as it does in an edit.
    val shown = BaseShown(screen, when (screen) {
        BaseScreen.MEAL -> vm.surfaces.meal
        BaseScreen.MEAL_LIBRARY -> target
        else -> null
    })
    val last = remember { BaseScreenMemory(shown.screen) }
    val move = remember(shown.screen) { baseMove(last.screen, shown.screen).also { last.screen = shown.screen } }
    Scaffold(
        containerColor = LocalBreakfastColors.current.bg,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            // The Library and a meal's Library are one screen to it: the change between them is the bar's and the title's.
            AnimatedContent(targetState = shown, transitionSpec = { motion.baseChange() }, contentKey = { it.key }, label = "base") { state ->
                // Leaving from the frame the change starts, so a quick second tap cannot reach it.
                val leaving = state.key != shown.key
                val body = Modifier.animateEnterExit(enter = motion.baseBodyIn(move), exit = motion.baseBodyOut(move))
                CompositionLocalProvider(LocalScreenBody provides body) {
                    Box(if (leaving) Modifier.inert() else Modifier) { BaseScreenContent(vm, state.screen.surface(), state.meal, Modifier.padding(pad)) }
                }
            }
            if (!vm.entryEdit.editing) BaseBottomArea(vm, shown.screen, shown.meal, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A base screen and the meal it is about (the Meal screen's, or the one a meal's Library adds to). */
private data class BaseShown(val screen: BaseScreen, val meal: MealSlot?) {
    /** Which composition draws it: a meal's own, the one Library, or the screen's. */
    val key: Any get() = when (screen) {
        BaseScreen.MEAL -> screen to meal
        BaseScreen.LIBRARY, BaseScreen.MEAL_LIBRARY -> BaseScreen.LIBRARY
        else -> screen
    }
}

/** The base screen shown last, so a change knows where it comes from. */
private class BaseScreenMemory(var screen: BaseScreen)

private fun BaseScreen.surface(): Surface = when (this) {
    BaseScreen.DAILY -> Surface.DAILY
    BaseScreen.MEAL -> Surface.MEAL
    BaseScreen.LIBRARY, BaseScreen.MEAL_LIBRARY -> Surface.LIBRARY
    BaseScreen.RECIPES -> Surface.RECIPES
}

/** One base screen's content, above the bottom area that [BaseScreens] draws over it. */
@Composable
private fun BaseScreenContent(vm: BreakfastViewModel, surface: Surface, meal: MealSlot?, modifier: Modifier) {
    when (surface) {
        Surface.LIBRARY -> LibraryScreen(
            vm.library, vm.resolve, vm.day, vm.surfaces, vm.foodForm, vm::automaticMealNow, modifier,
            target = meal,
        )
        Surface.RECIPES -> RecipesScreen(vm.recipes, onNew = vm.recipeForm::openNew, modifier)
        Surface.DAILY -> DailyScreen(
            vm.session, vm.day, vm.settings, vm.log, vm.contribution, vm.entryEdit, vm.surfaces, vm.undo, vm.feedback,
            vm.search, vm.profile.values, modifier,
        )
        else -> MealScreen(
            vm.session, vm.day, vm.settings, vm.log, vm.contribution, vm.entryEdit, vm.surfaces, vm.undo, vm.feedback,
            vm.search, vm.scan, modifier,
            meal = meal,
        )
    }
}

/** An activity stop counts as leaving the app unless it is a recreation for a configuration change. */
internal fun stopMeansLeaving(changingConfigurations: Boolean): Boolean = !changingConfigurations
