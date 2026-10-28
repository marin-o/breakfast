package com.iron.breakfast.app

import com.iron.breakfast.domain.DraftRow
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.resolve.food
import com.iron.breakfast.settings.SettingsPage
import com.iron.breakfast.today.entry
import com.iron.breakfast.transfer.validDiaryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What is open and in which order, over every feature owner wired as the app wires them ([AppKit]): what is drawn,
 * what system Back does, and whether anything sits over the base screen. One test per stacking case of the
 * architecture inventory (§2.4), plus the base screens, the top check and the combinations that must not occur.
 */
class SurfacesTest {
    private val app = AppKit()
    private val barcode = "4006381333931"
    private val egg = entry("e1").copy(mealSlot = MealSlot.LUNCH)

    private fun shows(vararg surfaces: String) = assertEquals(surfaces.toList(), app.shown())

    // ---- The base screens ----

    @Test fun dailyAloneLeavesBackToTheSystem() {
        shows("DAILY")
        assertFalse(app.back())
    }

    @Test fun backOnAMealGoesToDaily() {
        app.openMeal(MealSlot.LUNCH)
        shows("MEAL")
        assertTrue(app.back())
        shows("DAILY")
    }

    @Test fun backOnTheLibraryGoesWhereItCameFrom() {
        app.openLibrary()
        shows("LIBRARY")
        app.back()
        shows("DAILY")
        app.openArchivedLibrary()
        app.back()
        assertFalse("Archived foods go back to the Library", app.library.archivedOnly)
        shows("LIBRARY")
        app.openMeal(MealSlot.DINNER)
        app.openFocusedLibrary(MealSlot.DINNER)
        shows("LIBRARY")
        app.back()
        shows("MEAL")
        assertEquals(MealSlot.DINNER, app.meal)
    }

    // ---- 1, 2. The scanner ----

    @Test fun theScannerOverAFoodFormGoesBackToTheFormAsItWas() {
        app.openLibrary()
        app.foodForm.open()
        app.foodForm.draft.name = "Kefir"
        app.foodForm.beginBarcodeRescan()
        shows("SCANNER")
        app.back()
        shows("LIBRARY", "FOOD_FORM")
        assertEquals("Kefir", app.foodForm.draft.name)
        assertEquals("", app.foodForm.draft.barcode)
        // A barcode read there comes back into the form, with no lookup.
        app.foodForm.beginBarcodeRescan()
        app.scan.onBarcode(barcode)
        shows("LIBRARY", "FOOD_FORM")
        assertEquals(barcode, app.foodForm.draft.barcode)
        assertEquals("Kefir", app.foodForm.draft.name)
    }

    @Test fun theScannerIsDrawnAloneAndBackKeepsTheScreenBeneath() {
        app.openMeal(MealSlot.LUNCH)
        app.scan.begin()
        app.notices.show("Saved.")
        shows("SCANNER")
        app.back()
        shows("MEAL", "NOTICE")
    }

    // ---- 3. Scan review over the scanner's backdrop ----

    @Test fun aScannedProductIsReviewedOverTheBackdropAndRescanReturnsToTheScanner() {
        app.barcodes[barcode] = food("kefir", "Kefir")
        app.openMeal(MealSlot.LUNCH)
        app.scan.begin()
        app.scan.onBarcode(barcode)
        shows("SCAN_REVIEW_BACKDROP", "PRODUCT_REVIEW")
        app.review.rescan()
        shows("SCANNER")
        app.back()
        shows("MEAL")
        app.scan.begin()
        app.scan.onBarcode(barcode)
        app.review.close()
        shows("MEAL")
    }

    // ---- 4. The resolver after a background save ----

    private fun addLeavesParts(vararg parts: String) {
        app.unmatched = parts.toList()
        app.log.open(MealSlot.LUNCH)
        app.log.onDraft("2 eggs, ${parts.joinToString(", ")}")
        app.log.submit()
    }

    @Test fun theLogsAddOpensTheResolverOverDaily() {
        addLeavesParts("ayran")
        shows("DAILY", "RESOLVER")
        assertFalse(app.reminderShown())
        app.back()
        shows("DAILY")
        assertEquals("what is left stays in the draft", "ayran", app.log.draft)
    }

    @Test fun theLibrarysMixedBatchOpensTheResolverOverTheLibrary() {
        app.libraryFoods = listOf(food("egg"))
        app.unmatched = listOf("ayran", "kefir")
        app.openFocusedLibrary(MealSlot.LUNCH)
        app.library.onInput("2 egg, ayran, kefir")
        app.library.submitInput()
        shows("LIBRARY", "RESOLVER")
        app.back()
        shows("LIBRARY")
        assertEquals("ayran, kefir", app.library.input)
    }

    // ---- 5. The resolver's children, and the resolver coming back ----

    @Test fun theSearchFromTheResolverGoesBackToTheResolver() {
        addLeavesParts("ayran")
        app.search.open(app.resolve.currentName(), fromResolver = true)
        shows("DAILY", "OFF_SEARCH")
        app.back()
        shows("DAILY", "RESOLVER")
    }

    @Test fun aFoodFormFromTheResolverGoesBackToTheResolverAndAChangedOneAsksFirst() {
        addLeavesParts("ayran")
        app.foodForm.open(app.resolve.currentName(), fromResolver = true)
        shows("DAILY", "FOOD_FORM")
        app.back()
        shows("DAILY", "RESOLVER")
        app.foodForm.open(app.resolve.currentName(), fromResolver = true)
        app.foodForm.draft.calories = "40"
        app.back()
        shows("DAILY", "FOOD_FORM", "DISCARD_FOOD")
        app.foodForm.discard()
        shows("DAILY", "RESOLVER")
    }

    @Test fun aReviewPickedFromTheResolversSearchGoesBackToTheResolver() {
        addLeavesParts("ayran")
        app.search.open(app.resolve.currentName(), fromResolver = true)
        app.search.choose(food("off-ayran", "Ayran"))
        shows("DAILY", "PRODUCT_REVIEW")
        app.review.close()
        shows("DAILY", "RESOLVER")
    }

    @Test fun theResolverWaitsUnderTheScanBackdropOfARescannedQueuedPart() {
        app.barcodes[barcode] = food("kefir", "Kefir")
        addLeavesParts("ayran")
        app.search.open(app.resolve.currentName(), fromResolver = true)
        app.search.choose(food("off-ayran", "Ayran"))
        app.review.rescan()
        shows("SCANNER")
        app.scan.onBarcode(barcode)
        assertTrue(app.resolve.resolverOpen)
        shows("SCAN_REVIEW_BACKDROP", "PRODUCT_REVIEW")
        app.review.close()
        shows("DAILY", "RESOLVER")
    }

    @Test fun aSavedNewFoodMovesTheQueueOnAndTheLastOneEndsIt() {
        addLeavesParts("ayran", "kefir")
        app.foodForm.open(app.resolve.currentName(), fromResolver = true)
        app.foodForm.draft.calories = "40"
        app.foodForm.save()
        shows("DAILY", "RESOLVER", "NOTICE")
        app.notices.dismiss()
        app.foodForm.open(app.resolve.currentName(), fromResolver = true)
        app.foodForm.draft.calories = "60"
        app.foodForm.save()
        app.notices.dismiss()
        shows("DAILY")
        assertEquals("", app.log.draft)
    }

    // ---- 6. The Log ↔ Library part-resolve frame ----

    private fun sendPartFromAMeal() {
        app.openMeal(MealSlot.DINNER)
        app.log.open(MealSlot.DINNER)
        app.log.onDraft("2 eggs, ayran 200ml")
        shows("MEAL", "LOG")
        app.log.resolveNotInFoods(DraftRow.Unresolved("ayran 200ml", 8, 19))
    }

    private fun backAtTheLogAsItWas() {
        shows("MEAL", "LOG")
        assertEquals(MealSlot.DINNER, app.meal)
        assertEquals("2 eggs, ayran 200ml", app.log.draft)
        assertFalse(app.resolve.resolvingPart)
    }

    @Test fun aPartSentToTheLibraryComesBackToTheLogUnchangedOnBack() {
        app.libraryFoods = listOf(food("egg"))
        sendPartFromAMeal()
        shows("LIBRARY")
        assertTrue(app.back())
        backAtTheLogAsItWas()
    }

    @Test fun theNoMatchScreenOfAPartGoesBackToTheLog() {
        sendPartFromAMeal()
        shows("LIBRARY", "LIBRARY_NO_MATCH")
        app.back()
        backAtTheLogAsItWas()
    }

    @Test fun theSearchOverAPartsLibraryGoesBackToTheLibraryThenTheLog() {
        sendPartFromAMeal()
        app.search.open("ayran")
        shows("LIBRARY", "OFF_SEARCH")
        app.back()
        shows("LIBRARY", "LIBRARY_NO_MATCH")
        app.back()
        backAtTheLogAsItWas()
    }

    @Test fun aNewFoodOverAPartsLibraryGoesBackToTheLibraryAndItsSaveIsThePick() {
        app.libraryFoods = listOf(food("egg"))
        sendPartFromAMeal()
        app.foodForm.open("ayran")
        shows("LIBRARY", "FOOD_FORM")
        app.back()
        shows("LIBRARY")
        app.foodForm.open("ayran")
        app.foodForm.draft.name = "Ayran"
        app.foodForm.draft.calories = "40"
        app.foodForm.save()
        shows("MEAL", "LOG", "NOTICE")
        assertEquals("2 eggs, Ayran 200ml", app.log.draft)
    }

    @Test fun aScannedProductForAPartIsThePickAndTheLogComesBack() {
        app.libraryFoods = listOf(food("egg"))
        app.barcodes[barcode] = food("ayran", "Ayran")
        sendPartFromAMeal()
        app.scan.begin()
        shows("SCANNER")
        app.scan.onBarcode(barcode)
        shows("SCAN_REVIEW_BACKDROP", "PRODUCT_REVIEW")
        app.review.add(MealSlot.LUNCH)
        shows("MEAL", "LOG")
        assertEquals(listOf("review store ayran"), app.calls)
        assertTrue(app.log.draft.contains("Ayran"))
    }

    @Test fun settingsOverAPartsLibraryGoesBackPageByPageThenToTheLog() {
        app.libraryFoods = listOf(food("egg"))
        sendPartFromAMeal()
        app.openSettings()
        app.settings.openPage(SettingsPage.ABOUT)
        app.settings.openPage(SettingsPage.LICENCES)
        shows("LIBRARY", "SETTINGS")
        app.back()
        assertEquals(SettingsPage.ABOUT, app.settings.page)
        app.back()
        assertNull(app.settings.page)
        shows("LIBRARY", "SETTINGS")
        app.back()
        shows("LIBRARY")
        app.back()
        backAtTheLogAsItWas()
    }

    // ---- 7. Entry detail and edits ----

    @Test fun theEntryDetailIsDrawnAloneAndBackShowsWhatWaited() {
        app.diaryHas(egg)
        app.openMeal(MealSlot.LUNCH)
        app.entryEdit.openDetail(egg)
        app.notices.show("Saved.")
        shows("ENTRY_DETAIL")
        app.back()
        shows("MEAL", "NOTICE")
    }

    @Test fun anEditFromDailyShowsItsMealBeneathAndBackReturnsToDaily() {
        app.diaryHas(egg)
        app.entryEdit.beginQuick(egg)
        shows("MEAL", "ENTRY_QUICK_EDIT")
        assertEquals(MealSlot.LUNCH, app.meal)
        app.back()
        shows("DAILY")
        app.entryEdit.beginFull(egg)
        app.entryEdit.openFoodPicker()
        shows("MEAL", "ENTRY_FULL_EDIT", "ENTRY_FOOD_PICKER")
        app.entryEdit.closeFoodPicker()
        app.back()
        shows("DAILY")
    }

    @Test fun anEditFromAMealGoesBackToTheMeal() {
        app.diaryHas(egg)
        app.openMeal(MealSlot.LUNCH)
        app.entryEdit.openDetail(egg)
        app.entryEdit.closeDetail()
        app.entryEdit.beginFull(egg)
        shows("MEAL", "ENTRY_FULL_EDIT")
        app.back()
        shows("MEAL")
    }

    @Test fun openInLibraryShowsTheFoodPageOverTheLibraryTab() {
        app.diary.foods = listOf(food("f-e1", "Egg"))
        app.libraryFoods = listOf(food("f-e1", "Egg"))
        app.diaryHas(egg)
        app.entryEdit.openDetail(egg)
        app.openLibraryFoodDetail(egg)
        shows("LIBRARY", "LIBRARY_FOOD")
        app.back()
        shows("LIBRARY")
        app.back()
        shows("DAILY")
    }

    // ---- 8, 9. Dialogs and pages over the Library and Settings ----

    @Test fun aFoodTappedInAMealsLibraryOpensTheAmountSheet() {
        val egg = food("egg")
        app.libraryFoods = listOf(egg)
        app.openMeal(MealSlot.LUNCH)
        app.openFocusedLibrary(MealSlot.LUNCH)
        app.library.openFood(egg)
        shows("LIBRARY", "LIBRARY_FOOD_ADD")
        app.library.closeFood()
        app.openLibrary()
        app.library.openFood(egg)
        shows("LIBRARY", "LIBRARY_FOOD")
    }

    @Test fun editAndContributeReplaceTheFoodPage() {
        val kefir = food("kefir", "Kefir").copy(barcode = barcode)
        app.libraryFoods = listOf(kefir)
        app.openLibrary()
        app.library.openFood(kefir)
        app.library.closeFood(); app.foodForm.openEdit(kefir)
        shows("LIBRARY", "FOOD_FORM")
        app.back()
        shows("LIBRARY")
        app.library.openFood(kefir)
        app.library.closeFood(); app.contribution.openForFood(kefir)
        app.offIo.runAll()
        shows("LIBRARY", "CONTRIBUTION_REVIEW")
        app.back()
        shows("LIBRARY")
    }

    @Test fun theContributionScreensFollowEachOtherAndBackLeavesThem() {
        val kefir = food("kefir", "Kefir").copy(barcode = barcode)
        app.openLibrary()
        app.contribution.openForFood(kefir)
        app.offIo.runAll()
        app.contribution.confirm()
        shows("LIBRARY", "CONTRIBUTION_SIGN_IN")
        app.back()
        shows("LIBRARY")
        app.contribution.review()
        app.contribution.confirm()
        app.contribution.submit()
        shows("LIBRARY", "CONTRIBUTION_FAILURE")
        app.back()
        shows("LIBRARY")
    }

    /** As drawn: the sign-in ignores Back while it submits, and Back reaches the screen beneath it. */
    @Test fun backWhileTheSignInSubmitsReachesTheScreenBeneath() {
        val kefir = food("kefir", "Kefir").copy(barcode = barcode)
        app.openLibrary()
        app.contribution.openForFood(kefir)
        app.offIo.runAll()
        app.contribution.confirm()
        app.contribution.onUsername("ana")
        app.contribution.onPassword("secret")
        app.contribution.submit()
        assertTrue(app.contribution.busy)
        app.back()
        shows("DAILY", "CONTRIBUTION_SIGN_IN")
    }

    @Test fun theMixedBatchsMealPickerOpensOverTheLibrary() {
        app.libraryFoods = listOf(food("egg"))
        app.openLibrary()
        app.library.onInput("2 egg, ayran")
        app.library.submitInput()
        shows("LIBRARY", "MIXED_MEAL_PICKER", "NOTICE")
    }

    @Test fun settingsDialogsOpenOverSettings() {
        app.openLibrary()
        app.openSettings()
        app.settings.openPage(SettingsPage.DATA)
        app.transfer.openDeleteAll()
        shows("LIBRARY", "SETTINGS", "DELETE_ALL")
        app.transfer.cancelDeleteAll()
        app.documents.documents["diary.json"] = validDiaryText()
        app.transfer.import("diary.json")
        shows("LIBRARY", "SETTINGS", "REPLACE_DIARY")
    }

    // ---- 11. Notices ----

    @Test fun aNoticeIsDrawnOverEveryScreen() {
        app.openLibrary()
        app.openSettings()
        app.notices.show("Diary exported.")
        shows("LIBRARY", "SETTINGS", "NOTICE")
    }

    // ---- The top check ----

    @Test fun noLocalMatchShowsOnlyWithNothingOverTheLibrary() {
        app.openLibrary()
        app.library.onQuery("zzkefir")
        shows("LIBRARY", "LIBRARY_NO_MATCH")
        app.foodForm.open("zzkefir")
        shows("LIBRARY", "FOOD_FORM")
        app.back()
        shows("LIBRARY", "LIBRARY_NO_MATCH")
        app.back()
        shows("LIBRARY")
        assertEquals("", app.library.query)
    }

    @Test fun theExportReminderShowsOnlyOnAnUncoveredDaily() {
        assertTrue(app.reminderShown())
        app.log.open(null)
        assertFalse(app.reminderShown())
        app.log.close()
        app.scan.begin()
        assertFalse(app.reminderShown())
        app.scan.end()
        assertTrue(app.reminderShown())
    }

    // ---- Combinations that must not occur ----

    @Test fun settingsNeverOutlivesTheLibrary() {
        app.openLibrary()
        app.openSettings()
        app.closeLibrary()
        assertFalse(app.settingsOpen)
        app.openLibrary()
        app.openSettings()
        app.exitLibrary()
        assertFalse(app.settingsOpen)
        shows("DAILY")
    }

    @Test fun theSearchTheFoodFormAndTheResolverReplaceEachOther() {
        addLeavesParts("ayran")
        app.search.open(app.resolve.currentName(), fromResolver = true)
        app.foodForm.open(app.resolve.currentName(), fromResolver = true)
        shows("DAILY", "FOOD_FORM")
        app.search.open(app.resolve.currentName(), fromResolver = true)
        shows("DAILY", "OFF_SEARCH")
        assertFalse(app.foodForm.open)
        assertFalse(app.resolve.resolverOpen)
    }

    @Test fun theLibraryAndAMealNeverShowTogether() {
        app.openMeal(MealSlot.LUNCH)
        app.openLibrary()
        assertNull(app.meal)
        shows("LIBRARY")
    }
}
