package mk.bosa.breakfast.contribution

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mk.bosa.breakfast.app.Notices
import mk.bosa.breakfast.data.OffContributionDraft
import mk.bosa.breakfast.data.OffContributionResult
import mk.bosa.breakfast.data.OffPreflightResult
import mk.bosa.breakfast.domain.FoodRecord
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.parseDecimal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * What a contribution needs from Open Food Facts; `OpenFoodFactsClient` implements it. Both calls block, so they run on
 * the IO dispatcher; a call once started runs to its end even when the flow no longer waits for its answer.
 */
interface OffContributions {
    /** Reads the product the draft would change: does it exist, may it be contributed. */
    fun preflight(draft: OffContributionDraft): OffPreflightResult
    fun contribute(draft: OffContributionDraft, username: String, password: String): OffContributionResult
}

/**
 * The Open Food Facts contribution flow: a candidate ([pending]), its preflight, the review, the sign-in and the
 * failure screens, and the submit.
 *
 * - A candidate comes from Contribute on a Library food ([openForFood]) or from a local save of a scanned or Open
 *   Food Facts product ([offerAfterLocalSave]); Daily and Meal then show Contribute ([review]).
 * - A preflight runs before the review; only the latest one counts (a token), so a result for an older candidate is
 *   dropped. After an answer was lost ([uncertain]), Retry reads the product again before anything is sent.
 * - The typed password lives only here, in memory, and is cleared when the submit starts and when the sign-in is
 *   dismissed. Never in saved state.
 * - "Remember password" ([rememberLogin], off by default) keeps the login in [rememberedLogin]: written only after a
 *   contribution succeeds with it, read each time the sign-in opens. Unticking forgets it at once, and so does Delete
 *   all ([forgetLogin]). Open Food Facts rejecting the saved password forgets the password and keeps the username.
 *   Data that cannot be read counts as nothing saved, with an empty form and no message.
 * - Leaving the sign-in ([dismissAuth], Back included) while it submits cancels the submit: its answer, whenever it
 *   comes, is not applied and opens nothing. Like a lost answer, the product is read again before the next review,
 *   and no new submit starts while the cancelled call is still out.
 *
 * Calls to [off] run on [io]; their results are applied in [scope] (the view model's, on Main). The blocking call
 * itself is not cut off (a request already sent may still reach Open Food Facts), but a cancelled submit's result is
 * dropped as it returns; a stale preflight is dropped by its token.
 */
internal class ContributionFlow(
    private val off: OffContributions,
    private val rememberedLogin: RememberedLogin,
    private val notices: Notices,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** "Contribute" (Settings, Scanner) is Show. */
    private val showContribute: () -> Boolean,
) {
    var pending by mutableStateOf<OffContributionDraft?>(null); private set
    var reviewOpen by mutableStateOf(false); private set
    var authOpen by mutableStateOf(false); private set
    var failure by mutableStateOf<String?>(null); private set
    /** The failure shown is "may have been sent": the answer to a sent contribution was lost. */
    var uncertain by mutableStateOf(false); private set
    var username by mutableStateOf(""); private set
    var password by mutableStateOf(""); private set
    var rememberLogin by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var preflightBusy by mutableStateOf(false); private set
    private var preflightComplete by mutableStateOf(false)
    private var preflightToken = 0L
    private var submitting: Job? = null
    /** The login that filled the form, while the form holds it. */
    private var prefilled: SavedLogin? = null
    /** Only the latest read of the remembered login counts, and only while the form is untouched. */
    private var recallToken = 0L
    /** Contribute calls still running on [io], a cancelled one included. */
    private val callsOut = AtomicInteger(0)
    var languageCode by mutableStateOf("en"); private set
    var packageQuantityMl by mutableStateOf(""); private set

    /** Contribute on Daily and Meal: a candidate waits ([pending]) and "Contribute" is Show ([contributionShown]). */
    val actionShown: Boolean get() = contributionShown(showContribute(), available = pending != null)

    /** Contribute on a Library food: a food without a barcode cannot be contributed, which is said. */
    fun openForFood(food: FoodRecord?) {
        preflightToken++
        val draft = food?.let { OffContributionDraft.fromFood(it) }
        pending = draft
        if (draft == null) {
            preflightBusy = false
            preflightComplete = false
            notices.show("Open Food Facts submission is available only for foods with a barcode.")
            return
        }
        preflightComplete = false
        preflight(draft)
    }

    /**
     * A local save produced [draft] as the candidate (null: this save offers none, and any earlier one goes). It
     * replaces any earlier candidate, including a preflight still running for it, and gets its own preflight.
     */
    fun offerAfterLocalSave(draft: OffContributionDraft?) {
        preflightToken++
        preflightBusy = false
        preflightComplete = false
        pending = draft
    }

    /** The diary was replaced: a candidate made from the old diary goes, with its preflight and review. */
    fun reset() {
        offerAfterLocalSave(null)
        reviewOpen = false
    }

    /** Contribute on Daily or Meal: the review, after a preflight if none has completed for this candidate. */
    fun review() {
        val draft = pending ?: return
        if (preflightBusy) return
        if (preflightComplete) reviewOpen = true
        else preflight(draft)
    }

    fun dismissReview() { reviewOpen = false }

    /** Continue to sign in: a new product needs its language, and a liquid one its package quantity. */
    fun confirm() {
        val draft = pending
        if (draft != null && draft.createNewProduct && draft.languageCode.trim().lowercase() !in setOf("en", "mk")) {
            notices.show("Choose en (English) or mk (Macedonian) for the new product.")
            return
        }
        if (draft != null && draft.createNewProduct && draft.basisUnit == MeasureUnit.MILLILITER &&
            (draft.packageQuantityMl == null || !draft.packageQuantityMl.isFinite() || draft.packageQuantityMl <= 0.0)
        ) {
            notices.show("Enter a positive package quantity in ml before continuing.")
            return
        }
        if (draft != null) {
            reviewOpen = false
            openSignIn()
        }
    }

    private fun openSignIn() {
        authOpen = true
        val token = ++recallToken
        scope.launch {
            val recalled = withContext(io) { rememberedLogin.recall() }
            if (token != recallToken) return@launch
            when (recalled) {
                RecalledLogin.Nothing -> Unit
                RecalledLogin.Unreadable -> clearForm()
                is RecalledLogin.Found -> {
                    val login = recalled.login
                    if (login.password != null) { username = login.username; password = login.password }
                    else if (username.isBlank()) username = login.username
                    prefilled = login
                    rememberLogin = true
                }
            }
        }
    }

    private fun clearForm() {
        username = ""
        password = ""
        rememberLogin = false
        prefilled = null
    }

    /**
     * Leaves the sign-in: its arrow, Cancel and system Back alike, also while it submits. The password is cleared.
     * While it submits, the submit is cancelled: its answer is ignored and nothing beneath the sign-in changes.
     */
    fun dismissAuth() {
        // The request may still reach Open Food Facts: what it holds is read again before the next review.
        if (busy) preflightComplete = false
        submitting?.cancel()
        submitting = null
        busy = false
        authOpen = false
        password = ""
        prefilled = null
        recallToken++
    }

    fun dismissFailure() { failure = null; uncertain = false; pending = null }

    /** Retry on the failure screen. After a lost answer the product is read again first, so a retry starts from what Open Food Facts now holds. */
    fun retry() {
        val wasUncertain = uncertain
        failure = null
        uncertain = false
        val draft = pending
        if (wasUncertain && draft != null) { preflightComplete = false; preflight(draft) }
        else openSignIn()
    }

    fun onUsername(value: String) { recallToken++; username = value }
    fun onPassword(value: String) { recallToken++; password = value }

    /** Unticking forgets the saved login, and takes it out of the form; what the user typed instead stays. */
    fun toggleRememberLogin() {
        recallToken++
        rememberLogin = !rememberLogin
        if (rememberLogin) return
        rememberedLogin.forget()
        prefilled?.let { login ->
            if (username.trim() == login.username) username = ""
            if (password == login.password) password = ""
        }
        prefilled = null
    }

    /** Delete all data: the saved login and the one in memory go, the box with them. */
    fun forgetLogin() {
        recallToken++
        rememberedLogin.forget()
        clearForm()
    }

    fun onLanguage(value: String) {
        languageCode = value
        pending = pending?.copy(languageCode = value.trim().lowercase())
    }
    fun onPackageQuantityMl(value: String) {
        packageQuantityMl = value
        pending = pending?.copy(packageQuantityMl = parseDecimal(value))
    }

    /**
     * Sends the candidate with the typed account. One submit at a time, and none while a cancelled one's call is still
     * out; the password is cleared as it starts.
     */
    fun submit() {
        val draft = pending ?: return
        if (busy || callsOut.get() > 0) return
        if (username.isBlank() || password.isBlank()) {
            failure = "Enter your Open Food Facts username and password to submit."
            uncertain = false
            authOpen = false
            return
        }
        val user = username.trim()
        val secret = password
        val sentSavedLogin = prefilled?.let { it.username == user && it.password == secret } == true
        recallToken++
        prefilled = null
        password = ""
        busy = true
        submitting = scope.launch {
            // A cancelled submit ends here as the call returns ([dismissAuth]): withContext does not resume it.
            val result = withContext(io) {
                callsOut.incrementAndGet()
                try { off.contribute(draft, user, secret) } finally { callsOut.decrementAndGet() }
            }
            when (result) {
                OffContributionResult.Success -> {
                    keepLogin(user, secret)
                    pending = null
                    authOpen = false
                    failure = null
                    notices.show("Contribution sent to Open Food Facts.")
                }
                OffContributionResult.AuthFailed -> { if (sentSavedLogin) rememberedLogin.forgetPassword(); authOpen = false; failure = "Open Food Facts could not authenticate that account. Check the username and password, then retry." }
                is OffContributionResult.Ineligible -> { authOpen = false; failure = result.message }
                is OffContributionResult.Rejected -> { authOpen = false; failure = result.message }
                is OffContributionResult.PreflightChanged -> { authOpen = false; failure = result.message }
                OffContributionResult.Unavailable -> { authOpen = false; failure = "Open Food Facts was unavailable. Retry when you have a connection." }
                OffContributionResult.Uncertain -> { authOpen = false; failure = CONTRIBUTION_UNCERTAIN }
                is OffContributionResult.Error -> { authOpen = false; failure = result.message }
            }
            uncertain = result == OffContributionResult.Uncertain
            busy = false
            submitting = null
        }
    }

    /** After a success with the box ticked; the box is read again as sealing ends, as it can be unticked meanwhile. */
    private suspend fun keepLogin(user: String, secret: String) {
        if (!rememberLogin) return
        val sealed = withContext(io) { rememberedLogin.seal(user, secret) }
        if (!rememberLogin) return
        if (sealed != null) rememberedLogin.store(sealed)
        else { rememberedLogin.forget(); rememberLogin = false }
    }

    private fun preflight(draft: OffContributionDraft) {
        val token = ++preflightToken
        preflightBusy = true
        scope.launch {
            val result = withContext(io) { off.preflight(draft) }
            if (token != preflightToken) return@launch
            preflightBusy = false
            preflightComplete = true
            when (result) {
                OffPreflightResult.Existing -> pending = draft.copy(createNewProduct = false)
                OffPreflightResult.NewProduct -> {
                    val language = contributionLanguageFor(draft.name)
                    languageCode = language
                    packageQuantityMl = ""
                    pending = draft.copy(createNewProduct = true, languageCode = language)
                }
                is OffPreflightResult.Ineligible -> {
                    pending = null
                    notices.show(result.message)
                }
                OffPreflightResult.Unavailable -> {
                    pending = null
                    notices.show("Open Food Facts was unavailable. Retry when you have a connection.")
                }
            }
            if (pending != null) reviewOpen = true
        }
    }
}
