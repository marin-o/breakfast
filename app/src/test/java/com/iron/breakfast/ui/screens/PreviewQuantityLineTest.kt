package com.iron.breakfast.ui.screens

import com.iron.breakfast.domain.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewQuantityLineTest {
    @Test fun countedPiecesReadPlainlyWithoutApproximation() {
        assertEquals("2 pieces", previewQuantityLine(2.0, MeasureUnit.PIECE, approximate = true))
        assertEquals("1 piece", previewQuantityLine(1.0, MeasureUnit.PIECE, approximate = true))
        assertEquals("1 piece", previewQuantityLine(1.0, MeasureUnit.PIECE, approximate = false))
    }

    @Test fun approximationStaysWhereTheAmountWasConverted() {
        assertEquals("≈1 tbsp", previewQuantityLine(1.0, MeasureUnit.TABLESPOON, approximate = true))
        assertEquals("100 g", previewQuantityLine(100.0, MeasureUnit.GRAM, approximate = false))
    }
}
