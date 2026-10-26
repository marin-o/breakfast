package com.iron.breakfast.contribution

import com.iron.breakfast.QueueDispatcher
import com.iron.breakfast.app.Notices
import com.iron.breakfast.data.OffContributionDraft
import com.iron.breakfast.data.OffContributionResult
import com.iron.breakfast.data.OffPreflightResult
import com.iron.breakfast.domain.FoodOrigin
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MeasureUnit
import com.iron.breakfast.domain.NutritionBasis
import com.iron.breakfast.domain.NutritionValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionFlowTest {
    private class FakeOff : OffContributions {
        val preflights = mutableListOf<String>()
        val sent = mutableListOf<Triple<OffContributionDraft, String, String>>()
        var preflightResult: OffPreflightResult = OffPreflightResult.NewProduct
        var contributeResult: OffContributionResult = OffContributionResult.Success
        override fun preflight(draft: OffContributionDraft): OffPreflightResult { preflights += draft.barcode; return preflightResult }
        override fun contribute(draft: OffContributionDraft, username: String, password: String): OffContributionResult {
            sent += Triple(draft, username, password)
            return contributeResult
        }
    }

    private val off = FakeOff()
    private val notices = Notices()
    private val io = QueueDispatcher()
    private val flow = ContributionFlow(off, notices, CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), io)

    private fun food(barcode: String?, name: String = "Kefir", unit: MeasureUnit = MeasureUnit.GRAM) = FoodRecord(
        "f-$barcode", name, emptyList(), "dairy", FoodOrigin.CUSTOM, NutritionBasis(100.0, unit, NutritionValues(60.0, 3.0, 4.0, 3.0)),
        barcode = barcode,
    )

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
        // A second tap while it runs, and Back, do nothing; the sign-in stays.
        flow.submit()
        flow.dismissAuth()
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
