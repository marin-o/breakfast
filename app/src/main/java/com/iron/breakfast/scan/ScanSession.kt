package com.iron.breakfast.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iron.breakfast.app.Notices
import com.iron.breakfast.data.OffResult
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.normalizeBarcode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a barcode is looked up: the Library first, then Open Food Facts. The view model adapts the engine and the client. */
internal interface BarcodeLookup {
    suspend fun localFood(barcode: String): FoodRecord?
    /** A cancel disconnects the request in flight. */
    suspend fun openFoodFacts(barcode: String): OffResult
}

/**
 * The scanner: whether it is [open], why its camera cannot be used ([unavailable], [unavailableReason]), and the
 * barcode lookup in flight ([lookupBusy], [lookupCode]).
 *
 * A barcode ([onBarcode], from the camera or manual entry) goes, in order:
 * - to the one who asked for it ([beginFor], the food form's barcode field): no lookup;
 * - to product review when the Library ([BarcodeLookup.localFood]) or Open Food Facts has it ([onProduct]);
 * - to New food with the barcode ([onManualForm]) when it is unknown and "Look up unknown barcodes" is Off
 *   ([lookUpBarcodes]), or when Open Food Facts lacks it, has it incomplete, or cannot be reached (with a notice).
 *
 * A lookup runs in [scope], its reads on [io]. Its result applies only while it still targets what it was started for:
 * the same scan (a token that [begin], [end] and [invalidateLookup] move on) and the same day and meal ([selectedDay],
 * [targetMeal], as at [begin]). Leaving the scanner ([end]) or a newer barcode cancels it, which also stops its Open
 * Food Facts request.
 */
internal class ScanSession(
    private val lookup: BarcodeLookup,
    private val notices: Notices,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** The "Look up unknown barcodes" setting. */
    private val lookUpBarcodes: () -> Boolean,
    private val selectedDay: () -> String,
    /** The meal a scanned product would go to (the meal open beneath), null on Daily or the Library tab. */
    private val targetMeal: () -> MealSlot?,
    /** A resolve queue is being resolved: a found product reviews as its part. */
    private val queueActive: () -> Boolean,
    private val onProduct: (food: FoodRecord, origin: ProductReviewOrigin) -> Unit,
    private val onManualForm: (name: String, brand: String, barcode: String, fromOff: Boolean) -> Unit,
) {
    var open by mutableStateOf(false); private set
    /** The scanner is still open but its camera preview cannot be used. */
    var unavailable by mutableStateOf(false); private set
    var unavailableReason by mutableStateOf<ScannerUnavailableReason?>(null); private set
    /** The scanner stays visible while a barcode is looked up. */
    var lookupBusy by mutableStateOf(false); private set
    var lookupCode by mutableStateOf(""); private set
    private var token = 0L
    /** Who asked for the next barcode ([beginFor]); null for a scan that looks it up. */
    private var requester: ((String) -> Unit)? = null
    private var job: Job? = null
    // What the scan was opened for, set by [begin]: the scanner is open only after it.
    private var originDay = ""
    private var originMeal: MealSlot? = null

    /** Opens the scanner for the day and meal on screen now. */
    fun begin() {
        token++
        originDay = selectedDay()
        originMeal = targetMeal()
        unavailable = false
        unavailableReason = null
        lookupBusy = false
        lookupCode = ""
        open = true
    }

    /**
     * Opens the scanner for one barcode, which goes to [onBarcode] instead of a lookup (the food form's barcode field).
     * The request lasts until a barcode is read or the scanner closes; a [begin] in between (the camera permission
     * granted on return) keeps it.
     */
    fun beginFor(onBarcode: (String) -> Unit) {
        requester = onBarcode
        begin()
    }

    /** Back, the arrow or Cancel on the lookup: the scanner closes and a lookup in flight stops. */
    fun end() {
        token++
        job?.cancel()
        job = null
        open = false
        unavailable = false
        unavailableReason = null
        lookupBusy = false
        lookupCode = ""
        requester = null
    }

    /** The day or the meal on screen changed: a lookup in flight no longer applies. */
    fun invalidateLookup() { token++ }

    /** No usable camera: the scanner stays open so manual entry remains available. */
    fun cameraUnavailable() {
        val transition = scannerUnavailableTransition()
        unavailable = transition.unavailable
        unavailableReason = ScannerUnavailableReason.NO_USABLE_CAMERA
        open = transition.scannerOpen
    }

    fun permissionDenied() {
        unavailable = true
        unavailableReason = ScannerUnavailableReason.PERMISSION_DENIED
        open = true
    }

    /** [code] comes from the scanner or manual entry, already a product barcode; anything else is ignored before any lookup or notice. */
    fun onBarcode(code: String) {
        val barcode = normalizeBarcode(code) ?: return
        unavailable = false
        unavailableReason = null
        requester?.let { asked ->
            requester = null
            open = false
            asked(barcode)
            return
        }
        // A barcode read while another is looked up (the camera bound again after a recreation) replaces that lookup, so
        // only one result ever applies; the earlier one's Open Food Facts request stops.
        job?.cancel()
        val mine = token
        val day = originDay
        val meal = originMeal
        val reviewOrigin = productReviewOrigin(meal, queueActive())
        val step = unknownBarcodeStep(lookUpBarcodes())
        lookupCode = barcode
        lookupBusy = true
        job = scope.launch {
            /** Applies [then] only while this lookup still targets what it was started for. */
            fun finish(then: () -> Unit) {
                if (mine == token && day == selectedDay() && meal == targetMeal()) {
                    open = false
                    lookupBusy = false
                    then()
                }
            }
            val local = withContext(io) { lookup.localFood(barcode) }
            if (local != null) { finish { onProduct(local, reviewOrigin) }; return@launch }
            // "Look up unknown barcodes" Off: straight to the manual product form, no network request.
            if (step == UnknownBarcodeStep.MANUAL_FORM) { finish { onManualForm("", "", barcode, false) }; return@launch }
            when (val result = withContext(io) { lookup.openFoodFacts(barcode) }) {
                is OffResult.Found -> finish { onProduct(result.food, reviewOrigin) }
                is OffResult.Ineligible -> finish {
                    notices.show("Nutrition is incomplete. Review it as a personal food.")
                    onManualForm(result.name.orEmpty(), result.brand.orEmpty(), result.barcode, true)
                }
                OffResult.Missing -> finish { notices.show("Product not found. Add it manually with barcode $barcode"); onManualForm("", "", barcode, false) }
                OffResult.Unavailable -> finish { notices.show("No connection. Add the product manually."); onManualForm("", "", barcode, false) }
                is OffResult.Error -> finish { notices.show(result.message); onManualForm("", "", barcode, false) }
            }
        }
    }
}
