package com.iron.breakfast.app

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.domain.dayKeyFor
import com.iron.breakfast.settings.DisplayDecimal
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.data.OpenFoodFactsClient
import com.iron.breakfast.domain.ShorthandParser
import com.iron.breakfast.domain.NutritionCalculator
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.resolveSelectedDay
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.food.FoodDraft
import com.iron.breakfast.food.FoodDraftFields
import com.iron.breakfast.scan.ScannerUnavailableReason
import com.iron.breakfast.scan.ProductReviewOrigin
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.food.FoodFormFocus
import com.iron.breakfast.today.UndoOffer
import com.iron.breakfast.resolve.LogResolveRequest
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.library.LibraryLogDraft
import com.iron.breakfast.data.DiaryImport
import com.iron.breakfast.entry.EntryEditMode
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.openStorage
import com.iron.breakfast.suspendRunCatching
import com.iron.breakfast.SettingsPage
import com.iron.breakfast.settingsPageBack
import com.iron.breakfast.ui.components.LongDraft
import com.iron.breakfast.settings.shiftDayStartHour
import com.iron.breakfast.settings.ExportReminder
import com.iron.breakfast.settings.exportReminderDue
import com.iron.breakfast.settings.exportReminderOnScreen
import com.iron.breakfast.transfer.DiaryPicker
import com.iron.breakfast.data.deleteSafetyCopy
import com.iron.breakfast.settings.deleteAllAllowed
import com.iron.breakfast.settings.safetyCopyStep
import com.iron.breakfast.settings.SafetyCopyStep
import com.iron.breakfast.data.diaryFileOf
import com.iron.breakfast.data.DiarySettings
import com.iron.breakfast.data.writeSafetyCopy
import com.iron.breakfast.transfer.DIARY_DELETE_FAILED
import com.iron.breakfast.settings.MotionChoice
import com.iron.breakfast.settings.TextSizeChoice
import com.iron.breakfast.settings.ThemeChoice
import com.iron.breakfast.settings.DecimalMark
import com.iron.breakfast.settings.UndoWindow
import com.iron.breakfast.settings.MealMode
import com.iron.breakfast.settings.shiftMealBound
import com.iron.breakfast.settings.BeforeDayStart
import com.iron.breakfast.settings.automaticMeal
import com.iron.breakfast.lastUsedMeal
import com.iron.breakfast.data.writeDiaryChecked
import com.iron.breakfast.data.readDiaryText
import com.iron.breakfast.transfer.DIARY_EXPORTED
import com.iron.breakfast.transfer.DIARY_EXPORT_FAILED
import com.iron.breakfast.data.decodeDiary
import com.iron.breakfast.transfer.DIARY_IMPORT_REFUSED
import com.iron.breakfast.exitLibraryTarget
import com.iron.breakfast.LibraryExit
import com.iron.breakfast.domain.libraryRecencyOrder
import com.iron.breakfast.shouldApplyLibraryRefresh
import com.iron.breakfast.Overlay
import com.iron.breakfast.topOverlay
import com.iron.breakfast.isLibraryNoMatch
import com.iron.breakfast.library.libraryLogDraft
import com.iron.breakfast.ui.label
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.data.OffPreflightResult
import com.iron.breakfast.contribution.contributionLanguageFor
import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.rowStillInDraft
import com.iron.breakfast.domain.replaceDraftPart
import com.iron.breakfast.contribution.contributionDraftAfterLocalSave
import com.iron.breakfast.domain.Calculation
import com.iron.breakfast.library.librarySearchText
import com.iron.breakfast.logTargetMealOf
import com.iron.breakfast.log.LogDraftView
import com.iron.breakfast.domain.DraftView
import com.iron.breakfast.library.libraryInputSegments
import com.iron.breakfast.ui.plain
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.logSubmitPlan
import com.iron.breakfast.LogSubmitPlan
import com.iron.breakfast.domain.ShorthandLexicon
import com.iron.breakfast.domain.mealMarkerSlots
import com.iron.breakfast.settings.undoWindowMs
import com.iron.breakfast.undoRemainingMs
import com.iron.breakfast.undoAfterLog
import com.iron.breakfast.undoOpen
import com.iron.breakfast.domain.normalizeBarcode
import com.iron.breakfast.foodFormBack
import com.iron.breakfast.FoodFormBack
import com.iron.breakfast.contributionAuthCanDismiss
import com.iron.breakfast.domain.parseDecimal
import com.iron.breakfast.data.OffContributionResult
import com.iron.breakfast.contribution.CONTRIBUTION_UNCERTAIN
import com.iron.breakfast.activeResolutionTarget
import com.iron.breakfast.undoEndsAt
import com.iron.breakfast.DiaryChange
import com.iron.breakfast.shouldApplyOffSearchResult
import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.productReviewOrigin
import com.iron.breakfast.scan.productPrefill
import com.iron.breakfast.scan.ProductPrefill
import com.iron.breakfast.reviewLogPlan
import com.iron.breakfast.canSaveProductToLibrary
import com.iron.breakfast.scan.ProductDraft
import com.iron.breakfast.scan.parseProductUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.scannerUnavailableTransition
import com.iron.breakfast.settings.unknownBarcodeStep
import com.iron.breakfast.settings.UnknownBarcodeStep
import com.iron.breakfast.data.OffResult
import com.iron.breakfast.domain.resolveNutrition
import com.iron.breakfast.domain.sameBarcode
import com.iron.breakfast.domain.validateBarcodeInput
import com.iron.breakfast.foodSaveRefusal
import com.iron.breakfast.domain.canonicalBarcode
import com.iron.breakfast.resolverLogPlan
import com.iron.breakfast.data.plausibleNutrition
import com.iron.breakfast.domain.editedCustomFoodRecord
import com.iron.breakfast.domain.TypedPart

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
    /** Settings, Data. */
    internal var exportReminder by mutableStateOf(settings.exportReminder); private set
    var keepSafetyCopy by mutableStateOf(settings.keepSafetyCopy); private set
    /**
     * "Export your diary." is due: Daily draws it, and it counts as shown once it is on screen
     * ([exportReminderOnScreenNow]). Cleared by Export, a successful export, or leaving the app.
     */
    var exportReminderVisible by mutableStateOf(false); private set
    private var exportReminderRecorded = false
    /** The Delete all data dialog, and its "Also settings and learned picks" chip (off each time it opens). */
    var deleteAllOpen by mutableStateOf(false); private set
    var deleteAllAlsoSettings by mutableStateOf(false); private set
    /** Settings, Logging: how long Undo stays, the meal a log without one goes to, and the ranking switches. */
    internal var undoWindow by mutableStateOf(settings.undoWindow); private set
    internal var mealMode by mutableStateOf(settings.mealMode); private set
    internal var mealBounds by mutableStateOf(settings.mealBounds); private set
    internal var beforeDayStart by mutableStateOf(settings.beforeDayStart); private set
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
                checkExportReminder()
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
    fun onAppStopped() { stopped = true; exportReminderVisible = false }
    /** The activity is on screen again after a stop: a user following today moves to the new today. */
    fun onAppStarted() {
        if (!stopped) return
        stopped = false
        checkDay(returning = true)
        if (ready) refresh()
        checkExportReminder()
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
    internal fun chooseExportReminder(reminder: ExportReminder) {
        // Turned on from Never: the first reminder comes one interval from now.
        if (exportReminder == ExportReminder.NEVER && reminder != ExportReminder.NEVER) settings.exportReminderEnabledMs = clock()
        exportReminder = reminder
        settings.exportReminder = reminder
        if (reminder == ExportReminder.NEVER) exportReminderVisible = false else checkExportReminder()
    }
    /** Marks the export reminder due when [exportReminderDue]; it is recorded as shown only once Daily draws it. */
    private fun checkExportReminder() {
        if (exportReminderVisible || !ready) return
        if (exportReminder != ExportReminder.NEVER && settings.exportReminderEnabledMs == null) settings.exportReminderEnabledMs = clock()
        if (!exportReminderDue(exportReminder, settings.lastExportMs, settings.exportReminderShownMs, settings.exportReminderEnabledMs, clock())) return
        exportReminderRecorded = false
        exportReminderVisible = true
    }
    /** Daily draws the export reminder now ([exportReminderOnScreen]); the first time, it counts as shown. */
    fun exportReminderOnScreenNow() {
        if (!exportReminderVisible || exportReminderRecorded) return
        exportReminderRecorded = true
        settings.exportReminderShownMs = clock()
    }
    /** True when Daily should draw the export reminder now. */
    fun exportReminderShownOnDaily(): Boolean = exportReminderOnScreen(exportReminderVisible, logOpen, topOverlay())
    /** The reminder's Export: the same save picker as Settings, Export diary. */
    fun exportFromReminder() {
        if (!ready || busy) return
        exportReminderVisible = false
        requestDiaryPicker(DiaryPicker.EXPORT)
    }
    /** "Safety copy" Don't keep also removes the copy kept so far. */
    fun chooseKeepSafetyCopy(keep: Boolean) {
        keepSafetyCopy = keep
        settings.keepSafetyCopy = keep
        if (!keep) removeSafetyCopy()
    }
    private fun removeSafetyCopy() {
        val dir = getApplication<Application>().noBackupFilesDir
        viewModelScope.launch(Dispatchers.IO) { if (!deleteSafetyCopy(dir)) Log.w(LOG_TAG, "The safety copy could not be deleted") }
    }
    fun openDeleteAll() { if (ready && !busy) { deleteAllAlsoSettings = false; deleteAllOpen = true } }
    fun toggleDeleteAllAlsoSettings() { deleteAllAlsoSettings = !deleteAllAlsoSettings }
    fun cancelDeleteAll() { deleteAllOpen = false; deleteAllAlsoSettings = false }
    /**
     * Delete all in the dialog: the diary is first saved to the safety copy, whatever "Safety copy" says (no copy, no
     * delete); then entries and foods go in one transaction and the catalogue is seeded again. With the chip on, the
     * learned preferences go in the same transaction and every setting returns to its default.
     */
    fun confirmDeleteAll() {
        if (!deleteAllAllowed(deleteAllOpen, ready, busy)) return
        val alsoSettings = deleteAllAlsoSettings
        cancelDeleteAll()
        val safetyDir = getApplication<Application>().noBackupFilesDir
        launchBusy {
            val deleted = withContext(NonCancellable) {
                try {
                    if (safetyCopyStep(keepSafetyCopy, deletingAll = true) == SafetyCopyStep.WRITE) {
                        val current = diaryFileOf(engine.exportRows(), DiarySettings(dayStartHour, longDraft), clock())
                        withContext(Dispatchers.IO) { writeSafetyCopy(safetyDir, current) }
                    }
                    engine.deleteAllData(alsoPreferences = alsoSettings)
                    true
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Delete all failed; the stored diary is unchanged", e)
                    false
                }
            }
            if (!deleted) { notice = DIARY_DELETE_FAILED; return@launchBusy }
            // Nothing deleted may be undone, pinned, confirmed or resolved into the empty diary.
            offerUndo(null); loggedBatchIds = emptyList(); logPins.clear(); feedbackIds = emptySet(); pendingContribution = null
            abandonUnknownResolution(); closeEntryDetail()
            if (alsoSettings) resetSettings()
            refresh()
            refreshLibrary()
        }
    }
    /** Every setting back to its default, on the phone and on screen. */
    private fun resetSettings() {
        settings.clear()
        dayStartHour = settings.dayStartHour; longDraft = settings.longDraft
        undoWindow = settings.undoWindow; mealMode = settings.mealMode; mealBounds = settings.mealBounds; beforeDayStart = settings.beforeDayStart
        countTyping = settings.countTyping; engine.countTyping = countTyping
        learnFromPicks = settings.learnFromPicks; engine.learnFromPicks = learnFromPicks
        theme = settings.theme; showMacros = settings.showMacros; showMissingNote = settings.showMissingNote
        decimalMark = settings.decimalMark; DisplayDecimal.mark = decimalMark
        motion = settings.motion; textSize = settings.textSize; haptics = settings.haptics
        flashlightAtStart = settings.flashlightAtStart; lookUpBarcodes = settings.lookUpBarcodes; showContribute = settings.showContribute
        exportReminder = settings.exportReminder; keepSafetyCopy = settings.keepSafetyCopy; exportReminderVisible = false
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
    internal fun chooseBeforeDayStart(choice: BeforeDayStart) {
        beforeDayStart = choice
        settings.beforeDayStart = choice
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
    /**
     * The meal the "Meal" setting gives a log with no chosen, typed or preset meal, at this moment; null for Ask. The
     * Log page falls back to it, and the Library preselects it (see [libraryPresetMeal]).
     */
    internal fun automaticMealNow(): MealSlot? = automaticMeal(
        mealMode, java.time.Instant.ofEpochMilli(clock()).atZone(zone).hour, mealBounds, lastUsedMeal(entries), dayStartHour, beforeDayStart,
    )
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
            if (written) { settings.lastExportMs = clock(); exportReminderVisible = false }
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
     * Replace diary: the current diary is first saved to [SAFETY_COPY_NAME] in `no_backup` (no copy, no replace; with
     * "Safety copy" Don't keep, none is written and an old one removed), then every table becomes the file's rows in
     * one transaction. The phone's settings stay as they are.
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
                        when (safetyCopyStep(keepSafetyCopy, deletingAll = false)) {
                            SafetyCopyStep.WRITE -> {
                                val current = diaryFileOf(engine.exportRows(), DiarySettings(dayStartHour, longDraft), clock())
                                withContext(Dispatchers.IO) { writeSafetyCopy(safetyDir, current) }
                            }
                            // "Safety copy" Don't keep: no copy, and an old one is removed.
                            SafetyCopyStep.DELETE -> withContext(Dispatchers.IO) { deleteSafetyCopy(safetyDir) }
                        }
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
        // Add finishes the draft in both "Count what I'm typing" modes: it saves what the finished draft counts.
        if (entryEditMode == EntryEditMode.NONE) logDraftCommitted = true
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
        if (engine.planner(capturedPins).segments(captured).none { ShorthandLexicon.mealSlotOf(it.text) == null }) {
            mealDraftError = "Add at least one food."
            return
        }
        launchBusy {
            suspendRunCatching { engine.save(captured, mealSlot = target, dayKey = selectedDayKey, pins = capturedPins) }.onSuccess { saved ->
                // Nothing resolved: nothing was saved, so the Log stays open with the typed text and says what to fix.
                if (saved.entries.isEmpty() && saved.unresolvedSegments.isNotEmpty()) {
                    mealDraftError = "Some foods need a match."
                    return@onSuccess
                }
                // Only the pins this save used: one added while it planned off the main thread belongs to the new text.
                capturedPins.forEach { (part, food) -> if (logPins[part] == food) logPins.remove(part) }
                closeLog()
                refresh(); if (logDraft == captured) logDraft = saved.unresolvedSegments.joinToString(", "); unknownQueue = saved.unresolvedSegments; unknownQueueTotal = saved.unresolvedSegments.size; unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }; resolutionContinuation = saved.unresolvedSegments.isNotEmpty(); resolverOpen = saved.unresolvedSegments.isNotEmpty(); mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
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
    fun currentUnknownName(): String = unknownQueue.firstOrNull()?.let { typedUnknown(it).foodPhrase.trim().ifBlank { it.trim() } }.orEmpty()
    /** A part of the unknown queue as the Log read it: its food text, typed amount and unit ("mleko200g" is mleko, 200 g). */
    internal fun typedUnknown(raw: String): TypedPart = engine.planner().typed(raw)
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
        if (libraryOpen) input = unknownQueue.joinToString(", ") else logDraft = unknownQueue.joinToString(", ")
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
        val typed = typedUnknown(raw)
        val amount = typed.amount ?: food.defaultAmount
        val unit = typed.unit ?: food.defaultUnit
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
                    is ReplacementResult.Success -> {
                        // The pending Undo stays (see [undoEndsAt]); it ends at the next log, edit or delete.
                        if (undoEndsAt(DiaryChange.NOT_RIGHT_PICK)) offerUndo(null)
                        feedbackIds = feedbackIds - entry.id; refresh()
                    }
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
            ?.let { productPrefill(it, food, typedUnknown(it)) }
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

/** The notice when an Undo could not be applied. */
private const val UNDO_FAILED = "Could not undo."

internal const val LOG_TAG = "Breakfast"
