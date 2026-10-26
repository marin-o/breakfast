package com.iron.breakfast



import android.Manifest
import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.iron.breakfast.data.DiaryImport
import com.iron.breakfast.data.DiarySettings
import com.iron.breakfast.data.decodeDiary
import com.iron.breakfast.data.diaryFileOf
import com.iron.breakfast.data.encodeDiary
import com.iron.breakfast.data.readDiaryText
import com.iron.breakfast.data.SAFETY_COPY_NAME
import com.iron.breakfast.data.diaryFileName
import com.iron.breakfast.data.writeDiaryChecked
import com.iron.breakfast.data.writeSafetyCopy
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.data.OffContributionResult
import com.iron.breakfast.data.OffPreflightResult
import com.iron.breakfast.data.OffResult
import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.data.OpenFoodFactsClient
import com.iron.breakfast.data.plausibleNutrition
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
import com.iron.breakfast.domain.canonicalBarcode
import com.iron.breakfast.domain.sameBarcode
import com.iron.breakfast.domain.decimalInput
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.domain.resolveNutrition
import com.iron.breakfast.domain.splitAliases
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.LogPlan
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.SegmentResolution
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.replaceDraftPart
import com.iron.breakfast.domain.DraftView
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.domain.ShorthandLexicon
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.toMeasureUnit
import com.iron.breakfast.domain.dayKeyFor
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
import androidx.compose.runtime.CompositionLocalProvider
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.LocalHaptics
import com.iron.breakfast.ui.components.LocalMotionForcedReduced
import com.iron.breakfast.ui.components.LongDraft
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.isLargeFont
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.ChoiceOption
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.SecondaryButton
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.time.LocalDate
import java.time.ZoneId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.iron.breakfast.ui.screens.SettingsScreen
import kotlin.math.abs
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    /** The Theme and Text size this activity was created with; a new one is applied by [applyTheme] or [applyTextSize]. */
    private var appliedTheme = ThemeChoice.SYSTEM
    private var appliedTextSize = TextSizeChoice.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val settings = AppSettings(newBase)
        appliedTheme = settings.theme
        appliedTextSize = settings.textSize
        // Overridden before the window and its theme exist, so the window background (values-night) and the density of
        // every window follow: below API 31 the forced night mode (there is no per-app one), and the Larger font scale.
        val base = newBase.resources.configuration
        activityOverrideOf(base.uiMode, base.fontScale, Build.VERSION.SDK_INT, appliedTheme, appliedTextSize)?.let { override ->
            applyOverrideConfiguration(Configuration().apply {
                override.uiMode?.let { uiMode = it }
                override.fontScale?.let { fontScale = it }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // API 31+: the system's per-app night mode, which also colours the launch window on the next cold start. The
        // same value again changes nothing; a different one (the setting reset elsewhere) recreates the activity once.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(applicationNightModeOf(appliedTheme))
        }
        super.onCreate(savedInstanceState)
        // Keep system bars aligned with the light/dark background token.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(LightBreakfastColors.bg.toArgb(), DarkBreakfastColors.bg.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(LightBreakfastColors.bg.toArgb(), DarkBreakfastColors.bg.toArgb()),
        )
        // The system's contrast scrim is a white veil in light mode, which showed as a white nav bar under `bg`.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            val vm: BreakfastViewModel = viewModel()
            LaunchedEffect(vm.theme) { applyTheme(vm.theme) }
            LaunchedEffect(vm.textSize) { applyTextSize(vm.textSize) }
            CompositionLocalProvider(LocalMotionForcedReduced provides (vm.motion == MotionChoice.REDUCED), LocalHaptics provides vm.haptics) {
                BreakfastTheme(dark = darkThemeOf(vm.theme, isSystemInDarkTheme())) { IronApp(vm) }
            }
        }
    }

    /**
     * A new Theme setting: API 31+ sets the per-app night mode (the system recreates the activity with it and uses it
     * for the launch window); below that the activity is recreated so [attachBaseContext] applies it.
     */
    /** A new Text size: the activity is recreated, so [attachBaseContext] gives every window the new font scale. */
    private fun applyTextSize(textSize: TextSizeChoice) {
        if (textSize == appliedTextSize) return
        appliedTextSize = textSize
        recreate()
    }

    private fun applyTheme(theme: ThemeChoice) {
        if (theme == appliedTheme) return
        appliedTheme = theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(applicationNightModeOf(theme))
        } else {
            recreate()
        }
    }
}

enum class EntryEditMode { NONE, QUICK, FULL }

enum class ProductReviewOrigin { MEAL, LIBRARY, QUEUE }

enum class ScannerUnavailableReason { PERMISSION_DENIED, NO_USABLE_CAMERA }

/** The system document picker Settings asks for: save a new diary file, or open one to import. */
enum class DiaryPicker { EXPORT, IMPORT }


private enum class EntryGesturePhase { UP, HORIZONTAL, VERTICAL, CONSUMED }

/**
 * A "Not in your foods" part sent to the library. [draftText] is the draft as it was, [start] and [end] the part's span
 * in it, [part] its text; [preset], [chosen] and [slot] are where the Log was, so it comes back there.
 */
/** The library list's query for what the bar shows: a typed amount is not part of the food name the list filters by. */
internal fun librarySearchText(text: String, parser: ShorthandParser): String =
    parser.parse(text).let { if (it.amount == null) text else it.foodPhrase }

/** A one-time Undo, shown as a snackbar until [UNDO_WINDOW_MS] after [offeredAtMs] or the next log, edit or delete. */
internal sealed interface UndoOffer {
    val offeredAtMs: Long
    data class Deleted(val entry: MealEntry, override val offeredAtMs: Long) : UndoOffer
    /** A logged batch; [entries] are the rows as saved, so Undo can leave any entry edited since. */
    data class Logged(val message: String, val entries: List<MealEntry>, override val offeredAtMs: Long) : UndoOffer
}

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
    private val clock: () -> Long = System::currentTimeMillis
    private val settings = AppSettings(application)
    private val zone: ZoneId get() = ZoneId.systemDefault()
    /** The hour a diary day starts at, from Settings. */
    var dayStartHour by mutableStateOf(settings.dayStartHour); private set
    /** Today's day by the clock and [dayStartHour]. The 60-second loop and every return keep it current. */
    var todayKey by mutableStateOf(dayKeyFor(clock(), zone, dayStartHour)); private set
    var settingsOpen by mutableStateOf(false); private set
    /** How the composer shows a long draft, from Settings. */
    var longDraft by mutableStateOf(settings.longDraft); private set
    /** Settings, Display. The activity applies [theme]; the decimal mark is read through [DisplayDecimal]. */
    internal var theme by mutableStateOf(settings.theme); private set
    var showMacros by mutableStateOf(settings.showMacros); private set
    var showMissingNote by mutableStateOf(settings.showMissingNote); private set
    internal var decimalMark by mutableStateOf(settings.decimalMark.also { DisplayDecimal.mark = it }); private set
    /** Settings, Accessibility; the activity applies them. */
    internal var motion by mutableStateOf(settings.motion); private set
    internal var textSize by mutableStateOf(settings.textSize); private set
    var haptics by mutableStateOf(settings.haptics); private set
    /** Settings, Scanner. */
    var flashlightAtStart by mutableStateOf(settings.flashlightAtStart); private set
    var lookUpBarcodes by mutableStateOf(settings.lookUpBarcodes); private set
    var showContribute by mutableStateOf(settings.showContribute); private set
    /** Settings, Logging: how long Undo stays, the meal a log without one goes to, and the ranking switches. */
    internal var undoWindow by mutableStateOf(settings.undoWindow); private set
    internal var mealMode by mutableStateOf(settings.mealMode); private set
    internal var mealBounds by mutableStateOf(settings.mealBounds); private set
    var countTyping by mutableStateOf(settings.countTyping); private set
    var learnFromPicks by mutableStateOf(settings.learnFromPicks); private set
    private val engine = BreakfastEngine(BreakfastStore(application)).also {
        it.countTyping = countTyping
        it.learnFromPicks = learnFromPicks
    }
    private val off = OpenFoodFactsClient()
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()
    /** The Library bar's text, or the amount being edited. Never the log draft, so leaving either cannot touch it. */
    var input by mutableStateOf(""); private set
    /** The log draft Daily and Meal share: the composer shows it and the Log page edits it. Only Add or clearing it removes it. */
    var logDraft by mutableStateOf(""); private set
    /** True after Done or Add: the draft is no longer being typed, so every part follows the finished rules. */
    var logDraftCommitted by mutableStateOf(false); private set
    var foods by mutableStateOf<List<FoodRecord>>(emptyList()); private set
    var entries by mutableStateOf<List<MealEntry>>(emptyList()); private set
    /** True while the user is on today, not on a day they picked: a return from the background then opens the new today. */
    private var followingToday = restoreSelectedDay(savedState).followingToday
    /** Opened or restored after being closed: a user following today lands on today; a past day they picked stays. */
    var selectedDayKey by mutableStateOf(
        restoreSelectedDay(savedState).let { resolveSelectedDay(it.dayKey, it.followingToday, returning = true, clock(), zone, dayStartHour) }
    ); private set
    var slot by mutableStateOf(savedState.get<String>("meal_slot")?.let { runCatching { MealSlot.valueOf(it) }.getOrNull() }); private set
    var customOpen by mutableStateOf(false); private set
    var editingLibraryFoodId by mutableStateOf<String?>(null); private set
    val draft = FoodDraft()
    /** The food form as it opened; Back asks before discarding only when [draft] differs from it. */
    private var draftBaseline by mutableStateOf(FoodDraftFields())
    var discardFoodOpen by mutableStateOf(false); private set
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
    /** The field New food moves focus to after a refused save; consumed by the form. */
    internal var foodFormFocus by mutableStateOf<FoodFormFocus?>(null); private set
    private var foodFormFocusSerial = 0L
    fun foodFormFocusHandled() { foodFormFocus = null }
    /** The failure shown is "may have been sent": the answer to a sent contribution was lost. */
    var contributionUncertain by mutableStateOf(false); private set
    var contributionUsername by mutableStateOf(""); private set
    var contributionPassword by mutableStateOf(""); private set
    var contributionBusy by mutableStateOf(false); private set
    var contributionPreflightBusy by mutableStateOf(false); private set
    private var contributionPreflightComplete by mutableStateOf(false)
    private var contributionPreflightToken = 0L
    var contributionLanguageCode by mutableStateOf("en"); private set
    var contributionPackageQuantityMl by mutableStateOf(""); private set
    internal var undoOffer by mutableStateOf<UndoOffer?>(null); private set
    private var undoExpiryJob: Job? = null
    val undoEntry: MealEntry? get() = (undoOffer as? UndoOffer.Deleted)?.entry
    val loggedBatchMessage: String? get() = (undoOffer as? UndoOffer.Logged)?.message
    var mealDraftError by mutableStateOf<String?>(null); private set
    /** The full-screen Log page. [logPreset] comes from the `+` or composer that opened it; [logChosen] from the Log's meal menu. */
    var logOpen by mutableStateOf(false); private set
    var logPreset by mutableStateOf<MealSlot?>(null); private set
    var logChosen by mutableStateOf<MealSlot?>(null); private set
    /** A "Not in your foods" part sent to the library: picking a food there puts it back in the draft, not in the log. */
    var logResolve by mutableStateOf<LogResolveRequest?>(null); private set
    /** Foods picked from the library this session, by the part text they now read as; the planner resolves those parts to them. */
    // Observable, so a pin added without a text change still recomputes the Log page's draft.
    private val logPins = mutableStateMapOf<String, FoodRecord>()
    /** The pins as they are now, for a draft computed off the main thread. */
    internal fun logPinsSnapshot(): Map<String, FoodRecord> = logPins.toMap()
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
    private var offSearchJob: Job? = null
    private var offSearchGeneration = 0L
    var libraryBatchMealPickerOpen by mutableStateOf(false); private set
    var selectedEntry by mutableStateOf<MealEntry?>(null); private set
    private var selectedEntryId: String? = savedState["entry_id"]
    private var barcodeRequestToken = 0L
    /** The barcode lookup in flight; leaving the scanner cancels it, which also stops its Open Food Facts request. */
    private var barcodeLookupJob: Job? = null
    private var rescanningBarcode = false
    private var barcodeOriginDay = selectedDayKey
    private var barcodeOriginMeal: MealSlot? = slot
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    /** Storage failed to open; the dialog stays until a retry opens it. */
    var storageUnavailable by mutableStateOf(false); private set
    /** A diary file that passed every check, waiting for Replace diary. */
    internal var pendingImport by mutableStateOf<DiaryImport.Valid?>(null); private set
    var entryEditMode by mutableStateOf(EntryEditMode.NONE); private set
    var editingEntryId by mutableStateOf<String?>(null); private set
    var editingFoodName by mutableStateOf<String?>(null); private set
    var editingFoodId by mutableStateOf<String?>(null); private set
    var editingFoodPickerOpen by mutableStateOf(false); private set
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
    /** A word still being typed that Add held back (Count off) while a resolve queue rewrites the draft; it stays last. */
    private var logHeldPart: String? = null
    /** The entries the last batch saved, for the resolve header. Not the Undo, which expires. */
    private var loggedBatchIds: List<String> = emptyList()

    init {
        if (selectedDayKey == todayKey) followingToday = true
        saveSelectedDay(savedState, SavedDay(selectedDayKey, followingToday))
        // Typed work comes back after the process was killed: the log draft, and an open food form.
        restoreLogDraft(savedState).let { logDraft = it.text; logDraftCommitted = it.committed }
        restoreFoodForm(savedState)?.let { form ->
            draft.restore(form.draft)
            draftBaseline = form.baseline
            editingLibraryFoodId = form.editingFoodId
            customOpen = true
        }
        // Every change is mirrored, so an added or cleared draft and a closed or saved form drop their keys.
        viewModelScope.launch { snapshotFlow { SavedLogDraft(logDraft, logDraftCommitted) }.collect { saveLogDraft(savedState, it) } }
        viewModelScope.launch { snapshotFlow { foodFormToSave() }.collect { saveFoodForm(savedState, it) } }
        openStore()
    }
    private var storeOpening = false
    /** Opens storage; on failure the "Storage unavailable" dialog offers [retryStorage]. The cause goes to the log only. */
    private fun openStore() {
        if (ready || storeOpening) return
        storeOpening = true
        viewModelScope.launch {
            val opened = openStorage({ engine.initialize(); refresh() }) { Log.e(LOG_TAG, "Storage could not be opened", it) }
            storeOpening = false
            storageUnavailable = !opened
            if (opened) {
                ready = true
                // A restored Edit food whose food is gone has nothing to edit.
                if (customOpen && editingLibraryFoodId?.let { id -> foods.none { it.id == id } } == true) closeCustom()
            }
        }
    }
    /** "Try again" on the storage dialog: runs the same open again. */
    fun retryStorage() = openStore()
    /**
     * The open food form, for [saveFoodForm]. Not while a part is being resolved or a resolve queue is open: what Save
     * does there depends on state that does not come back, so a restored form would save somewhere else.
     */
    private fun foodFormToSave(): SavedFoodForm? =
        if (!customOpen || logResolve != null || resolutionContinuation) null
        else SavedFoodForm(editingLibraryFoodId, draft.state(), draftBaseline)
    private fun refresh() {
        foods = engine.foods()
        entries = engine.persistedMeal().entries
        selectedEntry = selectedEntryId?.let { id -> entries.firstOrNull { it.id == id } }
        // A write that committed but could not reload the diary is reloaded now, and the screens read it again.
        viewModelScope.launch { if (suspendRunCatching { engine.reloadIfStale() }.getOrDefault(false)) refresh() }
    }
    /** The 60-second loop: today is recomputed, while the selected day stays where it is. */
    fun reloadToday() { checkDay(returning = false); if (ready) refresh() }
    private var stopped = false
    /** The activity left the screen (backgrounded or closed). */
    fun onAppStopped() { stopped = true }
    /** The activity is on screen again after a stop: a user following today moves to the new today. */
    fun onAppStarted() {
        if (!stopped) return
        stopped = false
        checkDay(returning = true)
        if (ready) refresh()
    }
    /** Recomputes today and applies [resolveSelectedDay]; the selection moves only when [returning]. */
    private fun checkDay(returning: Boolean) {
        val now = clock()
        todayKey = dayKeyFor(now, zone, dayStartHour)
        val day = resolveSelectedDay(selectedDayKey, followingToday, returning, now, zone, dayStartHour)
        if (day != selectedDayKey) selectDay(day, following = true)
    }
    private fun todayNow(): String = dayKeyFor(clock(), zone, dayStartHour).also { todayKey = it }
    private fun selectDay(value: String, following: Boolean) {
        selectedDayKey = value
        followingToday = following
        saveSelectedDay(savedState, SavedDay(value, following))
        barcodeRequestToken++
    }
    private fun selectMeal(value: MealSlot?) { slot = value; savedState["meal_slot"] = value?.name }
    fun shiftSelectedDay(delta: Long) { goToDay(runCatching { LocalDate.parse(selectedDayKey).plusDays(delta).toString() }.getOrDefault(selectedDayKey)) }
    fun goToday() = goToDay(todayNow())
    /** Selects a day from the arrows, swipe or date picker. Days after today are refused, so Next stops at today. */
    fun goToDay(dayKey: String) {
        val today = todayNow()
        if (dayKey > today) return
        selectDay(dayKey, following = dayKey == today)
    }
    /**
     * The Settings page open over the list of groups; null is the list. Kept in the view model only, so it survives a
     * recreation (a theme change); after the process was killed Settings is closed anyway.
     */
    internal var settingsPage by mutableStateOf<SettingsPage?>(null); private set
    /** Settings, over the Library, on the list of groups; its arrow and Back return there. */
    fun openSettings() { settingsPage = null; settingsOpen = true }
    fun closeSettings() { settingsOpen = false; settingsPage = null }
    internal fun openSettingsPage(page: SettingsPage) { settingsPage = page }
    /** Back or the arrow in Settings: a page goes back by [settingsPageBack], the list closes Settings. */
    fun settingsBack() {
        val page = settingsPage ?: return closeSettings()
        settingsPage = settingsPageBack(page)
    }
    fun chooseLongDraft(mode: LongDraft) {
        longDraft = mode
        settings.longDraft = mode
    }
    /** The "Day starts at" stepper. Today follows the new start at once; a user on today stays on today. */
    fun shiftDayStart(delta: Int) {
        val hour = shiftDayStartHour(dayStartHour, delta)
        dayStartHour = hour
        settings.dayStartHour = hour
        checkDay(returning = true)
    }
    fun chooseFlashlightAtStart(on: Boolean) {
        flashlightAtStart = on
        settings.flashlightAtStart = on
    }
    fun chooseLookUpBarcodes(on: Boolean) {
        lookUpBarcodes = on
        settings.lookUpBarcodes = on
    }
    fun chooseShowContribute(show: Boolean) {
        showContribute = show
        settings.showContribute = show
    }
    internal fun chooseMotion(choice: MotionChoice) {
        motion = choice
        settings.motion = choice
    }
    internal fun chooseTextSize(choice: TextSizeChoice) {
        textSize = choice
        settings.textSize = choice
    }
    fun chooseHaptics(on: Boolean) {
        haptics = on
        settings.haptics = on
    }
    internal fun chooseTheme(choice: ThemeChoice) {
        theme = choice
        settings.theme = choice
    }
    fun chooseShowMacros(show: Boolean) {
        showMacros = show
        settings.showMacros = show
    }
    fun chooseShowMissingNote(show: Boolean) {
        showMissingNote = show
        settings.showMissingNote = show
    }
    internal fun chooseDecimalMark(mark: DecimalMark) {
        decimalMark = mark
        settings.decimalMark = mark
        DisplayDecimal.mark = mark
    }
    internal fun chooseUndoWindow(window: UndoWindow) {
        undoWindow = window
        settings.undoWindow = window
        // An offer showing now follows the new window from the moment it was made.
        undoOffer?.let { offerUndo(it); liveUndoOffer() }
    }
    internal fun chooseMealMode(mode: MealMode) {
        mealMode = mode
        settings.mealMode = mode
    }
    /** A "By time" stepper; a step that would break the order is refused (the stepper shows it disabled). */
    internal fun stepMealBound(meal: MealSlot, delta: Int) {
        val bounds = shiftMealBound(mealBounds, meal, delta)
        mealBounds = bounds
        settings.mealBounds = bounds
    }
    fun chooseCountTyping(on: Boolean) {
        countTyping = on
        settings.countTyping = on
        engine.countTyping = on
    }
    fun chooseLearnFromPicks(on: Boolean) {
        learnFromPicks = on
        settings.learnFromPicks = on
        engine.learnFromPicks = on
    }
    /** The meal the "Meal" setting gives a log with no chosen, typed or preset meal, at this moment; null for Ask. */
    private fun automaticMealNow(): MealSlot? =
        automaticMeal(mealMode, java.time.Instant.ofEpochMilli(clock()).atZone(zone).hour, mealBounds, lastUsedMeal(entries))
    /** The picker Settings asked for; IronApp, always composed, launches it so a result after recreation still lands. */
    var diaryPickerRequest by mutableStateOf<DiaryPicker?>(null); private set
    fun requestDiaryPicker(picker: DiaryPicker) { if (ready && !busy) diaryPickerRequest = picker }
    fun diaryPickerLaunched() { diaryPickerRequest = null }
    /** Waits until storage is open and no write is running; a picker result can arrive before either (after recreation). */
    private suspend fun awaitIdleStore() {
        while (!ready || busy) snapshotFlow { ready && !busy }.first { it }
    }
    /**
     * Export diary: writes every stored row and the settings to [uri], the new file the save picker just created. It
     * says "Diary exported." only after the copy was read back and imports; on any failure the file is deleted, so
     * no empty or partial file looks like a backup.
     */
    fun exportDiary(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            awaitIdleStore()
            busy = true
            val written = withContext(NonCancellable) {
                val problem = try {
                    val file = diaryFileOf(engine.exportRows(), DiarySettings(dayStartHour, longDraft), clock())
                    withContext(Dispatchers.IO) {
                        writeDiaryChecked(
                            file,
                            write = { bytes ->
                                val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri)
                                (out ?: error("no output stream")).use { it.write(bytes) }
                            },
                            readBack = { resolver.openInputStream(uri)?.use { readDiaryText(it) } },
                        )
                    }
                } catch (e: Exception) {
                    "write failed: $e"
                }
                if (problem != null) {
                    Log.e(LOG_TAG, "Diary export failed: $problem")
                    withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
                }
                problem == null
            }
            busy = false
            notice = if (written) DIARY_EXPORTED else DIARY_EXPORT_FAILED
        }
    }
    /** Import diary: reads and checks the whole file at [uri]; only a file that passes everything asks to replace. */
    fun importDiary(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            awaitIdleStore()
            busy = true
            val result = try {
                withContext(Dispatchers.IO) {
                    val text = (resolver.openInputStream(uri) ?: error("no input stream")).use { readDiaryText(it) }
                    if (text == null) DiaryImport.Invalid("file too big") else decodeDiary(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiaryImport.Invalid("unreadable: $e")
            } finally {
                busy = false
            }
            when (result) {
                is DiaryImport.Valid -> pendingImport = result
                is DiaryImport.Invalid -> { Log.w(LOG_TAG, "Diary import refused: ${result.reason}"); notice = DIARY_IMPORT_REFUSED }
            }
        }
    }
    fun cancelImport() { pendingImport = null }
    /**
     * Replace diary: the current diary is first saved to [SAFETY_COPY_NAME] in `no_backup` (no copy, no replace), then
     * every table becomes the file's rows in one transaction. The phone's settings stay as they are.
     */
    fun confirmImport() {
        val pending = pendingImport ?: return
        if (!ready || busy) return
        pendingImport = null
        busy = true
        val safetyDir = getApplication<Application>().noBackupFilesDir
        viewModelScope.launch {
            try {
                val replaced = withContext(NonCancellable) {
                    try {
                        val current = diaryFileOf(engine.exportRows(), DiarySettings(dayStartHour, longDraft), clock())
                        withContext(Dispatchers.IO) { writeSafetyCopy(safetyDir, current) }
                        engine.replaceDiary(pending.rows)
                        true
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Diary import failed; the stored diary is unchanged", e)
                        false
                    }
                }
                if (!replaced) {
                    notice = DIARY_IMPORT_REFUSED
                    return@launch
                }
                // Nothing from the old diary may be undone, pinned or confirmed into the new one.
                offerUndo(null); loggedBatchIds = emptyList(); logPins.clear(); feedbackIds = emptySet()
                refresh()
                refreshLibrary()
            } finally {
                busy = false
            }
        }
    }
    fun openLibrary() {
        cancelEdit(); selectMeal(null); libraryOpen = true; libraryQuery = ""; libraryArchivedOnly = false; refreshLibrary()
    }
    fun openArchivedLibrary() {
        cancelEdit(); selectMeal(null); libraryOpen = true; libraryQuery = ""; libraryArchivedOnly = true; refreshLibrary()
    }
    fun closeLibrary() = leaveLibrary(returnTo = null)
    /**
     * The Library's arrow and system Back, by [exitLibraryTarget]. A Library opened from a meal keeps that meal in
     * [slot], so [slot] is where it came from.
     */
    fun exitLibrary() {
        when (exitLibraryTarget(logResolve != null, libraryArchivedOnly, slot)) {
            LibraryExit.LIBRARY -> openLibrary()
            LibraryExit.MEAL -> leaveLibrary(returnTo = slot)
            LibraryExit.LOG, LibraryExit.DAILY -> closeLibrary()
        }
    }
    private fun leaveLibrary(returnTo: MealSlot?) {
        logResolve?.let { endResolve(it, it.draftText); return }
        libraryRefreshJob?.cancel(); libraryRefreshGeneration++; libraryOpen = false; selectedLibraryFood = null; libraryMealPickerFood = null; expandedLibraryFoodId = null; libraryPendingAmount = null; libraryPendingUnit = null; libraryPendingBatch = emptyList(); libraryBatchMealPickerOpen = false; libraryArchivedOnly = false; settingsOpen = false; settingsPage = null; selectMeal(returnTo)
        libraryQuery = ""
        libraryResultsQuery = null
        input = ""
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
    fun openLibraryFood(food: FoodRecord) {
        // While resolving, a tapped food is the pick, like its +: the part keeps its typed amount and meal.
        if (logResolve != null) { logLibraryFood(food); return }
        selectedLibraryFood = food
    }
    /** What is drawn over Daily, Meal or Library right now, by [topOverlay]. */
    internal fun topOverlay(): Overlay? = topOverlay(
        scanner = scannerOpen, entryDetail = selectedEntry != null, productReview = selectedProduct != null,
        resolver = resolverOpen, offSearch = searchOpen, foodForm = customOpen,
        libraryFood = selectedLibraryFood != null, entryEdit = entryEditMode != EntryEditMode.NONE,
        settings = settingsOpen,
    )
    /** A library search that finished with nothing and has no other screen over it: the "No local match" screen. */
    fun libraryNoMatch(): Boolean = isLibraryNoMatch(
        ready, libraryOpen, libraryQuery, libraryResultsQuery, libraryFoods.isEmpty(),
        otherScreenOpen = topOverlay() != null,
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
        launchBusy {
            suspendRunCatching { drafts.forEach { draft -> engine.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, capturedDayKey) } }
                .onSuccess { offerUndo(null); refresh(); refreshLibrary(); notice = "Added ${drafts.size} food${if (drafts.size == 1) "" else "s"} to ${target.label()} on $capturedDayKey" }
                .onFailure { notice = it.message ?: "Could not log this food." }
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
        launchBusy {
            suspendRunCatching { engine.archiveFood(food.id) }
                .onSuccess { selectedLibraryFood = null; if (editingLibraryFoodId == food.id) closeCustom(); refresh(); refreshLibrary(); notice = "Food archived; saved diary entries remain readable." }
                .onFailure { notice = it.message ?: "Could not archive this food." }
        }
    }
    fun restoreLibraryFood(food: FoodRecord) {
        if (!ready || busy || !food.archived) return
        launchBusy {
            suspendRunCatching { engine.restoreFood(food.id) }
                .onSuccess { refresh(); selectedLibraryFood = foods.firstOrNull { it.id == food.id }; refreshLibrary(); notice = "Food restored to your library." }
                .onFailure { notice = it.message ?: "Could not restore this food." }
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
        // A tap while the save runs is ignored, not reported as invalid.
        if (!ready || busy) return
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0 || foodId.isBlank()) {
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
                            offerUndo(null)
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
                    // This save owns busy until it ends, whatever the session is now (nothing else clears it).
                    busy = false
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
    private fun abandonUnknownResolution() {
        logHeldPart = null
        unknownQueue = emptyList()
        unknownQueueTarget = null
        unknownQueueTotal = 0
        resolutionContinuation = false
        resolverOpen = false
    }
    fun openMeal(s: MealSlot) { abandonUnknownResolution(); selectMeal(s); barcodeRequestToken++; cancelEdit(); mealDraftError = null }
    fun openFocusedLibrary(meal: MealSlot?, query: String = "") {
        abandonUnknownResolution()
        cancelEdit()
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
        // A row drawn from an older draft (a keystroke since) names a span that may no longer hold its text.
        if (!ready || busy || !rowStillInDraft(logDraft, row)) return
        val part = logDraft.substring(row.start, row.end)
        val query = engine.planner(logPins.toMap()).libraryQueryFor(part)
        logResolve = LogResolveRequest(logDraft, row.start, row.end, part, logPreset, logChosen, slot)
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
        discardFoodOpen = false
        selectedProduct = null
        logDraft = draftInput
        logDraftCommitted = false
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
        launchBusy {
            suspendRunCatching { engine.cacheFood(draft.food) }
                .onSuccess { stored ->
                    pendingContribution = contributionDraftAfterLocalSave(draft.food, fromScan)
                    selectedProductFromScan = false
                    refresh(); refreshLibrary()
                    finishResolve(stored)
                }
                .onFailure { notice = it.message ?: "Could not save this food." }
        }
    }

    fun openLibraryFoodDetail(entry: MealEntry) {
        closeEntryDetail()
        cancelEdit()
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
    /** Leaves the meal for Daily. The log draft and its pins stay. */
    fun closeMeal() {
        logResolve?.let { endResolve(it, it.draftText); return }
        abandonUnknownResolution(); selectMeal(null); barcodeRequestToken++; cancelEdit() }
    /** The keyboard's Done: the draft is committed, so its last word is no longer being typed. */
    fun commitLogDraft() { logDraftCommitted = true }
    /** The Log page's typing. Clearing the draft also drops the foods picked for it. */
    fun onLogDraft(v: String) {
        logDraft = v
        logDraftCommitted = false
        if (v.isBlank()) logPins.clear()
    }
    fun onInput(v: String) {
        input = v
        if (libraryOpen) {
            libraryQuery = librarySearchText(v, parser)
            refreshLibrary()
        }
    }
    /** The meal the Log page writes to, by [logTargetMealOf] precedence. Also drives the picker label. */
    internal fun logTargetMeal(): MealSlot? = logTargetMealOf(logChosen, mealMarkers(logDraft), logPreset, automaticMealNow())
    /**
     * Everything the Log page shows for the current draft, computed in one pass from one planner, so the rows,
     * the choices and the add-bar sum all come from the same resolution.
     */
    internal fun logDraftView(
        text: String = logDraft,
        committed: Boolean = logDraftCommitted,
        pins: Map<String, FoodRecord> = logPins.toMap(),
    ): LogDraftView {
        val planner = engine.planner(pins)
        // One planner draft: rows, the choice group, suggestions and the add-bar sum all come from the same resolution.
        val draft = if (ready) planner.draft(text, typing = !committed) else DraftView.EMPTY
        return LogDraftView(
            rows = draft.rows,
            choices = draft.choices,
            choiceHint = draft.choiceHint,
            preselected = draft.preselected,
            activePhrase = draft.activePhrase,
            // The sum is the draft's own count: what Add would save.
            logged = draft.logged,
            // Each choice's kcal from the same planner and text as the sum, so the two never disagree.
            choiceCalculations = draft.choices.map { planner.calculationFor(draft.activeText, it.food) },
        )
    }

    /**
     * Tapping a choice or a suggestion: the typed part becomes the amount, unit and name it would log, pinned to the
     * tapped food so it stays that food. Pins live as long as the draft (cleared on Add or an empty draft).
     */
    fun chooseQuickSuggestion(food: FoodRecord) {
        val chosen = engine.planner(logPins.toMap()).choose(logDraft, food, typing = !logDraftCommitted)
        chosen.part?.let { logPins[it] = food }
        onLogDraft(chosen.input)
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
        launchBusy {
            suspendRunCatching { engine.save(cleaned, mealSlot = target, dayKey = capturedDayKey) }
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
                    offerLoggedUndo("${saved.entries.size} food${if (saved.entries.size == 1) "" else "s"} logged to ${target.label()}", saved.entries)
                }
                .onFailure { notice = it.message ?: "Could not save this batch." }
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
        input = "${entry.enteredAmount.plain()} ${entry.enteredUnit.displayLabel()}"
        mealDraftError = null
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
        input = "${entry.enteredAmount.plain()} ${entry.enteredUnit.displayLabel()} ${entry.foodName}"
        mealDraftError = null
        quickHintSession += 1
        quickEditHintVisible = false
    }
    fun cancelEdit() {
        if (entryEditMode == EntryEditMode.FULL && fullSaveJob?.isActive == true) return
        fullSaveJob?.cancel()
        fullSaveJob = null
        // Leaves busy alone: only the write that set it clears it, so a quick-edit save in flight still blocks a second write.
        editSession += 1
        entryEditMode = EntryEditMode.NONE
        editingEntryId = null
        editingFoodName = null
        editingFoodId = null
        editingFoodPickerOpen = false
        input = ""
        quickHintSession += 1
        quickEditHintVisible = false
        if (editReturnToDaily) selectMeal(null)
        editReturnToDaily = false
        mealDraftError = null
    }
    /** An entry edit could not be saved: the message stays until the edit changes ([editInputChanged]). */
    private fun markEditInvalid(message: String) {
        mealDraftError = message
    }
    /** The amount, unit, food, meal or date being edited changed, so an earlier save error no longer applies. */
    fun editInputChanged() {
        if (entryEditMode != EntryEditMode.NONE) mealDraftError = null
    }
    /** Add on the Log page, or Save in quick edit, which reads [input] and never the draft. */
    fun submit() {
        // Add finishes the draft, unless a word still being typed waits until it is finished or picked (Count off).
        if (entryEditMode == EntryEditMode.NONE && countTyping) logDraftCommitted = true
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
        when (val plan = logSubmitPlan(ready, busy, logDraft, mealMarkers(logDraft), logChosen, logPreset, automaticMealNow())) {
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

    /** Closes the Log page. The draft stays in [logDraft]. */
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

    /** Library's mixed batch without a meal: the chosen meal saves the bar's text, as it does when the meal is known. */
    fun chooseMixedMeal(target: MealSlot) {
        mixedMealPickerOpen = false
        val raw = input.trim()
        if (raw.isBlank() || !ready || busy) return
        submitLibraryMixedInput(raw, target)
    }

    private fun submitToMeal(target: MealSlot) {
        if (!ready || busy) return
        val editing = editingEntryId
        val mode = entryEditMode
        if (editing != null && mode != EntryEditMode.NONE) {
            val session = editSession
            launchBusy {
                suspendRunCatching {
                    if (mode == EntryEditMode.QUICK) engine.replaceEntryAmountUnit(editing, input)
                    else engine.replaceEntryFromText(editing, input)
                }.onSuccess { result ->
                    if (entryEditMode != mode || editingEntryId != editing || editSession != session) return@onSuccess
                    when (result) {
                        is ReplacementResult.Success -> { offerUndo(null); refresh(); cancelEdit() }
                        is ReplacementResult.Failure -> markEditInvalid(result.reason)
                    }
                }.onFailure {
                    if (entryEditMode == mode && editingEntryId == editing && editSession == session) {
                        markEditInvalid(it.message ?: "Could not edit this entry.")
                    }
                }
            }
            return
        }
        // Save takes the draft as typed: the planner skips meal words by span, so save and the rows share one segmentation.
        val captured = logDraft
        val capturedPins = logPins.toMap()
        // Add saves what the add bar counts: with Count off, a word still being typed is held in the draft.
        val typing = !logDraftCommitted
        val planner = engine.planner(capturedPins)
        val held = planner.heldPart(captured, typing)
        if (planner.countedParts(captured, typing).none { ShorthandLexicon.mealSlotOf(it) == null }) {
            mealDraftError = "Add at least one food."
            return
        }
        launchBusy {
            suspendRunCatching { engine.save(captured, mealSlot = target, dayKey = selectedDayKey, pins = capturedPins, typing = typing) }.onSuccess { saved ->
                // Nothing resolved: nothing was saved, so the Log stays open with the typed text and says what to fix.
                if (saved.entries.isEmpty() && saved.unresolvedSegments.isNotEmpty()) {
                    mealDraftError = "Some foods need a match."
                    return@onSuccess
                }
                // Only the pins this save used: one added while it planned off the main thread belongs to the new text.
                capturedPins.forEach { (part, food) -> if (logPins[part] == food) logPins.remove(part) }
                closeLog()
                refresh(); if (logDraft == captured) logDraft = (saved.unresolvedSegments + listOfNotNull(held)).joinToString(", "); logHeldPart = held.takeIf { saved.unresolvedSegments.isNotEmpty() }; unknownQueue = saved.unresolvedSegments; unknownQueueTotal = saved.unresolvedSegments.size; unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }; resolutionContinuation = saved.unresolvedSegments.isNotEmpty(); resolverOpen = saved.unresolvedSegments.isNotEmpty(); mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
                feedbackIds = saved.entries.filter { it.approximateConversion }.mapTo(mutableSetOf()) { it.id }; feedbackUntil = if (feedbackIds.isEmpty()) 0L else System.currentTimeMillis() + 30_000L
                loggedBatchIds = saved.entries.map { it.id }
                offerLoggedUndo("${saved.entries.size} food${if (saved.entries.size == 1) "" else "s"} added to ${target.label()}", saved.entries)
            }.onFailure { mealDraftError = it.message ?: "Could not save this meal." }
        }
    }
    private fun mealMarkers(value: String): List<MealSlot> = mealMarkerSlots(value)
    /**
     * Runs [block] as the one write that owns [busy]: set now, before the launch, and cleared in finally when the
     * write ends, so a throwing success or failure handler cannot leave the app stuck busy.
     */
    private fun launchBusy(block: suspend () -> Unit) {
        busy = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }
    /** Replaces the Undo offer; null clears it. An offer clears itself when its window ends. */
    private fun offerUndo(offer: UndoOffer?) {
        undoExpiryJob?.cancel()
        undoExpiryJob = null
        undoOffer = offer
        if (offer == null) return
        undoExpiryJob = viewModelScope.launch {
            while (true) {
                // "Until next action": no timer, the next log, edit or delete replaces the offer.
                val window = undoWindowMs(undoWindow) ?: return@launch
                val left = undoRemainingMs(offer.offeredAtMs, clock(), window)
                if (left <= 0L) break
                delay(left)
            }
            if (undoOffer === offer) undoOffer = null
        }
    }
    /** A batch that saved nothing leaves the current offer alone. */
    private fun offerLoggedUndo(message: String, entries: List<MealEntry>) {
        val next = undoAfterLog(undoOffer, if (entries.isEmpty()) null else UndoOffer.Logged(message, entries, clock()))
        if (next !== undoOffer) offerUndo(next)
    }
    /** The offer, while its window is open; an expired one is cleared. */
    private fun liveUndoOffer(): UndoOffer? {
        val offer = undoOffer ?: return null
        if (undoOpen(offer.offeredAtMs, clock(), undoWindowMs(undoWindow))) return offer
        offerUndo(null)
        return null
    }
    fun delete(entry: MealEntry) {
        if (!ready || busy) return
        launchBusy {
            suspendRunCatching { engine.deleteEntry(entry.id) }
                .onSuccess { offerUndo(UndoOffer.Deleted(entry, clock())); refresh() }
                .onFailure { notice = "Could not edit this entry." }
        }
    }
    /** Puts the deleted entry back with its own id and snapshot. */
    fun undo() {
        val offer = liveUndoOffer() as? UndoOffer.Deleted ?: return
        if (!ready || busy) return
        launchBusy {
            suspendRunCatching { engine.restoreEntry(offer.entry) }
                .onSuccess { offerUndo(null); refresh() }
                .onFailure { notice = UNDO_FAILED }
        }
    }
    /** Deletes the logged batch in one transaction; an entry edited since it was logged stays. */
    fun undoLoggedBatch() {
        val offer = liveUndoOffer() as? UndoOffer.Logged ?: return
        if (!ready || busy) return
        launchBusy {
            suspendRunCatching { engine.deleteEntries(offer.entries) }
                .onSuccess { loggedBatchIds = emptyList(); offerUndo(null); refresh() }
                .onFailure { notice = UNDO_FAILED }
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
        draftBaseline = draft.fields()
        discardFoodOpen = false
        customOpen = true
        searchOpen = false
        resolverOpen = false
    }
    /** Back or the arrow on New food or Edit food: a changed form asks before it is discarded. */
    fun leaveCustom() {
        when (foodFormBack(saving = busy, dirty = draft.isDirty(draftBaseline))) {
            FoodFormBack.IGNORE -> Unit
            FoodFormBack.ASK -> discardFoodOpen = true
            FoodFormBack.CLOSE -> closeCustom()
        }
    }
    fun keepEditingFood() { discardFoodOpen = false }
    fun discardFood() { if (busy) return; discardFoodOpen = false; closeCustom() }
    fun closeCustom() {
        customOpen = false; editingLibraryFoodId = null; rescanningBarcode = false; discardFoodOpen = false; draft.reset()
        if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false
    }
    fun commitDraftServing() { draft.commitServing() }
    fun beginBarcodeRescan() { rescanningBarcode = true; beginScan() }
    fun openLibraryEdit(food: FoodRecord) {
        rescanningBarcode = false
        editingLibraryFoodId = food.id
        draft.load(food)
        draftBaseline = draft.fields()
        discardFoodOpen = false
        customOpen = true; selectedLibraryFood = null
    }
    fun closeResolver() { resolverSearchJob?.cancel(); abandonUnknownResolution(); resolverSearchOpen = false }
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
    fun dismissContributionFailure() { contributionFailure = null; contributionUncertain = false; pendingContribution = null }
    fun retryContribution() {
        val uncertain = contributionUncertain
        contributionFailure = null
        contributionUncertain = false
        val draft = pendingContribution
        // After a lost answer the product is read again first, so a retry starts from what Open Food Facts now holds.
        if (uncertain && draft != null) { contributionPreflightComplete = false; startContributionPreflight(draft) }
        else contributionAuthOpen = true
    }
    fun onContributionUsername(v: String) { contributionUsername = v }
    fun onContributionPassword(v: String) { contributionPassword = v }
    fun onContributionLanguage(v: String) {
        contributionLanguageCode = v
        pendingContribution = pendingContribution?.copy(languageCode = v.trim().lowercase())
    }
    fun onContributionPackageQuantityMl(v: String) {
        contributionPackageQuantityMl = v
        pendingContribution = pendingContribution?.copy(packageQuantityMl = parseDecimal(v))
    }
    fun submitContribution() {
        val draft = pendingContribution ?: return
        if (contributionBusy) return
        if (contributionUsername.isBlank() || contributionPassword.isBlank()) {
            contributionFailure = "Enter your Open Food Facts username and password to submit."
            contributionUncertain = false
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
                    OffContributionResult.Uncertain -> { contributionAuthOpen = false; contributionFailure = CONTRIBUTION_UNCERTAIN }
                    is OffContributionResult.Error -> { contributionAuthOpen = false; contributionFailure = result.message }
                }
                contributionUncertain = result == OffContributionResult.Uncertain
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
        discardFoodOpen = false
        resolverOpen = false
        searchQuery = prefill ?: ""
        offResults = emptyList()
        offSearchMessage = null
        offSearchUnavailable = false
    }
    fun closeSearch() { offSearchJob?.cancel(); offSearchGeneration++; searchBusy = false; searchOpen = false; if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
    fun onSearch(v: String) { offSearchJob?.cancel(); offSearchGeneration++; searchBusy = false; searchQuery = v; offResults = emptyList(); offSearchMessage = null; offSearchUnavailable = false }
    private var resolverSearchJob: Job? = null
    /** The resolver's search, ranked off the main thread; a result is shown only while its query is still the one typed. */
    fun onResolverQuery(v: String) {
        resolverQuery = v
        resolverSearchJob?.cancel()
        // The previous query's rows go at once, so none can be tapped for the new text while it is ranked.
        resolverResults = emptyList()
        if (v.isBlank()) return
        resolverSearchJob = viewModelScope.launch {
            val results = withContext(Dispatchers.Default) { engine.search(v) }
            if (resolverQuery == v) resolverResults = results
        }
    }
    private fun advanceUnknownQueue() {
        // A search still running was ranked before this food was added; it must not fill the next unknown's list.
        resolverSearchJob?.cancel()
        unknownQueue = unknownQueue.drop(1)
        // What is left to resolve stays where it was typed: the Library bar, or the log draft.
        if (libraryOpen) input = unknownQueue.joinToString(", ") else logDraft = (unknownQueue + listOfNotNull(logHeldPart)).joinToString(", ")
        resolverQuery = ""
        resolverResults = emptyList()
        resolverSearchOpen = false
        resolverOpen = unknownQueue.isNotEmpty()
        if (unknownQueue.isEmpty()) {
            unknownQueueTarget = null
            resolutionContinuation = false
            logHeldPart = null
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
        launchBusy {
            suspendRunCatching { engine.cacheFoodAndLog(food, amount, unit, target, capturedDayKey) }
                .onSuccess { offerUndo(null); advanceUnknownQueue(); refresh() }
                .onFailure { notice = it.message ?: "Could not resolve this food." }
        }
    }
    /** Leaves the current unresolved food unlogged and moves to the next one. */
    fun skipCurrentUnknown() { if (unknownQueue.isNotEmpty()) advanceUnknownQueue() }
    /** Names of the foods the batch that produced this queue already saved, for the resolve header. */
    fun savedBatchNames(): List<String> = loggedBatchIds.mapNotNull { id -> entries.firstOrNull { it.id == id }?.foodName }
    fun feedbackOpen(entry: MealEntry): Boolean = entry.id in feedbackIds && System.currentTimeMillis() < feedbackUntil
    fun feedbackCount(): Int = feedbackIds.count { id -> entries.any { it.id == id } && System.currentTimeMillis() < feedbackUntil }
    fun alternatives(entry: MealEntry): List<RankedFood> = if (feedbackOpen(entry)) engine.alternativesFor(entry) else emptyList()
    fun confirm(entry: MealEntry) { if (!ready || busy || !feedbackOpen(entry)) return; launchBusy { suspendRunCatching { engine.reinforce(entry.foodId) }.onSuccess { feedbackIds = feedbackIds - entry.id } } }
    /** A "Not right?" pick: the entry becomes [food], which is reinforced. A refused replacement says why and reinforces nothing. */
    fun chooseAlternative(entry: MealEntry, food: FoodRecord) {
        if (!ready || busy || !feedbackOpen(entry)) return
        launchBusy {
            suspendRunCatching {
                engine.replaceEntry(entry.id, food.id).also { if (it is ReplacementResult.Success) engine.reinforce(food.id) }
            }.onSuccess { result ->
                when (result) {
                    is ReplacementResult.Success -> { feedbackIds = feedbackIds - entry.id; refresh() }
                    is ReplacementResult.Failure -> notice = result.reason
                }
            }
        }
    }
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
        launchBusy {
            suspendRunCatching { engine.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, capturedDayKey) }
                .onSuccess {
                    offerUndo(null)
                    selectedProduct = null
                    pendingContribution = contributionDraftAfterLocalSave(draft.food, fromScan)
                    selectedProductFromScan = false
                    selectedProductOrigin = ProductReviewOrigin.LIBRARY
                    if (resolvesQueue) advanceUnknownQueue() else resolutionContinuation = false
                    refresh(); refreshLibrary()
                    notice = "Added to ${target.label()}"
                }
                .onFailure { notice = it.message ?: "This amount cannot be logged." }
        }
    }

    fun saveSelectedToLibrary() {
        val food = selectedProduct ?: return
        if (logResolve != null) { resolveWithProduct(); return }
        if (!canSaveProductToLibrary(slot, resolutionContinuation, unknownQueue.isNotEmpty())) return
        if (!ready || busy) return
        val draft = productDraft(food) ?: run { notice = "Enter a positive amount, a supported unit, and valid calories."; return }
        launchBusy {
            suspendRunCatching { engine.updateFood(draft.food) }
                .onSuccess {
                    selectedProduct = null
                    selectedProductFromScan = false
                    selectedProductOrigin = ProductReviewOrigin.LIBRARY
                    refresh()
                    refreshLibrary()
                    notice = "Saved ${draft.food.name} to your food library."
                }
                .onFailure { notice = it.message ?: "Could not save this food." }
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
    fun endScan() { barcodeRequestToken++; barcodeLookupJob?.cancel(); barcodeLookupJob = null; scannerOpen = false; scannerUnavailableState = false; scannerUnavailableReason = null; scannerLookupBusy = false; scannerLookupCode = ""; rescanningBarcode = false }
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
    /** [code] comes from the scanner or manual entry, already a product barcode; anything else is ignored before any lookup or notice. */
    fun onBarcode(code: String) {
        val barcode = normalizeBarcode(code) ?: return
        scannerUnavailableState = false
        scannerUnavailableReason = null
        if (rescanningBarcode) {
            rescanningBarcode = false
            scannerOpen = false
            draft.barcode = barcode; draft.barcodeFromScan = true
            return
        }
        val token = barcodeRequestToken
        val originDay = barcodeOriginDay
        val originMeal = barcodeOriginMeal
        val reviewOrigin = productReviewOrigin(originMeal, resolutionContinuation, unknownQueue.isNotEmpty())
        val step = unknownBarcodeStep(lookUpBarcodes)
        scannerLookupCode = barcode
        scannerLookupBusy = true
        barcodeLookupJob = viewModelScope.launch(Dispatchers.IO) {
            val local = engine.foodByBarcode(barcode)
            if (local != null) {
                withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; chooseProduct(local, fromScan = true, origin = reviewOrigin) } }
                return@launch
            }
            // "Look up unknown barcodes" Off: straight to the manual product form, no network request.
            if (step == UnknownBarcodeStep.MANUAL_FORM) {
                withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; openCustom(barcode = barcode) } }
                return@launch
            }
            when (val result = off.barcode(barcode)) {
                is OffResult.Found -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; chooseProduct(result.food, fromScan = true, origin = reviewOrigin) } }
                is OffResult.Ineligible -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notice = "Nutrition is incomplete. Review it as a personal food."; openCustom(result.name.orEmpty(), result.brand.orEmpty(), result.barcode, fromOff = true) } }
                OffResult.Missing -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notice = "Product not found. Add it manually with barcode $barcode"; openCustom(barcode = barcode) } }
                OffResult.Unavailable -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notice = "No connection. Add the product manually."; openCustom(barcode = barcode) } }
                is OffResult.Error -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notice = result.message; openCustom(barcode = barcode) } }
            }
        }
    }
    fun saveCustom() {
        // A tap while the save runs is ignored, not reported as invalid.
        if (!ready || busy) return
        val name = draft.name; val brand = draft.brand; val barcode = draft.barcode.trim()
        val aliasList = draft.allAliases(); val servingList = draft.servings
        val input = draft.nutritionInput()
        val resolved = resolveNutrition(input)
        val editingId = editingLibraryFoodId
        val originalBarcode = editingId?.let { id -> foods.firstOrNull { it.id == id }?.barcode?.trim() }
        val barcodeUnchanged = barcode == originalBarcode || sameBarcode(barcode, originalBarcode)
        val barcodeError = if (barcodeUnchanged) null else validateBarcodeInput(barcode, foods, editingId)
        // A refused save names the problem and moves focus to the field to fix (New food reads the request).
        foodSaveRefusal(name, input, resolved, barcodeError)?.let { refusal ->
            notice = refusal.message
            if (editingId == null) foodFormFocus = FoodFormFocus(refusal.field, ++foodFormFocusSerial)
            return
        }
        val kcal = resolved.calories ?: return
        // A new or changed code is stored in its canonical form; an unchanged one keeps the spelling it has.
        val storedBarcode = if (barcodeUnchanged && originalBarcode != null) originalBarcode else canonicalBarcode(barcode) ?: barcode
        val basisAmount = resolved.basisAmount
        val basisUnit = resolved.basisUnit
        val nutrition = NutritionValues(kcal, resolved.protein, resolved.carbohydrates, resolved.fat)
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
        }?.takeIf { plausibleNutrition(it.normalizedNutrition) } else null
        val offSource = fromOff.takeIf { it }?.let { "Open Food Facts" }
        val aliasText = aliasList.joinToString(", ")
        val capturedDayKey = selectedDayKey
        launchBusy { suspendRunCatching {
            if (editingId != null) {
                val original = foods.firstOrNull { it.id == editingId } ?: error("That personal food is no longer available.")
                engine.updateFood(editedCustomFoodRecord(original, name, brand, storedBarcode, aliasText, nutrition, basisAmount, basisUnit, aliases = aliasList, servings = servingList))
                original
            } else {
                val target = saveTarget
                if (target != null) {
                    val entry = engine.saveCustomAndLog(name, brand, storedBarcode, aliasText, nutrition, basisAmount, basisUnit, target, basisAmount, basisUnit, dayKey = capturedDayKey, sourceName = offSource, sourceDescription = "Saved from an incomplete Open Food Facts barcode result")
                    engine.foodById(entry.foodId) ?: error("Saved food could not be reloaded.")
                } else engine.saveCustomFood(name, brand, storedBarcode, aliasText, nutrition, basisAmount, basisUnit, sourceName = offSource, sourceDescription = "Saved from an incomplete Open Food Facts barcode result")
            }
        }.onSuccess { saved ->
            if (editingId == null && saveTarget != null) offerUndo(null)
            refresh()
            customOpen = false
            discardFoodOpen = false
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
        }.onFailure { notice = it.message ?: "Could not save this food." } }
    }
}

/** The body of the contribution failure screen when the answer to a sent contribution was lost. */
private const val CONTRIBUTION_UNCERTAIN = "Check Open Food Facts before sending again."

/** The notice when an Undo could not be applied. */
private const val UNDO_FAILED = "Could not undo."
private const val LOG_TAG = "Breakfast"
/** Any file may be offered: a diary arrives as JSON, as a plain binary from a file manager, or as text from a messenger or email. Import checks it in full anyway. */
private val DIARY_FILE_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")
private const val DIARY_EXPORTED = "Diary exported."
private const val DIARY_EXPORT_FAILED = "Could not export the diary."
private const val DIARY_IMPORT_REFUSED = "That file can't be imported."

/** A request to focus [field]; [serial] makes a repeated refusal of the same field a new request. */
internal data class FoodFormFocus(val field: FoodFormField, val serial: Long)

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
        ?.let(::splitAliases)
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
    /** True exactly when Add saves the part being typed; the first choice or suggestion is then the food it saves. */
    val preselected: Boolean,
    val activePhrase: String,
    /** What Add will save; the add bar's sum and count come from this. */
    val logged: List<SegmentResolution>,
    /** The kcal each of [choices] would log, in the same order. */
    val choiceCalculations: List<Calculation> = emptyList(),
)

/** What Add saves for the Log's [input]: the same plan the draft's sum uses. Done and Add never open library search. */
internal fun draftLogPlan(input: String, planner: LogPlanner): LogPlan = planner.plan(input)

internal fun MealSlot.label() = name.lowercase().replaceFirstChar { it.titlecase() }
/** A number for a field the user edits: whole without ".0", else one decimal, always with a point (input reads both marks). */
internal fun Double.plain() = if (this % 1 == 0.0) "%.0f".format(Locale.ROOT, this) else "%.1f".format(Locale.ROOT, this)
/** [plain] as shown on screen: with the "Decimal mark" setting's mark. */
internal fun Double.pretty() = markDecimals(plain(), DisplayDecimal.mark)

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
    if (vm.searchOpen) OffSearchScreen(vm)
    if (vm.resolverOpen && !scanReviewBackdropVisible) ResolveUnknownScreen(vm)
    vm.selectedProduct?.let { ProductReviewSheet(vm, it) }
    if (vm.contributionReviewOpen) vm.pendingContribution?.let { ContributionReviewScreen(vm, it) }
    if (vm.contributionAuthOpen) ContributionSignInScreen(vm)
    vm.contributionFailure?.let { ContributionFailureScreen(vm, it) }
    vm.notice?.let { message -> AlertDialog(onDismissRequest = vm::dismissNotice, confirmButton = { TextButton(vm::dismissNotice) { Text("OK") } }, text = { Text(message) }) }
    if (vm.pendingImport != null) AlertDialog(
        onDismissRequest = vm::cancelImport,
        confirmButton = { TextButton(vm::confirmImport) { Text("Replace diary") } },
        dismissButton = { TextButton(vm::cancelImport) { Text("Cancel") } },
        text = { Text("This replaces entries and foods, not settings.") },
    )
    if (vm.storageUnavailable) AlertDialog(onDismissRequest = {}, confirmButton = { TextButton(vm::retryStorage) { Text("Try again") } }, title = { Text("Storage unavailable") }, text = { Text("Storage could not be opened.") })
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




/** The 48dp outline under a scanned product's log, as M-Food-Detail draws it. */
@Composable internal fun ContributionAction(vm: BreakfastViewModel) {
    SecondaryButton("Contribute to Open Food Facts", vm::reviewContribution)
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
                    if (draft.basisUnit == MeasureUnit.MILLILITER) Field("Package quantity, ml", vm.contributionPackageQuantityMl, { vm.onContributionPackageQuantityMl(it.decimalInput()) }, Modifier.padding(top = 16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
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
                Text(if (vm.contributionUncertain) "May have been sent" else "Not sent", color = colors.ink, style = BreakfastType.Title)
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
