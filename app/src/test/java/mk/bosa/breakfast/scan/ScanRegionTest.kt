package mk.bosa.breakfast.scan

import androidx.compose.ui.geometry.Offset
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
    fun theDecodersCornersCountFromTheCrop() {
        val corners = listOf(Offset(30f, 40f), Offset(130f, 42f), Offset(130f, 60f), Offset(30f, 58f))
        // Turned 90 degrees, the 100 px cut off the buffer's left end is the upright frame's top.
        val cropped = ScanFrame(640, 480, 90, Rect(100f, 0f, 540f, 480f))
        assertEquals(Rect(30f, 140f, 130f, 160f), uprightBox(corners, cropped))
        // Turned 270 degrees, the 200 px cut off the buffer's right end is the top.
        val other = ScanFrame(640, 480, 270, Rect(0f, 0f, 440f, 480f))
        assertEquals(Rect(30f, 240f, 130f, 260f), uprightBox(corners, other))
    }

    /** The decoder's corners for a code over [xs] and [ys], in crop pixels, judged with no feathering. */
    private fun cornersCount(xs: ClosedFloatingPointRange<Float>, ys: ClosedFloatingPointRange<Float>): Boolean {
        val corners = listOf(Offset(xs.start, ys.start), Offset(xs.endInclusive, ys.start), Offset(xs.endInclusive, ys.endInclusive), Offset(xs.start, ys.endInclusive))
        // The 100 px cut off the buffer's left end is the upright frame's top. The 960x880 preview is twice the 480x440 crop,
        // so a corner lands at twice its crop position and the 300..660 x 500..700 brackets are 150..330 x 250..350 in crop pixels.
        val cropped = ScanFrame(640, 480, 90, Rect(100f, 0f, 540f, 480f))
        return barcodeInViewfinder(uprightBox(corners, cropped), cropped, Size(960f, 880f), finder, 0f)
    }

    @Test
    fun aCodeWithBarsInsideCountsWhenTheCentreOfItsReadRowsIsInside() {
        // The read rows reach above the brackets, but their centre, 270, is inside.
        assertTrue(cornersCount(160f..320f, 240f..300f))
        assertTrue(cornersCount(160f..320f, 300f..300f))
    }

    @Test
    fun aCodeWhoseReadRowsCentreIsOutsideDoesNotCount() {
        // Mostly above the brackets: the rows it read centre on 240, though they reach 248.
        assertFalse(cornersCount(160f..320f, 232f..248f))
        assertFalse(cornersCount(160f..320f, 352f..360f))
    }

    @Test
    fun aNeighbourWhoseBarsRunPastTheEdgeDoesNotCount() {
        // Its rows sit inside the brackets, but its bars start before them, or end after them.
        assertFalse(cornersCount(140f..320f, 290f..296f))
        assertFalse(cornersCount(160f..340f, 290f..296f))
    }

    @Test
    fun aCodeLyingSidewaysIsJudgedAlongItsBarsToo() {
        assertTrue(cornersCount(140f..200f, 255f..345f))
        // Bars past the bottom of the brackets, the read rows' centre inside.
        assertFalse(cornersCount(180f..200f, 255f..355f))
        // Bars inside, the read rows' centre to the left of the brackets.
        assertFalse(cornersCount(130f..160f, 255f..345f))
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
