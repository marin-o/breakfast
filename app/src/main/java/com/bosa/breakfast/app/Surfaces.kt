package com.bosa.breakfast.app

import com.bosa.breakfast.onboarding.OnboardingState
import com.bosa.breakfast.profile.ProfileState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bosa.breakfast.library.LibraryExit
import com.bosa.breakfast.contribution.ContributionFlow
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MealEntry
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.Recipe
import com.bosa.breakfast.domain.isRecipeId
import com.bosa.breakfast.recipes.RecipeBook
import com.bosa.breakfast.recipes.RecipeForm
import com.bosa.breakfast.entry.EntryEdit
import com.bosa.breakfast.entry.EntryEditMode
import com.bosa.breakfast.library.exitLibraryTarget
import com.bosa.breakfast.food.FoodForm
import com.bosa.breakfast.library.LibraryState
import com.bosa.breakfast.log.LogComposer
import com.bosa.breakfast.resolve.LogResolveRequest
import com.bosa.breakfast.resolve.ResolveFlow
import com.bosa.breakfast.scan.ProductReview
import com.bosa.breakfast.scan.ProductReviewOrigin
import com.bosa.breakfast.scan.ScanSession
import com.bosa.breakfast.search.OffSearch
import com.bosa.breakfast.settings.SettingsState
import com.bosa.breakfast.transfer.DiaryTransfer

/**
 * Everything the root composable draws, in drawing order: each surface is drawn over the ones declared before it.
 * The base screens come first, the two drawn alone last.
 */
internal enum class Surface(
    /** Drawn over the base screen: the base then takes no Back, and "No local match" and the export reminder stay hidden. */
    val coversBase: Boolean = false,
) {
    DAILY, MEAL, LIBRARY,
    /** The Recipes tab: the list of recipes. */
    RECIPES,
    /** The scanner's frozen frame behind a scanned product's review; it replaces the base, the Log and the resolver. */
    SCAN_REVIEW_BACKDROP,
    LOG(coversBase = true),
    ENTRY_QUICK_EDIT(coversBase = true), ENTRY_FULL_EDIT(coversBase = true), ENTRY_FOOD_PICKER,
    /** A food tapped in a meal's Library: the amount sheet. */
    LIBRARY_FOOD_ADD(coversBase = true),
    /** A food tapped on the Library tab: its page. */
    LIBRARY_FOOD(coversBase = true),
    /** A recipe tapped on the Recipes tab: its page. */
    RECIPE(coversBase = true),
    /** A recipe row's "+": the amount sheet with its units and the meal. */
    RECIPE_LOG(coversBase = true),
    /** New recipe and Edit. */
    RECIPE_FORM(coversBase = true),
    /** The recipe form's ingredient search, over the form. */
    RECIPE_SEARCH(coversBase = true),
    /** A food picked in the ingredient search: the amount sheet, whose Add adds the ingredient. */
    RECIPE_INGREDIENT_ADD(coversBase = true),
    DISCARD_RECIPE,
    /** "Delete recipe" asks first, over the recipe page or its form. */
    DELETE_RECIPE,
    MIXED_MEAL_PICKER, BATCH_MEAL_PICKER,
    FOOD_FORM(coversBase = true), SETTINGS(coversBase = true), PROFILE(coversBase = true), DISCARD_PROFILE,
    /** Deleting a point of the weight curve asks first, over the Profile page. */
    DELETE_WEIGHT_POINT,
    DISCARD_FOOD,
    /** "Delete food" asks first, over the food page or Edit food. */
    DELETE_FOOD,
    LIBRARY_NO_MATCH,
    OFF_SEARCH(coversBase = true), RESOLVER(coversBase = true), PRODUCT_REVIEW(coversBase = true),
    CONTRIBUTION_REVIEW, CONTRIBUTION_SIGN_IN, CONTRIBUTION_FAILURE,
    NOTICE, REPLACE_DIARY, DELETE_ALL, STORAGE_UNAVAILABLE,
    /** Drawn alone, nothing else (not even a notice) until it closes; under the scan backdrop it waits undrawn. */
    ENTRY_DETAIL(coversBase = true),
    /** Drawn alone, over whatever it was opened from, which comes back as it was. */
    SCANNER(coversBase = true),
    ONBOARDING(coversBase = true);

    val base: Boolean get() = this == DAILY || this == MEAL || this == LIBRARY || this == RECIPES
}

/**
 * What is open, in which order: the one model the root composable draws from ([shown]), system Back follows ([back])
 * and the top check reads ([baseCovered]).
 *
 * Each feature keeps its own state (the Log page, the scanner, the resolver, a dialog's flag); [shown] derives the
 * ordered list of surfaces from it, so drawing order, Back and "is anything on top" cannot disagree. Stacking that
 * is legitimate stays: the scanner over the food form (Back returns to the form), the resolver under its children
 * (it comes back when one closes without resolving) and after a background save, the Log ↔ Library part-resolve
 * frame, Settings and its pages over Daily or the Library, the entry detail and its edits, the contribution screens, dialogs
 * and notices.
 *
 * It owns the two surfaces that are the app's own: the Meal screen ([meal]) and Settings ([settingsOpen], over Daily
 * or the Library), and the
 * transitions between features that decide what replaces what: leaving the Library ([exitLibrary]), the return to
 * the Log ([returnToLog]), and a child of the resolver taking its place ([searchOpened], [foodFormOpened],
 * [productScanned]).
 */
internal class Surfaces(
    private val session: DiarySession,
    private val notices: Notices,
    private val settings: SettingsState,
    private val log: LogComposer,
    private val library: LibraryState,
    private val resolve: ResolveFlow,
    private val entryEdit: EntryEdit,
    private val foodForm: FoodForm,
    private val search: OffSearch,
    private val scan: ScanSession,
    private val review: ProductReview,
    private val contribution: ContributionFlow,
    private val transfer: DiaryTransfer<*>,
    private val recipes: RecipeBook,
    private val recipeForm: RecipeForm,
    private val profile: ProfileState,
    private val onboarding: OnboardingState,
    /** The Meal screen's meal restored after process death (the Library does not reopen). */
    initialMeal: MealSlot?,
) {
    /**
     * The Meal screen's meal; null on Daily. While the Library is open it shows instead, adding to its own
     * [LibraryState.target]; leaving the Library says which of the two comes back.
     */
    var meal by mutableStateOf(initialMeal); private set
    /**
     * Settings, over Daily or the Library (the bottom bar's icon); its page is [SettingsState.page]. Back returns to
     * the screen it was opened over; every way out of the Library closes it.
     */
    var settingsOpen by mutableStateOf(false); private set
    /** The Profile page, opened and left as Settings is. */
    val profileOpen: Boolean get() = profile.open

    /** What is drawn now, bottom first, in [Surface] order. */
    fun shown(): List<Surface> {
        // Alone, over everything: the state of what was open or restored waits under it, though not its scroll position.
        if (onboarding.open) return listOf(Surface.ONBOARDING)
        if (scan.open) return listOf(Surface.SCANNER)
        val backdrop = review.product != null && review.fromScan
        if (!backdrop && entryEdit.selectedEntry != null) return listOf(Surface.ENTRY_DETAIL)
        val editing = entryEdit.editingEntry() != null
        val open = Surface.entries.filter { isOpen(it, backdrop, editing) }
        // "No local match" is the Library's own result screen: it shows only with nothing over the Library.
        return if (library.noMatch(otherScreenOpen = open.any { it.coversBase })) (open + Surface.LIBRARY_NO_MATCH).sortedBy { it.ordinal } else open
    }

    private fun isOpen(surface: Surface, backdrop: Boolean, editing: Boolean): Boolean = when (surface) {
        Surface.DAILY -> !backdrop && !library.open && !recipes.open && meal == null
        Surface.MEAL -> !backdrop && !library.open && !recipes.open && meal != null
        Surface.LIBRARY -> !backdrop && library.open
        Surface.RECIPES -> !backdrop && recipes.open && !library.open
        Surface.SCAN_REVIEW_BACKDROP -> backdrop
        Surface.LOG -> !backdrop && log.open
        Surface.ENTRY_QUICK_EDIT -> entryEdit.mode == EntryEditMode.QUICK && editing
        Surface.ENTRY_FULL_EDIT -> entryEdit.mode == EntryEditMode.FULL && editing
        Surface.ENTRY_FOOD_PICKER -> entryEdit.pickerOpen
        Surface.LIBRARY_FOOD_ADD -> library.selectedFood != null && mealOnScreen() != null
        Surface.LIBRARY_FOOD -> library.selectedFood != null && mealOnScreen() == null
        Surface.RECIPE -> recipes.page != null
        Surface.RECIPE_LOG -> recipes.logging != null
        Surface.RECIPE_FORM -> recipeForm.open
        Surface.RECIPE_SEARCH -> recipeForm.open && recipeForm.searchOpen
        Surface.RECIPE_INGREDIENT_ADD -> recipeForm.open && recipeForm.adding != null
        Surface.DISCARD_RECIPE -> recipeForm.discardOpen
        Surface.DELETE_RECIPE -> recipes.deleteRequest != null
        Surface.MIXED_MEAL_PICKER -> library.mixedMealPickerOpen
        Surface.BATCH_MEAL_PICKER -> library.batchMealPickerOpen
        Surface.FOOD_FORM -> foodForm.open
        Surface.SETTINGS -> settingsOpen
        Surface.PROFILE -> profile.open
        Surface.DISCARD_PROFILE -> profile.discardOpen
        Surface.DELETE_WEIGHT_POINT -> profile.deleteRequest != null
        Surface.DISCARD_FOOD -> foodForm.discardOpen
        Surface.DELETE_FOOD -> library.deleteRequest != null
        Surface.LIBRARY_NO_MATCH -> false
        Surface.OFF_SEARCH -> search.open
        Surface.RESOLVER -> !backdrop && resolve.resolverOpen
        Surface.PRODUCT_REVIEW -> review.product != null
        Surface.CONTRIBUTION_REVIEW -> contribution.reviewOpen && contribution.pending != null
        Surface.CONTRIBUTION_SIGN_IN -> contribution.authOpen
        Surface.CONTRIBUTION_FAILURE -> contribution.failure != null
        Surface.NOTICE -> notices.message != null
        Surface.REPLACE_DIARY -> transfer.pendingImport != null
        Surface.DELETE_ALL -> transfer.deleteAllOpen
        Surface.STORAGE_UNAVAILABLE -> session.storageUnavailable
        Surface.ENTRY_DETAIL, Surface.SCANNER, Surface.ONBOARDING -> false
    }

    /** The top check: something is drawn over the base screen. */
    fun baseCovered(): Boolean = shown().any { it.coversBase }

    /**
     * System Back takes the topmost surface of [shown] that handles it; false leaves Back to the system (Daily: the app
     * goes back). The root passes the list it just drew, so it is derived once.
     */
    fun takesBack(shown: List<Surface> = shown()): Boolean = backAction(shown) != null

    /** System Back. A dialog or sheet takes Back in its own window, so Back reaches here only for the screens. */
    fun back(): Boolean {
        val action = backAction(shown()) ?: return false
        action()
        return true
    }

    private fun backAction(shown: List<Surface>): (() -> Unit)? {
        val covered = shown.any { it.coversBase }
        return shown.asReversed().firstNotNullOfOrNull { backOf(it, covered) }
    }

    private fun backOf(surface: Surface, covered: Boolean): (() -> Unit)? = when (surface) {
        Surface.DAILY -> null
        // The same exits as the arrows: a meal's Library goes to the meal, a part's to the Log.
        Surface.MEAL -> ::closeMeal.takeIf { !covered }
        Surface.LIBRARY -> ::exitLibrary.takeIf { !covered }
        Surface.RECIPES -> ::closeRecipes.takeIf { !covered }
        Surface.RECIPE -> recipes::closeRecipe
        Surface.RECIPE_FORM -> recipeForm::leave
        Surface.RECIPE_SEARCH -> recipeForm::closeSearch
        Surface.LOG -> log::close
        Surface.ENTRY_QUICK_EDIT, Surface.ENTRY_FULL_EDIT -> ::cancelEdit
        Surface.LIBRARY_FOOD -> library::closeFood
        Surface.FOOD_FORM -> foodForm::leave
        Surface.SETTINGS -> ::settingsBack
        Surface.PROFILE -> profile::leave
        Surface.LIBRARY_NO_MATCH -> ::leaveNoMatch
        Surface.OFF_SEARCH -> search::close
        Surface.RESOLVER -> resolve::close
        Surface.CONTRIBUTION_REVIEW -> contribution::dismissReview
        // Also while it submits, as its arrow and Cancel do: the submit is cancelled and only the sign-in closes.
        Surface.CONTRIBUTION_SIGN_IN -> contribution::dismissAuth
        Surface.CONTRIBUTION_FAILURE -> contribution::dismissFailure
        Surface.ENTRY_DETAIL -> entryEdit::closeDetail
        Surface.SCANNER -> scan::end
        Surface.ONBOARDING -> onboarding::back.takeIf { onboarding.takesBack }
        // Dialogs and sheets, in their own window; the backdrop goes with its review sheet.
        Surface.SCAN_REVIEW_BACKDROP, Surface.ENTRY_FOOD_PICKER, Surface.LIBRARY_FOOD_ADD, Surface.RECIPE_LOG, Surface.RECIPE_INGREDIENT_ADD,
        Surface.DISCARD_RECIPE, Surface.DELETE_RECIPE, Surface.DISCARD_PROFILE, Surface.DELETE_WEIGHT_POINT, Surface.MIXED_MEAL_PICKER,
        Surface.BATCH_MEAL_PICKER, Surface.DISCARD_FOOD, Surface.DELETE_FOOD, Surface.PRODUCT_REVIEW, Surface.NOTICE, Surface.REPLACE_DIARY,
        Surface.DELETE_ALL, Surface.STORAGE_UNAVAILABLE -> null
    }

    private fun cancelEdit() { entryEdit.cancel() }

    /**
     * The meal of the screen on show: the Library's [LibraryState.target] while it is open, else the Meal screen's
     * [meal]. What a scan, a review, a new food or an edit logs to when nothing else decides.
     */
    fun mealOnScreen(): MealSlot? = if (library.open) library.target else meal

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
    private fun showLibrary(target: MealSlot?, query: String, bar: String = query) {
        entryEdit.cancel()
        meal = null
        recipes.close()
        library.show(target, query, bar)
    }
    fun openLibrary() = showLibrary(null, "")
    /** The Library adding to [target] (a meal's "Add food", a part being resolved), searching [query]. */
    fun openFocusedLibrary(target: MealSlot, query: String = "") {
        // A part sent from the Log keeps the rest of its queue; a meal's Add food ends any.
        if (!resolve.resolvingPart) resolve.abandon()
        showLibrary(target, query)
    }
    /** An entry's "Open in Library": the Library tab with that food's page open; a recipe's entry opens its recipe. */
    fun openLibraryFoodDetail(entry: MealEntry) {
        if (isRecipeId(entry.foodId)) {
            entryEdit.closeDetail()
            openRecipes()
            session.recipes.firstOrNull { it.id == entry.foodId }?.let(recipes::openRecipe)
            return
        }
        entryEdit.closeDetail()
        showLibrary(null, entry.foodName, bar = "")
        library.showFood(session.foods.firstOrNull { it.id == entry.foodId })
    }
    fun closeLibrary() = leaveLibrary(returnTo = null)
    /** The Library's arrow and system Back, by [exitLibraryTarget]. */
    fun exitLibrary() {
        when (exitLibraryTarget(resolve.resolvingPart, library.target)) {
            LibraryExit.MEAL -> leaveLibrary(returnTo = library.target)
            LibraryExit.LOG, LibraryExit.DAILY -> closeLibrary()
        }
    }
    /** Leaves the Library (and Settings over it) for [returnTo] (null: Daily); a part being resolved goes back to the Log. */
    private fun leaveLibrary(returnTo: MealSlot?) {
        if (resolve.cancelPart()) return
        library.close()
        closePagesOverTheTabs()
        meal = returnTo
    }
    /**
     * The Recipes tab (its bottom-bar tab), over whatever base screen showed: the Library and a meal close, as the tabs
     * replace each other; an open entry edit ends.
     */
    fun openRecipes() {
        if (recipes.open) return
        entryEdit.cancel()
        if (library.open) closeLibrary()
        closePagesOverTheTabs()
        meal = null
        recipes.show()
    }
    /** Leaves the Recipes tab (and Settings over it) for Daily: its Meals tab and Back. */
    fun closeRecipes() {
        recipes.close()
        closePagesOverTheTabs()
        meal = null
    }
    /** [recipe]'s page on the Recipes tab, which opens if it was not (a recipe saved from a form restored over Daily). */
    fun showRecipe(recipe: Recipe) {
        openRecipes()
        recipes.openRecipe(recipe)
    }

    /** "No local match"'s arrow and Back: a part being resolved goes back to the Log, else the list shows again. */
    fun leaveNoMatch() { if (resolve.resolvingPart) closeLibrary() else library.onQuery("") }

    /** Settings, over Daily or the Library, on the list of groups; its arrow and Back return there. */
    fun openSettings() { settings.showList(); settingsOpen = true }
    private fun closeSettings() { settingsOpen = false; settings.showList() }
    /** Back or the arrow in Settings: a page goes back ([SettingsState.back]), the list closes Settings. */
    fun settingsBack() { if (!settings.back()) closeSettings() }
    /** The Profile page, from the bottom bar's icon at its left; it starts from what is saved. */
    fun openProfile() = profile.show()
    /** Settings, "Show the tutorial": what it was opened over comes back when it ends, unless an end says otherwise. */
    fun openOnboarding() = onboarding.show()
    /** The tutorial's "Open Profile": done, and the page is open. */
    fun onboardingToProfile() { onboarding.finish(); openProfile() }
    /** The tutorial's "Start logging": done, and Meals shows, whatever a replay was opened over. */
    fun onboardingToMeals() {
        onboarding.finish()
        if (library.open) closeLibrary()
        closeRecipes()
    }
    /** A tab changing leaves neither of the bar's two pages over it. */
    private fun closePagesOverTheTabs() { closeSettings(); profile.close() }

    /** The resolve frame ended ([ResolveFlow]): the Library and what was over it close, and the Log opens where it was. */
    fun returnToLog(request: LogResolveRequest, pick: FoodRecord?) {
        leaveLibrary(returnTo = request.slot)
        search.hide()
        foodForm.hide()
        review.hide()
        log.resume(request, pick)
    }
    /** The search opened: it replaces the resolver and the food form. */
    fun searchOpened(fromResolver: Boolean) { resolve.childOpened(fromResolver); foodForm.hide() }
    /** The food form opened: it replaces the resolver and the search. */
    fun foodFormOpened(fromResolver: Boolean) { resolve.childOpened(fromResolver); search.hide() }
    /** The scanner found [food]: its review replaces the search. */
    fun productScanned(food: FoodRecord, origin: ProductReviewOrigin) { search.hide(); review.choose(food, fromScan = true, origin = origin) }
    /** An entry edit began: its entry's meal shows beneath it, and the Log's error goes. */
    fun editBegan(meal: MealSlot) { this.meal = meal; log.clearError() }
    /** An entry edit ended: one opened from Daily ([returnToDaily]) returns there, and the Log's error goes. */
    fun editEnded(returnToDaily: Boolean) { if (returnToDaily) meal = null; log.clearError() }
}
