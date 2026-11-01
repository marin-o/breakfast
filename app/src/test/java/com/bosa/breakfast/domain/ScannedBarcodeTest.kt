package com.bosa.breakfast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannedBarcodeTest {
    @Test fun aQrCodesTextIsIgnoredAndAProductCodeIsLookedUp() {
        assertNull(scannedProductBarcode("WIFI:S:x;;"))
        assertNull(scannedProductBarcode("https://example.com/?token=1234567890123"))
        assertNull(scannedProductBarcode(""))
        assertEquals("3017620422003", scannedProductBarcode("3017620422003"))
        assertEquals("3017620422003", scannedProductBarcode(" 3017620422003\n"))
    }

    @Test fun eanAndUpcLengthsPassAsBeforeWhenNotUpcE() {
        assertEquals("96385074", scannedProductBarcode("96385074"))
        assertEquals("042100005264", scannedProductBarcode("042100005264"))
        assertNull(scannedProductBarcode("1234567"))
    }

    @Test fun upcEExpandsToUpcAInEveryForm() {
        assertEquals("065100004327", expandUpcE("654321"))
        assertEquals("065100004327", expandUpcE("0654321"))
        assertEquals("065100004327", expandUpcE("06543217"))
        assertEquals("042100005264", expandUpcE("04252614"))
        assertEquals("012300000451", expandUpcE("01234531"))
        assertEquals("012340000053", expandUpcE("0123454"))
        assertEquals("012345000058", expandUpcE("123455"))
        assertEquals("012345000065", expandUpcE("01234565"))
    }

    @Test fun upcEWithAWrongCheckDigitOrNumberSystemIsNotUpcE() {
        assertNull(expandUpcE("06543210"))
        assertNull(expandUpcE("26543217"))
        assertNull(expandUpcE("12345"))
        assertNull(expandUpcE("065432170"))
    }

    @Test fun aScannedUpcEIsLookedUpAsItsUpcA() {
        assertEquals("042100005264", scannedProductBarcode("04252614", upcE = true))
        // An already expanded value, if ML Kit returns one, stays a normal product code.
        assertEquals("042100005264", scannedProductBarcode("042100005264", upcE = true))
        assertNull(scannedProductBarcode("WIFI:S:x;;", upcE = true))
    }
}
