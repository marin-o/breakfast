package com.iron.breakfast.app

import com.iron.breakfast.domain.dayKeyFor
import com.iron.breakfast.profile.WeightStore
import com.iron.breakfast.profile.ProfileState
import com.iron.breakfast.profile.ProfilePrefs
import com.iron.breakfast.scan.ProductReviewOrigin
import com.iron.breakfast.search.OffPartSearch
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import com.iron.breakfast.contribution.ContributionFlow
import com.iron.breakfast.contribution.OffContributions
import com.iron.breakfast.data.DIARY_DAMAGED
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.entry.EntryEdit
import com.iron.breakfast.entry.EntryEditStore
import com.iron.breakfast.food.FoodForm
import com.iron.breakfast.food.FoodFormStore
import com.iron.breakfast.log.lastUsedMeal
import com.iron.breakfast.library.LibraryState
import com.iron.breakfast.library.LibraryStore
import com.iron.breakfast.log.LogComposer
import com.iron.breakfast.log.LogStore
import com.iron.breakfast.log.automaticMeal
import com.iron.breakfast.recipes.RecipeBook
import com.iron.breakfast.recipes.RecipeForm
import com.iron.breakfast.recipes.RecipeStore
import com.iron.breakfast.resolve.ResolveFlow
import com.iron.breakfast.resolve.ResolveStore
import com.iron.breakfast.scan.BarcodeLookup
import com.iron.breakfast.scan.ProductReview
import com.iron.breakfast.scan.ProductStore
import com.iron.breakfast.scan.ScanSession
import com.iron.breakfast.search.OffSearch
import com.iron.breakfast.search.OffSearchService
import com.iron.breakfast.settings.SettingsState
import com.iron.breakfast.settings.undoWindowMs
import com.iron.breakfast.today.DayState
import com.iron.breakfast.today.MatchFeedback
import com.iron.breakfast.today.MatchStore
import com.iron.breakfast.today.UndoController
import com.iron.breakfast.today.UndoStore
import com.iron.breakfast.transfer.DiaryData
import com.iron.breakfast.transfer.DiaryFiles
import com.iron.breakfast.transfer.DiaryPicker
import com.iron.breakfast.transfer.DiaryTransfer
import com.iron.breakfast.transfer.ExportReminders
import com.iron.breakfast.transfer.TransferLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * What the owners read and write outside themselves: storage, Open Food Facts and the diary files. The view model
 * adapts `BreakfastEngine`, `OpenFoodFactsClient` and the content resolver; the JVM test kit (`AppKit`) passes fakes.
 */
internal class AppStores<D>(
    val diary: DiarySource,
    val undo: UndoStore,
    val match: MatchStore,
    val resolve: ResolveStore,
    val log: LogStore,
    val library: LibraryStore,
    val entryEdit: EntryEditStore,
    val foodForm: FoodFormStore,
    val products: ProductStore,
    val barcodes: BarcodeLookup,
    val diaryData: DiaryData,
    val diaryFiles: DiaryFiles<D>,
    val transferLog: TransferLog,
    val contributions: OffContributions,
    val offSearch: OffSearchService,
    val recipes: RecipeStore,
    /** About, Licences: the bundled licence as text (an app asset); throws when it cannot be read. */
    val licence: () -> String,
    val weights: WeightStore,
    val profilePrefs: ProfilePrefs,
)

/**
 * Where the owners run: [scope] (the view model's), [io] for files and the network, [main] for what the Library hands
 * back to the main thread, [compute] for ranking and planning. [sleep] is the wait of the Undo and "Not right?" timers.
 */
internal class AppThreads(
    val scope: CoroutineScope,
    val io: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val compute: CoroutineDispatcher,
    val sleep: suspend (Long) -> Unit = { delay(it) },
)

/**
 * Every feature owner and [Surfaces], wired to each other once, for the view model and the JVM tests alike: the
 * owner-to-owner and owner-to-[Surfaces] callbacks, the session listeners (Undo, "Not right?" feedback, the Library
 * list, [SessionReset]), the settings observers, the saved state (restored here, and mirrored after every change),
 * and what runs once storage has opened ([openStore]).
 *
 * The view model owns what is Android's: the engine and the Open Food Facts client behind [stores], the lifecycle
 * (stop, start, the 60-second loop) and the log tag.
 */
internal class AppOwners<D>(
    private val stores: AppStores<D>,
    /** Every user setting and the open Settings page; the effects that are not settings are wired in [init]. */
    val settings: SettingsState,
    private val savedState: SavedStateHandle,
    private val threads: AppThreads,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    onOpenFailed: (Throwable) -> Unit,
) {
    /** Today and the day on show (see [DayState]); a day picked stops a scan looking up for the old one. */
    val day = DayState(
        clock = clock,
        zone = zone,
        dayStartHour = { settings.dayStartHour },
        restored = restoreSelectedDay(savedState),
        save = { saveSelectedDay(savedState, it) },
        onSelected = { scan.invalidateLookup() },
    )
    /** The app's one modal message (see [Notices]); features speak through it. */
    val notices = Notices()
    /** What TalkBack reads out for the snackbars that come and go, each once (see [Announcements]). */
    val announcements = Announcements()
    /** "Logged!" after a food is logged to the meal and day the user chose (see [Confirmations]); it goes by itself. */
    val confirmations = Confirmations(threads.scope, announce = announcements::say, sleep = threads.sleep)
    /** The read model and the write gate (see [DiarySession]). */
    val session = DiarySession(
        source = stores.diary,
        scope = threads.scope,
        onOpenFailed = onOpenFailed,
        onRefreshed = {
            entryEdit.diaryReloaded()
            showDamagedNotice()
        },
    )
    /** The pending Undo, its expiry and Delete (see [UndoController]); Daily and Meal talk to it. */
    val undo = UndoController(
        session = session,
        store = stores.undo,
        notices = notices,
        windowMs = { undoWindowMs(settings.undoWindow) },
        clock = clock,
        scope = threads.scope,
        announce = announcements::say,
        sleep = threads.sleep,
    )
    /** "Not right?" after a logged batch (see [MatchFeedback]); held while the resolver is open. */
    val feedback = MatchFeedback(
        session = session,
        store = stores.match,
        notices = notices,
        // Held while the Log covers it with foods left to match, as while the resolver does.
        resolverOpen = ::feedbackHeld,
        clock = clock,
        scope = threads.scope,
        sleep = threads.sleep,
    )
    /** The Open Food Facts contribution flow (see [ContributionFlow]). */
    val contribution = ContributionFlow(stores.contributions, notices, threads.scope, threads.io, showContribute = { settings.showContribute })
    /** The Open Food Facts search (see [OffSearch]); what it opens over and returns to stays with the resolve flow. */
    val search: OffSearch = OffSearch(
        off = stores.offSearch,
        scope = threads.scope,
        io = threads.io,
        ready = { session.ready },
        onOpen = { fromResolver -> surfaces.searchOpened(fromResolver) },
        onClose = { resolve.childClosed() },
        onChoose = { food, origin -> review.choose(food, origin = origin) },
    )
    /** The Profile page and the macro targets Meals reads (see [ProfileState]). */
    val profile = ProfileState(
        session, stores.weights, stores.profilePrefs, notices,
        today = { dayKeyFor(clock(), zone(), settings.dayStartHour) },
        clock = clock,
    )
    /** The Open Food Facts results under the Log's unmatched foods (see [OffPartSearch]). */
    val offParts = OffPartSearch(stores.offSearch, threads.scope, threads.io, announce = announcements::say)
    /** Export, import, Delete all and the safety copy (see [DiaryTransfer]); the root launches its pickers. */
    val transfer = DiaryTransfer(
        session = session,
        data = stores.diaryData,
        files = stores.diaryFiles,
        settings = settings,
        notices = notices,
        clock = clock,
        scope = threads.scope,
        io = threads.io,
        log = stores.transferLog,
        onExported = { reminders.hide() },
    )
    /** "Export your diary." on Daily (see [ExportReminders]). */
    val reminders: ExportReminders = ExportReminders(settings, session, clock, requestExport = { transfer.requestPicker(DiaryPicker.EXPORT) })
    /** "Not in your foods": the Log ↔ Library resolve frame, the unknown queue and the resolver (see [ResolveFlow]). */
    val resolve: ResolveFlow = ResolveFlow(
        session = session,
        store = stores.resolve,
        notices = notices,
        scope = threads.scope,
        compute = threads.compute,
        selectedDay = { day.selectedDayKey },
        openLibrary = { meal, query -> surfaces.openFocusedLibrary(meal, query) },
        returnToLog = { request, pick -> surfaces.returnToLog(request, pick) },
        confirmLogged = confirmations::logged,
    )
    /** The Log draft and the Log page (see [LogComposer]). */
    val log: LogComposer = LogComposer(
        session = session,
        store = stores.log,
        selectedDay = { day.selectedDayKey },
        automaticMeal = ::automaticMealNow,
        openMeal = ::mealOnScreen,
        editing = { entryEdit.editing },
        afterSave = { unmatched, meal, source, typed ->
            resolve.afterSave(unmatched, meal, source, typed)
            // Add is the only thing that searches, one food at a time, under its row; a part with local choices has them.
            offParts.start(unmatched.filter { log.localChoices(it).isEmpty() }.map(resolve::nameOf))
        },
        onQueueLeft = { source ->
            resolve.leftBy(source)
            offParts.clear()
        },
        onDraftEdited = { source, parts ->
            resolve.sourceEdited(source, parts)
            offParts.retain(resolve.parts.map(resolve::nameOf))
        },
        onMealChosen = resolve::changeMeal,
        queuedParts = { resolve.parts },
        resolvePart = resolve::beginPart,
        // Typed work comes back after the process was killed: the log draft (and an open food form, in [init]).
        compute = threads.compute,
        initial = restoreLogDraft(savedState),
    )
    /** The Library list, its bar, the food page and logging from it (see [LibraryState]). */
    val library: LibraryState = LibraryState(
        session = session,
        store = stores.library,
        notices = notices,
        scope = threads.scope,
        io = threads.io,
        main = threads.main,
        compute = threads.compute,
        selectedDay = { day.selectedDayKey },
        resolvingPart = { resolve.resolvingPart },
        onPartPicked = resolve::finishPart,
        // Nothing that still holds the deleted food can store it again: its form, a Log pin, an open review.
        onDeleted = { food ->
            foodForm.foodDeleted(food.id)
            log.dropPinsOf(food.id)
            review.foodDeleted(food.id)
            recipeForm.dropPinsOf(food.id)
        },
        afterSave = resolve::afterSave,
        confirmLogged = confirmations::logged,
    )
    /** The entry detail and the quick and full edits (see [EntryEdit]); an edit shows its meal and returns to Daily through here. */
    val entryEdit: EntryEdit = EntryEdit(
        session = session,
        store = stores.entryEdit,
        openMeal = ::mealOnScreen,
        // The edit's meal shows beneath it; the Log's error goes, as it always did when an edit began.
        onBegin = { meal -> surfaces.editBegan(meal) },
        onEnd = { returnToDaily -> surfaces.editEnded(returnToDaily) },
        initialEntryId = savedState[ENTRY_ID_KEY],
        saveEntryId = { savedState[ENTRY_ID_KEY] = it },
    )
    /** New food and Edit food (see [FoodForm]); what it opens over and returns to stays with the resolve flow. */
    val foodForm: FoodForm = FoodForm(
        session = session,
        store = stores.foodForm,
        notices = notices,
        selectedDay = { day.selectedDayKey },
        openMeal = ::mealOnScreen,
        queue = resolve::queueView,
        resolvingPart = { resolve.resolvingPart },
        offerContribution = contribution::offerAfterLocalSave,
        startScanFor = { onBarcode -> scan.beginFor(onBarcode) },
        onOpen = { fromResolver -> surfaces.foodFormOpened(fromResolver) },
        onClose = resolve::childClosed,
        onSaved = resolve::foodSaved,
        confirmLogged = confirmations::logged,
        onSavedToLog = { food, origin -> review.choose(food, origin = origin, fetchServings = false) },
    )
    /** The scanner and its barcode lookup (see [ScanSession]); a found product goes to [review], an unknown one to [foodForm]. */
    val scan: ScanSession = ScanSession(
        lookup = stores.barcodes,
        notices = notices,
        scope = threads.scope,
        io = threads.io,
        lookUpBarcodes = { settings.lookUpBarcodes },
        selectedDay = { day.selectedDayKey },
        targetMeal = ::mealOnScreen,
        libraryTab = ::libraryTabOnScreen,
        queueActive = { resolve.queueActive },
        onProduct = { food, origin -> surfaces.productScanned(food, origin) },
        onManualForm = { name, brand, barcode, fromOff, reviewAfterSave ->
            // A new part of the Log's draft leaves the queue under it waiting.
            val overQueue = reviewAfterSave == ProductReviewOrigin.LOG_PART && resolve.queueActive
            foodForm.open(name, brand, barcode, fromResolver = overQueue, fromOff = fromOff, reviewAfterSave = reviewAfterSave)
        },
    )
    /** Product review after a scan or a search pick (see [ProductReview]); its queue and resolve hand-offs go to [resolve]. */
    val review: ProductReview = ProductReview(
        session = session,
        store = stores.products,
        notices = notices,
        selectedDay = { day.selectedDayKey },
        openMeal = ::mealOnScreen,
        automaticMeal = ::automaticMealNow,
        queue = resolve::queueView,
        resolvingPart = { resolve.resolvingPart },
        offerContribution = contribution::offerAfterLocalSave,
        onPartResolved = resolve::finishPart,
        onLogPart = log::addScannedPart,
        onLogged = resolve::childLogged,
        onClosed = resolve::childClosed,
        openSearch = { fromResolver, origin -> search.open(fromResolver = fromResolver, reviewOrigin = origin) },
        startScan = { forLog -> scan.begin(forLog) },
        confirmLogged = confirmations::logged,
        scope = threads.scope,
        io = threads.io,
    )
    /** The Recipes tab: the list, the recipe page, logging one and Delete recipe (see [RecipeBook]). */
    val recipes: RecipeBook = RecipeBook(
        session = session,
        store = stores.recipes,
        notices = notices,
        selectedDay = { day.selectedDayKey },
        confirmLogged = confirmations::logged,
        // Nothing that still holds the deleted recipe can store or log it again: its form, a Log pin.
        onDeleted = { id ->
            recipeForm.recipeDeleted(id)
            log.dropPinsOf(id)
        },
    )
    /** New recipe and Edit, with the ingredient search (see [RecipeForm]); a saved recipe shows its page. */
    val recipeForm: RecipeForm = RecipeForm(
        session = session,
        store = stores.recipes,
        notices = notices,
        scope = threads.scope,
        io = threads.io,
        main = threads.main,
        compute = threads.compute,
        onSaved = { recipe -> surfaces.showRecipe(recipe) },
    )
    /** What is open and in which order: drawing, Back and the top check (see [Surfaces]); the Meal screen and Settings. */
    val surfaces: Surfaces = Surfaces(
        session, notices, settings, log, library, resolve, entryEdit, foodForm, search, scan, review, contribution, transfer, recipes, recipeForm, profile,
        initialMeal = savedState.get<String>(MEAL_SLOT_KEY)?.let { runCatching { MealSlot.valueOf(it) }.getOrNull() },
    )

    init {
        restoreFoodForm(savedState)?.let(foodForm::restore)
        restoreRecipeForm(savedState)?.let(recipeForm::restore)
        restoreProfile(savedState)?.let(profile::restore)
        // Every change is mirrored, so an added or cleared draft and a closed or saved form drop their keys.
        threads.scope.launch { snapshotFlow { log.savedDraft() }.collect { saveLogDraft(savedState, it) } }
        threads.scope.launch { snapshotFlow { foodForm.savedForm() }.collect { saveFoodForm(savedState, it) } }
        threads.scope.launch { snapshotFlow { recipeForm.savedForm() }.collect { saveRecipeForm(savedState, it) } }
        threads.scope.launch { snapshotFlow { profile.savedPage() }.collect { saveProfile(savedState, it) } }
        // The meal on screen comes back after process death as the Meal screen (the Library does not reopen).
        threads.scope.launch { snapshotFlow { mealOnScreen() }.collect { savedState[MEAL_SLOT_KEY] = it?.name } }
        // After every committed write, in this order: Undo, "Not right?" feedback, the Library list.
        session.onChange(undo::onChange)
        session.onChange(feedback::onChange)
        session.onChange { change -> if (change.refreshesLibrary) library.refresh() }
        // Import and Delete all: one reset of everything that referred to the old diary (see [SessionReset]).
        session.onChange(SessionReset(sessionResetSteps(log, contribution, resolve, entryEdit, foodForm, recipes, recipeForm, profile))::onChange)
        // A new day start: today follows it at once, and a user on today stays on today (it counts as a return).
        settings.dayStartHourSetting.observe { day.check(returning = true) }
        // An Undo offer showing now follows the new window from the moment it was made.
        settings.undoWindowSetting.observe { undo.windowChanged() }
        settings.exportReminderSetting.observe(reminders::settingChanged)
        // Choosing "Safety copy" Don't keep also removes the copy kept so far; a reset is not a choice and never does.
        settings.keepSafetyCopySetting.onChosen { keep -> if (!keep) transfer.removeSafetyCopy() }
        openStore()
    }

    /** Opens storage; on failure the "Storage unavailable" dialog offers a retry, which runs this again. */
    fun openStore() = session.open {
        reminders.check()
        foodForm.closeIfFoodGone()
        recipeForm.closeIfRecipeGone()
    }

    /**
     * The meal the "Meal" setting gives a log with no chosen, typed or preset meal, at this moment; null for Ask. The
     * Log page falls back to it, and the Library preselects it (see [com.iron.breakfast.library.libraryPresetMeal]).
     */
    fun automaticMealNow(): MealSlot? = automaticMeal(
        settings.mealMode, Instant.ofEpochMilli(clock()).atZone(zone()).hour, settings.mealBounds, lastUsedMeal(session.entries),
        settings.dayStartHour, settings.beforeDayStart,
    )

    /**
     * After a damaged diary was set aside (`data/DamagedDiaries`): its notice, until the user dismissed it, also after
     * the app was killed before that. Checked after every reload, so damage found during the session shows it too.
     */
    private fun showDamagedNotice() {
        if (!session.damagedNoticePending() || notices.message == DIARY_DAMAGED) return
        notices.show(DIARY_DAMAGED, onDismiss = session::damagedNoticeSeen)
    }

    /** About, Licences: the bundled licence, read on [AppThreads.io]; empty when it cannot be read. */
    suspend fun licenceText(): String = withContext(threads.io) { runCatching { stores.licence() }.getOrDefault("") }

    /** "Not right?" waits while what shows it is covered by foods left to match: the resolver, or the Log with its rows. */
    internal fun feedbackHeld(): Boolean = resolve.resolverOpen || (log.open && resolve.queueActive)

    /** The Library tab itself is on show, not a meal's Library. */
    private fun libraryTabOnScreen(): Boolean = library.open && library.target == null

    /** The meal of the screen on show ([Surfaces.mealOnScreen]), for the owners that log to it. */
    private fun mealOnScreen(): MealSlot? = surfaces.mealOnScreen()
}

/** The meal on screen in `SavedStateHandle`. */
private const val MEAL_SLOT_KEY = "meal_slot"
/** The open entry detail in `SavedStateHandle`. */
private const val ENTRY_ID_KEY = "entry_id"
