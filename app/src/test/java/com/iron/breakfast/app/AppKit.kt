package com.iron.breakfast.app

import com.iron.breakfast.LibraryExit
import com.iron.breakfast.QueueDispatcher
import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.contribution.OffContributions
import com.iron.breakfast.contributionAuthCanDismiss
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
import com.iron.breakfast.exitLibraryTarget
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.food.FoodFormStore
import com.iron.breakfast.food.NewFood
import com.iron.breakfast.library.LibraryState
import com.iron.breakfast.library.LibraryStore
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.log.LogStore
import com.iron.breakfast.resolve.LogResolveRequest
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
import com.iron.breakfast.today.OpenDiary
import com.iron.breakfast.topOverlay
import com.iron.breakfast.transfer.DiaryTransfer
import com.iron.breakfast.transfer.FakeData
import com.iron.breakfast.transfer.FakeFiles
import com.iron.breakfast.transfer.RecordingLog
import com.iron.breakfast.transfer.exportReminderOnScreen
import kotlinx.coroutines.Dispatchers

/**
 * Every feature owner that opens a surface, over fakes, wired as the view model wires them, with the app's coordination
 * (the Meal screen, Settings, leaving the Library, the return to the Log) and the root composable's rules for what is
 * drawn ([shown]), what takes Back ([back]) and whether anything is over the base screen.
 *
 * Characterisation: the coordination and the three rules here are transcribed from `BreakfastViewModel` and `IronApp`
 * as they are before `Surfaces`; the tests in [SurfacesTest] pin them, and keep passing once this kit reads them from
 * `Surfaces` instead.
 */
internal class AppKit {
    val diary = OpenDiary()
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

    // The view model's own surface state before Surfaces.
    var meal: MealSlot? = null; private set
    var settingsOpen = false; private set

    val contribution = ContributionFlow(
        object : OffContributions {
            override fun preflight(draft: OffContributionDraft) = OffPreflightResult.Existing
            override fun contribute(draft: OffContributionDraft, username: String, password: String) = OffContributionResult.Success
        },
        notices, diary.scope, offIo,
    )
    val search = OffSearch(
        off = object : OffSearchService { override suspend fun search(query: String) = OffSearchResult.Success(emptyList()) },
        scope = diary.scope,
        io = Dispatchers.Unconfined,
        ready = { diary.session.ready },
        onOpen = ::searchOpened,
        onClose = { resolve.childClosed() },
        onChoose = { food -> review.choose(food) },
    )
    val transfer = DiaryTransfer(
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
        openLibrary = ::openFocusedLibrary,
        returnToLog = ::returnToLog,
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
            override suspend fun foodsMatching(query: String, includeArchived: Boolean) = libraryFoods
            override fun planner() = plannerFor(emptyMap())
            override fun search(phrase: String) = emptyList<RankedFood>()
            override suspend fun cacheFoodAndLog(food: FoodRecord, amount: Double, unit: MeasureUnit, meal: MealSlot, dayKey: String) {
                calls += "library log ${food.id} $meal"
            }
            override suspend fun save(text: String, meal: MealSlot, dayKey: String): SavedMeal {
                calls += "library save \"$text\" $meal"
                return SavedMeal(listOf(com.iron.breakfast.today.entry("batch")), NutritionValues.ZERO, unmatched)
            }
            override suspend fun archiveFood(id: String) {}
            override suspend fun restoreFood(id: String) {}
        },
        notices, diary.scope, Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined,
        selectedDay = { day },
        resolvingPart = { resolve.resolvingPart },
        onPartPicked = resolve::finishPart,
        onArchived = { food -> if (foodForm.editingFoodId == food.id) foodForm.close() },
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
        onBegin = { meal -> this.meal = meal; log.clearError() },
        onEnd = { returnToDaily -> if (returnToDaily) meal = null; log.clearError() },
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
        onOpen = { fromResolver -> resolve.childOpened(fromResolver); search.hide() },
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
        onProduct = { food, origin -> search.hide(); review.choose(food, fromScan = true, origin = origin) },
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

    /** Puts [entries] in the diary, as a reload would. */
    fun diaryHas(vararg entries: MealEntry) {
        diary.rows = entries.toList()
        diary.session.refresh()
    }

    // ---- The view model's coordination, transcribed as it is before Surfaces. ----

    fun mealOnScreen(): MealSlot? = if (library.open) library.target else meal
    fun openMeal(s: MealSlot) { resolve.abandon(); meal = s; scan.invalidateLookup(); entryEdit.cancel(); log.clearError() }
    fun closeMeal() {
        if (resolve.cancelPart()) return
        resolve.abandon(); meal = null; scan.invalidateLookup(); entryEdit.cancel()
    }
    private fun showLibrary(target: MealSlot?, query: String, archivedOnly: Boolean, bar: String = query) {
        entryEdit.cancel()
        meal = null
        library.show(target, query, archivedOnly, bar)
    }
    fun openLibrary() = showLibrary(null, "", archivedOnly = false)
    fun openArchivedLibrary() = showLibrary(null, "", archivedOnly = true)
    fun openFocusedLibrary(target: MealSlot, query: String = "") {
        resolve.abandon()
        showLibrary(target, query, archivedOnly = false)
    }
    fun openLibraryFoodDetail(entry: MealEntry) {
        entryEdit.closeDetail()
        showLibrary(null, entry.foodName, archivedOnly = false, bar = "")
        library.showFood(diary.session.foods.firstOrNull { it.id == entry.foodId })
    }
    fun closeLibrary() = leaveLibrary(returnTo = null)
    fun exitLibrary() {
        when (exitLibraryTarget(resolve.resolvingPart, library.archivedOnly, library.target)) {
            LibraryExit.LIBRARY -> openLibrary()
            LibraryExit.MEAL -> leaveLibrary(returnTo = library.target)
            LibraryExit.LOG, LibraryExit.DAILY -> closeLibrary()
        }
    }
    private fun leaveLibrary(returnTo: MealSlot?) {
        if (resolve.cancelPart()) return
        library.close()
        settingsOpen = false
        settings.showList()
        meal = returnTo
    }
    private fun returnToLog(request: LogResolveRequest, pick: FoodRecord?) {
        leaveLibrary(returnTo = request.slot)
        search.hide()
        foodForm.hide()
        review.hide()
        log.resume(request, pick)
    }
    private fun searchOpened(fromResolver: Boolean) { resolve.childOpened(fromResolver); foodForm.hide() }
    fun openSettings() { settings.showList(); settingsOpen = true }
    fun settingsBack() { if (!settings.back()) { settingsOpen = false; settings.showList() } }
    /** The "No local match" screen's arrow and Back. */
    fun leaveNoMatch() { if (resolve.resolvingPart) closeLibrary() else library.onQuery("") }

    // ---- IronApp's three rules, transcribed as they are before Surfaces. ----

    private fun topOverlayNow() = topOverlay(
        scanner = scan.open, entryDetail = entryEdit.selectedEntry != null, productReview = review.product != null,
        resolver = resolve.resolverOpen, offSearch = search.open, foodForm = foodForm.open,
        libraryFood = library.selectedFood != null, entryEdit = entryEdit.mode != EntryEditMode.NONE,
        settings = settingsOpen,
    )
    private fun libraryNoMatch() = library.noMatch(otherScreenOpen = topOverlayNow() != null)

    /** What the root composable draws, bottom first. */
    fun shown(): List<String> {
        if (scan.open) return listOf("SCANNER")
        val out = mutableListOf<String>()
        val backdrop = review.product != null && review.fromScan
        if (backdrop) out += "SCAN_REVIEW_BACKDROP" else {
            if (entryEdit.selectedEntry != null) return listOf("ENTRY_DETAIL")
            out += when { library.open -> "LIBRARY"; meal == null -> "DAILY"; else -> "MEAL" }
            if (log.open) out += "LOG"
        }
        when (entryEdit.mode) {
            EntryEditMode.QUICK -> if (entryEdit.editingEntry() != null) out += "ENTRY_QUICK_EDIT"
            EntryEditMode.FULL -> if (entryEdit.editingEntry() != null) out += "ENTRY_FULL_EDIT"
            EntryEditMode.NONE -> Unit
        }
        if (entryEdit.pickerOpen) out += "ENTRY_FOOD_PICKER"
        if (library.selectedFood != null) out += if (mealOnScreen() != null) "LIBRARY_FOOD_ADD" else "LIBRARY_FOOD"
        if (library.mixedMealPickerOpen) out += "MIXED_MEAL_PICKER"
        if (library.batchMealPickerOpen) out += "BATCH_MEAL_PICKER"
        if (foodForm.open) out += "FOOD_FORM"
        if (settingsOpen) out += "SETTINGS"
        if (foodForm.discardOpen) out += "DISCARD_FOOD"
        if (libraryNoMatch()) out += "LIBRARY_NO_MATCH"
        if (search.open) out += "OFF_SEARCH"
        if (resolve.resolverOpen && !backdrop) out += "RESOLVER"
        if (review.product != null) out += "PRODUCT_REVIEW"
        if (contribution.reviewOpen && contribution.pending != null) out += "CONTRIBUTION_REVIEW"
        if (contribution.authOpen) out += "CONTRIBUTION_SIGN_IN"
        if (contribution.failure != null) out += "CONTRIBUTION_FAILURE"
        if (notices.message != null) out += "NOTICE"
        if (transfer.pendingImport != null) out += "REPLACE_DIARY"
        if (transfer.deleteAllOpen) out += "DELETE_ALL"
        if (diary.session.storageUnavailable) out += "STORAGE_UNAVAILABLE"
        return out
    }

    /**
     * System Back as the activity's dispatcher delivers it (a dialog or sheet takes Back in its own window first): the
     * last composed enabled handler runs. False when none is enabled, so the system handles it (the app goes back).
     */
    fun back(): Boolean {
        val handlers = mutableListOf<() -> Unit>()
        if (scan.open) handlers += scan::end else {
            val backdrop = review.product != null && review.fromScan
            if (!backdrop) {
                if (entryEdit.selectedEntry != null) { entryEdit.closeDetail(); return true }
                if (log.open) handlers += log::close
            }
            if (entryEdit.mode != EntryEditMode.NONE && entryEdit.editingEntry() != null) handlers += { entryEdit.cancel() }
            if (library.selectedFood != null && mealOnScreen() == null) handlers += library::closeFood
            if (!log.open && topOverlayNow() == null) {
                if (library.open) handlers += ::exitLibrary else if (meal != null) handlers += ::closeMeal
            }
            if (foodForm.open) handlers += foodForm::leave
            if (settingsOpen) handlers += ::settingsBack
            if (libraryNoMatch()) handlers += ::leaveNoMatch
            if (search.open) handlers += search::close
            if (resolve.resolverOpen && !backdrop) handlers += resolve::close
            if (contribution.reviewOpen && contribution.pending != null) handlers += contribution::dismissReview
            if (contribution.authOpen && contributionAuthCanDismiss(contribution.busy)) handlers += contribution::dismissAuth
            if (contribution.failure != null) handlers += contribution::dismissFailure
        }
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }

    /** The "No local match" screen is drawn. */
    fun noMatchShown(): Boolean = "LIBRARY_NO_MATCH" in shown()

    /** A due export reminder is drawn on Daily. */
    fun reminderShown(): Boolean = exportReminderOnScreen(due = true, logOpen = log.open, overlay = topOverlayNow())
}
