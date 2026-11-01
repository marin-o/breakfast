package com.bosa.breakfast.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MOB-035: only an animator duration scale of 0 ("Remove animations") turns motion off; a slower scale still moves. */
class ReducedMotionTest {
    @Test fun onlyAZeroScaleReducesMotion() {
        assertTrue(motionReduced(0f))
        assertFalse(motionReduced(1f))
        assertFalse(motionReduced(0.5f))
        assertFalse(motionReduced(10f))
    }
}
