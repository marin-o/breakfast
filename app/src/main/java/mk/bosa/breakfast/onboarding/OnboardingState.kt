package mk.bosa.breakfast.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mk.bosa.breakfast.settings.SettingsState
import mk.bosa.breakfast.ui.components.StepPosition

/**
 * The tutorial's owner: whether it is [open] and on which page. It opens by itself until it is done ([finish], for good,
 * in `AppSettings`), and from Settings as a replay ([show]). [savedPage] is mirrored for process death: a page saved
 * means it was open, a first launch's or a replay's.
 */
internal class OnboardingState(private val settings: SettingsState, savedPage: Int?) {
    private val first = StepPosition(0, ONBOARDING_PAGES.size)

    var open by mutableStateOf(savedPage != null || !settings.tutorialDone); private set
    var position by mutableStateOf(first.at(savedPage ?: 0)); private set

    /** Back has somewhere to go: an earlier page, or, in a replay, what it was opened over. A first launch's first page has neither. */
    val takesBack: Boolean get() = position.canGoBack || settings.tutorialDone

    fun show() {
        position = first
        open = true
    }

    fun next() { position = position.next() }

    fun back() {
        if (position.canGoBack) position = position.back() else if (settings.tutorialDone) close()
    }

    fun settled(page: Int, byUser: Boolean) { position = position.settledAt(page, byUser) }

    /** Skip, or a way on from the last page: done for good. */
    fun finish() {
        settings.tutorialDone = true
        close()
    }

    private fun close() {
        open = false
        position = first
    }

    fun savedPage(): Int? = position.page.takeIf { open }
}
