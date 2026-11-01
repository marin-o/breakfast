package com.bosa.breakfast.contribution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings, Scanner, "Contribute": where the Contribute to Open Food Facts action shows. */
class ContributionShownTest {
    @Test fun contributeHideHidesTheActionEverywhere() {
        assertTrue(contributionShown(show = true, available = true))
        assertFalse(contributionShown(show = true, available = false))
        assertFalse(contributionShown(show = false, available = true))
        assertFalse(contributionShown(show = false, available = false))
    }
}
