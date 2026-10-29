package com.iron.breakfast.app

import com.iron.breakfast.QueueDispatcher
import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.contribution.OffContributions
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.data.OffContributionResult
import com.iron.breakfast.data.OffPreflightResult
import com.iron.breakfast.data.OffResult
import com.iron.breakfast.data.OffSearchResult
import com.iron.breakfast.domain.FoodCatalog
import com.iron.breakfast.domain.FoodMatcher
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.LogPlanner
import com.iron.breakfast.domain.MealEntry
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionValues
import com.iron.breakfast.domain.RankedFood
import com.iron.breakfast.domain.ReplacementResult
import com.iron.breakfast.domain.SavedMeal
import com.iron.breakfast.entry.EntryEdit
import com.iron.breakfast.entry.EntryEditMode
import com.iron.breakfast.entry.EntryEditStore
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.food.FoodFormStore
import com.iron.breakfast.food.NewFood
import com.iron.breakfast.library.LibraryState
import com.iron.breakfast.library.LibraryStore
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.log.LogStore
import com.iron.breakfast.resolve.ResolveFlow
import com.iron.breakfast.resolve.ResolveStore
import com.iron.breakfast.resolve.food
import com.iron.breakfast.scan.BarcodeLookup
import com.iron.breakfast.scan.ProductReview
import com.iron.breakfast.scan.ProductStore
import com.iron.breakfast.scan.ScanSession
import com.iron.breakfast.search.OffSearch
import com.iron.breakfast.search.OffSearchService
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.settings.FakeSettingsStore
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.transfer.DiaryTransfer
import com.iron.breakfast.transfer.FakeData
import com.iron.breakfast.transfer.FakeFiles
import com.iron.breakfast.transfer.RecordingLog
import com.iron.breakfast.transfer.exportReminderOnScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Every feature owner that opens a surface, over fakes, and [Surfaces]: what is drawn ([shown]), what takes Back
 * ([back]) and whether anything is over the base screen.
 *
 * Mirrored from `BreakfastViewModel`: every owner-to-owner and owner-to-[Surfaces] callback, the session's reload hook
 * (`onRefreshed` → `EntryEdit.diaryReloaded`), its Library refresh and [SessionReset] listeners (with the same
 * [sessionResetSteps]), and a day change invalidating a scan ([selectDay]). Not mirrored, since no surface depends on
 * them: the Undo and "Not right?" listeners, the export reminder's `check`/`hide`, `FoodForm.closeIfFoodGone` on
 * opening storage, the saved-state restore (the initial Log draft, food form, meal and entry id) and the settings
 * observers. Settings use their defaults; the scan lookup and the IO of search, import and export run unconfined.
 *
 * Characterisation: [SurfacesTest] was first written against the app's three encodings as they were before
 * `Surfaces` (the view model's coordination, `IronApp`'s composition order and BackHandler order, `topOverlay()`,
 * transcribed here at 6da279d) and passes unchanged against `Surfaces`.
 */
internal class AppKit {
    val diary = KitDiary(onRefreshed = { entryEdit.diaryReloaded() })
    val notices = Notices()
    /** Writes the fakes received, in order. */
    val calls = mutableListOf<String>()
    var day = "2026-10-28"
    /** What the Log's Add and the Library's mixed batch leave unmatched. */
    var unmatched: List<String> = emptyList()
    /** Barcodes the Library has. */
    val barcodes = mutableMapOf<String, FoodRecord>()
    /** Foods the Library's list finds for any query; empty gives "No local match". */
    var libraryFoods: List<FoodRecord> = emptyList()
    private val plannerFor = { pins: Map<String, FoodRecord> -> LogPlanner(FoodMatcher(FoodCatalog.fixtures), pins = pins) }
    private val transferEvents = mutableListOf<String>()
    /** Picked documents for an import, by name. */
    val documents = FakeFiles(transferEvents)
    /** The contribution's Open Food Facts calls wait here until [QueueDispatcher.runAll]. */
    val offIo = QueueDispatcher()

    val settings = SettingsState(AppSettings(FakeSettingsStore())) { 0L }

    val contribution = ContributionFlow(
        object : OffContributions {
            override fun preflight(draft: OffContributionDraft) = OffPreflightResult.Existing
            override fun contribute(draft: OffContributionDraft, username: String, password: String) = OffContributionResult.Success
        },
        notices, diary.scope, offIo,
    )
    val search: OffSearch = OffSearch(
        off = object : OffSearchService { override suspend fun search(query: String) = OffSearchResult.Success(emptyList()) },
        scope = diary.scope,
        io = Dispatchers.Unconfined,
        ready = { diary.session.ready },
        onOpen = { fromResolver -> surfaces.searchOpened(fromResolver) },
        onClose = { resolve.childClosed() },
        onChoose = { food -> review.choose(food) },
    )
    val transfer: DiaryTransfer<String> = DiaryTransfer(
        diary.session, FakeData(transferEvents), documents, settings, notices, { 0L }, diary.scope,
        Dispatchers.Unconfined, RecordingLog(), onExported = {},
    )
    val resolve: ResolveFlow = ResolveFlow(
        diary.session,
        object : ResolveStore {
            override fun typed(raw: String) = plannerFor(emptyMap()).typed(raw)
            override fun search(query: String) = emptyList<RankedFood>()
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                calls += "queue log ${food.id} $meal"
            }
        },
        notices, diary.scope, Dispatchers.Unconfined,
        selectedDay = { day },
        openLibrary = { meal, query -> surfaces.openFocusedLibrary(meal, query) },
        returnToLog = { request, pick -> surfaces.returnToLog(request, pick) },
    )
    val log: LogComposer = LogComposer(
        diary.session,
        object : LogStore {
            override fun planner(pins: Map<String, FoodRecord>) = plannerFor(pins)
            override suspend fun save(text: String, meal: MealSlot, dayKey: String, pins: Map<String, FoodRecord>): SavedMeal {
                calls += "log save \"$text\" $meal"
                return SavedMeal(listOf(com.iron.breakfast.today.entry("logged")), NutritionValues.ZERO, unmatched)
            }
        },
        selectedDay = { day },
        automaticMeal = { null },
        openMeal = ::mealOnScreen,
        editing = { entryEdit.mode != EntryEditMode.NONE },
        afterSave = resolve::afterSave,
        resolvePart = resolve::beginPart,
    )
    val library: LibraryState = LibraryState(
        diary.session,
        object : LibraryStore {
            override suspend fun foodsMatching(query: String) = libraryFoods
            override fun planner() = plannerFor(emptyMap())
            override fun search(phrase: String) = emptyList<RankedFood>()
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                calls += "library log ${food.id} $meal"
            }
            override suspend fun save(text: String, meal: MealSlot, dayKey: String): SavedMeal {
                calls += "library save \"$text\" $meal"
                return SavedMeal(listOf(com.iron.breakfast.today.entry("batch")), NutritionValues.ZERO, unmatched)
            }
            override suspend fun deleteFood(id: String) { calls += "delete food $id" }
        },
        notices, diary.scope, Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined,
        selectedDay = { day },
        resolvingPart = { resolve.resolvingPart },
        onPartPicked = resolve::finishPart,
        onDeleted = { food -> if (foodForm.editingFoodId == food.id) foodForm.close() },
        afterSave = resolve::afterSave,
    )
    val entryEdit: EntryEdit = EntryEdit(
        diary.session,
        object : EntryEditStore {
            override suspend fun replaceAmountUnit(entryId: String, text: String) = ReplacementResult.Failure("not in these tests")
            override suspend fun replaceEntry(entryId: String, foodId: String, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) =
                ReplacementResult.Failure("not in these tests")
        },
        openMeal = ::mealOnScreen,
        onBegin = { meal -> surfaces.editBegan(meal) },
        onEnd = { returnToDaily -> surfaces.editEnded(returnToDaily) },
        initialEntryId = null,
        saveEntryId = {},
    )
    val foodForm: FoodForm = FoodForm(
        diary.session,
        object : FoodFormStore {
            override suspend fun updateFood(food: FoodRecord) { calls += "update ${food.id}" }
            override suspend fun saveFood(food: NewFood): FoodRecord { calls += "save food ${food.name}"; return food(food.name.lowercase(), food.name) }
            override suspend fun saveFoodAndLog(food: NewFood, meal: MealSlot, dayKey: String): FoodRecord {
                calls += "save and log ${food.name} $meal"
                return food(food.name.lowercase(), food.name)
            }
        },
        notices,
        selectedDay = { day },
        openMeal = ::mealOnScreen,
        queue = resolve::queueView,
        resolvingPart = { resolve.resolvingPart },
        offerContribution = contribution::offerAfterLocalSave,
        startScanFor = { onBarcode -> scan.beginFor(onBarcode) },
        onOpen = { fromResolver -> surfaces.foodFormOpened(fromResolver) },
        onClose = resolve::childClosed,
        onSaved = resolve::foodSaved,
    )
    val scan: ScanSession = ScanSession(
        lookup = object : BarcodeLookup {
            override suspend fun localFood(barcode: String) = barcodes[barcode]
            override suspend fun openFoodFacts(barcode: String) = OffResult.Missing
        },
        notices = notices,
        scope = diary.scope,
        io = Dispatchers.Unconfined,
        lookUpBarcodes = { true },
        selectedDay = { day },
        targetMeal = ::mealOnScreen,
        queueActive = { resolve.queueActive },
        onProduct = { food, origin -> surfaces.productScanned(food, origin) },
        onManualForm = { name, brand, barcode, fromOff -> foodForm.open(name, brand, barcode, fromOff = fromOff) },
    )
    val review: ProductReview = ProductReview(
        diary.session,
        object : ProductStore {
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                calls += "review log ${food.id} $meal"
            }
            override suspend fun updateFood(food: FoodRecord) { calls += "review save ${food.id}" }
            override suspend fun cacheFood(food: FoodRecord): FoodRecord { calls += "review store ${food.id}"; return food }
        },
        notices,
        selectedDay = { day },
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

    val surfaces: Surfaces = Surfaces(
        diary.session, notices, settings, log, library, resolve, entryEdit, foodForm, search, scan, review, contribution, transfer,
        initialMeal = null,
    )

    init {
        // As the view model registers them, after its Undo and feedback listeners; then storage opens.
        diary.session.onChange { change -> if (change.refreshesLibrary) library.refresh() }
        diary.session.onChange(SessionReset(sessionResetSteps(log, contribution, resolve, entryEdit))::onChange)
        diary.session.open {}
    }

    /** The day picked, as the view model's `selectDay`: a lookup in flight no longer applies. */
    fun selectDay(value: String) { day = value; scan.invalidateLookup() }

    /** Puts [entries] in the diary, as a reload would. */
    fun diaryHas(vararg entries: MealEntry) {
        diary.rows = entries.toList()
        diary.session.refresh()
    }

    // ---- What the tests drive, by the names they were written with. ----

    val meal: MealSlot? get() = surfaces.meal
    val settingsOpen: Boolean get() = surfaces.settingsOpen
    fun mealOnScreen(): MealSlot? = surfaces.mealOnScreen()
    fun openMeal(s: MealSlot) = surfaces.openMeal(s)
    fun closeMeal() = surfaces.closeMeal()
    fun openLibrary() = surfaces.openLibrary()
    fun openFocusedLibrary(target: MealSlot, query: String = "") = surfaces.openFocusedLibrary(target, query)
    fun openLibraryFoodDetail(entry: MealEntry) = surfaces.openLibraryFoodDetail(entry)
    fun closeLibrary() = surfaces.closeLibrary()
    fun exitLibrary() = surfaces.exitLibrary()
    fun openSettings() = surfaces.openSettings()

    /** What the root composable draws, bottom first. */
    fun shown(): List<String> = surfaces.shown().map { it.name }

    /**
     * System Back as the activity's dispatcher delivers it (a dialog or sheet takes Back in its own window first). False
     * when nothing takes it, so the system handles it (the app goes back).
     */
    fun back(): Boolean = surfaces.back()

    /** A due export reminder is drawn on Daily. */
    fun reminderShown(): Boolean = exportReminderOnScreen(due = true, covered = surfaces.baseCovered())
}

/** The diary the kit's storage holds ([rows], [foods]), with the [DiarySession] over it, opened by [AppKit]. */
internal class KitDiary(onRefreshed: () -> Unit) {
    var rows: List<MealEntry> = emptyList()
    var foods: List<FoodRecord> = emptyList()
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    val session = DiarySession(
        source = object : DiarySource {
            override suspend fun open() {}
            override fun foods(): List<FoodRecord> = foods
            override fun entries(): List<MealEntry> = rows
            override suspend fun reloadIfStale() = false
        },
        scope = scope,
        onOpenFailed = { throw it },
        onRefreshed = onRefreshed,
    )
}
