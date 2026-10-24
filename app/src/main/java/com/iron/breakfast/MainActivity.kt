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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.iron.breakfast.domain.LogPlan
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.SegmentResolution
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.replaceDraftPart
import com.iron.breakfast.domain.DraftView
import com.iron.breakfast.domain.SegmentSpan
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.domain.ShorthandLexicon
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.toMeasureUnit
import com.iron.breakfast.domain.dayKeyForEpochMs
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.ui.screens.DailyScreen
import com.iron.breakfast.ui.screens.LogScreen
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
import com.iron.breakfast.ui.screens.MealScreen
import com.iron.breakfast.ui.screens.CreateFoodScreen
import com.iron.breakfast.ui.screens.LibraryNoMatchScreen
import com.iron.breakfast.ui.screens.OffSearchScreen
import com.iron.breakfast.ui.screens.ProductReviewSheet
import com.iron.breakfast.ui.screens.ResolveUnknownScreen
import com.iron.breakfast.ui.screens.ScanReviewBackdrop
import com.iron.breakfast.ui.screens.ScannerScreen
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.ui.theme.BreakfastType
import com.iron.breakfast.ui.theme.Gutter
import com.iron.breakfast.ui.theme.LocalBreakfastColors
import com.iron.breakfast.ui.theme.BreakfastTheme
import com.iron.breakfast.ui.theme.DarkBreakfastColors
import com.iron.breakfast.ui.theme.LightBreakfastColors
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
        // Keep system bars aligned with the light/dark background token.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(LightBreakfastColors.bg.toArgb(), DarkBreakfastColors.bg.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(LightBreakfastColors.bg.toArgb(), DarkBreakfastColors.bg.toArgb()),
        )
        // The system's contrast scrim is a white veil in light mode, which showed as a white nav bar under `bg`.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent { BreakfastTheme { IronApp() } }
    }
}

enum class EntryEditMode { NONE, QUICK, FULL }

enum class ProductReviewOrigin { MEAL, LIBRARY, QUEUE }

internal fun productReviewOrigin(slot: MealSlot?, resolving: Boolean, queueNotEmpty: Boolean): ProductReviewOrigin = when {
    resolving && queueNotEmpty -> ProductReviewOrigin.QUEUE
    slot != null -> ProductReviewOrigin.MEAL
    else -> ProductReviewOrigin.LIBRARY
}

enum class ScannerUnavailableReason { PERMISSION_DENIED, NO_USABLE_CAMERA }


private enum class EntryGesturePhase { UP, HORIZONTAL, VERTICAL, CONSUMED }

/**
 * A "Not in your foods" part sent to the library. [draftText] is the draft as it was, [start] and [end] the part's span
 * in it, [part] its text; [preset], [chosen] and [slot] are where the Log was, so it comes back there.
 */
/** The library list's query for what the bar shows: a typed amount is not part of the food name the list filters by. */
internal fun librarySearchText(text: String, parser: ShorthandParser): String =
    parser.parse(text).let { if (it.amount == null) text else it.foodPhrase }

data class LogResolveRequest(
    val draftText: String,
    val start: Int,
    val end: Int,
    val part: String,
    val preset: MealSlot?,
    val chosen: MealSlot?,
    val slot: MealSlot?,
)

class BreakfastViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val engine = BreakfastEngine(BreakfastStore(application))
    private val off = OpenFoodFactsClient()
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()
    var input by mutableStateOf(""); private set
    /** True after Done or Add: the text is no longer being typed, so every part follows the finished rules. */
    var inputCommitted by mutableStateOf(false); private set
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
    var scannerUnavailableReason by mutableStateOf<ScannerUnavailableReason?>(null); private set
    /** The scanner remains visible while the barcode request is in flight. */
    var scannerLookupBusy by mutableStateOf(false); private set
    var scannerLookupCode by mutableStateOf(""); private set
    var selectedProduct by mutableStateOf<FoodRecord?>(null); private set
    var selectedProductFromScan by mutableStateOf(false); private set
    var selectedProductOrigin by mutableStateOf(ProductReviewOrigin.LIBRARY); private set
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
    /** The full-screen Log page. [logPreset] comes from the `+` or composer that opened it; [logChosen] from the Log's meal menu. */
    var logOpen by mutableStateOf(false); private set
    var logPreset by mutableStateOf<MealSlot?>(null); private set
    var logChosen by mutableStateOf<MealSlot?>(null); private set
    /** A "Not in your foods" part sent to the library: picking a food there puts it back in the draft, not in the log. */
    var logResolve by mutableStateOf<LogResolveRequest?>(null); private set
    /** Foods picked from the library this session, by the part text they now read as; the planner resolves those parts to them. */
    private val logPins = mutableMapOf<String, FoodRecord>()
    var logMealMenuOpen by mutableStateOf(false); private set
    /** True when the meal menu was opened by Add with no meal known, so choosing a meal logs straight away. */
    private var logMealSubmitPending = false
    /** Library's mixed batch without a meal: asks which meal, then saves the typed text. */
    var mixedMealPickerOpen by mutableStateOf(false); private set
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
    private var libraryDraftCaptured = false
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
    var resolverSearchOpen by mutableStateOf(false); private set
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
    fun shiftSelectedDay(delta: Long) { goToDay(runCatching { LocalDate.parse(selectedDayKey).plusDays(delta).toString() }.getOrDefault(selectedDayKey)) }
    fun goToday() = goToDay(dayKeyForEpochMs(System.currentTimeMillis()))
    /** Selects a day from the arrows, swipe or date picker. Days after today are refused, so Next stops at today. */
    fun goToDay(dayKey: String) {
        if (dayKey > dayKeyForEpochMs(System.currentTimeMillis())) return
        selectDay(dayKey)
        barcodeRequestToken++
    }
    fun openLibrary() {
        if (!libraryOpen) {
            libraryInputBeforeOpen = input
            libraryDraftCaptured = true
        }
        cancelEdit(); selectMeal(null); libraryOpen = true; libraryQuery = ""; libraryArchivedOnly = false; refreshLibrary()
    }
    fun openArchivedLibrary() {
        if (!libraryOpen) {
            libraryInputBeforeOpen = input
            libraryDraftCaptured = true
        }
        cancelEdit(); selectMeal(null); libraryOpen = true; libraryQuery = ""; libraryArchivedOnly = true; refreshLibrary()
    }
    fun closeLibrary() {
        logResolve?.let { endResolve(it, it.draftText); return }
        val restoreInput = if (libraryOpen && libraryDraftCaptured) libraryInputBeforeOpen.orEmpty() else null
        libraryRefreshJob?.cancel(); libraryRefreshGeneration++; libraryOpen = false; selectedLibraryFood = null; libraryMealPickerFood = null; expandedLibraryFoodId = null; libraryPendingAmount = null; libraryPendingUnit = null; libraryPendingBatch = emptyList(); libraryBatchMealPickerOpen = false; libraryArchivedOnly = false; selectMeal(null)
        libraryQuery = ""
        libraryResultsQuery = null
        if (restoreInput != null) input = restoreInput
        libraryInputBeforeOpen = null
        libraryDraftCaptured = false
    }
    fun cancelLibraryMealPicker() { libraryMealPickerFood = null; libraryPendingAmount = null; libraryPendingUnit = null }
    /** The library's search: the bar shows it and the list filters by it, so both always read the same text. */
    fun onLibraryQuery(value: String) {
        if (libraryOpen) input = value
        libraryQuery = value
        refreshLibrary()
    }
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
        if (logResolve != null) { finishResolve(food); return }
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
        if (logResolve != null) { finishResolve(food); return }
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
    fun openEntryDetail(entry: MealEntry) {
        // A double tap is resolved by combinedClickable into quick edit. If a
        // delayed single-tap callback arrives after that, it must not reopen
        // the detail route over the edit sheet.
        if (entryEditMode != EntryEditMode.NONE) return
        selectedEntry = entry
        selectedEntryId = entry.id
        savedState["entry_id"] = entry.id
    }
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
        if (!libraryOpen) {
            libraryInputBeforeOpen = null
            libraryDraftCaptured = false
        }
        selectMeal(meal)
        libraryOpen = true
        // The bar shows the input, so it starts as the query the list is filtered by.
        input = query
        libraryQuery = query
        libraryArchivedOnly = false
        refreshLibrary()
    }
    /**
     * Sends a "Not in your foods" part to the library search, prefilled with its food text. Picking a food there does
     * not log: [finishResolve] puts it back in the draft in place of the part.
     */
    fun resolveNotInFoods(row: DraftRow.Unresolved) {
        if (!ready || busy || row.start < 0 || row.start >= row.end || row.end > input.length) return
        val part = input.substring(row.start, row.end)
        val query = engine.planner(logPins.toMap()).libraryQueryFor(part)
        logResolve = LogResolveRequest(input, row.start, row.end, part, logPreset, logChosen, slot)
        logOpen = false
        mealDraftError = null
        openFocusedLibrary(logTargetMeal() ?: MealSlot.BREAKFAST, query)
    }

    /** Puts the picked [food] in place of the resolving part, pins it so it resolves to that food, and returns to the Log. */
    private fun finishResolve(food: FoodRecord) {
        val request = logResolve ?: return
        val text = engine.planner(logPins.toMap()).pickedText(request.part, food)
        logPins[text] = food
        endResolve(request, replaceDraftPart(request.draftText, request.start, request.end, text))
    }

    /** Leaves the library for the Log page: the draft becomes [draftInput], and the Log opens where it was. */
    private fun endResolve(request: LogResolveRequest, draftInput: String) {
        logResolve = null
        closeLibrary()
        selectMeal(request.slot)
        searchOpen = false
        customOpen = false
        selectedProduct = null
        input = draftInput
        inputCommitted = false
        mealDraftError = null
        logPreset = request.preset
        logChosen = request.chosen
        logOpen = true
    }

    /** A product picked from the search or scanner while resolving: stored as the food it is, then picked. */
    private fun resolveWithProduct() {
        val food = selectedProduct ?: return
        if (!ready || busy) return
        val draft = productDraft(food) ?: run {
            notice = "Enter a positive amount, a supported unit, and valid calories."
            return
        }
        val fromScan = selectedProductFromScan
        busy = true
        viewModelScope.launch {
            runCatching { engine.cacheFood(draft.food) }
                .onSuccess { stored ->
                    pendingContribution = contributionDraftAfterLocalSave(draft.food, fromScan)
                    selectedProductFromScan = false
                    refresh(); refreshLibrary()
                    finishResolve(stored)
                }
                .onFailure { notice = it.message ?: "Could not save this food." }
                .also { busy = false }
        }
    }

    fun openLibraryFoodDetail(entry: MealEntry) {
        closeEntryDetail()
        cancelEdit()
        if (!libraryOpen) {
            libraryInputBeforeOpen = null
            libraryDraftCaptured = false
        }
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
    fun closeMeal() {
        logResolve?.let { endResolve(it, it.draftText); return }
        logPins.clear()
        abandonUnknownResolution(clearDraft = true); selectMeal(null); barcodeRequestToken++; cancelEdit() }
    /** The keyboard's Done: the typed text is committed, so its last word is no longer being typed. */
    fun commitInput() { inputCommitted = true }
    fun onInput(v: String) {
        input = v
        inputCommitted = false
        if (libraryOpen) {
            libraryQuery = librarySearchText(v, parser)
            refreshLibrary()
        }
    }
    /** The meal the Log page writes to, by [logTargetMealOf] precedence. Also drives the picker label. */
    internal fun logTargetMeal(): MealSlot? = logTargetMealOf(logChosen, mealMarkers(input), logPreset)
    /** The part being typed: the last smart part, unless the input ends with a separator. Null when nothing is being typed. */
    private fun activeSpan(planner: LogPlanner): SegmentSpan? {
        if (!ready || input.isBlank() || inputCommitted || planner.endsOpen(input)) return null
        return planner.segments(input).lastOrNull()
    }
    /**
     * Everything the Log page shows for the current draft, computed in one pass from one planner, so the rows,
     * the choices and the add-bar sum all come from the same resolution.
     */
    internal fun logDraftView(): LogDraftView {
        val planner = engine.planner(logPins.toMap())
        // One planner draft: rows, the choice group, suggestions and the add-bar sum all come from the same resolution.
        val draft = if (ready) planner.draft(input, typing = !inputCommitted) else DraftView.EMPTY
        return LogDraftView(
            rows = draft.rows,
            choices = draft.choices,
            choiceHint = draft.choiceHint,
            preselected = draft.preselected,
            activePhrase = draft.activePhrase,
            // The sum is the draft's own count: what Add would save.
            logged = draft.logged,
        )
    }
    /** kcal of [food] at the amount Add would log for the active part with that food: the same numbers the choice shows. */
    internal fun quickAddSuggestionCalories(food: FoodRecord): Double =
        engine.planner(logPins.toMap()).calculationFor(activeSpan(engine.planner(logPins.toMap()))?.text.orEmpty(), food).nutrition.calories

    /** Tapping a choice or a suggestion: the typed part becomes the amount, unit and name it would log. */
    fun chooseQuickSuggestion(food: FoodRecord) {
        onInput(engine.planner(logPins.toMap()).withChoice(input, food, typing = !inputCommitted))
    }
    fun submitLibraryInput() {
        val raw = input.trim()
        if (raw.isBlank() || !ready || busy) return
        // While a part is being resolved, Done searches; it never logs.
        if (logResolve != null) { onLibraryQuery(raw); return }
        val markers = mealMarkers(raw)
        if (markers.size > 1) { notice = "Choose one meal marker."; return }
        val target = markers.singleOrNull()
        val effectiveTarget = target ?: slot
        val segments = libraryInputSegments(raw, engine.planner())
        if (segments.isEmpty() || segments.all { it.amount == null }) {
            onLibraryQuery(segments.joinToString(", ") { it.foodPhrase })
            input = ""
            return
        }
        if (segments.any { it.amount == null }) {
            if (effectiveTarget == null) {
                libraryPendingBatch = emptyList()
                notice = "Choose a meal before adding a mixed batch with an unquantified food."
                mixedMealPickerOpen = true
                return
            }
            submitLibraryMixedInput(raw, effectiveTarget)
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
        closeEntryDetail()
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
        closeEntryDetail()
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
        inputCommitted = true
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
        when (val plan = logSubmitPlan(ready, busy, input, mealMarkers(input), logChosen, logPreset)) {
            LogSubmitPlan.Ignore -> Unit
            is LogSubmitPlan.Error -> mealDraftError = plan.message
            LogSubmitPlan.OpenMenu -> {
                mealDraftError = null
                logMealSubmitPending = true
                logMealMenuOpen = true
            }
            is LogSubmitPlan.Save -> submitToMeal(plan.target)
        }
    }

    /** Opens the Log page. [preset] is the meal of the `+` or composer that opened it; null for the Daily composer. */
    fun openLog(preset: MealSlot?) {
        if (entryEditMode != EntryEditMode.NONE) return
        logPreset = preset
        logChosen = null
        logMealMenuOpen = false
        logMealSubmitPending = false
        mealDraftError = null
        logOpen = true
    }

    /** Closes the Log page. The draft stays in [input]. */
    fun closeLog() {
        logOpen = false
        logMealMenuOpen = false
        logMealSubmitPending = false
    }

    fun openLogMealMenu() { logMealMenuOpen = true }
    fun dismissLogMealMenu() { logMealMenuOpen = false; logMealSubmitPending = false }

    /** Choosing a meal from the menu wins over everything else; it logs at once only if Add asked for it. */
    fun chooseLogMeal(target: MealSlot) {
        logChosen = target
        logMealMenuOpen = false
        if (logMealSubmitPending) {
            logMealSubmitPending = false
            submit()
        }
    }

    fun dismissMixedMealPicker() { mixedMealPickerOpen = false }

    /** Library's mixed batch without a meal: the chosen meal saves the typed text. */
    fun chooseMixedMeal(target: MealSlot) {
        mixedMealPickerOpen = false
        submitToMeal(target)
    }

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
        // Save takes the draft as typed: the planner skips meal words by span, so save and the rows share one segmentation.
        if (engine.planner(logPins.toMap()).segments(input).none { ShorthandLexicon.mealSlotOf(it.text) == null }) {
            mealDraftError = "Add at least one food."
            return
        }
        busy = true
        viewModelScope.launch {
            runCatching { engine.save(input, mealSlot = target, dayKey = selectedDayKey, pins = logPins.toMap()) }.onSuccess { saved ->
                // Nothing resolved: nothing was saved, so the Log stays open with the typed text and says what to fix.
                if (saved.entries.isEmpty() && saved.unresolvedSegments.isNotEmpty()) {
                    mealDraftError = "Some foods need a match."
                    return@onSuccess
                }
                logPins.clear()
                closeLog()
                refresh(); if (input == captured) input = saved.unresolvedSegments.joinToString(", "); unknownQueue = saved.unresolvedSegments; unknownQueueTotal = saved.unresolvedSegments.size; unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }; resolutionContinuation = saved.unresolvedSegments.isNotEmpty(); resolverOpen = saved.unresolvedSegments.isNotEmpty(); mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
                feedbackIds = saved.entries.filter { it.approximateConversion }.mapTo(mutableSetOf()) { it.id }; feedbackUntil = if (feedbackIds.isEmpty()) 0L else System.currentTimeMillis() + 30_000L
                loggedBatchIds = saved.entries.map { it.id }
                loggedBatchMessage = "${saved.entries.size} food${if (saved.entries.size == 1) "" else "s"} added to ${target.label()}"
            }.onFailure { mealDraftError = it.message ?: "Could not save this meal." }.also { busy = false }
        }
    }
    private fun mealMarkers(value: String): List<MealSlot> = mealMarkerSlots(value)
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
    fun closeResolver() { abandonUnknownResolution(); resolverSearchOpen = false }
    fun beginResolverSearch() {
        resolverSearchOpen = true
        if (resolverQuery.isBlank()) resolverQuery = currentUnknownName()
        onResolverQuery(resolverQuery)
    }
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
    fun dismissContributionAuth() {
        if (!contributionAuthCanDismiss(contributionBusy)) return
        contributionAuthOpen = false
        contributionPassword = ""
    }
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
        contributionBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = off.contribute(draft, username, password)
            withContext(Dispatchers.Main) {
                when (result) {
                    OffContributionResult.Success -> {
                        pendingContribution = null
                        contributionAuthOpen = false
                        contributionFailure = null
                        notice = "Contribution sent to Open Food Facts."
                    }
                    OffContributionResult.AuthFailed -> { contributionAuthOpen = false; contributionFailure = "Open Food Facts could not authenticate that account. Check the username and password, then retry." }
                    is OffContributionResult.Ineligible -> { contributionAuthOpen = false; contributionFailure = result.message }
                    is OffContributionResult.Rejected -> { contributionAuthOpen = false; contributionFailure = result.message }
                    is OffContributionResult.PreflightChanged -> { contributionAuthOpen = false; contributionFailure = result.message }
                    OffContributionResult.Unavailable -> { contributionAuthOpen = false; contributionFailure = "Open Food Facts was unavailable. Retry when you have a connection." }
                    is OffContributionResult.Error -> { contributionAuthOpen = false; contributionFailure = result.message }
                }
                contributionBusy = false
            }
        }
    }
    fun clearSelectedProduct() { selectedProduct = null; selectedProductFromScan = false; selectedProductOrigin = ProductReviewOrigin.LIBRARY; if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
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
        resolverSearchOpen = false
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
    fun chooseProduct(food: FoodRecord, fromScan: Boolean = false, origin: ProductReviewOrigin? = null) {
        selectedProduct = food
        selectedProductFromScan = fromScan
        selectedProductOrigin = origin ?: productReviewOrigin(slot, resolutionContinuation, unknownQueue.isNotEmpty())
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
        if (logResolve != null) { resolveWithProduct(); return }
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
                    selectedProductOrigin = ProductReviewOrigin.LIBRARY
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
        if (logResolve != null) { resolveWithProduct(); return }
        if (!canSaveProductToLibrary(slot, resolutionContinuation, unknownQueue.isNotEmpty())) return
        val draft = productDraft(food) ?: run { notice = "Enter a positive amount, a supported unit, and valid calories."; return }
        if (!ready || busy) return
        busy = true
        viewModelScope.launch {
            runCatching { engine.updateFood(draft.food) }
                .onSuccess {
                    selectedProduct = null
                    selectedProductFromScan = false
                    selectedProductOrigin = ProductReviewOrigin.LIBRARY
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
    fun beginScan() { barcodeRequestToken++; barcodeOriginDay = selectedDayKey; barcodeOriginMeal = slot; scannerUnavailableState = false; scannerUnavailableReason = null; scannerLookupBusy = false; scannerLookupCode = ""; scannerOpen = true }
    fun endScan() { barcodeRequestToken++; scannerOpen = false; scannerUnavailableState = false; scannerUnavailableReason = null; scannerLookupBusy = false; scannerLookupCode = ""; rescanningBarcode = false }
    fun cancelBarcodeLookup() = endScan()
    /** Keep the scanner route open so manual barcode entry remains available after camera failure. */
    fun scannerUnavailable() {
        val transition = scannerUnavailableTransition()
        scannerUnavailableState = transition.unavailable
        scannerUnavailableReason = ScannerUnavailableReason.NO_USABLE_CAMERA
        scannerOpen = transition.scannerOpen
    }
    fun scannerPermissionDenied() {
        scannerUnavailableState = true
        scannerUnavailableReason = ScannerUnavailableReason.PERMISSION_DENIED
        scannerOpen = true
    }
    private fun barcodeRequestStillTargets(token: Long, day: String, meal: MealSlot?): Boolean = token == barcodeRequestToken && day == selectedDayKey && meal == slot
    fun onBarcode(code: String) {
        scannerUnavailableState = false
        scannerUnavailableReason = null
        if (rescanningBarcode) {
            rescanningBarcode = false
            scannerOpen = false
            val scanned = normalizeBarcode(code)
            if (scanned == null) notice = "Not a product barcode." else { draft.barcode = scanned; draft.barcodeFromScan = true }
            return
        }
        val barcode = code.trim()
        val token = barcodeRequestToken
        val originDay = barcodeOriginDay
        val originMeal = barcodeOriginMeal
        val reviewOrigin = productReviewOrigin(originMeal, resolutionContinuation, unknownQueue.isNotEmpty())
        scannerLookupCode = barcode
        scannerLookupBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val local = engine.foodByBarcode(barcode)
            if (local != null) {
                withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; chooseProduct(local, fromScan = true, origin = reviewOrigin) } }
                return@launch
            }
            when (val result = off.barcode(barcode)) {
                is OffResult.Found -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; chooseProduct(result.food, fromScan = true, origin = reviewOrigin) } }
                is OffResult.Ineligible -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notice = "Nutrition is incomplete. Review it as a personal food."; openCustom(result.name.orEmpty(), result.brand.orEmpty(), result.barcode, fromOff = true) } }
                OffResult.Missing -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notice = "Product not found. Add it manually with barcode $barcode"; openCustom(barcode = barcode) } }
                OffResult.Unavailable -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notice = "No connection. Add the product manually."; openCustom(barcode = barcode) } }
                is OffResult.Error -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notice = result.message } }
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
        }.onSuccess { saved ->
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
            if (logResolve != null && editingId == null) finishResolve(saved)
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

/** The library's view of a draft: the parts without meal words, each with the amount and unit it types. */
internal fun libraryInputSegments(value: String, planner: LogPlanner? = null): List<LibraryInputSegment> {
    val parser = ShorthandParser()
    return (planner?.segments(value)?.map { it.text } ?: parser.split(value))
        .filter { ShorthandLexicon.mealSlotOf(it) == null }
        .map { raw ->
            val typed = planner?.typed(raw) ?: parser.parse(raw).let { TypedPart(it.foodPhrase, it.amount, it.unit) }
            LibraryInputSegment(raw, typed.foodPhrase, typed.amount, typed.unit)
        }
}

internal fun defaultServingCalculation(food: FoodRecord): Calculation =
    NutritionCalculator().calculate(food, food.defaultAmount, food.defaultUnit)

internal fun mealMarkerSlots(value: String): List<MealSlot> = ShorthandParser().split(value).mapNotNull(ShorthandLexicon::mealSlotOf)

internal fun removeMealMarkerToken(value: String, target: MealSlot): String =
    ShorthandParser().removingParts(value) { ShorthandLexicon.mealSlotOf(it.text) == target }

/** Drops every meal-word part; the Log page's chosen meal then wins over a typed one. */
internal fun removeAllMealMarkerTokens(value: String): String =
    ShorthandParser().removingParts(value) { ShorthandLexicon.mealSlotOf(it.text) != null }

/**
 * The Log page's target meal. Precedence: a meal picked from the Log menu, then a single typed meal word
 * (it beats a preset), then the preset. Two different typed words give no target; the plan reports the error.
 */
internal fun logTargetMealOf(chosen: MealSlot?, typedMarkers: List<MealSlot>, preset: MealSlot?): MealSlot? =
    chosen ?: typedMarkers.singleOrNull() ?: preset

/** The picker label: the target meal's name, or "Meal" when nothing is chosen, typed or preset. */
internal fun logMealPickerLabel(target: MealSlot?): String = target?.label() ?: "Meal"

internal sealed interface LogSubmitPlan {
    /** Nothing to do: the app is not ready or a save is already running. */
    data object Ignore : LogSubmitPlan
    data class Error(val message: String) : LogSubmitPlan
    /** No meal is known: open the Log's meal menu; choosing a meal submits. */
    data object OpenMenu : LogSubmitPlan
    data class Save(val target: MealSlot) : LogSubmitPlan
}

/** Decides what Add does with the Log input. Pure, so the no-meal path and the busy guard can be unit-tested. */
internal fun logSubmitPlan(ready: Boolean, busy: Boolean, input: String, typedMarkers: List<MealSlot>, chosen: MealSlot?, preset: MealSlot?): LogSubmitPlan {
    if (!ready || busy) return LogSubmitPlan.Ignore
    if (input.isBlank()) return LogSubmitPlan.Error("Add at least one food.")
    if (typedMarkers.size > 1) return LogSubmitPlan.Error("Choose one meal marker, or remove the extra markers.")
    val target = logTargetMealOf(chosen, typedMarkers, preset) ?: return LogSubmitPlan.OpenMenu
    return LogSubmitPlan.Save(target)
}

/**
 * What Add logs for a Log draft: the input with meal words removed, as submit() sends it to save().
 * An amountless draft opens the Library search instead, so it logs nothing.
 */
internal data class LogDraftView(
    /** The draft's rows in typed order: entries, and parts shown as "Not in your foods". */
    val rows: List<DraftRow>,
    /** The group under the rows: the word being typed's suggestions, or the part's choices. */
    val choices: List<RankedFood>,
    /** The "which one?" hint, shown for real choices only. */
    val choiceHint: Boolean,
    /** True only when the first choice is a full match that Add would log; a partial match is offered unselected. */
    val preselected: Boolean,
    val activePhrase: String,
    /** What Add will save; the add bar's sum and count come from this. */
    val logged: List<SegmentResolution>,
)

/** What Add saves for the Log's [input]: the same plan the draft's sum uses. Done and Add never open library search. */
internal fun draftLogPlan(input: String, planner: LogPlanner): LogPlan = planner.plan(input)

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
    if (vm.logOpen) Unit // LogScreen owns Back while it is open.
    else if (vm.libraryOpen && vm.selectedLibraryFood == null && !vm.customOpen && !vm.searchOpen && !vm.resolverOpen && vm.selectedProduct == null && vm.entryEditMode == EntryEditMode.NONE) {
        // Archived foods is a child destination of Library. Keep system/predictive
        // Back aligned with the top-bar arrow before leaving the Library flow.
        BackHandler { if (vm.libraryArchivedOnly) vm.openLibrary() else vm.closeLibrary() }
    } else if (vm.slot != null && vm.entryEditMode == EntryEditMode.NONE && !vm.customOpen && !vm.searchOpen && !vm.resolverOpen && vm.selectedProduct == null && vm.selectedEntry == null) {
        BackHandler { vm.closeMeal() }
    }
    if (vm.mixedMealPickerOpen) MixedMealPickerDialog(vm)
    if (vm.libraryMealPickerFood != null) LibraryMealPickerDialog(vm, vm.libraryMealPickerFood!!)
    if (vm.libraryBatchMealPickerOpen) LibraryBatchMealPickerDialog(vm)
    if (vm.customOpen) {
        if (vm.editingLibraryFoodId != null) LibraryFoodEditScreen(vm) else CreateFoodScreen(vm)
    }
    if (vm.libraryNoMatch()) LibraryNoMatchScreen(vm)
    if (vm.searchOpen) OffSearchScreen(vm)
    if (vm.resolverOpen && !scanReviewBackdropVisible) ResolveUnknownScreen(vm)
    vm.selectedProduct?.let { ProductReviewSheet(vm, it) }
    if (vm.contributionReviewOpen) vm.pendingContribution?.let { ContributionReviewScreen(vm, it) }
    if (vm.contributionAuthOpen) ContributionSignInScreen(vm)
    vm.contributionFailure?.let { ContributionFailureScreen(vm, it) }
    vm.notice?.let { message -> AlertDialog(onDismissRequest = vm::dismissNotice, confirmButton = { TextButton(vm::dismissNotice) { Text("OK") } }, text = { Text(message) }) }
    vm.initializationError?.let { message -> AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Storage unavailable") }, text = { Text(message) }) }
}




@Composable private fun MixedMealPickerDialog(vm: BreakfastViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissMixedMealPicker,
        title = { Text("Which meal?") },
        text = {
            Column {
                Text("Choose where to add this batch.")
                MealSlot.entries.forEach { target ->
                    TextButton({ vm.chooseMixedMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) }
                }
            }
        },
        confirmButton = { TextButton(vm::dismissMixedMealPicker) { Text("Cancel") } }
    )
}

@Composable private fun LibraryMealPickerDialog(vm: BreakfastViewModel, food: FoodRecord) {
    AlertDialog(
        onDismissRequest = vm::cancelLibraryMealPicker,
        title = { Text("Add ${food.name} to which meal?") },
        text = { Column { Text("Choose a meal on ${vm.selectedDayKey}"); MealSlot.entries.forEach { target -> TextButton({ vm.chooseLibraryMeal(target) }, Modifier.fillMaxWidth()) { Text(target.label()) } } } },
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

@Composable private fun ContributionReviewScreen(vm: BreakfastViewModel, draft: OffContributionDraft) {
    val colors = LocalBreakfastColors.current
    BackHandler(onBack = vm::dismissContributionReview)
    val normalized = draft.normalizedNutrition
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter)) {
            TopBar("Contribute", vm::dismissContributionReview, Modifier.padding(top = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(listOfNotNull(draft.name, draft.brand).joinToString(", "), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 4.dp))
                Hairline(Modifier.padding(top = 16.dp))
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    SectionHeading("Will send")
                    if (draft.createNewProduct) {
                        ContributionLine("Name", draft.name)
                        draft.brand?.let { ContributionLine("Brand", it) }
                    }
                    ContributionLine("Barcode", draft.barcode)
                    Text("per 100 ${draft.basisUnit.label}", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 10.dp))
                    ContributionLine("Energy", normalized.calories.pretty(), "kcal")
                    ContributionLine("Fat", normalized.fat?.pretty() ?: "0", "g")
                    ContributionLine("Carbohydrate", normalized.carbohydrates?.pretty() ?: "0", "g")
                    ContributionLine("Protein", normalized.protein?.pretty() ?: "0", "g")
                    if (!draft.createNewProduct) Text("Existing product: only nutrition changes.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 16.dp))
                }
                if (draft.createNewProduct) {
                    Text("Language", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 24.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceOption("EN", vm.contributionLanguageCode == "en", { vm.onContributionLanguage("en") }, Modifier.widthIn(min = 64.dp))
                        ChoiceOption("MK", vm.contributionLanguageCode == "mk", { vm.onContributionLanguage("mk") }, Modifier.widthIn(min = 64.dp))
                    }
                    if (draft.basisUnit == MeasureUnit.MILLILITER) Field("Package quantity, ml", vm.contributionPackageQuantityMl, vm::onContributionPackageQuantityMl, Modifier.padding(top = 16.dp))
                    Text("New product: Breakfast creates it.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 12.dp))
                }
                Hairline(Modifier.padding(top = 24.dp))
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    SectionHeading("Not sent")
                    Text("Amounts, aliases, servings", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton("Continue to sign in", vm::confirmContribution, kind = ButtonKind.Neutral)
                TertiaryButton("Decline", vm::dismissContributionReview, Modifier.fillMaxWidth(), soft = true)
            }
        }
    }
}

@Composable private fun ContributionSignInScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { passwordFocus.requestFocus() }
    BackHandler(enabled = contributionAuthCanDismiss(vm.contributionBusy), onBack = vm::dismissContributionAuth)
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            TopBar("Sign in", vm::dismissContributionAuth, Modifier.padding(top = 8.dp), backEnabled = contributionAuthCanDismiss(vm.contributionBusy))
            Text("Open Food Facts account", color = colors.ink, style = BreakfastType.Section, modifier = Modifier.padding(top = 16.dp))
            Field("Username", vm.contributionUsername, vm::onContributionUsername, Modifier.padding(top = 16.dp))
            Field(
                "Password", vm.contributionPassword, vm::onContributionPassword, Modifier.padding(top = 16.dp),
                fieldModifier = Modifier.focusRequester(passwordFocus),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.submitContribution() }),
            )
            Text("Password is not saved.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 12.dp))
            PrimaryButton(if (vm.contributionBusy) "Submitting" else "Submit", vm::submitContribution, Modifier.padding(top = 24.dp), enabled = !vm.contributionBusy, kind = ButtonKind.Neutral)
            if (contributionAuthCanDismiss(vm.contributionBusy)) TertiaryButton("Cancel", vm::dismissContributionAuth, Modifier.fillMaxWidth().padding(top = 4.dp), soft = true)
        }
    }
}

@Composable private fun ContributionFailureScreen(vm: BreakfastViewModel, message: String) {
    val colors = LocalBreakfastColors.current
    BackHandler(onBack = vm::dismissContributionFailure)
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Gutter)) {
            TopBar("Contribute", vm::dismissContributionFailure, Modifier.padding(top = 8.dp))
            Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Not sent", color = colors.ink, style = BreakfastType.Title)
                Text(
                    message,
                    color = colors.soft,
                    style = BreakfastType.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp),
                )
                Text(
                    "Your food and entries are saved on this phone.",
                    color = colors.soft,
                    style = BreakfastType.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).widthIn(max = 290.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton("Retry", vm::retryContribution, kind = ButtonKind.Neutral)
                TertiaryButton("Dismiss", vm::dismissContributionFailure, Modifier.fillMaxWidth(), soft = true)
            }
        }
    }
}

@Composable private fun ContributionLine(label: String, value: String, unit: String? = null) {
    val colors = LocalBreakfastColors.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val valueModifier = if (unit == null) Modifier else Modifier.widthIn(min = 64.dp * fontScale)
    val unitModifier = Modifier.widthIn(min = 44.dp * fontScale).padding(start = 8.dp)
    // Plain values (name, brand, barcode) are soft; amounts are ink with a soft unit.
    val valueColor = if (unit == null) colors.soft else colors.ink
    val valueStyle = if (unit == null) BreakfastType.Body else BreakfastType.BodyMedium
    if (unit != null && isLargeFont(fontScale)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(label, color = colors.ink, style = BreakfastType.Body)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(value, color = valueColor, style = valueStyle, textAlign = TextAlign.End, modifier = valueModifier)
                Text(unit, color = colors.soft, style = BreakfastType.Secondary, maxLines = 1, modifier = unitModifier)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = colors.ink, style = BreakfastType.Body, modifier = Modifier.weight(1f))
            Text(value, color = valueColor, style = valueStyle, textAlign = TextAlign.End, modifier = valueModifier)
            if (unit != null) Text(unit, color = colors.soft, style = BreakfastType.Secondary, maxLines = 1, modifier = unitModifier)
        }
    }
}

/** Where a reviewed product is logged. While a resolve queue is active the meal is the queue's own and Add always advances it. */
internal data class ReviewLogPlan(val meal: MealSlot, val advancesQueue: Boolean)

internal fun contributionAuthCanDismiss(busy: Boolean): Boolean = !busy

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
