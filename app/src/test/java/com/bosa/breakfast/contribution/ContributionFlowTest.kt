package com.bosa.breakfast.contribution

import com.bosa.breakfast.testFood
import com.bosa.breakfast.QueueDispatcher
import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.data.OffContributionDraft
import com.bosa.breakfast.data.OffContributionResult
import com.bosa.breakfast.data.OffPreflightResult
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionFlowTest {
    private class FakeOff : OffContributions {
        val preflights = mutableListOf<String>()
        val sent = mutableListOf<Triple<OffContributionDraft, String, String>>()
        var preflightResult: OffPreflightResult = OffPreflightResult.NewProduct
        var contributeResult: OffContributionResult = OffContributionResult.Success
        /** Runs while the contribution is being sent, before its answer. */
        var whileSending: () -> Unit = {}
        override fun preflight(draft: OffContributionDraft): OffPreflightResult { preflights += draft.barcode; return preflightResult }
        override fun contribute(draft: OffContributionDraft, username: String, password: String): OffContributionResult {
            sent += Triple(draft, username, password)
            whileSending()
            return contributeResult
        }
    }

    private val off = FakeOff()
    private val notices = Notices()
    private val io = QueueDispatcher()
    /** "Contribute" in Settings. */
    private var showContribute = true
    private val flow = ContributionFlow(off, notices, CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), io, showContribute = { showContribute })

    private fun food(barcode: String?, name: String = "Kefir", unit: MeasureUnit = MeasureUnit.GRAM) =
        testFood("f-$barcode", name, category = "dairy", unit = unit, values = NutritionValues(60.0, 3.0, 4.0, 3.0), barcode = barcode)

    private fun draft(barcode: String, unit: MeasureUnit = MeasureUnit.GRAM) = OffContributionDraft.fromFood(food(barcode, unit = unit))!!

    /** A candidate past its preflight, on the review. */
    private fun reviewing(result: OffPreflightResult = OffPreflightResult.Existing, food: FoodRecord = food("4006381333931")) {
        off.preflightResult = result
        flow.openForFood(food)
        io.runAll()
        assertTrue(flow.reviewOpen)
    }

    private fun signedIn() {
        reviewing()
        flow.confirm()
        flow.onUsername(" ana ")
        flow.onPassword("secret")
    }

    @Test fun aFoodWithoutABarcodeCannotBeContributed() {
        flow.openForFood(food(barcode = null))
        assertNull(flow.pending)
        assertEquals("Open Food Facts submission is available only for foods with a barcode.", notices.message)
        assertTrue(off.preflights.isEmpty())
    }

    @Test fun aNewProductGetsItsLanguageFromTheNameAndOpensTheReview() {
        off.preflightResult = OffPreflightResult.NewProduct
        flow.openForFood(food("4006381333931", name = "Кефир"))
        assertTrue(flow.preflightBusy)
        io.runAll()
        assertFalse(flow.preflightBusy)
        assertTrue(flow.pending!!.createNewProduct)
        assertEquals("mk", flow.languageCode)
        assertEquals("mk", flow.pending!!.languageCode)
        assertTrue(flow.reviewOpen)
    }

    @Test fun onlyTheLatestPreflightCounts() {
        flow.openForFood(food("4006381333931"))
        flow.openForFood(food("5000112548167"))
        io.runAll()
        assertEquals(listOf("4006381333931", "5000112548167"), off.preflights)
        assertEquals("5000112548167", flow.pending!!.barcode)
        assertFalse(flow.preflightBusy)
    }

    @Test fun anIneligibleOrUnreachableProductDropsTheCandidateAndSaysWhy() {
        off.preflightResult = OffPreflightResult.Ineligible("Already complete.")
        flow.openForFood(food("4006381333931"))
        io.runAll()
        assertNull(flow.pending)
        assertFalse(flow.reviewOpen)
        assertEquals("Already complete.", notices.message)
        off.preflightResult = OffPreflightResult.Unavailable
        flow.openForFood(food("4006381333931"))
        io.runAll()
        assertEquals("Open Food Facts was unavailable. Retry when you have a connection.", notices.message)
    }

    /** Daily and Meal show Contribute for a waiting candidate, unless "Contribute" is Hide. */
    @Test fun theActionShowsForACandidateUnlessContributeIsHidden() {
        assertFalse("no candidate", flow.actionShown)
        flow.offerAfterLocalSave(draft("5000112548167"))
        assertTrue(flow.actionShown)
        showContribute = false
        assertFalse(flow.actionShown)
    }

    @Test fun aLocalSaveReplacesTheCandidateAndItsRunningPreflight() {
        flow.openForFood(food("4006381333931"))
        val saved = draft("5000112548167")
        flow.offerAfterLocalSave(saved)
        io.runAll()
        assertEquals(saved, flow.pending)
        assertFalse(flow.preflightBusy)
        assertFalse(flow.reviewOpen)
        // Its own preflight runs when Contribute is tapped.
        flow.review()
        io.runAll()
        assertEquals("5000112548167", off.preflights.last())
        assertTrue(flow.reviewOpen)
    }

    @Test fun aCandidateWhosePreflightCompletedOpensTheReviewAtOnce() {
        reviewing()
        flow.dismissReview()
        flow.review()
        assertTrue(flow.reviewOpen)
        assertEquals(1, off.preflights.size)
        // A local save that offers none removes it.
        flow.offerAfterLocalSave(null)
        assertNull(flow.pending)
    }

    @Test fun aNewProductNeedsEnOrMkAndALiquidOneItsPackageQuantity() {
        reviewing(OffPreflightResult.NewProduct, food("4006381333931", unit = MeasureUnit.MILLILITER))
        flow.onLanguage("de")
        flow.confirm()
        assertEquals("Choose en (English) or mk (Macedonian) for the new product.", notices.message)
        assertFalse(flow.authOpen)
        flow.onLanguage(" EN ")
        flow.confirm()
        assertEquals("Enter a positive package quantity in ml before continuing.", notices.message)
        flow.onPackageQuantityMl("0")
        flow.confirm()
        assertFalse(flow.authOpen)
        flow.onPackageQuantityMl("500")
        flow.confirm()
        assertTrue(flow.authOpen)
        assertFalse(flow.reviewOpen)
        assertEquals(500.0, flow.pending!!.packageQuantityMl!!, 0.0)
    }

    @Test fun theSubmitClearsThePasswordAsItStartsAndRunsOnce() {
        signedIn()
        flow.submit()
        assertEquals("", flow.password)
        assertTrue(flow.busy)
        // A second tap while it runs does nothing; the sign-in stays.
        flow.submit()
        assertTrue(flow.authOpen)
        io.runAll()
        assertEquals(1, off.sent.size)
        assertEquals("ana", off.sent.single().second)
        assertEquals("secret", off.sent.single().third)
        assertFalse(flow.busy)
        assertNull(flow.pending)
        assertFalse(flow.authOpen)
        assertEquals("Contribution sent to Open Food Facts.", notices.message)
    }

    @Test fun leavingTheSignInWhileItSubmitsCancelsTheSubmitAndIgnoresItsAnswer() {
        reviewing(OffPreflightResult.NewProduct)
        val candidate = flow.pending
        flow.confirm()
        flow.onUsername("ana")
        flow.onPassword("secret")
        off.contributeResult = OffContributionResult.AuthFailed
        // Back while the request is out: the sign-in closes at once, and no other submit starts while it is out.
        off.whileSending = {
            assertTrue(flow.busy)
            flow.dismissAuth()
            assertFalse(flow.authOpen)
            assertFalse(flow.busy)
            assertEquals("", flow.password)
            flow.confirm()
            flow.onPassword("secret")
            flow.submit()
            assertFalse(flow.busy)
            flow.dismissAuth()
        }
        flow.submit()
        // The answer comes after: it opens nothing and changes nothing.
        io.runAll()
        assertEquals(1, off.sent.size)
        assertFalse(flow.authOpen)
        assertFalse(flow.busy)
        assertNull(flow.failure)
        assertFalse(flow.uncertain)
        assertEquals(candidate, flow.pending)
        assertNull(notices.message)
        assertEquals("", flow.password)
        // The request may have created the product: the next Contribute reads it again before the review.
        off.preflightResult = OffPreflightResult.Existing
        flow.review()
        io.runAll()
        assertEquals(2, off.preflights.size)
        assertFalse(flow.pending!!.createNewProduct)
        assertTrue(flow.reviewOpen)
        // A late success is ignored as well.
        off.contributeResult = OffContributionResult.Success
        off.whileSending = { flow.dismissAuth() }
        flow.confirm()
        flow.onPassword("secret")
        flow.submit()
        io.runAll()
        assertEquals(2, off.sent.size)
        assertFalse(off.sent.last().first.createNewProduct)
        assertNotNull(flow.pending)
        assertNull(notices.message)
        // A submit left before it was sent never goes.
        off.whileSending = {}
        flow.review()
        io.runAll()
        flow.confirm()
        flow.onPassword("secret")
        flow.submit()
        flow.dismissAuth()
        io.runAll()
        assertEquals(2, off.sent.size)
        assertNull(notices.message)
        assertFalse(flow.authOpen)
    }

    @Test fun leavingTheSignInClearsThePassword() {
        signedIn()
        flow.dismissAuth()
        assertFalse(flow.authOpen)
        assertEquals("", flow.password)
        assertEquals(" ana ", flow.username)
    }

    @Test fun anEmptyAccountIsRefusedBeforeAnythingIsSent() {
        reviewing()
        flow.confirm()
        flow.submit()
        assertEquals("Enter your Open Food Facts username and password to submit.", flow.failure)
        assertFalse(flow.authOpen)
        assertTrue(off.sent.isEmpty())
    }

    @Test fun aLostAnswerIsReadAgainBeforeARetry() {
        signedIn()
        off.contributeResult = OffContributionResult.Uncertain
        flow.submit()
        io.runAll()
        assertTrue(flow.uncertain)
        assertEquals(CONTRIBUTION_UNCERTAIN, flow.failure)
        flow.retry()
        assertNull(flow.failure)
        assertFalse(flow.authOpen)
        io.runAll()
        assertEquals(2, off.preflights.size)
        assertTrue(flow.reviewOpen)
    }

    @Test fun anyOtherFailureRetriesStraightToTheSignIn() {
        signedIn()
        off.contributeResult = OffContributionResult.AuthFailed
        flow.submit()
        io.runAll()
        assertFalse(flow.uncertain)
        flow.retry()
        assertTrue(flow.authOpen)
        assertEquals("", flow.password)
        assertEquals(1, off.preflights.size)
        flow.dismissAuth()
        flow.dismissFailure()
        assertNull(flow.pending)
    }

    @Test fun aReplacedDiaryDropsTheCandidateItsPreflightAndReview() {
        reviewing()
        flow.reset()
        assertNull(flow.pending)
        assertFalse(flow.reviewOpen)
        flow.openForFood(food("4006381333931"))
        flow.reset()
        io.runAll()
        assertNull(flow.pending)
        assertFalse(flow.preflightBusy)
        assertFalse(flow.reviewOpen)
    }
}
