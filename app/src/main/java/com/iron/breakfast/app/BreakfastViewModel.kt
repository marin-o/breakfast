package com.iron.breakfast.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.domain.dayKeyFor
import com.iron.breakfast.domain.BreakfastEngine
import com.iron.breakfast.data.BreakfastStore
import com.iron.breakfast.data.OpenFoodFactsClient
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.resolveSelectedDay
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.scan.BarcodeLookup
import com.iron.breakfast.scan.ProductReview
import com.iron.breakfast.scan.ProductStore
import com.iron.breakfast.scan.ScanSession
import com.iron.breakfast.food.FoodFormStore
import com.iron.breakfast.food.NewFood
import com.iron.breakfast.resolve.LogResolveRequest
import com.iron.breakfast.resolve.ResolveFlow
import com.iron.breakfast.resolve.ResolveStore
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.entry.EntryEditMode
import com.iron.breakfast.entry.EntryEdit
import com.iron.breakfast.entry.EntryEditStore
import com.iron.breakfast.transfer.DiaryPicker
import com.iron.breakfast.log.automaticMeal
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.log.LogStore
import com.iron.breakfast.library.LibraryState
import com.iron.breakfast.library.LibraryStore
import com.iron.breakfast.lastUsedMeal
import com.iron.breakfast.exitLibraryTarget
import com.iron.breakfast.LibraryExit
import com.iron.breakfast.Overlay
import com.iron.breakfast.topOverlay
import com.iron.breakfast.settings.undoWindowMs
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
        resolverOpen = { resolve.resolverOpen },
        clock = clock,
        scope = viewModelScope,
    )
    /** The Open Food Facts contribution flow (see [ContributionFlow]). */
    internal val contribution = ContributionFlow(off, notices, viewModelScope, Dispatchers.IO)
    /** The Open Food Facts search (see [OffSearch]); what it opens over and returns to stays with the resolve flow. */
    internal val search = OffSearch(
        off = off,
        scope = viewModelScope,
        io = Dispatchers.IO,
        ready = { ready },
        onOpen = ::searchOpened,
        onClose = { resolve.childClosed() },
        onChoose = { food -> review.choose(food) },
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
    val foods: List<FoodRecord> get() = session.foods
    val entries: List<MealEntry> get() = session.entries
    /** True while the user is on today, not on a day they picked: a return from the background then opens the new today. */
    private var followingToday = restoreSelectedDay(savedState).followingToday
    /** Opened or restored after being closed: a user following today lands on today; a past day they picked stays. */
    var selectedDayKey by mutableStateOf(
        restoreSelectedDay(savedState).let { resolveSelectedDay(it.dayKey, it.followingToday, returning = true, clock(), zone, settings.dayStartHour) }
    ); private set
    /**
     * The Meal screen's meal; null on Daily. While the Library is open it shows instead, adding to its own
     * [LibraryState.target]; leaving the Library says which of the two comes back.
     */
    var meal by mutableStateOf(savedState.get<String>(MEAL_SLOT_KEY)?.let { runCatching { MealSlot.valueOf(it) }.getOrNull() }); private set
    /** "Not in your foods": the Log ↔ Library resolve frame, the unknown queue and the resolver (see [ResolveFlow]). */
    internal val resolve: ResolveFlow = ResolveFlow(
        session = session,
        store = object : ResolveStore {
            override fun typed(raw: String) = engine.planner().typed(raw)
            override fun search(query: String) = engine.search(query)
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                engine.cacheFoodAndLog(food, amount, unit, meal, dayKey)
            }
        },
        notices = notices,
        scope = viewModelScope,
        compute = Dispatchers.Default,
        selectedDay = { selectedDayKey },
        openLibrary = ::openFocusedLibrary,
        returnToLog = ::returnToLog,
    )
    /** The Log draft and the Log page (see [LogComposer]). */
    internal val log: LogComposer = LogComposer(
        session = session,
        store = object : LogStore {
            override fun planner(pins: Map<String, FoodRecord>) = engine.planner(pins)
            override suspend fun save(text: String, meal: MealSlot, dayKey: String, pins: Map<String, FoodRecord>) =
                engine.save(text, mealSlot = meal, dayKey = dayKey, pins = pins)
        },
        selectedDay = { selectedDayKey },
        automaticMeal = ::automaticMealNow,
        openMeal = ::mealOnScreen,
        editing = { entryEdit.mode != EntryEditMode.NONE },
        afterSave = resolve::afterSave,
        resolvePart = resolve::beginPart,
        // Typed work comes back after the process was killed: the log draft (and an open food form, in [init]).
        initial = restoreLogDraft(savedState),
    )
    /** The Library list, its bar, the food page and logging from it (see [LibraryState]). */
    internal val library: LibraryState = LibraryState(
        session = session,
        store = object : LibraryStore {
            override suspend fun foodsMatching(query: String, includeArchived: Boolean) = engine.foodsMatching(query, includeArchived)
            override fun planner() = engine.planner()
            override fun search(phrase: String) = engine.search(phrase)
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                engine.cacheFoodAndLog(food, amount, unit, meal, dayKey)
            }
            override suspend fun save(text: String, meal: MealSlot, dayKey: String) = engine.save(text, mealSlot = meal, dayKey = dayKey)
            override suspend fun archiveFood(id: String) { engine.archiveFood(id) }
            override suspend fun restoreFood(id: String) { engine.restoreFood(id) }
        },
        notices = notices,
        scope = viewModelScope,
        io = Dispatchers.IO,
        main = Dispatchers.Main,
        compute = Dispatchers.Default,
        selectedDay = { selectedDayKey },
        resolvingPart = { resolve.resolvingPart },
        onPartPicked = resolve::finishPart,
        onArchived = { food -> if (foodForm.editingFoodId == food.id) foodForm.close() },
        afterSave = resolve::afterSave,
    )
    /** The entry detail and the quick and full edits (see [EntryEdit]); an edit shows its meal and returns to Daily through here. */
    internal val entryEdit: EntryEdit = EntryEdit(
        session = session,
        store = object : EntryEditStore {
            override suspend fun replaceAmountUnit(entryId: String, text: String) = engine.replaceEntryAmountUnit(entryId, text)
            override suspend fun replaceEntry(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) =
                engine.replaceEntryDraft(entryId, foodId, amount, unit, meal, dayKey)
        },
        openMeal = ::mealOnScreen,
        // The edit's meal shows beneath it; the Log's error goes, as it always did when an edit began.
        onBegin = { meal -> this.meal = meal; log.clearError() },
        onEnd = { returnToDaily -> if (returnToDaily) meal = null; log.clearError() },
        initialEntryId = savedState["entry_id"],
        saveEntryId = { savedState["entry_id"] = it },
    )
    /** New food and Edit food (see [FoodForm]); what it opens over and returns to stays with the resolve flow. */
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
        openMeal = ::mealOnScreen,
        queue = resolve::queueView,
        resolvingPart = { resolve.resolvingPart },
        offerContribution = contribution::offerAfterLocalSave,
        startScanFor = { onBarcode -> scan.beginFor(onBarcode) },
        // It replaces the resolver and the search.
        onOpen = { fromResolver -> resolve.childOpened(fromResolver); search.hide() },
        onClose = resolve::childClosed,
        onSaved = resolve::foodSaved,
    )
    /** The scanner and its barcode lookup (see [ScanSession]); a found product goes to [review], an unknown one to [foodForm]. */
    internal val scan: ScanSession = ScanSession(
        lookup = object : BarcodeLookup {
            override suspend fun localFood(barcode: String) = engine.foodByBarcode(barcode)
            override suspend fun openFoodFacts(barcode: String) = off.barcode(barcode)
        },
        notices = notices,
        scope = viewModelScope,
        io = Dispatchers.IO,
        lookUpBarcodes = { settings.lookUpBarcodes },
        selectedDay = { selectedDayKey },
        targetMeal = ::mealOnScreen,
        queueActive = { resolve.queueActive },
        onProduct = { food, origin -> search.hide(); review.choose(food, fromScan = true, origin = origin) },
        onManualForm = { name, brand, barcode, fromOff -> foodForm.open(name, brand, barcode, fromOff = fromOff) },
    )
    /** Product review after a scan or a search pick (see [ProductReview]); its queue and resolve hand-offs go to [resolve]. */
    internal val review: ProductReview = ProductReview(
        session = session,
        store = object : ProductStore {
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                engine.cacheFoodAndLog(food, amount, unit, meal, dayKey)
            }
            override suspend fun updateFood(food: FoodRecord) = engine.updateFood(food)
            override suspend fun cacheFood(food: FoodRecord) = engine.cacheFood(food)
        },
        notices = notices,
        selectedDay = { selectedDayKey },
        openMeal = ::mealOnScreen,
        queue = resolve::queueView,
        resolvingPart = { resolve.resolvingPart },
        offerContribution = contribution::offerAfterLocalSave,
        onPartResolved = resolve::finishPart,
        onLogged = resolve::childLogged,
        onClosed = resolve::childClosed,
        openSearch = { fromResolver -> search.open(fromResolver = fromResolver) },
        startScan = { scan.begin() },
    )
    val ready: Boolean get() = session.ready
    /** A diary write is running ([DiarySession.write]); user actions that write are refused meanwhile. */
    val busy: Boolean get() = session.busy
    /** Storage failed to open; the dialog stays until a retry opens it. */
    val storageUnavailable: Boolean get() = session.storageUnavailable

    init {
        if (selectedDayKey == todayKey) followingToday = true
        saveSelectedDay(savedState, SavedDay(selectedDayKey, followingToday))
        restoreFoodForm(savedState)?.let(foodForm::restore)
        // Every change is mirrored, so an added or cleared draft and a closed or saved form drop their keys.
        viewModelScope.launch { snapshotFlow { log.savedDraft() }.collect { saveLogDraft(savedState, it) } }
        viewModelScope.launch { snapshotFlow { foodForm.savedForm() }.collect { saveFoodForm(savedState, it) } }
        // The meal on screen comes back after process death as the Meal screen (the Library does not reopen).
        viewModelScope.launch { snapshotFlow { mealOnScreen() }.collect { savedState[MEAL_SLOT_KEY] = it?.name } }
        // After every committed write, in this order: Undo, "Not right?" feedback, the Library list.
        session.onChange(undo::onChange)
        session.onChange(feedback::onChange)
        session.onChange { change -> if (change.refreshesLibrary) library.refresh() }
        // Import and Delete all: one reset of everything that referred to the old diary (see [SessionReset]).
        session.onChange(SessionReset(listOf(log::clearPins, contribution::reset, resolve::abandon, entryEdit::closeDetail))::onChange)
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
        scan.invalidateLookup()
    }
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
    /**
     * The meal of the screen on show: the Library's [LibraryState.target] while it is open, else the Meal screen's
     * [meal]. What a scan, a review, a new food or an edit logs to when nothing else decides.
     */
    internal fun mealOnScreen(): MealSlot? = if (library.open) library.target else meal

    fun openMeal(s: MealSlot) { resolve.abandon(); meal = s; scan.invalidateLookup(); entryEdit.cancel(); log.clearError() }
    /** Leaves the meal for Daily. The log draft and its pins stay; a part being resolved goes back to the Log. */
    fun closeMeal() {
        if (resolve.cancelPart()) return
        resolve.abandon(); meal = null; scan.invalidateLookup(); entryEdit.cancel()
    }
    /**
     * Opens the Library over the screen beneath. Library and meal navigation end an open entry edit ([EntryEdit.cancel],
     * which also clears the Log's error); the Library's [LibraryState.show] says what its bar starts with.
     */
    private fun showLibrary(target: MealSlot?, query: String, archivedOnly: Boolean, bar: String = query) {
        entryEdit.cancel()
        meal = null
        library.show(target, query, archivedOnly, bar)
    }
    fun openLibrary() = showLibrary(null, "", archivedOnly = false)
    fun openArchivedLibrary() = showLibrary(null, "", archivedOnly = true)
    /** The Library adding to [target] (a meal's "Add food", a part being resolved), searching [query]. */
    fun openFocusedLibrary(target: MealSlot, query: String = "") {
        resolve.abandon()
        showLibrary(target, query, archivedOnly = false)
    }
    /** An entry's "Open in Library": the Library tab with that food's page open. */
    fun openLibraryFoodDetail(entry: MealEntry) {
        entryEdit.closeDetail()
        showLibrary(null, entry.foodName, archivedOnly = false, bar = "")
        library.showFood(foods.firstOrNull { it.id == entry.foodId })
    }
    fun closeLibrary() = leaveLibrary(returnTo = null)
    /** The Library's arrow and system Back, by [exitLibraryTarget]. */
    fun exitLibrary() {
        when (exitLibraryTarget(resolve.resolvingPart, library.archivedOnly, library.target)) {
            LibraryExit.LIBRARY -> openLibrary()
            LibraryExit.MEAL -> leaveLibrary(returnTo = library.target)
            LibraryExit.LOG, LibraryExit.DAILY -> closeLibrary()
        }
    }
    /** Leaves the Library (and Settings over it) for [returnTo] (null: Daily); a part being resolved goes back to the Log. */
    private fun leaveLibrary(returnTo: MealSlot?) {
        if (resolve.cancelPart()) return
        library.close()
        settingsOpen = false
        settings.showList()
        meal = returnTo
    }
    /** The resolve frame ended ([ResolveFlow]): the Library and what was over it close, and the Log opens where it was. */
    private fun returnToLog(request: LogResolveRequest, pick: FoodRecord?) {
        leaveLibrary(returnTo = request.slot)
        search.hide()
        foodForm.hide()
        review.hide()
        log.resume(request, pick)
    }
    /** [search] opened: it replaces the resolver and the food form. */
    private fun searchOpened(fromResolver: Boolean) { resolve.childOpened(fromResolver); foodForm.hide() }
    /** What is drawn over Daily, Meal or Library right now, by [topOverlay]. */
    internal fun topOverlay(): Overlay? = topOverlay(
        scanner = scan.open, entryDetail = entryEdit.selectedEntry != null, productReview = review.product != null,
        resolver = resolve.resolverOpen, offSearch = search.open, foodForm = foodForm.open,
        libraryFood = library.selectedFood != null, entryEdit = entryEdit.mode != EntryEditMode.NONE,
        settings = settingsOpen,
    )
    /** A library search that finished with nothing and has no other screen over it: the "No local match" screen. */
    fun libraryNoMatch(): Boolean = library.noMatch(otherScreenOpen = topOverlay() != null)
}

/** The meal on screen in `SavedStateHandle`. */
private const val MEAL_SLOT_KEY = "meal_slot"

internal const val LOG_TAG = "Breakfast"
