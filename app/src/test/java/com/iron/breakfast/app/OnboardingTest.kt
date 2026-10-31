package com.iron.breakfast.app

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.iron.breakfast.recipes.chili
import com.iron.breakfast.settings.AppSettings
import com.iron.breakfast.settings.FakeSettingsStore
import com.iron.breakfast.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tutorial through [Surfaces] as the app wires it: when it shows, what ends it and where that lands. */
class OnboardingTest {
    private fun firstLaunch(saved: SavedStateHandle = SavedStateHandle(), store: FakeSettingsStore = FakeSettingsStore()) = AppKit(saved, settingsStore = store)
    private val AppKit.onboarding get() = owners.onboarding
    private fun AppKit.done() = AppSettings(settingsStore).tutorialDone

    @Test fun aFirstLaunchShowsItAlone() {
        val app = firstLaunch()
        assertEquals(listOf("ONBOARDING"), app.shown())
        assertTrue(app.surfaces.baseCovered())
        assertFalse(app.done())
    }

    @Test fun onceDoneItDoesNotShow() {
        assertEquals(listOf("DAILY"), AppKit().shown())
    }

    @Test fun nextAndBackMoveThroughThePages() {
        val app = firstLaunch()
        app.onboarding.next()
        app.onboarding.next()
        assertEquals(2, app.onboarding.position.page)
        assertTrue(app.back())
        assertEquals(1, app.onboarding.position.page)
        app.onboarding.settled(4, byUser = true)
        assertTrue(app.onboarding.position.isLast)
    }

    /** Back on the first page of a first launch leaves the app as Daily does; nothing is marked done. */
    @Test fun backOnTheFirstPageOfAFirstLaunchIsTheSystems() {
        val app = firstLaunch()
        assertFalse(app.back())
        assertEquals(listOf("ONBOARDING"), app.shown())
        assertFalse(app.done())
    }

    @Test fun skipEndsItForGood() {
        val store = FakeSettingsStore()
        val app = firstLaunch(store = store)
        app.onboarding.finish()
        assertEquals(listOf("DAILY"), app.shown())
        assertTrue(app.done())
        assertEquals(listOf("DAILY"), firstLaunch(store = store).shown())
    }

    /** A double tap on the last Next: the second lands on Next still, and nothing ends. */
    @Test fun aDoubleTapOnTheLastNextEndsNothing() {
        val app = firstLaunch()
        app.onboarding.settled(3, byUser = true)
        app.onboarding.next()
        app.onboarding.next()
        assertEquals(listOf("ONBOARDING"), app.shown())
        assertFalse(app.done())
        assertFalse("Next stays until the pager is there", app.onboarding.position.arrivedAtLast)
        app.onboarding.settled(4, byUser = false)
        assertTrue(app.onboarding.position.arrivedAtLast)
    }

    @Test fun theDoneFlagReachesTheDiskBeforeItReturns() {
        val store = FakeSettingsStore()
        firstLaunch(store = store).onboarding.finish()
        assertEquals(1, store.commits)
    }

    @Test fun startLoggingLandsOnMealsFromAFirstLaunch() {
        val app = firstLaunch()
        app.onboarding.settled(4, byUser = true)
        app.surfaces.onboardingToMeals()
        assertEquals(listOf("DAILY"), app.shown())
        assertTrue(app.done())
    }

    @Test fun openProfileOpensItAndEndsTheTutorial() {
        val app = firstLaunch()
        app.surfaces.onboardingToProfile()
        assertEquals(listOf("DAILY", "PROFILE"), app.shown())
        assertTrue(app.done())
    }

    /** Replayed from About: Skip and the first page's Back return there. */
    @Test fun aReplayReturnsToTheSettingsPageItCameFrom() {
        val app = AppKit()
        app.openSettings()
        app.settings.openPage(SettingsPage.ABOUT)
        app.surfaces.openOnboarding()
        assertEquals(listOf("ONBOARDING"), app.shown())
        app.onboarding.finish()
        assertEquals(listOf("DAILY", "SETTINGS"), app.shown())
        assertEquals(SettingsPage.ABOUT, app.settings.page)
        app.surfaces.openOnboarding()
        assertTrue(app.back())
        assertEquals(listOf("DAILY", "SETTINGS"), app.shown())
    }

    @Test fun aReplayStartsOnTheFirstPage() {
        val app = AppKit()
        app.surfaces.openOnboarding()
        app.onboarding.next()
        app.onboarding.finish()
        app.surfaces.openOnboarding()
        assertEquals(0, app.onboarding.position.page)
    }

    /** Start logging lands on Meals whatever a replay was opened over. */
    @Test fun startLoggingFromAReplayLeavesEverythingForMeals() {
        val app = AppKit()
        app.recipesAre(chili)
        app.surfaces.openRecipes()
        app.openSettings()
        app.surfaces.openOnboarding()
        app.surfaces.onboardingToMeals()
        assertEquals(listOf("DAILY"), app.shown())
        val library = AppKit()
        library.openLibrary()
        library.openSettings()
        library.surfaces.openOnboarding()
        library.surfaces.onboardingToMeals()
        assertEquals(listOf("DAILY"), library.shown())
    }

    @Test fun aKilledProcessComesBackOnTheSamePage() {
        val saved = SavedStateHandle()
        val store = FakeSettingsStore()
        val app = firstLaunch(saved, store)
        app.onboarding.settled(3, byUser = true)
        Snapshot.sendApplyNotifications()
        val again = firstLaunch(saved, store)
        assertEquals(listOf("ONBOARDING"), again.shown())
        assertEquals(3, again.onboarding.position.page)
    }

    @Test fun aKilledReplayComesBackAndAClosedOneDoesNot() {
        val saved = SavedStateHandle()
        val app = AppKit(saved)
        app.surfaces.openOnboarding()
        app.onboarding.next()
        Snapshot.sendApplyNotifications()
        val again = AppKit(saved)
        assertEquals(listOf("ONBOARDING"), again.shown())
        assertEquals(1, again.onboarding.position.page)
        again.onboarding.finish()
        Snapshot.sendApplyNotifications()
        assertEquals(listOf("DAILY"), AppKit(saved).shown())
    }

    /** What was saved waits under it, whole: a Log draft, a restored form. */
    @Test fun aSavedDraftAndFormWaitUnderIt() {
        val saved = SavedStateHandle()
        val before = AppKit(saved)
        before.log.onDraft("2 eggs")
        before.recipeForm.openNew()
        before.recipeForm.onName("Soup")
        Snapshot.sendApplyNotifications()
        val app = firstLaunch(saved)
        assertEquals(listOf("ONBOARDING"), app.shown())
        assertEquals("2 eggs", app.log.draft)
        app.onboarding.finish()
        assertEquals(listOf("DAILY", "RECIPE_FORM"), app.shown())
        assertEquals("Soup", app.recipeForm.fields.name)
        assertEquals("2 eggs", app.log.draft)
    }

}
