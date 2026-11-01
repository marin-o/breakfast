package com.bosa.breakfast.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Four across only when what each must hold fits a quarter; otherwise the two-by-two grid. */
class QuarterFitTest {
    @Test fun aNutrientNumberWithItsPaddingFitsAMockWidePhoneButNotA360One() {
        val number = 44.dp + 32.dp
        assertTrue(fitsQuarter(number, 342.dp, 8.dp))
        assertFalse(fitsQuarter(number, 312.dp, 8.dp))
    }

    @Test fun aLabelAndItsDotFitAThirdOnlyWhenTheColumnHoldsThem() {
        assertTrue(fitsColumn(100.dp, 324.dp, 12.dp, 3))
        assertFalse(fitsColumn(100.1.dp, 324.dp, 12.dp, 3))
    }

    @Test fun mealChoicesFollowTheirGap() {
        val breakfast = 66.dp + 26.dp
        assertFalse(fitsQuarter(breakfast, 342.dp, 6.dp))
        assertTrue(fitsQuarter(breakfast, 412.dp, 6.dp))
    }
}
