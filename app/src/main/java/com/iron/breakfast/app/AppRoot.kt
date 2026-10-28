package com.iron.breakfast.app

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.iron.breakfast.stopMeansLeaving
import com.iron.breakfast.scan.ScannerScreen
import com.iron.breakfast.scan.ScanReviewBackdrop
import com.iron.breakfast.entry.EntryDetailScreen
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.entry.EntryEditMode
import com.iron.breakfast.library.LibraryComposer
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
    // What is open and in which order (see [Surfaces]): drawn bottom first, each surface keyed so it keeps its state
    // while others open and close over or under it. The base screen is one Scaffold whichever of the three it shows.
    for (surface in vm.surfaces.shown()) key(if (surface.base) Surface.DAILY else surface) { DrawSurface(vm, surface) }
    // System Back goes to the topmost surface that takes it; a dialog or sheet takes it in its own window first.
    BackHandler(enabled = vm.surfaces.takesBack()) { vm.surfaces.back() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawSurface(vm: BreakfastViewModel, surface: Surface) {
    when (surface) {
        Surface.DAILY, Surface.MEAL, Surface.LIBRARY -> Scaffold(
            containerColor = LocalBreakfastColors.current.bg,
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
            bottomBar = {
                // The tray rides above the keyboard and clear of a side display cutout.
                Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).imePadding()) {
                    // Daily and Meal composers float over their own content; only Library uses this bar.
                    if (vm.entryEdit.mode == EntryEditMode.NONE && surface == Surface.LIBRARY && !vm.library.archivedOnly) LibraryComposer(vm)
                }
            }
        ) { pad ->
            when (surface) {
                Surface.LIBRARY -> LibraryScreen(vm, Modifier.padding(pad))
                Surface.DAILY -> DailyScreen(vm, Modifier.padding(pad))
                else -> MealScreen(vm, Modifier.padding(pad))
            }
        }
        Surface.SCAN_REVIEW_BACKDROP -> ScanReviewBackdrop(vm.review::close)
        // Drawn over the screen beneath, which keeps its scroll position. Dialogs and sheets after it render above it.
        Surface.LOG -> LogScreen(vm)
        Surface.ENTRY_QUICK_EDIT -> vm.entryEdit.editingEntry()?.let { EntryQuickEditSheet(vm, it) }
        Surface.ENTRY_FULL_EDIT -> vm.entryEdit.editingEntry()?.let { EntryFullEditScreen(vm, it) }
        Surface.ENTRY_FOOD_PICKER -> EntryFoodPicker(vm)
        Surface.LIBRARY_FOOD_ADD -> vm.library.selectedFood?.let { FocusedFoodAddDialog(vm, it) }
        Surface.LIBRARY_FOOD -> vm.library.selectedFood?.let { LibraryFoodDetailDialog(vm, it) }
        Surface.MIXED_MEAL_PICKER -> MixedMealPickerDialog(vm)
        Surface.BATCH_MEAL_PICKER -> LibraryBatchMealPickerDialog(vm)
        Surface.FOOD_FORM -> if (vm.foodForm.editingFoodId != null) EditFoodScreen(vm) else CreateFoodScreen(vm)
        Surface.SETTINGS -> SettingsScreen(vm)
        Surface.DISCARD_FOOD -> AlertDialog(
            onDismissRequest = vm.foodForm::keepEditing,
            confirmButton = { TextButton(vm.foodForm::discard) { Text("Discard") } },
            dismissButton = { TextButton(vm.foodForm::keepEditing) { Text("Keep editing") } },
        )
        Surface.LIBRARY_NO_MATCH -> LibraryNoMatchScreen(vm)
        Surface.OFF_SEARCH -> OffSearchScreen(vm)
        Surface.RESOLVER -> ResolveUnknownScreen(vm)
        Surface.PRODUCT_REVIEW -> vm.review.product?.let { ProductReviewSheet(vm, it) }
        Surface.CONTRIBUTION_REVIEW -> vm.contribution.pending?.let { ContributionReviewScreen(vm, it) }
        Surface.CONTRIBUTION_SIGN_IN -> ContributionSignInScreen(vm)
        Surface.CONTRIBUTION_FAILURE -> vm.contribution.failure?.let { ContributionFailureScreen(vm, it) }
        Surface.NOTICE -> vm.notices.message?.let { message -> AlertDialog(onDismissRequest = vm.notices::dismiss, confirmButton = { TextButton(vm.notices::dismiss) { Text("OK") } }, text = { Text(message) }) }
        Surface.REPLACE_DIARY -> AlertDialog(
            onDismissRequest = vm.transfer::cancelImport,
            confirmButton = { TextButton(vm.transfer::confirmImport) { Text("Replace diary") } },
            dismissButton = { TextButton(vm.transfer::cancelImport) { Text("Cancel") } },
            text = { Text("This replaces entries and foods, not settings.") },
        )
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
        Surface.ENTRY_DETAIL -> vm.entryEdit.selectedEntry?.let { EntryDetailScreen(vm, it) }
        Surface.SCANNER -> ScannerScreen(vm)
    }
}
