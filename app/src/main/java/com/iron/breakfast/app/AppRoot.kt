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
import com.iron.breakfast.library.LibraryMealPickerDialog
import com.iron.breakfast.library.LibraryBatchMealPickerDialog
import com.iron.breakfast.library.LibraryFoodEditScreen
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
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportDiary) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importDiary) }
    LaunchedEffect(vm.diaryPickerRequest) {
        val picker = vm.diaryPickerRequest ?: return@LaunchedEffect
        vm.diaryPickerLaunched()
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
    if (vm.scannerOpen) { ScannerScreen(vm); return }
    val scanReviewBackdropVisible = vm.selectedProduct != null && vm.selectedProductFromScan
    if (scanReviewBackdropVisible) ScanReviewBackdrop(vm::clearSelectedProduct)
    else {
    if (vm.selectedEntry != null) {
        EntryDetailScreen(vm, vm.selectedEntry!!)
        return
    }
    Scaffold(
        containerColor = LocalBreakfastColors.current.bg,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        bottomBar = {
            // The tray rides above the keyboard and clear of a side display cutout.
            Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).imePadding()) {
                // Daily and Meal composers float over their own content; only Library uses this bar.
                if (vm.entryEditMode == EntryEditMode.NONE && vm.libraryOpen && !vm.libraryArchivedOnly) LibraryComposer(vm)
            }
        }
    ) { pad ->
        when {
            vm.libraryOpen -> LibraryScreen(vm, Modifier.padding(pad))
            vm.slot == null -> DailyScreen(vm, Modifier.padding(pad))
            else -> MealScreen(vm, Modifier.padding(pad))
        }
    }
    // Drawn over the screen beneath, which keeps its scroll position. Dialogs and sheets below render above it.
    if (vm.logOpen) LogScreen(vm)
    }
    when (vm.entryEditMode) {
        EntryEditMode.QUICK -> vm.editingEntry()?.let { EntryQuickEditSheet(vm, it) }
        EntryEditMode.FULL -> vm.editingEntry()?.let { EntryFullEditScreen(vm, it) }
        EntryEditMode.NONE -> Unit
    }
    if (vm.editingFoodPickerOpen) EntryFoodPicker(vm)
    if (vm.selectedLibraryFood != null) {
        if (vm.slot != null) FocusedFoodAddDialog(vm, vm.selectedLibraryFood!!)
        else LibraryFoodDetailDialog(vm, vm.selectedLibraryFood!!)
    }
    // LogScreen and every overlay own Back while they are open.
    if (!vm.logOpen && vm.topOverlay() == null) {
        if (vm.libraryOpen) {
            // The same exit as the Library's top-bar arrow: Archived goes to Library, a meal's Library to the meal.
            BackHandler { vm.exitLibrary() }
        } else if (vm.slot != null) {
            BackHandler { vm.closeMeal() }
        }
    }
    if (vm.mixedMealPickerOpen) MixedMealPickerDialog(vm)
    if (vm.libraryMealPickerFood != null) LibraryMealPickerDialog(vm, vm.libraryMealPickerFood!!)
    if (vm.libraryBatchMealPickerOpen) LibraryBatchMealPickerDialog(vm)
    if (vm.customOpen) {
        if (vm.editingLibraryFoodId != null) LibraryFoodEditScreen(vm) else CreateFoodScreen(vm)
    }
    if (vm.settingsOpen) SettingsScreen(vm)
    if (vm.discardFoodOpen) AlertDialog(
        onDismissRequest = vm::keepEditingFood,
        confirmButton = { TextButton(vm::discardFood) { Text("Discard") } },
        dismissButton = { TextButton(vm::keepEditingFood) { Text("Keep editing") } },
    )
    if (vm.libraryNoMatch()) LibraryNoMatchScreen(vm)
    if (vm.search.open) OffSearchScreen(vm)
    if (vm.resolverOpen && !scanReviewBackdropVisible) ResolveUnknownScreen(vm)
    vm.selectedProduct?.let { ProductReviewSheet(vm, it) }
    if (vm.contribution.reviewOpen) vm.contribution.pending?.let { ContributionReviewScreen(vm, it) }
    if (vm.contribution.authOpen) ContributionSignInScreen(vm)
    vm.contribution.failure?.let { ContributionFailureScreen(vm, it) }
    vm.notices.message?.let { message -> AlertDialog(onDismissRequest = vm.notices::dismiss, confirmButton = { TextButton(vm.notices::dismiss) { Text("OK") } }, text = { Text(message) }) }
    if (vm.pendingImport != null) AlertDialog(
        onDismissRequest = vm::cancelImport,
        confirmButton = { TextButton(vm::confirmImport) { Text("Replace diary") } },
        dismissButton = { TextButton(vm::cancelImport) { Text("Cancel") } },
        text = { Text("This replaces entries and foods, not settings.") },
    )
    if (vm.deleteAllOpen) AlertDialog(
        onDismissRequest = vm::cancelDeleteAll,
        confirmButton = { TextButton(vm::confirmDeleteAll) { Text("Delete all") } },
        dismissButton = { TextButton(vm::cancelDeleteAll) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("This deletes all entries and foods.")
                ChoiceOption("Also settings and learned picks", vm.deleteAllAlsoSettings, vm::toggleDeleteAllAlsoSettings)
            }
        },
    )
    if (vm.storageUnavailable) AlertDialog(onDismissRequest = {}, confirmButton = { TextButton(vm::retryStorage) { Text("Try again") } }, title = { Text("Storage unavailable") }, text = { Text("Storage could not be opened.") })
}
