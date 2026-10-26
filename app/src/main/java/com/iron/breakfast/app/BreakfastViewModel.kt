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
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.domain.dayKeyFor
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
import com.iron.breakfast.resolve.LogResolveRequest
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.library.LibraryLogDraft
import com.iron.breakfast.data.DiaryImport
import com.iron.breakfast.entry.EntryEditMode
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.settings.ExportReminder
import com.iron.breakfast.transfer.exportReminderDue
import com.iron.breakfast.transfer.exportReminderOnScreen
import com.iron.breakfast.transfer.DiaryPicker
import com.iron.breakfast.data.deleteSafetyCopy
import com.iron.breakfast.transfer.deleteAllAllowed
import com.iron.breakfast.transfer.safetyCopyStep
import com.iron.breakfast.transfer.SafetyCopyStep
import com.iron.breakfast.data.diaryFileOf
import com.iron.breakfast.data.DiarySettings
import com.iron.breakfast.data.writeSafetyCopy
import com.iron.breakfast.transfer.DIARY_DELETE_FAILED
import com.iron.breakfast.log.automaticMeal
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
import com.iron.breakfast.domain.normalizeBarcode
import com.iron.breakfast.foodFormBack
import com.iron.breakfast.FoodFormBack
import com.iron.breakfast.activeResolutionTarget
import com.iron.breakfast.domain.SavedMeal
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
import com.iron.breakfast.scan.unknownBarcodeStep
import com.iron.breakfast.scan.UnknownBarcodeStep
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
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.today.MatchFeedback
import com.iron.breakfast.today.MatchStore
import com.iron.breakfast.today.UndoController
import com.iron.breakfast.today.UndoStore
import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.search.OffSearch

class BreakfastViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val clock: () -> Long = System::currentTimeMillis
    /** Every user setting and the open Settings page; the effects that are not settings are wired in [init]. */
    internal val settings = SettingsState(AppSettings(application), clock)
    private val zone: ZoneId get() = ZoneId.systemDefault()
    /** Today's day by the clock and the day start setting. The 60-second loop and every return keep it current. */
    var todayKey by mutableStateOf(dayKeyFor(clock(), zone, settings.dayStartHour)); private set
    /** Settings is open over the Library (its page is [SettingsState.page]). A surface of the app, so it stays here. */
    var settingsOpen by mutableStateOf(false); private set
    /**
     * "Export your diary." is due: Daily draws it, and it counts as shown once it is on screen
     * ([exportReminderOnScreenNow]). Cleared by Export, a successful export, or leaving the app.
     */
    var exportReminderVisible by mutableStateOf(false); private set
    private var exportReminderRecorded = false
    /** The Delete all data dialog, and its "Also settings and learned picks" chip (off each time it opens). */
    var deleteAllOpen by mutableStateOf(false); private set
    var deleteAllAlsoSettings by mutableStateOf(false); private set
    private val engine = BreakfastEngine(
        BreakfastStore(application),
        countTypingSetting = { settings.countTyping },
        learnFromPicksSetting = { settings.learnFromPicks },
    )
    /** The app's one modal message (see [Notices]); features speak through it. */
    internal val notices = Notices()
    /** The read model and the write gate (see [DiarySession]). */
    private val session = DiarySession(
        source = object : DiarySource {
            override suspend fun open() = engine.initialize()
            override fun foods() = engine.foods()
            override fun entries() = engine.persistedMeal().entries
            override suspend fun reloadIfStale() = engine.reloadIfStale()
        },
        scope = viewModelScope,
        onOpenFailed = { Log.e(LOG_TAG, "Storage could not be opened", it) },
        onRefreshed = { selectedEntry = selectedEntryId?.let { id -> entries.firstOrNull { it.id == id } } },
    )
    private val off = OpenFoodFactsClient()
    /** The pending Undo, its expiry and Delete (see [UndoController]); Daily and Meal talk to it. */
    internal val undo = UndoController(
        session = session,
        store = object : UndoStore {
            override suspend fun deleteEntry(id: String) = engine.deleteEntry(id)
            override suspend fun restoreEntry(entry: MealEntry) { engine.restoreEntry(entry) }
            override suspend fun deleteEntries(entries: List<MealEntry>) { engine.deleteEntries(entries) }
        },
        notices = notices,
        windowMs = { undoWindowMs(settings.undoWindow) },
        clock = clock,
        scope = viewModelScope,
    )
    /** "Not right?" after a logged batch (see [MatchFeedback]); held while the resolver is open. */
    internal val feedback = MatchFeedback(
        session = session,
        store = object : MatchStore {
            override fun alternativesFor(entry: MealEntry) = engine.alternativesFor(entry)
            override suspend fun replaceEntry(entryId: String, foodId: String) = engine.replaceEntry(entryId, foodId)
            override suspend fun reinforce(foodId: String) = engine.reinforce(foodId)
        },
        notices = notices,
        resolverOpen = { resolverOpen },
        clock = clock,
        scope = viewModelScope,
    )
    /** The Open Food Facts contribution flow (see [ContributionFlow]). */
    internal val contribution = ContributionFlow(off, notices, viewModelScope, Dispatchers.IO)
    /** The Open Food Facts search (see [OffSearch]); what it opens over and returns to stays with the resolve flow here. */
    internal val search = OffSearch(
        off = off,
        scope = viewModelScope,
        io = Dispatchers.IO,
        ready = { ready },
        onOpen = ::searchOpened,
        onClose = ::searchClosed,
        onChoose = { food -> chooseProduct(food) },
    )
    private val parser = ShorthandParser()
    private val calculator = NutritionCalculator()
    /** The Library bar's text, or the amount being edited. Never the log draft, so leaving either cannot touch it. */
    var input by mutableStateOf(""); private set
    /** The log draft Daily and Meal share: the composer shows it and the Log page edits it. Only Add or clearing it removes it. */
    var logDraft by mutableStateOf(""); private set
    /** True after Done or Add: the draft is no longer being typed, so every part follows the finished rules. */
    var logDraftCommitted by mutableStateOf(false); private set
    val foods: List<FoodRecord> get() = session.foods
    val entries: List<MealEntry> get() = session.entries
    /** True while the user is on today, not on a day they picked: a return from the background then opens the new today. */
    private var followingToday = restoreSelectedDay(savedState).followingToday
    /** Opened or restored after being closed: a user following today lands on today; a past day they picked stays. */
    var selectedDayKey by mutableStateOf(
        restoreSelectedDay(savedState).let { resolveSelectedDay(it.dayKey, it.followingToday, returning = true, clock(), zone, settings.dayStartHour) }
    ); private set
    var slot by mutableStateOf(savedState.get<String>("meal_slot")?.let { runCatching { MealSlot.valueOf(it) }.getOrNull() }); private set
    var customOpen by mutableStateOf(false); private set
    var editingLibraryFoodId by mutableStateOf<String?>(null); private set
    val draft = FoodDraft()
    /** The food form as it opened; Back asks before discarding only when [draft] differs from it. */
    private var draftBaseline by mutableStateOf(FoodDraftFields())
    var discardFoodOpen by mutableStateOf(false); private set
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
    /** The field New food moves focus to after a refused save; consumed by the form. */
    internal var foodFormFocus by mutableStateOf<FoodFormFocus?>(null); private set
    private var foodFormFocusSerial = 0L
    fun foodFormFocusHandled() { foodFormFocus = null }
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
    var libraryBatchMealPickerOpen by mutableStateOf(false); private set
    var selectedEntry by mutableStateOf<MealEntry?>(null); private set
    private var selectedEntryId: String? = savedState["entry_id"]
    private var barcodeRequestToken = 0L
    /** The barcode lookup in flight; leaving the scanner cancels it, which also stops its Open Food Facts request. */
    private var barcodeLookupJob: Job? = null
    private var rescanningBarcode = false
    private var barcodeOriginDay = selectedDayKey
    private var barcodeOriginMeal: MealSlot? = slot
    val ready: Boolean get() = session.ready
    /** A diary write is running ([DiarySession.write]); user actions that write are refused meanwhile. */
    val busy: Boolean get() = session.busy
    /** Storage failed to open; the dialog stays until a retry opens it. */
    val storageUnavailable: Boolean get() = session.storageUnavailable
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
        // After every committed write, in this order: Undo, "Not right?" feedback, the Library list.
        session.onChange(undo::onChange)
        session.onChange(feedback::onChange)
        session.onChange { change -> if (change.refreshesLibrary) refreshLibrary() }
        // A new day start: today follows it at once, and a user on today stays on today (it counts as a return).
        settings.dayStartHourSetting.observe { checkDay(returning = true) }
        // An Undo offer showing now follows the new window from the moment it was made.
        settings.undoWindowSetting.observe { undo.windowChanged() }
        settings.exportReminderSetting.observe { if (it == ExportReminder.NEVER) exportReminderVisible = false else checkExportReminder() }
        // "Safety copy" Don't keep also removes the copy kept so far.
        settings.keepSafetyCopySetting.observe { keep -> if (!keep) removeSafetyCopy() }
        openStore()
    }
    /** Opens storage; on failure the "Storage unavailable" dialog offers [retryStorage]. The cause goes to the log only. */
    private fun openStore() = session.open {
        checkExportReminder()
        // A restored Edit food whose food is gone has nothing to edit.
        if (customOpen && editingLibraryFoodId?.let { id -> foods.none { it.id == id } } == true) closeCustom()
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
    /** The 60-second loop: today is recomputed, while the selected day stays where it is. */
    fun reloadToday() { checkDay(returning = false); if (ready) session.refresh() }
    private var stopped = false
    /** The activity left the screen (backgrounded or closed). */
    fun onAppStopped() { stopped = true; exportReminderVisible = false }
    /** The activity is on screen again after a stop: a user following today moves to the new today. */
    fun onAppStarted() {
        if (!stopped) return
        stopped = false
        checkDay(returning = true)
        if (ready) session.refresh()
        checkExportReminder()
    }
    /** Recomputes today and applies [resolveSelectedDay]; the selection moves only when [returning]. */
    private fun checkDay(returning: Boolean) {
        val now = clock()
        todayKey = dayKeyFor(now, zone, settings.dayStartHour)
        val day = resolveSelectedDay(selectedDayKey, followingToday, returning, now, zone, settings.dayStartHour)
        if (day != selectedDayKey) selectDay(day, following = true)
    }
    private fun todayNow(): String = dayKeyFor(clock(), zone, settings.dayStartHour).also { todayKey = it }
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
    /** Settings, over the Library, on the list of groups; its arrow and Back return there. */
    fun openSettings() { settings.showList(); settingsOpen = true }
    fun closeSettings() { settingsOpen = false; settings.showList() }
    /** Back or the arrow in Settings: a page goes back ([SettingsState.back]), the list closes Settings. */
    fun settingsBack() { if (!settings.back()) closeSettings() }
    /** Marks the export reminder due when [exportReminderDue]; it is recorded as shown only once Daily draws it. */
    private fun checkExportReminder() {
        if (exportReminderVisible || !ready) return
        if (settings.exportReminder != ExportReminder.NEVER && settings.exportReminderEnabledMs == null) settings.exportReminderEnabledMs = clock()
        if (!exportReminderDue(settings.exportReminder, settings.lastExportMs, settings.exportReminderShownMs, settings.exportReminderEnabledMs, clock())) return
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
        session.write(
            change = { deleted -> DiaryChange.DiaryReplaced.takeIf { deleted } },
            onSuccess = { deleted ->
                if (!deleted) { notices.show(DIARY_DELETE_FAILED); return@write }
                // Nothing deleted may be pinned or resolved into the empty diary (Undo and feedback end with the change).
                logPins.clear(); contribution.reset()
                abandonUnknownResolution(); closeEntryDetail()
                if (alsoSettings) settings.resetToDefaults()
            },
        ) {
            withContext(NonCancellable) {
                try {
                    if (safetyCopyStep(settings.keepSafetyCopy, deletingAll = true) == SafetyCopyStep.WRITE) {
                        val current = diaryFileOf(engine.exportRows(), DiarySettings(settings.dayStartHour, settings.longDraft), clock())
                        withContext(Dispatchers.IO) { writeSafetyCopy(safetyDir, current) }
                    }
                    engine.deleteAllData(alsoPreferences = alsoSettings)
                    true
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Delete all failed; the stored diary is unchanged", e)
                    false
                }
            }
        }
    }
    /**
     * The meal the "Meal" setting gives a log with no chosen, typed or preset meal, at this moment; null for Ask. The
     * Log page falls back to it, and the Library preselects it (see [libraryPresetMeal]).
     */
    internal fun automaticMealNow(): MealSlot? = automaticMeal(
        settings.mealMode, java.time.Instant.ofEpochMilli(clock()).atZone(zone).hour, settings.mealBounds, lastUsedMeal(entries), settings.dayStartHour, settings.beforeDayStart,
    )
    /** The picker Settings asked for; IronApp, always composed, launches it so a result after recreation still lands. */
    var diaryPickerRequest by mutableStateOf<DiaryPicker?>(null); private set
    fun requestDiaryPicker(picker: DiaryPicker) { if (ready && !busy) diaryPickerRequest = picker }
    fun diaryPickerLaunched() { diaryPickerRequest = null }
    /**
     * Export diary: writes every stored row and the settings to [uri], the new file the save picker just created. It
     * says "Diary exported." only after the copy was read back and imports; on any failure the file is deleted, so
     * no empty or partial file looks like a backup.
     */
    fun exportDiary(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        // A picker result can arrive before storage is open or while a write runs (after recreation): it waits.
        session.whenIdle(
            onSuccess = { written: Boolean ->
                if (written) { settings.lastExportMs = clock(); exportReminderVisible = false }
                notices.show(if (written) DIARY_EXPORTED else DIARY_EXPORT_FAILED)
            },
        ) {
            withContext(NonCancellable) {
                val problem = try {
                    val file = diaryFileOf(engine.exportRows(), DiarySettings(settings.dayStartHour, settings.longDraft), clock())
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
        }
    }
    /** Import diary: reads and checks the whole file at [uri]; only a file that passes everything asks to replace. */
    fun importDiary(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        // Holds the gate while the file is read, so no write changes the diary under the check.
        session.whenIdle(
            onSuccess = { result: DiaryImport ->
                when (result) {
                    is DiaryImport.Valid -> pendingImport = result
                    is DiaryImport.Invalid -> { Log.w(LOG_TAG, "Diary import refused: ${result.reason}"); notices.show(DIARY_IMPORT_REFUSED) }
                }
            },
        ) {
            try {
                withContext(Dispatchers.IO) {
                    val text = (resolver.openInputStream(uri) ?: error("no input stream")).use { readDiaryText(it) }
                    if (text == null) DiaryImport.Invalid("file too big") else decodeDiary(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiaryImport.Invalid("unreadable: $e")
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
        val safetyDir = getApplication<Application>().noBackupFilesDir
        session.write(
            change = { replaced -> DiaryChange.DiaryReplaced.takeIf { replaced } },
            onSuccess = { replaced ->
                if (!replaced) {
                    notices.show(DIARY_IMPORT_REFUSED)
                    return@write
                }
                // Nothing from the old diary may be pinned into the new one (Undo and feedback end with the change).
                logPins.clear()
            },
        ) {
                withContext(NonCancellable) {
                    try {
                        when (safetyCopyStep(settings.keepSafetyCopy, deletingAll = false)) {
                            SafetyCopyStep.WRITE -> {
                                val current = diaryFileOf(engine.exportRows(), DiarySettings(settings.dayStartHour, settings.longDraft), clock())
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
        libraryRefreshJob?.cancel(); libraryRefreshGeneration++; libraryOpen = false; selectedLibraryFood = null; libraryMealPickerFood = null; expandedLibraryFoodId = null; libraryPendingAmount = null; libraryPendingUnit = null; libraryPendingBatch = emptyList(); libraryBatchMealPickerOpen = false; libraryArchivedOnly = false; settingsOpen = false; settings.showList(); selectMeal(returnTo)
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
        resolver = resolverOpen, offSearch = search.open, foodForm = customOpen,
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
    fun noticeForInvalidLibraryAmount() { notices.show("Enter a positive amount and a supported unit such as g, ml, tbsp, tsp, or piece.") }

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
        session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = { notices.show("Added ${drafts.size} food${if (drafts.size == 1) "" else "s"} to ${target.label()} on $capturedDayKey") },
            onFailure = { notices.show(it.message ?: "Could not log this food.") },
        ) { drafts.forEach { draft -> engine.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, capturedDayKey) } }
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
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { selectedLibraryFood = null; if (editingLibraryFoodId == food.id) closeCustom(); notices.show("Food archived; saved diary entries remain readable.") },
            onFailure = { notices.show(it.message ?: "Could not archive this food.") },
        ) { engine.archiveFood(food.id) }
    }
    fun restoreLibraryFood(food: FoodRecord) {
        if (!ready || busy || !food.archived) return
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { selectedLibraryFood = foods.firstOrNull { it.id == food.id }; notices.show("Food restored to your library.") },
            onFailure = { notices.show(it.message ?: "Could not restore this food.") },
        ) { engine.restoreFood(food.id) }
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
        val edit = editSession
        // A tap while the save runs is ignored, not reported as invalid.
        if (!ready || busy) return
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0 || foodId.isBlank()) {
            markEditInvalid("Enter a positive amount and choose a food.")
            return
        }
        fullSaveJob?.cancel()
        // This save owns the gate until it ends, whatever the edit session is now; a result for an edit no longer open
        // reloads the diary and nothing else.
        fullSaveJob = session.write(
            change = { result -> entryEditChange(result, stillOpen = editSession == edit && editingEntryId == id) },
            nonCancellable = true,
            onSuccess = { result ->
                if (editSession == edit && editingEntryId == id) when (result) {
                    is ReplacementResult.Success -> { fullSaveJob = null; cancelEdit() }
                    is ReplacementResult.Failure -> markEditInvalid(result.reason)
                }
            },
            onFailure = { error -> if (editSession == edit && editingEntryId == id) markEditInvalid(error.message ?: "Could not save this entry.") },
        ) { engine.replaceEntryDraft(id, foodId, parsedAmount, unit, meal, dayKey) }
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
        search.hide()
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
            notices.show("Enter a positive amount, a supported unit, and valid calories.")
            return
        }
        val fromScan = selectedProductFromScan
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = { stored ->
                contribution.offerAfterLocalSave(contributionDraftAfterLocalSave(draft.food, fromScan))
                selectedProductFromScan = false
                finishResolve(stored)
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) { engine.cacheFood(draft.food) }
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
        if (markers.size > 1) { notices.show("Choose one meal marker."); return }
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
                notices.show("Choose a meal before adding a mixed batch with an unquantified food.")
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
                    notices.show("One or more foods are not in your local library.")
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
        session.write(
            change = { saved -> DiaryChange.BatchLogged("${saved.entries.size} food${if (saved.entries.size == 1) "" else "s"} logged to ${target.label()}", saved.entries) },
            onSuccess = { saved ->
                    if (input == capturedInput) input = saved.unresolvedSegments.joinToString(", ")
                    unknownQueue = saved.unresolvedSegments
                    unknownQueueTotal = saved.unresolvedSegments.size
                    unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }
                    resolutionContinuation = saved.unresolvedSegments.isNotEmpty()
                    resolverOpen = saved.unresolvedSegments.isNotEmpty()
                    mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
            },
            onFailure = { notices.show(it.message ?: "Could not save this batch.") },
        ) { engine.save(cleaned, mealSlot = target, dayKey = capturedDayKey) }
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
            val edit = editSession
            fun stillEditing() = entryEditMode == mode && editingEntryId == editing && editSession == edit
            // A result for an edit no longer open reloads the diary and nothing else.
            session.write(
                change = { result -> entryEditChange(result, stillOpen = stillEditing()) },
                onSuccess = { result ->
                    if (stillEditing()) when (result) {
                        is ReplacementResult.Success -> cancelEdit()
                        is ReplacementResult.Failure -> markEditInvalid(result.reason)
                    }
                },
                onFailure = { if (stillEditing()) markEditInvalid(it.message ?: "Could not edit this entry.") },
            ) {
                if (mode == EntryEditMode.QUICK) engine.replaceEntryAmountUnit(editing, input)
                else engine.replaceEntryFromText(editing, input)
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
        // Nothing resolved: nothing was saved, so the Log stays open with the typed text and says what to fix.
        fun nothingSaved(saved: SavedMeal) = saved.entries.isEmpty() && saved.unresolvedSegments.isNotEmpty()
        session.write(
            change = { saved ->
                if (nothingSaved(saved)) null
                else DiaryChange.BatchLogged("${saved.entries.size} food${if (saved.entries.size == 1) "" else "s"} added to ${target.label()}", saved.entries)
            },
            onSuccess = { saved ->
                if (nothingSaved(saved)) {
                    mealDraftError = "Some foods need a match."
                    return@write
                }
                // Only the pins this save used: one added while it planned off the main thread belongs to the new text.
                capturedPins.forEach { (part, food) -> if (logPins[part] == food) logPins.remove(part) }
                closeLog()
                if (logDraft == captured) logDraft = saved.unresolvedSegments.joinToString(", "); unknownQueue = saved.unresolvedSegments; unknownQueueTotal = saved.unresolvedSegments.size; unknownQueueTarget = target.takeIf { saved.unresolvedSegments.isNotEmpty() }; resolutionContinuation = saved.unresolvedSegments.isNotEmpty(); resolverOpen = saved.unresolvedSegments.isNotEmpty(); mealDraftError = if (saved.unresolvedSegments.isNotEmpty()) "Some foods need a match." else null
            },
            onFailure = { mealDraftError = it.message ?: "Could not save this meal." },
        ) { engine.save(captured, mealSlot = target, dayKey = selectedDayKey, pins = capturedPins) }
    }
    private fun mealMarkers(value: String): List<MealSlot> = mealMarkerSlots(value)
    /** What a saved entry edit committed: nothing when refused; an edit, or one whose edit had closed meanwhile. */
    private fun entryEditChange(result: ReplacementResult, stillOpen: Boolean): DiaryChange? = when {
        result !is ReplacementResult.Success -> null
        stillOpen -> DiaryChange.EntryEdited
        else -> DiaryChange.EntryEditedAfterClose
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
        search.hide()
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
    /** [search] opened: it replaces the resolver and the food form; opened from elsewhere it abandons a resolve queue. */
    private fun searchOpened(fromResolver: Boolean) {
        if (!fromResolver && unknownQueue.isNotEmpty()) abandonUnknownResolution()
        resolutionContinuation = fromResolver
        customOpen = false
        discardFoodOpen = false
        resolverOpen = false
    }
    /** [search] closed: back to the resolver when it was opened from one with parts left. */
    private fun searchClosed() { if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
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
        session.write(
            change = DiaryChange.QueuedPartLogged,
            onSuccess = { advanceUnknownQueue() },
            onFailure = { notices.show(it.message ?: "Could not resolve this food.") },
        ) { engine.cacheFoodAndLog(food, amount, unit, target, capturedDayKey) }
    }
    /** Leaves the current unresolved food unlogged and moves to the next one. */
    fun skipCurrentUnknown() { if (unknownQueue.isNotEmpty()) advanceUnknownQueue() }
    fun chooseProduct(food: FoodRecord, fromScan: Boolean = false, origin: ProductReviewOrigin? = null) {
        selectedProduct = food
        selectedProductFromScan = fromScan
        selectedProductOrigin = origin ?: productReviewOrigin(slot, resolutionContinuation, unknownQueue.isNotEmpty())
        search.hide()
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
            notices.show("Enter a positive amount, a supported unit, and valid calories.")
            return
        }
        val plan = reviewLogPlan(resolutionContinuation, unknownQueue.isNotEmpty(), unknownQueueTarget, chosen)
        val target = plan.meal
        val resolvesQueue = plan.advancesQueue
        val fromScan = selectedProductFromScan
        val capturedDayKey = selectedDayKey
        session.write(
            change = DiaryChange.FoodsLogged,
            onSuccess = {
                selectedProduct = null
                contribution.offerAfterLocalSave(contributionDraftAfterLocalSave(draft.food, fromScan))
                selectedProductFromScan = false
                selectedProductOrigin = ProductReviewOrigin.LIBRARY
                if (resolvesQueue) advanceUnknownQueue() else resolutionContinuation = false
                notices.show("Added to ${target.label()}")
            },
            onFailure = { notices.show(it.message ?: "This amount cannot be logged.") },
        ) { engine.cacheFoodAndLog(draft.food, draft.amount, draft.unit, target, capturedDayKey) }
    }

    fun saveSelectedToLibrary() {
        val food = selectedProduct ?: return
        if (logResolve != null) { resolveWithProduct(); return }
        if (!canSaveProductToLibrary(slot, resolutionContinuation, unknownQueue.isNotEmpty())) return
        if (!ready || busy) return
        val draft = productDraft(food) ?: run { notices.show("Enter a positive amount, a supported unit, and valid calories."); return }
        session.write(
            change = DiaryChange.FoodsChanged,
            onSuccess = {
                selectedProduct = null
                selectedProductFromScan = false
                selectedProductOrigin = ProductReviewOrigin.LIBRARY
                notices.show("Saved ${draft.food.name} to your food library.")
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) { engine.updateFood(draft.food) }
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
        val step = unknownBarcodeStep(settings.lookUpBarcodes)
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
                is OffResult.Ineligible -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notices.show("Nutrition is incomplete. Review it as a personal food."); openCustom(result.name.orEmpty(), result.brand.orEmpty(), result.barcode, fromOff = true) } }
                OffResult.Missing -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notices.show("Product not found. Add it manually with barcode $barcode"); openCustom(barcode = barcode) } }
                OffResult.Unavailable -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notices.show("No connection. Add the product manually."); openCustom(barcode = barcode) } }
                is OffResult.Error -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notices.show(result.message); openCustom(barcode = barcode) } }
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
            notices.show(refusal.message)
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
            notices.show("Choose a meal before saving this food.")
            return
        }
        val fromOff = draft.fromOff
        val normalizedBarcode = normalizeBarcode(barcode)
        val candidate = if (fromOff) normalizedBarcode?.let {
            OffContributionDraft(
                barcode = it, name = name.trim(), brand = brand.trim().ifBlank { null },
                basisAmount = basisAmount, basisUnit = basisUnit, nutrition = nutrition
            )
        }?.takeIf { plausibleNutrition(it.normalizedNutrition) } else null
        val offSource = fromOff.takeIf { it }?.let { "Open Food Facts" }
        val aliasText = aliasList.joinToString(", ")
        val capturedDayKey = selectedDayKey
        session.write(
            // A new food saved with a meal is a log (it ends Undo); a food saved or edited alone is not.
            change = { _: FoodRecord -> if (editingId == null && saveTarget != null) DiaryChange.FoodsLogged else DiaryChange.FoodsChanged },
            onSuccess = { saved ->
                customOpen = false
                discardFoodOpen = false
                editingLibraryFoodId = null
                draft.reset()
                contribution.offerAfterLocalSave(candidate?.copy(languageCode = contributionLanguageFor(name), packageQuantityMl = null))
                if (resolvesQueue && unknownQueue.isNotEmpty()) advanceUnknownQueue() else resolutionContinuation = false
                notices.show(if (editingId != null) "Personal food updated for future logs." else if (saveTarget != null) "Saved to ${saveTarget.label()} and your food library." else "Saved to your food library.")
                if (logResolve != null && editingId == null) finishResolve(saved)
            },
            onFailure = { notices.show(it.message ?: "Could not save this food.") },
        ) {
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
        }
    }
}

internal const val LOG_TAG = "Breakfast"
