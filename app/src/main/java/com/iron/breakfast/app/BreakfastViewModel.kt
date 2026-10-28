package com.iron.breakfast.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.food.FoodFormStore
import com.iron.breakfast.food.NewFood
import com.iron.breakfast.resolve.ResolveQueueView
import com.iron.breakfast.scan.ScannerUnavailableReason
import com.iron.breakfast.scan.ProductReviewOrigin
import com.iron.breakfast.resolve.LogResolveRequest
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.library.LibraryLogDraft
import com.iron.breakfast.entry.EntryEditMode
import com.iron.breakfast.entry.EntryEdit
import com.iron.breakfast.entry.EntryEditStore
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.transfer.DiaryPicker
import com.iron.breakfast.log.automaticMeal
import com.iron.breakfast.lastUsedMeal
import com.iron.breakfast.exitLibraryTarget
import com.iron.breakfast.LibraryExit
import com.iron.breakfast.domain.libraryRecencyOrder
import com.iron.breakfast.shouldApplyLibraryRefresh
import com.iron.breakfast.Overlay
import com.iron.breakfast.topOverlay
import com.iron.breakfast.isLibraryNoMatch
import com.iron.breakfast.library.libraryLogDraft
import com.iron.breakfast.ui.label
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
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.logSubmitPlan
import com.iron.breakfast.LogSubmitPlan
import com.iron.breakfast.domain.ShorthandLexicon
import com.iron.breakfast.domain.mealMarkerSlots
import com.iron.breakfast.settings.undoWindowMs
import com.iron.breakfast.domain.normalizeBarcode
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
import com.iron.breakfast.domain.TypedPart
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.today.MatchFeedback
import com.iron.breakfast.today.MatchStore
import com.iron.breakfast.today.UndoController
import com.iron.breakfast.today.UndoStore
import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.search.OffSearch
import com.iron.breakfast.transfer.ContentDiaryFiles
import com.iron.breakfast.transfer.DiaryData
import com.iron.breakfast.transfer.DiaryTransfer
import com.iron.breakfast.transfer.ExportReminders
import com.iron.breakfast.transfer.TransferLog
import com.iron.breakfast.data.DiaryRows

class BreakfastViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val clock: () -> Long = System::currentTimeMillis
    /** Every user setting and the open Settings page; the effects that are not settings are wired in [init]. */
    internal val settings = SettingsState(AppSettings(application), clock)
    private val zone: ZoneId get() = ZoneId.systemDefault()
    /** Today's day by the clock and the day start setting. The 60-second loop and every return keep it current. */
    var todayKey by mutableStateOf(dayKeyFor(clock(), zone, settings.dayStartHour)); private set
    /** Settings is open over the Library (its page is [SettingsState.page]). A surface of the app, so it stays here. */
    var settingsOpen by mutableStateOf(false); private set
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
        onRefreshed = { entryEdit.diaryReloaded() },
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
    /** Export, import, Delete all and the safety copy (see [DiaryTransfer]); the root launches its pickers. */
    internal val transfer = DiaryTransfer(
        session = session,
        data = object : DiaryData {
            override suspend fun exportRows() = engine.exportRows()
            override suspend fun replaceDiary(rows: DiaryRows) { engine.replaceDiary(rows) }
            override suspend fun deleteAllData(alsoPreferences: Boolean) { engine.deleteAllData(alsoPreferences) }
        },
        files = ContentDiaryFiles(application.contentResolver, application.noBackupFilesDir),
        settings = settings,
        notices = notices,
        clock = clock,
        scope = viewModelScope,
        io = Dispatchers.IO,
        log = object : TransferLog {
            override fun error(message: String, cause: Throwable?) { Log.e(LOG_TAG, message, cause) }
            override fun warn(message: String) { Log.w(LOG_TAG, message) }
        },
        onExported = { reminders.hide() },
    )
    /** "Export your diary." on Daily (see [ExportReminders]). */
    internal val reminders: ExportReminders = ExportReminders(settings, session, clock, requestExport = { transfer.requestPicker(DiaryPicker.EXPORT) })
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
    /** The entry detail and the quick and full edits (see [EntryEdit]); an edit shows its meal and returns to Daily through here. */
    internal val entryEdit: EntryEdit = EntryEdit(
        session = session,
        store = object : EntryEditStore {
            override suspend fun replaceAmountUnit(entryId: String, text: String) = engine.replaceEntryAmountUnit(entryId, text)
            override suspend fun replaceEntry(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) =
                engine.replaceEntryDraft(entryId, foodId, amount, unit, meal, dayKey)
        },
        scope = viewModelScope,
        openMeal = { slot },
        // The edit's meal shows beneath it; the Log's error goes, as it always did when an edit began.
        onBegin = { meal -> selectMeal(meal); mealDraftError = null },
        onEnd = { returnToDaily -> if (returnToDaily) selectMeal(null); mealDraftError = null },
        initialEntryId = savedState["entry_id"],
        saveEntryId = { savedState["entry_id"] = it },
    )
    /** New food and Edit food (see [FoodForm]); what it opens over and returns to stays with the resolve flow here. */
    internal val foodForm: FoodForm = FoodForm(
        session = session,
        store = object : FoodFormStore {
            override suspend fun updateFood(food: FoodRecord) = engine.updateFood(food)
            override suspend fun saveFood(food: NewFood) = engine.saveCustomFood(
                food.name, food.brand, food.barcode, food.aliases, food.nutrition, food.basisAmount, food.basisUnit,
                sourceName = food.sourceName, sourceDescription = food.sourceDescription,
            )
            override suspend fun saveFoodAndLog(food: NewFood, meal: MealSlot, dayKey: String): FoodRecord {
                val entry = engine.saveCustomAndLog(
                    food.name, food.brand, food.barcode, food.aliases, food.nutrition, food.basisAmount, food.basisUnit, meal,
                    food.basisAmount, food.basisUnit, dayKey = dayKey, sourceName = food.sourceName, sourceDescription = food.sourceDescription,
                )
                return engine.foodById(entry.foodId) ?: error("Saved food could not be reloaded.")
            }
        },
        notices = notices,
        selectedDay = { selectedDayKey },
        openMeal = { slot },
        queue = ::resolveQueue,
        resolving = { logResolve != null || resolutionContinuation },
        offerContribution = contribution::offerAfterLocalSave,
        startScan = ::beginScan,
        onOpen = ::foodFormOpened,
        onClose = ::returnToResolverOrEnd,
        onSaved = ::foodSaved,
    )
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
    private var barcodeRequestToken = 0L
    /** The barcode lookup in flight; leaving the scanner cancels it, which also stops its Open Food Facts request. */
    private var barcodeLookupJob: Job? = null
    private var barcodeOriginDay = selectedDayKey
    private var barcodeOriginMeal: MealSlot? = slot
    val ready: Boolean get() = session.ready
    /** A diary write is running ([DiarySession.write]); user actions that write are refused meanwhile. */
    val busy: Boolean get() = session.busy
    /** Storage failed to open; the dialog stays until a retry opens it. */
    val storageUnavailable: Boolean get() = session.storageUnavailable
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
        restoreFoodForm(savedState)?.let(foodForm::restore)
        // Every change is mirrored, so an added or cleared draft and a closed or saved form drop their keys.
        viewModelScope.launch { snapshotFlow { SavedLogDraft(logDraft, logDraftCommitted) }.collect { saveLogDraft(savedState, it) } }
        viewModelScope.launch { snapshotFlow { foodForm.savedForm() }.collect { saveFoodForm(savedState, it) } }
        // After every committed write, in this order: Undo, "Not right?" feedback, the Library list.
        session.onChange(undo::onChange)
        session.onChange(feedback::onChange)
        session.onChange { change -> if (change.refreshesLibrary) refreshLibrary() }
        // Import and Delete all: one reset of everything that referred to the old diary (see [SessionReset]).
        session.onChange(SessionReset(listOf(logPins::clear, contribution::reset, ::abandonUnknownResolution, entryEdit::closeDetail))::onChange)
        // A new day start: today follows it at once, and a user on today stays on today (it counts as a return).
        settings.dayStartHourSetting.observe { checkDay(returning = true) }
        // An Undo offer showing now follows the new window from the moment it was made.
        settings.undoWindowSetting.observe { undo.windowChanged() }
        settings.exportReminderSetting.observe(reminders::settingChanged)
        // "Safety copy" Don't keep also removes the copy kept so far.
        settings.keepSafetyCopySetting.observe { keep -> if (!keep) transfer.removeSafetyCopy() }
        openStore()
    }
    /** Opens storage; on failure the "Storage unavailable" dialog offers [retryStorage]. The cause goes to the log only. */
    private fun openStore() = session.open {
        reminders.check()
        foodForm.closeIfFoodGone()
    }
    /** "Try again" on the storage dialog: runs the same open again. */
    fun retryStorage() = openStore()
    /** The 60-second loop: today is recomputed, while the selected day stays where it is. */
    fun reloadToday() { checkDay(returning = false); if (ready) session.refresh() }
    private var stopped = false
    /** The activity left the screen (backgrounded or closed). */
    fun onAppStopped() { stopped = true; reminders.hide() }
    /** The activity is on screen again after a stop: a user following today moves to the new today. */
    fun onAppStarted() {
        if (!stopped) return
        stopped = false
        checkDay(returning = true)
        if (ready) session.refresh()
        reminders.check()
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
    /**
     * The meal the "Meal" setting gives a log with no chosen, typed or preset meal, at this moment; null for Ask. The
     * Log page falls back to it, and the Library preselects it (see [libraryPresetMeal]).
     */
    internal fun automaticMealNow(): MealSlot? = automaticMeal(
        settings.mealMode, java.time.Instant.ofEpochMilli(clock()).atZone(zone).hour, settings.mealBounds, lastUsedMeal(entries), settings.dayStartHour, settings.beforeDayStart,
    )
    fun openLibrary() {
        leaveEntryEdit(); selectMeal(null); libraryOpen = true; libraryQuery = ""; libraryArchivedOnly = false; refreshLibrary()
    }
    fun openArchivedLibrary() {
        leaveEntryEdit(); selectMeal(null); libraryOpen = true; libraryQuery = ""; libraryArchivedOnly = true; refreshLibrary()
    }
    /**
     * Library and meal navigation end an open entry edit ([EntryEdit.cancel], which also clears the Log's error), and
     * the Library bar starts empty, as it did when the edit borrowed it (before B15).
     */
    private fun leaveEntryEdit() { entryEdit.cancel(); input = "" }
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
        scanner = scannerOpen, entryDetail = entryEdit.selectedEntry != null, productReview = selectedProduct != null,
        resolver = resolverOpen, offSearch = search.open, foodForm = foodForm.open,
        libraryFood = selectedLibraryFood != null, entryEdit = entryEdit.mode != EntryEditMode.NONE,
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
            onSuccess = { selectedLibraryFood = null; if (foodForm.editingFoodId == food.id) foodForm.close(); notices.show("Food archived; saved diary entries remain readable.") },
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
    private fun abandonUnknownResolution() {
        unknownQueue = emptyList()
        unknownQueueTarget = null
        unknownQueueTotal = 0
        resolutionContinuation = false
        resolverOpen = false
    }
    fun openMeal(s: MealSlot) { abandonUnknownResolution(); selectMeal(s); barcodeRequestToken++; leaveEntryEdit(); mealDraftError = null }
    fun openFocusedLibrary(meal: MealSlot?, query: String = "") {
        abandonUnknownResolution()
        leaveEntryEdit()
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
        foodForm.hide()
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
        entryEdit.closeDetail()
        leaveEntryEdit()
        selectMeal(null)
        libraryOpen = true
        libraryQuery = entry.foodName
        selectedLibraryFood = foods.firstOrNull { it.id == entry.foodId }
        refreshLibrary()
    }
    /** Leaves the meal for Daily. The log draft and its pins stay. */
    fun closeMeal() {
        logResolve?.let { endResolve(it, it.draftText); return }
        abandonUnknownResolution(); selectMeal(null); barcodeRequestToken++; leaveEntryEdit() }
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
    /** Add on the Log page. */
    fun submit() {
        // Add finishes the draft in both "Count what I'm typing" modes: it saves what the finished draft counts.
        logDraftCommitted = true
        if (!ready || busy) return
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
        if (entryEdit.mode != EntryEditMode.NONE) return
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
    fun closeResolver() { resolverSearchJob?.cancel(); abandonUnknownResolution(); resolverSearchOpen = false }
    fun beginResolverSearch() {
        resolverSearchOpen = true
        if (resolverQuery.isBlank()) resolverQuery = currentUnknownName()
        onResolverQuery(resolverQuery)
    }
    fun clearSelectedProduct() { selectedProduct = null; selectedProductFromScan = false; selectedProductOrigin = ProductReviewOrigin.LIBRARY; returnToResolverOrEnd() }
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
        foodForm.hide()
        resolverOpen = false
    }
    /** [search] closed: back to the resolver when it was opened from one with parts left. */
    private fun searchClosed() = returnToResolverOrEnd()
    /**
     * The food form opened ([FoodForm.open]): it replaces the resolver and the search; opened from elsewhere it abandons
     * a resolve queue.
     */
    private fun foodFormOpened(fromResolver: Boolean) {
        if (!fromResolver && unknownQueue.isNotEmpty()) abandonUnknownResolution()
        resolutionContinuation = fromResolver
        search.hide()
        resolverOpen = false
    }
    /** A screen opened from the resolver (food form, search, product review) closed: back to the resolver while parts are left. */
    private fun returnToResolverOrEnd() { if (resolutionContinuation && unknownQueue.isNotEmpty()) resolverOpen = true else resolutionContinuation = false }
    /** The resolve queue while one is being resolved, for the screens opened from it. */
    private fun resolveQueue(): ResolveQueueView? =
        if (resolutionContinuation && unknownQueue.isNotEmpty()) ResolveQueueView(unknownQueueTarget, unknownQueue, unknownQueueTotal) else null
    /** The food form saved: a queued part it resolved moves the queue on; a food made for a Log part is that part's pick. */
    private fun foodSaved(food: FoodRecord, newFood: Boolean, advancesQueue: Boolean) {
        if (advancesQueue && unknownQueue.isNotEmpty()) advanceUnknownQueue() else resolutionContinuation = false
        if (logResolve != null && newFood) finishResolve(food)
    }
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
    fun endScan() { barcodeRequestToken++; barcodeLookupJob?.cancel(); barcodeLookupJob = null; scannerOpen = false; scannerUnavailableState = false; scannerUnavailableReason = null; scannerLookupBusy = false; scannerLookupCode = ""; foodForm.rescanEnded() }
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
        if (foodForm.rescanRequested) {
            scannerOpen = false
            foodForm.rescanned(barcode)
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
                withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; foodForm.open(barcode = barcode) } }
                return@launch
            }
            when (val result = off.barcode(barcode)) {
                is OffResult.Found -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; chooseProduct(result.food, fromScan = true, origin = reviewOrigin) } }
                is OffResult.Ineligible -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notices.show("Nutrition is incomplete. Review it as a personal food."); foodForm.open(result.name.orEmpty(), result.brand.orEmpty(), result.barcode, fromOff = true) } }
                OffResult.Missing -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notices.show("Product not found. Add it manually with barcode $barcode"); foodForm.open(barcode = barcode) } }
                OffResult.Unavailable -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notices.show("No connection. Add the product manually."); foodForm.open(barcode = barcode) } }
                is OffResult.Error -> withContext(Dispatchers.Main) { if (barcodeRequestStillTargets(token, originDay, originMeal)) { scannerOpen = false; scannerLookupBusy = false; notices.show(result.message); foodForm.open(barcode = barcode) } }
            }
        }
    }
}

internal const val LOG_TAG = "Breakfast"
