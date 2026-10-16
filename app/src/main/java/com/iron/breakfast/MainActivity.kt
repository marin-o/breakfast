package com.iron.breakfast

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.data.OffContributionResult
import com.iron.breakfast.data.OffPreflightResult
import com.iron.breakfast.data.OffResult
import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.data.OpenFoodFactsClient
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.domain.normalizeBarcode
import com.iron.breakfast.domain.resolveNutrition
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.toMeasureUnit
import com.iron.breakfast.domain.dayKeyForEpochMs
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.ui.screens.DailyScreen
import com.iron.breakfast.ui.screens.DailyComposer
import com.iron.breakfast.ui.screens.EntryDetailScreen
import com.iron.breakfast.ui.screens.EntryFullEditScreen
import com.iron.breakfast.ui.screens.EntryFoodPicker
import com.iron.breakfast.ui.screens.EntryQuickEditSheet
import com.iron.breakfast.ui.screens.FocusedFoodAddDialog
import com.iron.breakfast.ui.screens.LibraryComposer
import com.iron.breakfast.ui.screens.FoodBarcodeField
import com.iron.breakfast.ui.screens.LibraryFoodDetailDialog
import com.iron.breakfast.ui.screens.LibraryFoodEditScreen
import com.iron.breakfast.ui.screens.LibraryScreen
import com.iron.breakfast.ui.screens.MealComposer
import com.iron.breakfast.ui.screens.MealScreen
import com.iron.breakfast.ui.screens.CreateFoodScreen
import com.iron.breakfast.ui.screens.LibraryNoMatchScreen
import com.iron.breakfast.ui.screens.OffSearchScreen
import com.iron.breakfast.ui.screens.ProductReviewSheet
import com.iron.breakfast.ui.screens.ResolveUnknownScreen
import com.iron.breakfast.ui.screens.ScanReviewBackdrop
import com.iron.breakfast.ui.screens.ScannerScreen
import com.iron.breakfast.ui.components.ReceiptNavigationBar
import com.iron.breakfast.ui.theme.BreakfastTheme
import com.iron.breakfast.ui.theme.DarkReceiptColors
import com.iron.breakfast.ui.theme.LightReceiptColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep system bars aligned with the same light/dark paper token as the receipt.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(LightReceiptColors.paper.toArgb(), DarkReceiptColors.paper.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(LightReceiptColors.paper.toArgb(), DarkReceiptColors.paper.toArgb()),
        )
        setContent { BreakfastTheme { IronApp() } }
    }
}

enum class EntryEditMode { NONE, QUICK, FULL }

internal data class QuickAddPreview(
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val calculation: Calculation,
)

private enum class EntryGesturePhase { UP, HORIZONTAL, VERTICAL, CONSUMED }

class BreakfastViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val engine = BreakfastEngine(BreakfastStore(application))
    private val off = OpenFoodFactsClient()
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()
    var input by mutableStateOf(""); private set
    var foods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var entries by mutableStateOf<List<MealEntry>>(emptyList()); private set
    var selectedDayKey by mutableStateOf(savedState["selected_day"] ?: dayKeyForEpochMs(System.currentTimeMillis())); private set
    var slot by mutableStateOf(savedState.get<String>("meal_slot")?.let { runCatching { MealSlot.valueOf(it) }.getOrNull() }); private set
    var customOpen by mutableStateOf(false); private set
    var editingLibraryFoodId by mutableStateOf<String?>(null); private set
    val draft = FoodDraft()
    var searchOpen by mutableStateOf(false); private set
    var searchQuery by mutableStateOf(""); private set
    var offResults by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var searchBusy by mutableStateOf(false); private set
    var offSearchMessage by mutableStateOf<String?>(null); private set
    /** True when the last OFF search failed for lack of a connection (not for any other reason). */
    var offSearchUnavailable by mutableStateOf(false); private set
    var scannerOpen by mutableStateOf(false); private set
    /** True when the scanner is still open but its camera preview cannot be used. */
    var scannerUnavailableState by mutableStateOf(false); private set
    var selectedProduct by mutableStateOf<FoodRecord?>(null); private set
    var selectedProductFromScan by mutableStateOf(false); private set
    var productAmount by mutableStateOf("100"); private set
    var productUnit by mutableStateOf("g"); private set
    var productName by mutableStateOf(""); private set
    var productCalories by mutableStateOf(""); private set
    var productProtein by mutableStateOf(""); private set
    var productCarbs by mutableStateOf(""); private set
    var productFat by mutableStateOf(""); private set
    var notice by mutableStateOf<String?>(null); private set
    var pendingContribution by mutableStateOf<OffContributionDraft?>(null); private set
    var contributionReviewOpen by mutableStateOf(false); private set
    var contributionAuthOpen by mutableStateOf(false); private set
    var contributionFailure by mutableStateOf<String?>(null); private set
    var contributionUsername by mutableStateOf(""); private set
    var contributionPassword by mutableStateOf(""); private set
    var contributionBusy by mutableStateOf(false); private set
    var contributionPreflightBusy by mutableStateOf(false); private set
    private var contributionPreflightComplete by mutableStateOf(false)
    private var contributionPreflightToken = 0L
    var contributionLanguageCode by mutableStateOf("en"); private set
    var contributionPackageQuantityMl by mutableStateOf(""); private set
    var undoEntry by mutableStateOf<MealEntry?>(null); private set
    var mealDraftError by mutableStateOf<String?>(null); private set
    var mealPickerOpen by mutableStateOf(false); private set
    var libraryOpen by mutableStateOf(false); private set
    var libraryQuery by mutableStateOf(""); private set
    /** True only on the explicit Archived foods destination; active foods are never mixed in. */
    var libraryArchivedOnly by mutableStateOf(false); private set
    var libraryFoods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    /** The query [libraryFoods] was computed for; the no-match screen waits until it equals [libraryQuery]. */
    var libraryResultsQuery by mutableStateOf<String?>(null); private set
    var selectedLibraryFood by mutableStateOf<FoodRecord?>(null); private set
    var libraryMealPickerFood by mutableStateOf<FoodRecord?>(null); private set
    var expandedLibraryFoodId by mutableStateOf<String?>(null); private set
    private var libraryPendingAmount: Double? = null
    private var libraryPendingUnit: MeasureUnit? = null
    private var libraryPendingBatch: List<LibraryLogDraft> = emptyList()
    private var libraryRefreshJob: Job? = null
    private var libraryRefreshGeneration = 0L
    /** Daily input present before entering Library; Library-owned typing is restored to this on exit. */
    private var libraryInputBeforeOpen: String? = null
    private var offSearchJob: Job? = null
    private var offSearchGeneration = 0L
    var libraryBatchMealPickerOpen by mutableStateOf(false); private set
    var selectedEntry by mutableStateOf<MealEntry?>(null); private set
    private var selectedEntryId: String? = savedState["entry_id"]
    private var barcodeRequestToken = 0L
    private var rescanningBarcode = false
    private var barcodeOriginDay = selectedDayKey
    private var barcodeOriginMeal: MealSlot? = slot
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var initializationError by mutableStateOf<String?>(null); private set
    var entryEditMode by mutableStateOf(EntryEditMode.NONE); private set
    var editingEntryId by mutableStateOf<String?>(null); private set
    var editingFoodName by mutableStateOf<String?>(null); private set
    var editingFoodId by mutableStateOf<String?>(null); private set
    var editingFoodPickerOpen by mutableStateOf(false); private set
    var editInvalid by mutableStateOf(false); private set
    var editInvalidSignal by mutableStateOf(0); private set
    var quickEditHintVisible by mutableStateOf(false); private set
    private var editSession = 0L
    private var editReturnToDaily = false
    private var quickHintSession = 0L
    private var fullSaveJob: Job? = null
    var unknownQueue by mutableStateOf<List<String>>(emptyList()); private set
    var unknownQueueTarget by mutableStateOf<MealSlot?>(null); private set
    var unknownQueueTotal by mutableStateOf(0); private set
    var resolutionContinuation by mutableStateOf(false); private set
    var resolverOpen by mutableStateOf(false); private set
    var resolverQuery by mutableStateOf(""); private set
    var resolverResults by mutableStateOf<List<RankedFood>>(emptyList()); private set
    var feedbackUntil by mutableStateOf(0L); private set
    var feedbackIds by mutableStateOf<Set<String>>(emptySet()); private set
    var loggedBatchMessage by mutableStateOf<String?>(null); private set
    private var loggedBatchIds: List<String> = emptyList()

    init { viewModelScope.launch { runCatching { engine.initialize(); refresh() }.onSuccess { ready = true }.onFailure { initializationError = "Storage could not be opened: ${it.message}" } } }
    private fun refresh() {
        foods = engine.foods()
        entries = engine.persistedMeal().entries
        selectedEntry = selectedEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    }
    fun reloadToday() { if (ready) refresh() }
    private fun selectDay(value: String) { selectedDayKey = value; savedState["selected_day"] = value }
    private fun selectMeal(value: MealSlot?) { slot = value; savedState["meal_slot"] = value?.name }
    fun shiftSelectedDay(delta: Long) { selectDay(runCatching { LocalDate.parse(selectedDayKey).plusDays(delta).toString() }.getOrDefault(selectedDayKey)); barcodeRequestToken++ }
    fun goToday() { selectDay(dayKeyForEpochMs(System.currentTimeMillis())); barcodeRequestToken++ }
    fun openLibrary() {
        if (!libraryOpen) libraryInputBeforeOpen = input
        cancelEdit(); selectMeal(null); libraryOpen = true; libraryQuery = ""; libraryArchivedOnly = false; refreshLibrary()
    }
    fun openArchivedLibrary() {
        if (!libraryOpen) libraryInputBeforeOpen = input
        cancelEdit(); selectMeal(null); libraryOpen = true; libraryQuery = ""; libraryArchivedOnly = true; refreshLibrary()
    }
    fun closeLibrary() {
        val restoreInput = if (libraryOpen) libraryInputBeforeOpen else null
        libraryRefreshJob?.cancel(); libraryRefreshGeneration++; libraryOpen = false; selectedLibraryFood = null; libraryMealPickerFood = null; expandedLibraryFoodId = null; libraryPendingAmount = null; libraryPendingUnit = null; libraryPendingBatch = emptyList(); libraryBatchMealPickerOpen = false; libraryArchivedOnly = false; selectMeal(null)
        libraryQuery = ""
        libraryResultsQuery = null
        if (restoreInput != null) input = restoreInput
        libraryInputBeforeOpen = null
    }
    fun cancelLibraryMealPicker() { libraryMealPickerFood = null; libraryPendingAmount = null; libraryPendingUnit = null }
    fun onLibraryQuery(value: String) { libraryQuery = value; refreshLibrary() }
    fun refreshLibrary() {
        if (!ready) return
        libraryRefreshJob?.cancel()
        val generation = ++libraryRefreshGeneration
        val captured = libraryQuery
        val capturedArchivedOnly = libraryArchivedOnly
        val capturedEntries = entries
        libraryRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            val matching = engine.foodsMatching(captured, includeArchived = capturedArchivedOnly)
            val ordered = libraryRecencyOrder(
                if (capturedArchivedOnly) matching.filter { it.archived } else matching,
                capturedEntries,
            )
            withContext(Dispatchers.Main) {
                if (shouldApplyLibraryRefresh(
                        generation, libraryRefreshGeneration, captured, libraryQuery,
                        capturedArchivedOnly, libraryArchivedOnly, libraryOpen,
                    )
                ) {
                    libraryFoods = ordered
                    libraryResultsQuery = captured
                }
            }
        }
    }
    fun openLibraryFood(food: FoodRecord) { selectedLibraryFood = food }
    /** A library search that finished with nothing and has no other screen over it: the "No local match" screen. */
    fun libraryNoMatch(): Boolean = isLibraryNoMatch(
        ready, libraryOpen, libraryQuery, libraryResultsQuery, libraryFoods.isEmpty(),
        otherScreenOpen = selectedLibraryFood != null || customOpen || searchOpen || resolverOpen || selectedProduct != null || scannerOpen || selectedEntry != null || entryEditMode != EntryEditMode.NONE,
    )
    fun closeLibraryFood() { selectedLibraryFood = null }
    fun lastLoggedEntry(food: FoodRecord): MealEntry? = entries.asSequence()
        .filter { it.foodId == food.id }
        .maxByOrNull { it.savedAtEpochMs }

    fun toggleLibraryFoodPicker(food: FoodRecord) {
        expandedLibraryFoodId = if (expandedLibraryFoodId == food.id) null else food.id
    }
    fun collapseLibraryFoodPicker() { expandedLibraryFoodId = null }
    fun noticeForInvalidLibraryAmount() { notice = "Enter a positive amount and a supported unit such as g, ml, tbsp, tsp, or piece." }

    fun logLibraryFood(food: FoodRecord, amount: Double = lastLoggedEntry(food)?.enteredAmount ?: food.defaultAmount, unit: MeasureUnit = lastLoggedEntry(food)?.enteredUnit ?: food.defaultUnit) {
        if (!ready || busy || food.archived) return
        val target = slot
        if (target == null) {
            libraryPendingAmount = amount
            libraryPendingUnit = unit
            libraryMealPickerFood = food
            return
        }
        logLibraryFood(food, target, amount, unit)
    }

    fun logLibraryFood(food: FoodRecord, target: MealSlot, amount: Double = lastLoggedEntry(food)?.enteredAmount ?: food.defaultAmount, unit: MeasureUnit = lastLoggedEntry(food)?.enteredUnit ?: food.defaultUnit) {
        if (!ready || busy || food.archived) return
        logLibraryBatch(listOf(libraryLogDraft(food, target, amount, unit)), target)
    }

    private fun logLibraryBatch(drafts: List<LibraryLogDraft>, target: MealSlot) {
        if (!ready || busy || drafts.isEmpty()) return
        val capturedDayKey = selectedDayKey
        busy = true
        viewModelScope.launch {
            runCatching { drafts.forEach { draft -> engine.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, capturedDayKey) } }
                .onSuccess { refresh(); refreshLibrary(); notice = "Added ${drafts.size} food${if (drafts.size == 1) "" else "s"} to ${target.label()} on $capturedDayKey" }
                .onFailure { notice = it.message ?: "Could not log this food." }
                .also { busy = false }
        }
    }

    fun chooseLibraryBatchMeal(target: MealSlot) {
        val drafts = libraryPendingBatch
        libraryPendingBatch = emptyList()
        libraryBatchMealPickerOpen = false
        logLibraryBatch(drafts, target)
    }
    fun dismissLibraryBatchMealPicker() {
        libraryPendingBatch = emptyList()
        libraryBatchMealPickerOpen = false
    }
    fun chooseLibraryMeal(target: MealSlot) {
        val food = libraryMealPickerFood ?: return
        libraryMealPickerFood = null
        if (!ready || busy) return
        val amount = libraryPendingAmount ?: lastLoggedEntry(food)?.enteredAmount ?: food.defaultAmount
        val unit = libraryPendingUnit ?: lastLoggedEntry(food)?.enteredUnit ?: food.defaultUnit
        libraryPendingAmount = null
        libraryPendingUnit = null
        logLibraryFood(food, target, amount, unit)
    }
    fun archiveLibraryFood(food: FoodRecord) {
        if (!ready || busy || food.archived || food.origin != com.iron.breakfast.domain.FoodOrigin.CUSTOM) return
        busy = true
        viewModelScope.launch {
            runCatching { engine.archiveFood(food.id) }
                .onSuccess { selectedLibraryFood = null; if (editingLibraryFoodId == food.id) closeCustom(); refresh(); refreshLibrary(); notice = "Food archived; saved diary entries remain readable." }
                .onFailure { notice = it.message ?: "Could not archive this food." }
                .also { busy = false }
        }
    }
    fun restoreLibraryFood(food: FoodRecord) {
        if (!ready || busy || !food.archived) return
        busy = true
        viewModelScope.launch {
            runCatching { engine.restoreFood(food.id) }
                .onSuccess { refresh(); selectedLibraryFood = foods.firstOrNull { it.id == food.id }; refreshLibrary(); notice = "Food restored to your library." }
                .onFailure { notice = it.message ?: "Could not restore this food." }
                .also { busy = false }
        }
    }
    fun openEntryDetail(entry: MealEntry) { selectedEntry = entry; selectedEntryId = entry.id; savedState["entry_id"] = entry.id }
    fun closeEntryDetail() { selectedEntry = null; selectedEntryId = null; savedState["entry_id"] = null }
    fun editingEntry(): MealEntry? = editingEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    fun saveFullEdit(foodId: String, amount: String, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
        val id = editingEntryId ?: return
        val session = editSession
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0 || foodId.isBlank() || !ready || busy) {
            markEditInvalid("Enter a positive amount and choose a food.")
            return
        }
        busy = true
        fullSaveJob?.cancel()
        fullSaveJob = viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    when (val result = engine.replaceEntryDraft(id, foodId, parsedAmount, unit, meal, dayKey)) {
                        is ReplacementResult.Success -> if (editSession == session && editingEntryId == id) {
                            refresh()
                            fullSaveJob = null
                            cancelEdit()
                        }
                        is ReplacementResult.Failure -> if (editSession == session && editingEntryId == id) markEditInvalid(result.reason)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (editSession == session && editingEntryId == id) markEditInvalid(error.message ?: "Could not save this entry.")
                } finally {
                    if (editSession == session && editingEntryId == id) busy = false
                }
            }
        }
    }
    fun openContributionForFood(food: FoodRecord?) {
        contributionPreflightToken++
        val draft = food?.let { OffContributionDraft.fromFood(it) }
        pendingContribution = draft
        if (draft == null) {
            contributionPreflightBusy = false
            contributionPreflightComplete = false
            notice = "Open Food Facts submission is available only for foods with a barcode."
            return
        }
        contributionPreflightComplete = false
        startContributionPreflight(draft)
    }

    private fun startContributionPreflight(draft: OffContributionDraft) {
        val token = ++contributionPreflightToken
        contributionPreflightBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = off.preflight(draft)
            withContext(Dispatchers.Main) {
                if (token != contributionPreflightToken) return@withContext
                contributionPreflightBusy = false
                contributionPreflightComplete = true
                when (result) {
                    OffPreflightResult.Existing -> pendingContribution = draft.copy(createNewProduct = false)
                    OffPreflightResult.NewProduct -> {
                        val language = contributionLanguageFor(draft.name)
                        contributionLanguageCode = language
                        contributionPackageQuantityMl = ""
                        pendingContribution = draft.copy(createNewProduct = true, languageCode = language)
                    }
                    is OffPreflightResult.Ineligible -> {
                        pendingContribution = null
                        notice = result.message
                    }
                    OffPreflightResult.Unavailable -> {
                        pendingContribution = null
                        notice = "Open Food Facts was unavailable. Retry when you have a connection."
                    }
                }
                if (pendingContribution != null) contributionReviewOpen = true
            }
        }
    }
    private fun abandonUnknownResolution(clearDraft: Boolean = false) {
        unknownQueue = emptyList()
        unknownQueueTarget = null
        unknownQueueTotal = 0
        resolutionContinuation = false
        resolverOpen = false
        if (clearDraft) {
            input = ""
            mealDraftError = null
        }
    }
    fun openMeal(s: MealSlot) { abandonUnknownResolution(); selectMeal(s); barcodeRequestToken++; cancelEdit(); mealDraftError = null }
    fun openFocusedLibrary(meal: MealSlot?, query: String = "") {
        abandonUnknownResolution()
        cancelEdit()
        if (!libraryOpen) libraryInputBeforeOpen = ""
        selectMeal(meal)
        libraryOpen = true
        libraryQuery = query
        libraryArchivedOnly = false
        refreshLibrary()
    }
    fun openLibraryFoodDetail(entry: MealEntry) {
        closeEntryDetail()
        cancelEdit()
        if (!libraryOpen) libraryInputBeforeOpen = ""
        selectMeal(null)
        libraryOpen = true
        libraryQuery = entry.foodName
        selectedLibraryFood = foods.firstOrNull { it.id == entry.foodId }
        refreshLibrary()
    }
    fun openEditFoodPicker() {
        editingFoodPickerOpen = true
        refreshLibrary()
    }
    fun closeEditFoodPicker() { editingFoodPickerOpen = false }
    fun chooseEditFood(food: FoodRecord) {
        editingFoodId = food.id
        editingFoodName = food.name
        editingFoodPickerOpen = false
    }
    fun editingFood(): FoodRecord? = editingFoodId?.let { id -> foods.firstOrNull { it.id == id } }
    fun editUnits(food: FoodRecord): List<MeasureUnit> = (listOf(food.defaultUnit, food.nutrition.unit) + food.conversions.map { it.unit })
        .filter { it != MeasureUnit.UNKNOWN }.distinct()
    fun draftCalculation(foodId: String, amount: Double, unit: MeasureUnit): Calculation? =
        foods.firstOrNull { it.id == foodId }?.let { calculator.calculate(it, amount, unit) }
    fun closeMeal() { abandonUnknownResolution(clearDraft = true); selectMeal(null); barcodeRequestToken++; cancelEdit() }
    fun onInput(v: String) {
        input = v
        if (libraryOpen && parser.parse(v).amount == null) onLibraryQuery(v)
    }
    internal fun quickAddTargetMeal(): MealSlot? = slot ?: mealMarkers(input).singleOrNull()
    private fun quickAddSegments(): List<String> = input.split(',').map(String::trim).filter(String::isNotBlank)
    private fun quickAddActiveSegment(): String = input.substringAfterLast(',').trim()
    internal fun quickAddSuggestions(): List<RankedFood> {
        if (!ready || input.isBlank()) return emptyList()
        val active = quickAddActiveSegment()
        if (active.isBlank() || mealMarkers(active).isNotEmpty()) return emptyList()
        val parsed = parser.parse(active)
        return engine.suggestions(parsed.foodPhrase).take(4)
    }
    internal fun quickAddSuggestionLabel(food: FoodRecord): String {
        val parsed = parser.parse(quickAddActiveSegment())
        return listOfNotNull(parsed.amount?.pretty(), parsed.unit?.displayLabel(), food.name)
            .joinToString(" ")
    }
    /** True when a typed food phrase resolves to a food (drives the "recognised" underline). */
    fun isRecognizedFood(phrase: String): Boolean = ready && phrase.isNotBlank() && engine.search(phrase).isNotEmpty()

    internal fun quickAddPreviews(): List<QuickAddPreview> {
        if (!ready || input.isBlank()) return emptyList()
        val segments = quickAddSegments()
        val completed = if (input.trimEnd().endsWith(',')) segments else segments.dropLast(1)
        return completed.mapNotNull { raw ->
        val parsed = parser.parse(raw)
        val food = engine.search(parsed.foodPhrase).firstOrNull()?.food ?: return@mapNotNull null
        val amount = parsed.amount ?: food.defaultAmount
        val unit = parsed.unit ?: food.defaultUnit
        val calculation = calculator.calculate(food, amount, unit)
        QuickAddPreview(food, amount, unit, calculation).takeIf { calculation.supported }
        }
    }
    fun chooseQuickSuggestion(food: FoodRecord) {
        val active = quickAddActiveSegment()
        val parsed = parser.parse(active)
        val amount = parsed.amount?.pretty()
        val unit = parsed.unit?.displayLabel()
        val replacement = listOfNotNull(amount, unit, food.name).joinToString(" ")
        val prefix = input.substringBeforeLast(',').trimEnd()
        onInput(if (prefix.isBlank()) replacement else "$prefix, $replacement")
    }
    fun submitLibraryInput() {
        val raw = input.trim()
        if (raw.isBlank() || !ready || busy) return
        val markers = mealMarkers(raw)
        if (markers.size > 1) { notice = "Choose one meal marker."; return }
        val target = markers.singleOrNull()
        val effectiveTarget = target ?: slot
        val cleaned = target?.let { removeMealMarker(raw, it) } ?: raw
        val segments = libraryInputSegments(cleaned)
        if (segments.isEmpty() || segments.all { it.amount == null }) {
            onLibraryQuery(segments.joinToString(", ") { it.foodPhrase })
            input = ""
            return
        }
        if (segments.any { it.amount == null }) {
            if (effectiveTarget == null) {
                libraryPendingBatch = emptyList()
                notice = "Choose a meal before adding a mixed batch with an unquantified food."
                mealPickerOpen = true
                return
            }
            submitLibraryMixedInput(cleaned, effectiveTarget)
            return
        }
        viewModelScope.launch {
            val drafts = segments.mapNotNull { segment ->
                engine.search(segment.foodPhrase).firstOrNull()?.food?.let { food ->
                    LibraryLogDraft(food, segment.amount!!, segment.unit ?: food.defaultUnit)
                }
            }
            withContext(Dispatchers.Main) {
                if (drafts.size != segments.size) {
                    onLibraryQuery(segments.joinToString(", ") { it.foodPhrase })
                    notice = "One or more foods are not in your local library."
                    return@withContext
                }
                if (effectiveTarget != null) logLibraryBatch(drafts, effectiveTarget) else {
                    libraryPendingBatch = drafts
                    libraryBatchMealPickerOpen = true
                }
                input = ""
            }
        }
    }

    private fun submitLibraryMixedInput(cleaned: String, target: MealSlot) {
        val capturedInput = input
        val capturedDayKey = selectedDayKey
        busy = true
        viewModelScope.launch {
            runCatching { engine.save(cleaned, mealSlot = target, dayKey = capturedDayKey) }
                .onSuccess { saved ->
                    refresh()
                    if (input == capturedInput) input = saved.unresolvedSegments.joinToString(", ")
                    unknownQueue = saved.unresolvedSegments
                    unknownQueueTotal = saved.unresolvedSegments.size
                    unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }
                    resolutionContinuation = saved.unresolvedSegments.isNotEmpty()
                    resolverOpen = saved.unresolvedSegments.isNotEmpty()
                    mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
                    feedbackIds = saved.entries.filter { it.approximateConversion }.mapTo(mutableSetOf()) { it.id }
                    feedbackUntil = if (feedbackIds.isEmpty()) 0L else System.currentTimeMillis() + 30_000L
                    loggedBatchIds = saved.entries.map { it.id }
                    loggedBatchMessage = "${saved.entries.size} food${if (saved.entries.size == 1) "" else "s"} logged to ${target.label()}"
                }
                .onFailure { notice = it.message ?: "Could not save this batch." }
                .also { busy = false }
        }
    }
    fun beginQuickEdit(entry: MealEntry) {
        if (busy) return
        editReturnToDaily = slot == null
        selectMeal(entry.mealSlot)
        editSession += 1
        editingEntryId = entry.id
        editingFoodId = entry.foodId
        editingFoodName = entry.foodName
        entryEditMode = EntryEditMode.QUICK
        input = "${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()}"
        mealDraftError = null
        editInvalid = false
        quickEditHintVisible = true
        val hintSession = ++quickHintSession
        viewModelScope.launch {
            delay(2_500)
            if (quickHintSession == hintSession && entryEditMode == EntryEditMode.QUICK) quickEditHintVisible = false
        }
    }
    fun beginFullEdit(entry: MealEntry) {
        if (busy) return
        editReturnToDaily = slot == null
        selectMeal(entry.mealSlot)
        editSession += 1
        editingEntryId = entry.id
        editingFoodId = entry.foodId
        editingFoodName = entry.foodName
        editingFoodPickerOpen = false
        entryEditMode = EntryEditMode.FULL
        input = "${entry.enteredAmount.pretty()} ${entry.enteredUnit.displayLabel()} ${entry.foodName}"
        mealDraftError = null
        editInvalid = false
        quickHintSession += 1
        quickEditHintVisible = false
    }
    fun cancelEdit() {
        if (entryEditMode == EntryEditMode.FULL && fullSaveJob?.isActive == true) return
        fullSaveJob?.cancel()
        fullSaveJob = null
        busy = false
        editSession += 1
        entryEditMode = EntryEditMode.NONE
        editingEntryId = null
        editingFoodName = null
        editingFoodId = null
        editingFoodPickerOpen = false
        input = ""
        editInvalid = false
        quickHintSession += 1
        quickEditHintVisible = false
        if (editReturnToDaily) selectMeal(null)
        editReturnToDaily = false
        mealDraftError = null
    }
    private fun markEditInvalid(message: String) {
        mealDraftError = message
        editInvalid = true
        val signal = editInvalidSignal + 1
        editInvalidSignal = signal
        viewModelScope.launch {
            delay(1_400)
            if (editInvalidSignal == signal) editInvalid = false
        }
    }
    fun submit() {
        if (!ready || busy) return
        if (entryEditMode != EntryEditMode.NONE) {
            if (input.isBlank()) {
                cancelEdit()
                return
            }
            val target = slot ?: return
            submitToMeal(target)
            return
        }
        if (input.isBlank()) { mealDraftError = "Add at least one food."; return }
        amountlessDailySearch(input)?.let { intent ->
            openFocusedLibrary(intent.meal, intent.phrase)
            input = ""
            return
        }
        val target = slot ?: run {
            val markers = mealMarkers(input)
            when {
                markers.isEmpty() -> { mealPickerOpen = true; mealDraftError = null; return }
                markers.size > 1 -> { mealDraftError = "Choose one meal marker, or remove the extra markers."; return }
                else -> markers.single()
            }
        }
        submitToMeal(target)
    }

    fun chooseMeal(target: MealSlot) {
        mealPickerOpen = false
        submitToMeal(target)
    }

    fun dismissMealPicker() { mealPickerOpen = false }

    private fun submitToMeal(target: MealSlot) {
        if (!ready || busy) return
        val captured = input
        val editing = editingEntryId
        val mode = entryEditMode
        if (editing != null && mode != EntryEditMode.NONE) {
            val session = editSession
            busy = true
            viewModelScope.launch {
                runCatching {
                    if (mode == EntryEditMode.QUICK) engine.replaceEntryAmountUnit(editing, input)
                    else engine.replaceEntryFromText(editing, input)
                }.onSuccess { result ->
                    if (entryEditMode != mode || editingEntryId != editing || editSession != session) return@onSuccess
                    when (result) {
                        is ReplacementResult.Success -> { refresh(); cancelEdit() }
                        is ReplacementResult.Failure -> markEditInvalid(result.reason)
                    }
                }.onFailure {
                    if (entryEditMode == mode && editingEntryId == editing && editSession == session) {
                        markEditInvalid(it.message ?: "Could not edit this entry.")
                    }
                }
                    .also { busy = false }
            }
            return
        }
        val cleaned = removeMealMarker(input, target)
        if (cleaned.isBlank()) { mealDraftError = "Add at least one food."; return }
        busy = true
        viewModelScope.launch {
            runCatching { engine.save(cleaned, mealSlot = target, dayKey = selectedDayKey) }.onSuccess { saved ->
                refresh(); if (input == captured) input = saved.unresolvedSegments.joinToString(", "); unknownQueue = saved.unresolvedSegments; unknownQueueTotal = saved.unresolvedSegments.size; unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }; resolutionContinuation = saved.unresolvedSegments.isNotEmpty(); resolverOpen = saved.unresolvedSegments.isNotEmpty(); mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
                feedbackIds = saved.entries.filter { it.approximateConversion }.mapTo(mutableSetOf()) { it.id }; feedbackUntil = if (feedbackIds.isEmpty()) 0L else System.currentTimeMillis() + 30_000L
                loggedBatchIds = saved.entries.map { it.id }
                loggedBatchMessage = "${saved.entries.size} food${if (saved.entries.size == 1) "" else "s"} logged to ${target.label()}"
            }.onFailure { mealDraftError = it.message ?: "Could not save this meal." }.also { busy = false }
        }
    }
    private fun mealMarkers(value: String): List<MealSlot> = mealMarkerSlots(value)
    private fun removeMealMarker(value: String, target: MealSlot): String = removeMealMarkerToken(value, target)
    fun delete(entry: MealEntry) { if (!ready || busy) return; busy = true; viewModelScope.launch { runCatching { engine.deleteEntry(entry.id) }.onSuccess { undoEntry = entry; refresh() }.also { busy = false } } }
    fun undo() { val e = undoEntry ?: return; if (!ready || busy) return; busy = true; viewModelScope.launch { runCatching { engine.restoreEntry(e) }.onSuccess { undoEntry = null; refresh() }.also { busy = false } } }
    fun undoLoggedBatch() {
        if (!ready || busy || loggedBatchIds.isEmpty()) return
        val ids = loggedBatchIds.toList()
        busy = true
        viewModelScope.launch {
            runCatching { ids.forEach { engine.deleteEntry(it) } }
                .onSuccess { loggedBatchIds = emptyList(); loggedBatchMessage = null; refresh() }
                .also { busy = false }
        }
    }
    fun openCustom(name: String = "", brand: String = "", barcode: String = "", fromResolver: Boolean = false, fromOff: Boolean = false) {
        if (!fromResolver && unknownQueue.isNotEmpty()) abandonUnknownResolution()
        resolutionContinuation = fromResolver
        rescanningBarcode = false
        editingLibraryFoodId = null
        draft.reset()
        draft.name = name
        draft.brand = brand
        draft.barcode = normalizeBarcode(barcode).orEmpty()
        draft.barcodeFromScan = draft.barcode.isNotEmpty()
        draft.fromOff = fromOff
        customOpen = true
        searchOpen = false
        resolverOpen = false
    }
    fun closeCustom() {
        customOpen = false; editingLibraryFoodId = null; rescanningBarcode = false; draft.reset()
        if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false
    }
    fun commitDraftServing() { draft.commitServing() }
    fun beginBarcodeRescan() { rescanningBarcode = true; beginScan() }
    fun openLibraryEdit(food: FoodRecord) {
        rescanningBarcode = false
        editingLibraryFoodId = food.id
        draft.load(food)
        customOpen = true; selectedLibraryFood = null
    }
    fun closeResolver() { abandonUnknownResolution() }
    fun dismissNotice() { notice = null }
    fun reviewContribution() {
        val draft = pendingContribution ?: return
        if (contributionPreflightBusy) return
        if (contributionPreflightComplete) contributionReviewOpen = true
        else startContributionPreflight(draft)
    }
    fun dismissContributionReview() { contributionReviewOpen = false }
    fun confirmContribution() {
        val draft = pendingContribution
        if (draft != null && draft.createNewProduct && draft.languageCode.trim().lowercase() !in setOf("en", "mk")) {
            notice = "Choose en (English) or mk (Macedonian) for the new product."
            return
        }
        if (draft != null && draft.createNewProduct && draft.basisUnit == MeasureUnit.MILLILITER &&
            (draft.packageQuantityMl == null || !draft.packageQuantityMl.isFinite() || draft.packageQuantityMl <= 0.0)
        ) {
            notice = "Enter a positive package quantity in ml before continuing."
            return
        }
        if (draft != null) {
            contributionReviewOpen = false
            contributionAuthOpen = true
        }
    }
    fun dismissContributionAuth() { contributionAuthOpen = false; contributionPassword = "" }
    fun dismissContributionFailure() { contributionFailure = null; pendingContribution = null }
    fun retryContribution() { contributionFailure = null; contributionAuthOpen = true }
    fun onContributionUsername(v: String) { contributionUsername = v }
    fun onContributionPassword(v: String) { contributionPassword = v }
    fun onContributionLanguage(v: String) {
        contributionLanguageCode = v
        pendingContribution = pendingContribution?.copy(languageCode = v.trim().lowercase())
    }
    fun onContributionPackageQuantityMl(v: String) {
        contributionPackageQuantityMl = v
        pendingContribution = pendingContribution?.copy(packageQuantityMl = v.toDoubleOrNull())
    }
    fun submitContribution() {
        val draft = pendingContribution ?: return
        if (contributionBusy) return
        if (contributionUsername.isBlank() || contributionPassword.isBlank()) {
            contributionFailure = "Enter your Open Food Facts username and password to submit."
            contributionAuthOpen = false
            return
        }
        val username = contributionUsername.trim()
        val password = contributionPassword
        contributionPassword = ""
        contributionAuthOpen = false
        contributionBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = off.contribute(draft, username, password)
            withContext(Dispatchers.Main) {
                when (result) {
                    OffContributionResult.Success -> {
                        pendingContribution = null
                        contributionFailure = null
                        notice = "Contribution sent to Open Food Facts."
                    }
                    OffContributionResult.AuthFailed -> contributionFailure = "Open Food Facts could not authenticate that account. Check the username and password, then retry."
                    is OffContributionResult.Ineligible -> contributionFailure = result.message
                    is OffContributionResult.Rejected -> contributionFailure = result.message
                    is OffContributionResult.PreflightChanged -> contributionFailure = result.message
                    OffContributionResult.Unavailable -> contributionFailure = "Open Food Facts was unavailable. Retry when you have a connection."
                    is OffContributionResult.Error -> contributionFailure = result.message
                }
                contributionBusy = false
            }
        }
    }
    fun clearSelectedProduct() { selectedProduct = null; selectedProductFromScan = false; if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
    fun onProductAmount(v: String) { productAmount = v }
    fun onProductUnit(v: String) { productUnit = v }
    fun onProductName(v: String) { productName = v }
    fun onProductCalories(v: String) { productCalories = v }
    fun onProductProtein(v: String) { productProtein = v }
    fun onProductCarbs(v: String) { productCarbs = v }
    fun onProductFat(v: String) { productFat = v }
    fun currentUnknownName(): String = unknownQueue.firstOrNull()?.let { parser.parse(it).foodPhrase.trim().ifBlank { it.trim() } }.orEmpty()
    fun openSearch(prefill: String? = null, fromResolver: Boolean = false) {
        offSearchJob?.cancel()
        offSearchGeneration++
        if (!fromResolver && unknownQueue.isNotEmpty()) abandonUnknownResolution()
        resolutionContinuation = fromResolver
        searchOpen = true
        customOpen = false
        resolverOpen = false
        searchQuery = prefill ?: ""
        offResults = emptyList()
        offSearchMessage = null
        offSearchUnavailable = false
    }
    fun closeSearch() { offSearchJob?.cancel(); offSearchGeneration++; searchBusy = false; searchOpen = false; if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
    fun onSearch(v: String) { offSearchJob?.cancel(); offSearchGeneration++; searchBusy = false; searchQuery = v; offResults = emptyList(); offSearchMessage = null; offSearchUnavailable = false }
    fun onResolverQuery(v: String) { resolverQuery = v; resolverResults = if (v.isBlank()) emptyList() else engine.search(v) }
    private fun advanceUnknownQueue() {
        unknownQueue = unknownQueue.drop(1)
        input = unknownQueue.joinToString(", ")
        resolverQuery = ""
        resolverResults = emptyList()
        resolverOpen = unknownQueue.isNotEmpty()
        if (unknownQueue.isEmpty()) {
            unknownQueueTarget = null
            resolutionContinuation = false
        }
    }
    fun resolveUnknown(food: FoodRecord) {
        val raw = unknownQueue.firstOrNull() ?: return
        val target = activeResolutionTarget(resolutionContinuation, unknownQueue, unknownQueueTarget) ?: return
        val parsed = parser.parse(raw)
        val amount = parsed.amount ?: food.defaultAmount
        val unit = parsed.unit ?: food.defaultUnit
        if (!ready || busy) return
        val capturedDayKey = selectedDayKey
        busy = true
        viewModelScope.launch {
            runCatching { engine.cacheFoodAndLog(food, amount, unit, target, capturedDayKey) }
                .onSuccess { advanceUnknownQueue(); refresh() }
                .onFailure { notice = it.message ?: "Could not resolve this food." }
                .also { busy = false }
        }
    }
    /** Leaves the current unresolved food unlogged and moves to the next one. */
    fun skipCurrentUnknown() { if (unknownQueue.isNotEmpty()) advanceUnknownQueue() }
    /** Names of the foods the batch that produced this queue already saved, for the resolve header. */
    fun savedBatchNames(): List<String> = loggedBatchIds.mapNotNull { id -> entries.firstOrNull { it.id == id }?.foodName }
    fun feedbackOpen(entry: MealEntry): Boolean = entry.id in feedbackIds && System.currentTimeMillis() < feedbackUntil
    fun feedbackCount(): Int = feedbackIds.count { id -> entries.any { it.id == id } && System.currentTimeMillis() < feedbackUntil }
    fun alternatives(entry: MealEntry): List<RankedFood> = if (feedbackOpen(entry)) engine.alternativesFor(entry) else emptyList()
    fun confirm(entry: MealEntry) { if (!ready || busy || !feedbackOpen(entry)) return; busy = true; viewModelScope.launch { runCatching { engine.reinforce(entry.foodId) }.onSuccess { feedbackIds = feedbackIds - entry.id }.also { busy = false } } }
    fun chooseAlternative(entry: MealEntry, food: FoodRecord) { if (!ready || busy || !feedbackOpen(entry)) return; busy = true; viewModelScope.launch { runCatching { engine.replaceEntry(entry.id, food.id); engine.reinforce(food.id) }.onSuccess { feedbackIds = feedbackIds - entry.id; refresh() }.also { busy = false } } }
    fun expireFeedback() { feedbackUntil = 0L; feedbackIds = emptySet() }
    fun holdFeedback() { if (resolverOpen && feedbackUntil > 0L) feedbackUntil += 1000L }
    fun submitSearch() {
        if (!ready || searchQuery.isBlank() || searchBusy) return
        val captured = searchQuery
        val generation = ++offSearchGeneration
        offSearchJob?.cancel()
        searchBusy = true
        offSearchMessage = null
        offSearchUnavailable = false
        offSearchJob = viewModelScope.launch(Dispatchers.IO) {
            val result = off.search(captured)
            withContext(Dispatchers.Main) {
                if (shouldApplyOffSearchResult(generation, offSearchGeneration, captured, searchQuery, searchOpen)) {
                    when (result) {
                        is OffSearchResult.Success -> {
                            offResults = result.foods
                            offSearchMessage = if (result.foods.isEmpty()) "No usable result for \"$captured\"." else null
                        }
                        OffSearchResult.Unavailable -> {
                            offSearchUnavailable = true
                            offResults = emptyList()
                            offSearchMessage = "Open Food Facts unavailable; try again or add manually."
                        }
                        is OffSearchResult.Error -> {
                            offResults = emptyList()
                            offSearchMessage = result.message
                        }
                    }
                }
                if (generation == offSearchGeneration) searchBusy = false
            }
        }
    }
    fun chooseProduct(food: FoodRecord, fromScan: Boolean = false) {
        selectedProduct = food
        selectedProductFromScan = fromScan
        searchOpen = false
        val prefill = unknownQueue.firstOrNull()
            ?.takeIf { resolutionContinuation }
            ?.let { productPrefill(it, food) }
            ?: ProductPrefill(100.0, food.nutrition.unit)
        productAmount = prefill.amount.toString()
        productUnit = prefill.unit.displayLabel()
        productName = food.name
        productCalories = food.nutrition.values.calories.toString()
        productProtein = food.nutrition.values.protein?.toString().orEmpty()
        productCarbs = food.nutrition.values.carbohydrates?.toString().orEmpty()
        productFat = food.nutrition.values.fat?.toString().orEmpty()
    }
    fun productCalculation(food: FoodRecord): Calculation? = productDraft(food)?.calculation
    /** Logs the reviewed product to [target] on the selected day; Add in the review sheet calls this. */
    fun addSelected(chosen: MealSlot) {
        val food = selectedProduct ?: return
        if (!ready || busy) return
        val draft = productDraft(food) ?: run {
            notice = "Enter a positive amount, a supported unit, and valid calories."
            return
        }
        val plan = reviewLogPlan(resolutionContinuation, unknownQueue.isNotEmpty(), unknownQueueTarget, chosen)
        val target = plan.meal
        val resolvesQueue = plan.advancesQueue
        val fromScan = selectedProductFromScan
        val capturedDayKey = selectedDayKey
        busy = true
        viewModelScope.launch {
            runCatching { engine.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, capturedDayKey) }
                .onSuccess {
                    selectedProduct = null
                    pendingContribution = contributionDraftAfterLocalSave(draft.food, fromScan)
                    selectedProductFromScan = false
                    if (resolvesQueue) advanceUnknownQueue() else resolutionContinuation = false
                    refresh(); refreshLibrary()
                    notice = "Added to ${target.label()}"
                }
                .onFailure { notice = it.message ?: "This amount cannot be logged." }
                .also { busy = false }
        }
    }

    fun saveSelectedToLibrary() {
        val food = selectedProduct ?: return
        if (!canSaveProductToLibrary(slot, resolutionContinuation, unknownQueue.isNotEmpty())) return
        val draft = productDraft(food) ?: run { notice = "Enter a positive amount, a supported unit, and valid calories."; return }
        if (!ready || busy) return
        busy = true
        viewModelScope.launch {
            runCatching { engine.updateFood(draft.food) }
                .onSuccess {
                    selectedProduct = null
                    selectedProductFromScan = false
                    refresh()
                    refreshLibrary()
                    notice = "Saved ${draft.food.name} to your food library."
                }
                .onFailure { notice = it.message ?: "Could not save this food." }
                .also { busy = false }
        }
    }

    private fun productDraft(food: FoodRecord): ProductDraft? {
        val amount = productAmount.toDoubleOrNull()
        val unit = parseProductUnit(productUnit)
        val kcal = productCalories.toDoubleOrNull()
        if (amount == null || !amount.isFinite() || amount <= 0.0 || unit == null ||
            kcal == null || !kcal.isFinite() || kcal < 0.0
        ) return null
        val draftFood = food.copy(
            name = productName.trim().ifBlank { food.name },
            nutrition = NutritionBasis(
                food.nutrition.amount,
                food.nutrition.unit,
                NutritionValues(
                    kcal,
                    productProtein.toDoubleOrNull(),
                    productCarbs.toDoubleOrNull(),
                    productFat.toDoubleOrNull()
                )
            )
        )
        val calculation = calculator.calculate(draftFood, amount, unit)
        return ProductDraft(draftFood, amount, unit, calculation).takeIf { calculation.supported }
    }
    fun beginScan() { barcodeRequestToken++; barcodeOriginDay = selectedDayKey; barcodeOriginMeal = slot; scannerUnavailableState = false; scannerOpen = true }
    fun endScan() { scannerOpen = false; scannerUnavailableState = false; rescanningBarcode = false }
    /** Keep the scanner route open so manual barcode entry remains available after camera failure. */
    fun scannerUnavailable() {
        val transition = scannerUnavailableTransition()
        scannerUnavailableState = transition.unavailable
        scannerOpen = transition.scannerOpen
    }
    private fun barcodeRequestStillTargets(token: Long, day: String, meal: MealSlot?): Boolean = token == barcodeRequestToken && day == selectedDayKey && meal == slot
    fun onBarcode(code: String) {
        scannerOpen = false
        scannerUnavailableState = false
        if (rescanningBarcode) {
            rescanningBarcode = false
            val scanned = normalizeBarcode(code)
            if (scanned == null) notice = "Not a product barcode." else { draft.barcode = scanned; draft.barcodeFromScan = true }
            return
        }
        val barcode = code.trim()
        val token = barcodeRequestToken
        val originDay = barcodeOriginDay
        val originMeal = barcodeOriginMeal
        viewModelScope.launch(Dispatchers.IO) {
            val local = engine.foodByBarcode(barcode)
            if (local != null) {
                withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) chooseProduct(local, fromScan = true) }
                return@launch
            }
            when (val result = off.barcode(barcode)) {
                is OffResult.Found -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) chooseProduct(result.food, fromScan = true) }
                is OffResult.Ineligible -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { notice = "Nutrition is incomplete. Review it as a personal food."; openCustom(result.name.orEmpty(), result.brand.orEmpty(), result.barcode, fromOff = true) } }
                OffResult.Missing -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { notice = "Product not found. Add it manually with barcode $barcode"; openCustom(barcode = barcode) } }
                OffResult.Unavailable -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { notice = "No connection. Add the product manually."; openCustom(barcode = barcode) } }
                is OffResult.Error -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) notice = result.message }
            }
        }
    }
    fun saveCustom() {
        val name = draft.name; val brand = draft.brand; val barcode = draft.barcode.trim()
        val aliasList = draft.allAliases(); val servingList = draft.servings
        val resolved = resolveNutrition(draft.nutritionInput())
        val kcal = resolved.calories; val proteinValue = resolved.protein; val carbsValue = resolved.carbohydrates; val fatValue = resolved.fat
        if (name.isBlank() || kcal == null) { notice = "Name and calories are required."; return }
        val editingId = editingLibraryFoodId
        val originalBarcode = editingId?.let { id -> foods.firstOrNull { it.id == id }?.barcode?.trim() }
        val barcodeError = if (barcode == originalBarcode) null else validateBarcodeInput(barcode, foods, editingId)
        if (barcodeError != null) { notice = barcodeError; return }
        val basisAmount = resolved.basisAmount
        val basisUnit = resolved.basisUnit
        val nutrition = NutritionValues(kcal, proteinValue, carbsValue, fatValue)
        if (!nutrition.calories.isFinite() || nutrition.calories < 0.0 || nutrition.calories > 10000.0 || listOf(nutrition.protein, nutrition.carbohydrates, nutrition.fat).any { it != null && (!it.isFinite() || it < 0.0 || it > 1000.0) }) { notice = "Nutrition values must be finite and nonnegative."; return }
        if (!ready || busy || !basisAmount.isFinite() || basisAmount <= 0.0) { notice = "Enter a positive nutrition basis."; return }
        val resolutionPlan = resolverLogPlan(resolutionContinuation, unknownQueue.isNotEmpty(), unknownQueueTarget, slot)
        val saveTarget = resolutionPlan?.meal
        val resolvesQueue = resolutionPlan?.advancesQueue == true
        if (resolutionContinuation && unknownQueue.isNotEmpty() && saveTarget == null) {
            notice = "Choose a meal before saving this food."
            return
        }
        val fromOff = draft.fromOff
        val normalizedBarcode = normalizeBarcode(barcode)
        val contribution = if (fromOff) normalizedBarcode?.let {
            OffContributionDraft(
                barcode = it, name = name.trim(), brand = brand.trim().ifBlank { null },
                basisAmount = basisAmount, basisUnit = basisUnit, nutrition = nutrition
            )
        } else null
        val offSource = fromOff.takeIf { it }?.let { "Open Food Facts" }
        val aliasText = aliasList.joinToString(", ")
        val capturedDayKey = selectedDayKey
        busy = true; viewModelScope.launch { runCatching {
            if (editingId != null) {
                val original = foods.firstOrNull { it.id == editingId } ?: error("That personal food is no longer available.")
                engine.updateFood(editedCustomFoodRecord(original, name, brand, barcode, aliasText, nutrition, basisAmount, basisUnit, aliases = aliasList, servings = servingList))
                original
            } else {
                val target = saveTarget
                if (target != null) {
                    val entry = engine.saveCustomAndLog(name, brand, barcode, aliasText, nutrition, basisAmount, basisUnit, target, basisAmount, basisUnit, dayKey = capturedDayKey, sourceName = offSource, sourceDescription = "Saved from an incomplete Open Food Facts barcode result")
                    engine.foodById(entry.foodId) ?: error("Saved food could not be reloaded.")
                } else engine.saveCustomFood(name, brand, barcode, aliasText, nutrition, basisAmount, basisUnit, sourceName = offSource, sourceDescription = "Saved from an incomplete Open Food Facts barcode result")
            }
        }.onSuccess {
            refresh()
            customOpen = false
            editingLibraryFoodId = null
            draft.reset()
            pendingContribution = contribution?.copy(
                languageCode = contributionLanguageFor(name),
                packageQuantityMl = null
            )
            contributionPreflightComplete = false
            refreshLibrary()
            if (resolvesQueue && unknownQueue.isNotEmpty()) advanceUnknownQueue() else resolutionContinuation = false
            notice = if (editingId != null) "Personal food updated for future logs." else if (saveTarget != null) "Saved to ${saveTarget.label()} and your food library." else "Saved to your food library."
        }.onFailure { notice = it.message ?: "Could not save this food." }.also { busy = false } }
    }
}

internal data class ProductPrefill(val amount: Double, val unit: MeasureUnit)

internal fun libraryRecencyOrder(foods: List<FoodRecord>, entries: List<MealEntry>): List<FoodRecord> {
    val latestByFood = entries.groupBy { it.foodId }.mapValues { (_, values) -> values.maxOfOrNull { it.savedAtEpochMs } ?: Long.MIN_VALUE }
    return foods.sortedWith(compareByDescending<FoodRecord> { latestByFood[it.id] ?: Long.MIN_VALUE }.thenBy { it.name.lowercase(Locale.ROOT) })
}

/**
 * Applies form edits to a stored food. [aliases] and [servings], when given, replace the stored lists
 * outright (so the last alias or serving can be removed); otherwise [aliasText] keeps its older
 * "blank leaves aliases alone" meaning and imported servings are preserved.
 * `sourceId` stays untouched for non-custom foods so an Open Food Facts food keeps its original code.
 */
internal fun editedCustomFoodRecord(
    original: FoodRecord,
    name: String,
    brand: String,
    barcode: String,
    aliasText: String,
    nutrition: NutritionValues,
    basisAmount: Double,
    basisUnit: MeasureUnit,
    aliases: List<String>? = null,
    servings: List<com.iron.breakfast.domain.ServingConversion>? = null,
): FoodRecord {
    val editedAliases = aliases ?: aliasText.trim().takeIf { it.isNotBlank() }
        ?.split(',')?.map(String::trim)?.filter(String::isNotBlank)
        ?: original.aliases
    val isCustom = original.origin == com.iron.breakfast.domain.FoodOrigin.CUSTOM
    val preserveImportedServings = !isCustom && original.nutrition.unit == basisUnit
    val basisConversion = com.iron.breakfast.domain.ServingConversion(basisUnit, basisAmount, "custom basis")
    val extraServings = servings.orEmpty().filterNot { it.unit == basisUnit && it.label.isNullOrBlank() }
    val importedBasis = original.conversions.filter { it.unit == basisUnit && it.label.isNullOrBlank() }
    val conversions = when {
        servings == null -> if (preserveImportedServings) original.conversions else listOf(basisConversion)
        preserveImportedServings -> importedBasis + extraServings
        else -> listOf(basisConversion) + extraServings
    }
    val keepDefault = preserveImportedServings && (servings == null || original.defaultUnit == basisUnit || extraServings.any { it.unit == original.defaultUnit })
    return original.copy(
        name = name.trim(), brand = brand.trim().ifBlank { null }, barcode = barcode.trim().ifBlank { null },
        sourceId = if (isCustom) barcode.trim().ifBlank { null } else original.sourceId,
        aliases = editedAliases,
        nutrition = NutritionBasis(basisAmount, basisUnit, nutrition),
        conversions = conversions,
        defaultAmount = if (keepDefault) original.defaultAmount else basisAmount,
        defaultUnit = if (keepDefault) original.defaultUnit else basisUnit,
        personalOverride = true
    )
}

private fun contributionLanguageFor(name: String): String =
    if (name.any { it in '\u0400'..'\u04ff' }) "mk" else "en"

internal fun productNutritionBasisLabel(food: FoodRecord): String =
    "Calories per ${food.nutrition.amount.pretty()} ${food.nutrition.unit.label}"

internal fun productPrefill(raw: String, food: FoodRecord): ProductPrefill {
    val parsed = ShorthandParser().parse(raw)
    return ProductPrefill(
        amount = parsed.amount ?: 100.0,
        unit = parsed.unit ?: food.nutrition.unit
    )
}

internal fun parseProductUnit(value: String): MeasureUnit? = value.trim().toMeasureUnit()

internal fun contributionDraftAfterLocalSave(food: FoodRecord, fromScan: Boolean): OffContributionDraft? =
    if (fromScan) OffContributionDraft.fromFood(food) else null

private data class ProductDraft(
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val calculation: Calculation
)

internal data class LibraryLogDraft(
    val food: FoodRecord,
    val amount: Double,
    val unit: MeasureUnit,
    val target: MealSlot? = null
)

internal fun libraryLogDraft(food: FoodRecord, target: MealSlot, amount: Double, unit: MeasureUnit): LibraryLogDraft =
    LibraryLogDraft(food, amount, unit, target)

internal data class LibraryInputSegment(
    val raw: String,
    val foodPhrase: String,
    val amount: Double?,
    val unit: MeasureUnit?
)

internal fun libraryInputSegments(value: String): List<LibraryInputSegment> {
    val parser = ShorthandParser()
    return parser.split(value).map { raw ->
        val parsed = parser.parse(raw)
        LibraryInputSegment(raw, parsed.foodPhrase, parsed.amount, parsed.unit)
    }
}

internal fun defaultServingCalculation(food: FoodRecord): Calculation =
    NutritionCalculator().calculate(food, food.defaultAmount, food.defaultUnit)

internal fun mealMarkerSlots(value: String): List<MealSlot> = ShorthandParser().split(value).mapNotNull { token ->
    when (token.trim().lowercase(Locale.ROOT)) {
        "breakfast" -> MealSlot.BREAKFAST
        "lunch" -> MealSlot.LUNCH
        "dinner" -> MealSlot.DINNER
        "snacks", "snack" -> MealSlot.SNACKS
        else -> null
    }
}

internal data class AmountlessDailySearch(val meal: MealSlot?, val phrase: String)

internal fun amountlessDailySearch(value: String): AmountlessDailySearch? {
    val parser = ShorthandParser()
    val markers = mealMarkerSlots(value)
    if (markers.size > 1) return null
    val meal = markers.singleOrNull()
    val cleaned = meal?.let { removeMealMarkerToken(value, it) } ?: value
    val segments = parser.split(cleaned)
    if (segments.isEmpty() || segments.any { parser.parse(it).amount != null }) return null
    val phrase = segments.joinToString(", ") { parser.parse(it).foodPhrase }.trim()
    return phrase.takeIf { it.isNotBlank() }?.let { AmountlessDailySearch(meal, it) }
}

internal fun removeMealMarkerToken(value: String, target: MealSlot): String = ShorthandParser().split(value)
    .filterNot { mealMarkerSlots(it).singleOrNull() == target }
    .joinToString(", ")

internal fun MealSlot.label() = name.lowercase().replaceFirstChar { it.titlecase() }
internal fun Double.pretty() = if (this % 1 == 0.0) "%.0f".format(Locale.ROOT, this) else "%.1f".format(Locale.ROOT, this)
internal fun Double?.macro() = this?.let { "${it.pretty()} g" } ?: "—"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronApp(vm: BreakfastViewModel = viewModel()) {
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); vm.reloadToday() } }
    LaunchedEffect(vm.feedbackUntil, vm.resolverOpen) { while (vm.feedbackUntil > 0L) { kotlinx.coroutines.delay(1000); if (vm.resolverOpen) vm.holdFeedback() else if (System.currentTimeMillis() >= vm.feedbackUntil) { vm.expireFeedback(); break } } }
    if (vm.scannerOpen) { ScannerScreen(vm); return }
    val scanReviewBackdropVisible = vm.selectedProduct != null && vm.selectedProductFromScan
    if (scanReviewBackdropVisible) ScanReviewBackdrop(vm::clearSelectedProduct)
    else {
    if (vm.selectedEntry != null) {
        EntryDetailScreen(vm, vm.selectedEntry!!)
        return
    }
    Scaffold(
        containerColor = com.iron.breakfast.ui.theme.LocalReceiptColors.current.paper,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        bottomBar = {
            // The tray rides above the keyboard and clear of a side display cutout.
            Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).imePadding()) {
                when {
                    vm.entryEditMode != EntryEditMode.NONE -> Unit
                    vm.libraryOpen && !vm.libraryArchivedOnly -> LibraryComposer(vm)
                    vm.slot == null -> DailyComposer(vm)
                    else -> MealComposer(vm, vm.slot!!)
                }
            }
        }
    ) { pad ->
        when {
            vm.libraryOpen -> LibraryScreen(vm, Modifier.padding(pad))
            vm.slot == null -> DailyScreen(vm, Modifier.padding(pad))
            else -> MealScreen(vm, Modifier.padding(pad))
        }
    }
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
    if (vm.libraryOpen && vm.selectedLibraryFood == null && !vm.customOpen && !vm.searchOpen && !vm.resolverOpen && vm.selectedProduct == null && vm.entryEditMode == EntryEditMode.NONE) {
        // Archived foods is a child destination of Library. Keep system/predictive
        // Back aligned with the top-bar arrow before leaving the Library flow.
        BackHandler { if (vm.libraryArchivedOnly) vm.openLibrary() else vm.closeLibrary() }
    } else if (vm.slot != null && vm.entryEditMode == EntryEditMode.NONE && !vm.customOpen && !vm.searchOpen && !vm.resolverOpen && vm.selectedProduct == null && vm.selectedEntry == null) {
        BackHandler { vm.closeMeal() }
    }
    if (vm.mealPickerOpen) MealPickerDialog(vm)
    if (vm.libraryMealPickerFood != null) LibraryMealPickerDialog(vm, vm.libraryMealPickerFood!!)
    if (vm.libraryBatchMealPickerOpen) LibraryBatchMealPickerDialog(vm)
    if (vm.customOpen) {
        if (vm.editingLibraryFoodId != null) LibraryFoodEditScreen(vm) else CreateFoodScreen(vm)
    }
    if (vm.libraryNoMatch()) LibraryNoMatchScreen(vm)
    if (vm.searchOpen) OffSearchScreen(vm)
    if (vm.resolverOpen && !scanReviewBackdropVisible) ResolveUnknownScreen(vm)
    vm.selectedProduct?.let { ProductReviewSheet(vm, it) }
    if (vm.contributionReviewOpen) vm.pendingContribution?.let { ContributionReviewDialog(vm, it) }
    if (vm.contributionAuthOpen) ContributionAuthDialog(vm)
    vm.contributionFailure?.let { ContributionFailureDialog(vm, it) }
    vm.notice?.let { message -> AlertDialog(onDismissRequest = vm::dismissNotice, confirmButton = { TextButton(vm::dismissNotice) { Text("OK") } }, text = { Text(message) }) }
    vm.initializationError?.let { message -> AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Storage unavailable") }, text = { Text(message) }) }
}




@Composable private fun MealPickerDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissMealPicker,
        title = { Text("Which meal?") },
        text = {
            Column {
                Text("Choose where to add this batch.")
                MealSlot.entries.forEach { target ->
                    TextButton({ vm.chooseMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) }
                }
            }
        },
        confirmButton = { TextButton(vm::dismissMealPicker) { Text("Cancel") } }
    )
}

@Composable private fun LibraryMealPickerDialog(vm: BreakfastViewModel, food: FoodRecord) {
    AlertDialog(
        onDismissRequest = vm::cancelLibraryMealPicker,
        title = { Text("Add ${food.name} to which meal?") },
        text = { Column { Text("${vm.selectedDayKey} · choose a meal"); MealSlot.entries.forEach { target -> TextButton({ vm.chooseLibraryMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) } } } },
        confirmButton = { TextButton(vm::cancelLibraryMealPicker) { Text("Cancel") } }
    )
}

@Composable private fun LibraryBatchMealPickerDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dismissLibraryBatchMealPicker() },
        title = { Text("Add foods to which meal?") },
        text = { Column { Text("Choose where to add this batch on the selected day."); MealSlot.entries.forEach { target -> TextButton({ vm.chooseLibraryBatchMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) } } } },
        confirmButton = { TextButton(vm::dismissLibraryBatchMealPicker) { Text("Cancel") } }
    )
}




@Composable internal fun ContributionAction(vm: BreakfastViewModel) {
    OutlinedButton(vm::reviewContribution, Modifier.fillMaxWidth()) {
        Text("Contribute to Open Food Facts")
    }
}

@Composable private fun ContributionReviewDialog(vm: BreakfastViewModel, draft: OffContributionDraft) {
    AlertDialog(
        onDismissRequest = vm::dismissContributionReview,
        title = { Text("Contribute correction?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("These public product fields will be sent to Open Food Facts:")
                draft.publicFields().forEach { Text(it) }
                if (draft.createNewProduct) {
                    OutlinedTextField(vm.contributionLanguageCode, vm::onContributionLanguage, label = { Text("Product language (en or mk)") }, singleLine = true)
                    if (draft.basisUnit == MeasureUnit.MILLILITER) {
                        OutlinedTextField(vm.contributionPackageQuantityMl, vm::onContributionPackageQuantityMl, label = { Text("Package quantity (ml) *") }, singleLine = true)
                    }
                    Text("This barcode is new to Open Food Facts; Breakfast will create the product with the fields above.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("This is an existing product. Only the recorded nutrition fields will be sent; its name, brand, and language stay unchanged.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Your meal amount, aliases, preferences, and serving estimates are not included.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(vm::confirmContribution) { Text("Continue to sign in") } },
        dismissButton = { TextButton(vm::dismissContributionReview) { Text("Decline") } }
    )
}

@Composable private fun ContributionAuthDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissContributionAuth,
        title = { Text("Open Food Facts sign in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Use your own Open Food Facts account. Breakfast does not save your password.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(vm.contributionUsername, vm::onContributionUsername, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    vm.contributionPassword,
                    vm::onContributionPassword,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.submitContribution() })
                )
            }
        },
        confirmButton = { Button(vm::submitContribution, enabled = !vm.contributionBusy) { Text(if (vm.contributionBusy) "Submitting…" else "Submit contribution") } },
        dismissButton = { TextButton(vm::dismissContributionAuth) { Text("Cancel") } }
    )
}

@Composable private fun ContributionFailureDialog(vm: BreakfastViewModel, message: String) {
    AlertDialog(
        onDismissRequest = vm::dismissContributionFailure,
        title = { Text("Contribution not sent") },
        text = { Text(message) },
        confirmButton = { TextButton(vm::retryContribution) { Text("Retry") } },
        dismissButton = { TextButton(vm::dismissContributionFailure) { Text("Dismiss") } }
    )
}

/** Where a reviewed product is logged. While a resolve queue is active the meal is the queue's own and Add always advances it. */
internal data class ReviewLogPlan(val meal: MealSlot, val advancesQueue: Boolean)

internal fun reviewLogPlan(resolutionContinuation: Boolean, queueNotEmpty: Boolean, queueTarget: MealSlot?, chosen: MealSlot): ReviewLogPlan =
    if (resolutionContinuation && queueNotEmpty && queueTarget != null) ReviewLogPlan(queueTarget, true) else ReviewLogPlan(chosen, false)

/** The queue owns the meal while resolving, even when the Daily screen has no selected meal. */
internal fun resolverLogPlan(
    resolutionContinuation: Boolean,
    queueNotEmpty: Boolean,
    queueTarget: MealSlot?,
    currentMeal: MealSlot?,
): ReviewLogPlan? = when {
    resolutionContinuation && queueNotEmpty -> queueTarget?.let { ReviewLogPlan(it, true) }
    else -> currentMeal?.let { ReviewLogPlan(it, false) }
}

internal fun activeResolutionTarget(
    resolutionContinuation: Boolean,
    queue: List<String>,
    queueTarget: MealSlot?,
): MealSlot? = resolverLogPlan(resolutionContinuation, queue.isNotEmpty(), queueTarget, null)?.meal

internal fun canSaveProductToLibrary(slot: MealSlot?, resolutionContinuation: Boolean, queueNotEmpty: Boolean): Boolean =
    slot == null && !(resolutionContinuation && queueNotEmpty)

internal data class ScannerFailureTransition(
    val scannerOpen: Boolean,
    val unavailable: Boolean,
)

internal fun scannerUnavailableTransition(): ScannerFailureTransition =
    ScannerFailureTransition(scannerOpen = true, unavailable = true)

internal fun shouldApplyOffSearchResult(
    responseGeneration: Long,
    currentGeneration: Long,
    responseQuery: String,
    currentQuery: String,
    searchOpen: Boolean,
): Boolean = searchOpen && responseGeneration == currentGeneration && responseQuery == currentQuery

internal fun shouldApplyLibraryRefresh(
    responseGeneration: Long,
    currentGeneration: Long,
    responseQuery: String,
    currentQuery: String,
    responseArchivedOnly: Boolean,
    currentArchivedOnly: Boolean,
    libraryOpen: Boolean,
): Boolean = libraryOpen && responseGeneration == currentGeneration && responseQuery == currentQuery && responseArchivedOnly == currentArchivedOnly

/** The "No local match" screen shows only once a finished library search for the current query is empty and nothing sits over it. */
internal fun isLibraryNoMatch(ready: Boolean, libraryOpen: Boolean, query: String, resultsQuery: String?, noResults: Boolean, otherScreenOpen: Boolean): Boolean =
    ready && libraryOpen && query.isNotBlank() && resultsQuery == query && noResults && !otherScreenOpen

/** "n of m" for the resolve sticker; the total never reads lower than what is left. */
internal fun resolvePosition(total: Int, remaining: Int): Pair<Int, Int> {
    val shown = maxOf(total, remaining)
    return (shown - remaining + 1).coerceIn(1, maxOf(shown, 1)) to shown
}
