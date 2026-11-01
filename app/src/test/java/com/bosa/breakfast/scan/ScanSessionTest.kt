package com.bosa.breakfast.scan

import com.bosa.breakfast.app.Notices
import com.bosa.breakfast.data.OffResult
import com.bosa.breakfast.domain.FoodOrigin
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.MealSlot
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.NutritionBasis
import com.bosa.breakfast.domain.NutritionValues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionTest {
    /** The Library answers at once; each Open Food Facts lookup waits until the test answers it. */
    private class FakeLookup : BarcodeLookup {
        val library = mutableMapOf<String, FoodRecord>()
        val asked = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        private val answers = mutableMapOf<String, CompletableDeferred<OffResult>>()
        override suspend fun localFood(barcode: String): FoodRecord? = library[barcode]
        override suspend fun openFoodFacts(barcode: String): OffResult {
            asked += barcode
            val answer = CompletableDeferred<OffResult>().also { answers[barcode] = it }
            try { return answer.await() } catch (e: CancellationException) { cancelled += barcode; throw e }
        }
        fun answer(barcode: String, result: OffResult) { answers.getValue(barcode).complete(result) }
    }

    private val lookup = FakeLookup()
    private val notices = Notices()
    private var lookUp = true
    private var day = "2026-10-28"
    private var meal: MealSlot? = null
    private var onLibraryTab = true
    private var queueActive = false
    private val events = mutableListOf<String>()
    private val scan = ScanSession(
        lookup, notices, CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), Dispatchers.Unconfined,
        lookUpBarcodes = { lookUp },
        selectedDay = { day },
        targetMeal = { meal },
        libraryTab = { onLibraryTab && meal == null },
        queueActive = { queueActive },
        onProduct = { food, origin -> events += "review ${food.name} $origin" },
        onManualForm = { name, brand, barcode, fromOff, origin -> events += "form '$name' '$brand' $barcode fromOff=$fromOff" + if (origin != null) " logging" else "" },
    )

    private val nutella = FoodRecord("off-1", "Nutella", emptyList(), "Spreads", FoodOrigin.GENERIC, NutritionBasis(100.0, MeasureUnit.GRAM, NutritionValues(539.0, 6.3, 57.5, 30.9)))

    @Test fun aScanFromDailyLogsWhetherTheFoodIsKnownOrNot() {
        onLibraryTab = false
        lookup.library["4006381333931"] = nutella
        scan.begin()
        scan.onBarcode("4006381333931")
        assertEquals("review Nutella MEAL", events.single())
        events.clear()
        lookUp = false
        scan.begin()
        scan.onBarcode("5901234123457")
        assertEquals("form '' '' 5901234123457 fromOff=false logging", events.single())
    }

    @Test fun aScanFromTheLogIconIsForTheDraftEvenWithAQueue() {
        queueActive = true
        lookup.library["4006381333931"] = nutella
        scan.begin(forLog = true)
        scan.onBarcode("4006381333931")
        assertEquals("review Nutella LOG_PART", events.single())
    }

    @Test fun aScanFromTheLibraryTabSavesAndItsUnknownFoodIsNotLogged() {
        lookup.library["4006381333931"] = nutella
        scan.begin()
        scan.onBarcode("4006381333931")
        assertEquals("review Nutella LIBRARY", events.single())
        events.clear()
        lookUp = false
        scan.begin()
        scan.onBarcode("5901234123457")
        assertEquals("form '' '' 5901234123457 fromOff=false", events.single())
    }

    @Test fun aLibraryBarcodeReviewsAtOnceForTheMealItWasScannedFor() {
        lookup.library["4006381333931"] = nutella
        meal = MealSlot.LUNCH
        scan.begin()
        assertTrue(scan.open)
        scan.onBarcode("4006381333931")
        assertFalse(scan.open)
        assertFalse(scan.lookupBusy)
        assertEquals(listOf("review Nutella MEAL"), events)
        assertTrue("nothing asked of Open Food Facts", lookup.asked.isEmpty())
    }

    @Test fun anythingButAProductCodeIsIgnored() {
        scan.begin()
        scan.onBarcode("https://example.com")
        scan.onBarcode("12345")
        assertTrue(scan.open)
        assertFalse(scan.lookupBusy)
        assertTrue(events.isEmpty())
    }

    @Test fun anOpenFoodFactsProductReviewsAsAQueuedPartWhileAQueueIsResolved() {
        queueActive = true
        scan.begin()
        scan.onBarcode("4006381333931")
        assertTrue(scan.lookupBusy)
        assertEquals("4006381333931", scan.lookupCode)
        lookup.answer("4006381333931", OffResult.Found(nutella))
        assertEquals(listOf("review Nutella QUEUE"), events)
        assertFalse(scan.open)
    }

    @Test fun anUnknownBarcodeOpensNewFoodWithTheReason() {
        scan.begin()
        scan.onBarcode("4006381333931")
        lookup.answer("4006381333931", OffResult.Missing)
        assertEquals("Product not found. Add it manually with barcode 4006381333931", notices.message)
        assertEquals(listOf("form '' '' 4006381333931 fromOff=false"), events)
        scan.begin()
        scan.onBarcode("5310382009733")
        lookup.answer("5310382009733", OffResult.Ineligible("Ajvar", "Podravka", "5310382009733"))
        assertEquals("Nutrition is incomplete. Review it as a personal food.", notices.message)
        assertEquals("form 'Ajvar' 'Podravka' 5310382009733 fromOff=true", events.last())
        scan.begin()
        scan.onBarcode("96385074")
        lookup.answer("96385074", OffResult.Unavailable)
        assertEquals("No connection. Add the product manually.", notices.message)
        scan.begin()
        scan.onBarcode("0036000291452")
        lookup.answer("0036000291452", OffResult.Error("Open Food Facts returned 500."))
        assertEquals("Open Food Facts returned 500.", notices.message)
        assertEquals("form '' '' 0036000291452 fromOff=false", events.last())
    }

    @Test fun withLookUpOffAnUnknownBarcodeGoesStraightToNewFoodWithoutANetworkRequest() {
        lookUp = false
        scan.begin()
        scan.onBarcode("4006381333931")
        assertTrue(lookup.asked.isEmpty())
        assertNull(notices.message)
        assertEquals(listOf("form '' '' 4006381333931 fromOff=false"), events)
        // A barcode in the Library still reviews.
        lookup.library["5310382009733"] = nutella
        scan.begin()
        scan.onBarcode("5310382009733")
        assertEquals("review Nutella LIBRARY", events.last())
    }

    @Test fun leavingTheScannerStopsTheLookupAndItsRequest() {
        scan.begin()
        scan.onBarcode("4006381333931")
        scan.end()
        assertEquals(listOf("4006381333931"), lookup.cancelled)
        assertFalse(scan.open)
        assertFalse(scan.lookupBusy)
        assertEquals("", scan.lookupCode)
        assertTrue(events.isEmpty())
    }

    /** B16, deliberate: before, both lookups ran and both applied (a review over a New food form, or two reviews). */
    @Test fun aSecondBarcodeDuringALookupReplacesIt() {
        scan.begin()
        scan.onBarcode("4006381333931")
        scan.onBarcode("5310382009733")
        assertEquals("the first request stops", listOf("4006381333931"), lookup.cancelled)
        assertEquals("5310382009733", scan.lookupCode)
        assertTrue(scan.lookupBusy)
        lookup.answer("4006381333931", OffResult.Missing)
        assertTrue("the cancelled lookup applies nothing", events.isEmpty())
        lookup.answer("5310382009733", OffResult.Found(nutella))
        assertEquals(listOf("review Nutella LIBRARY"), events)
        assertFalse(scan.open)
    }

    @Test fun aLookupForAnotherDayOrMealNoLongerApplies() {
        scan.begin()
        scan.onBarcode("4006381333931")
        // The coordinator moved the day: the result that arrives later is dropped.
        scan.invalidateLookup()
        lookup.answer("4006381333931", OffResult.Found(nutella))
        assertTrue(events.isEmpty())
        // The same when the day or the meal on screen differ from the ones the scan began on.
        scan.begin()
        scan.onBarcode("5310382009733")
        day = "2026-10-27"
        lookup.answer("5310382009733", OffResult.Found(nutella))
        assertTrue(events.isEmpty())
        day = "2026-10-28"
        scan.begin()
        scan.onBarcode("96385074")
        meal = MealSlot.DINNER
        lookup.answer("96385074", OffResult.Found(nutella))
        assertTrue(events.isEmpty())
    }

    @Test fun aScanAskedForOneBarcodeReturnsItWithNoLookup() {
        scan.beginFor { events += "asked $it" }
        assertTrue(scan.open)
        // The camera permission granted on return begins the scan again; the request stays.
        scan.begin()
        scan.onBarcode(" 036000291452 ")
        assertEquals(listOf("asked 036000291452"), events)
        assertFalse(scan.open)
        assertTrue(lookup.asked.isEmpty())
        // One barcode only: the next scan looks its barcode up.
        scan.begin()
        scan.onBarcode("4006381333931")
        assertEquals(listOf("4006381333931"), lookup.asked)
    }

    @Test fun leavingTheScannerDropsTheRequest() {
        scan.beginFor { events += "asked $it" }
        scan.end()
        scan.begin()
        scan.onBarcode("4006381333931")
        assertTrue(events.isEmpty())
        assertEquals(listOf("4006381333931"), lookup.asked)
    }

    @Test fun aCameraThatCannotBeUsedKeepsTheScannerForManualEntry() {
        scan.begin()
        scan.cameraUnavailable()
        assertTrue(scan.open)
        assertTrue(scan.unavailable)
        assertEquals(ScannerUnavailableReason.NO_USABLE_CAMERA, scan.unavailableReason)
        scan.permissionDenied()
        assertEquals(ScannerUnavailableReason.PERMISSION_DENIED, scan.unavailableReason)
        // A typed code clears it while it is looked up.
        scan.onBarcode("4006381333931")
        assertFalse(scan.unavailable)
        assertNull(scan.unavailableReason)
        scan.begin()
        assertFalse(scan.unavailable)
    }
}
