package com.bosa.breakfast.scan

/** What a scanned or typed barcode that is not in the Library leads to. */
internal enum class UnknownBarcodeStep { LOOK_UP, MANUAL_FORM }

/** "Look up unknown barcodes" On asks Open Food Facts; Off opens the manual product form with no network request. */
internal fun unknownBarcodeStep(lookUp: Boolean): UnknownBarcodeStep = if (lookUp) UnknownBarcodeStep.LOOK_UP else UnknownBarcodeStep.MANUAL_FORM
