package com.bosa.breakfast.scan

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A barcode counts only inside the brackets plus the feathering, mapped from the rotated analysis frame. */
class ScanRegionTest {
    // A portrait phone: a 640x480 buffer turned 90 degrees stands 480 wide and 640 tall.
    private val frame = ScanFrame(640, 480, 90, Rect(0f, 0f, 640f, 480f))
    // The preview is 960x1280, exactly twice the upright frame, so no crop.
    private val preview = Size(960f, 1280f)
    private val finder = Rect(300f, 500f, 660f, 700f)

    @Test
    fun aCodeInsideTheBracketsCounts() {
        // Upright 160..320 x 260..340 maps to 320..640 x 520..680.
        assertTrue(barcodeInViewfinder(Rect(160f, 260f, 320f, 340f), frame, preview, finder, 0f))
    }

    @Test
    fun aCodeElsewhereInViewDoesNotCount() {
        assertFalse(barcodeInViewfinder(Rect(10f, 20f, 100f, 60f), frame, preview, finder, 40f))
    }

    @Test
    fun featheringLetsACodeReachPastTheBrackets() {
        // Maps to 280..680 x 480..720: 20 px past each side.
        val box = Rect(140f, 240f, 340f, 360f)
        assertFalse(barcodeInViewfinder(box, frame, preview, finder, 0f))
        assertTrue(barcodeInViewfinder(box, frame, preview, finder, 20f))
    }

    @Test
    fun aCentreCroppedPreviewShiftsTheMapping() {
        // A 960x1600 preview crops the 480x640 frame's sides: scale 2.5, 120 px cut off on each side.
        val tall = Size(960f, 1600f)
        val box = Rect(200f, 300f, 280f, 340f)
        // Maps to 380..580 x 750..850.
        assertTrue(barcodeInViewfinder(box, frame, tall, Rect(370f, 740f, 590f, 860f), 0f))
        assertFalse(barcodeInViewfinder(box, frame, tall, Rect(500f, 740f, 590f, 860f), 0f))
    }

    @Test
    fun uprightRectTurnsEachRotation() {
        val r = Rect(10f, 20f, 30f, 60f)
        assertEquals(r, uprightRect(r, 640, 480, 0))
        assertEquals(Rect(420f, 10f, 460f, 30f), uprightRect(r, 640, 480, 90))
        assertEquals(Rect(610f, 420f, 630f, 460f), uprightRect(r, 640, 480, 180))
        assertEquals(Rect(20f, 610f, 60f, 630f), uprightRect(r, 640, 480, 270))
    }
}
