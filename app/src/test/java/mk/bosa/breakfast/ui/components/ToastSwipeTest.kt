package mk.bosa.breakfast.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToastSwipeTest {
    private fun exit(x: Float = 0f, y: Float = 0f, vx: Float = 0f, vy: Float = 0f) = toastExit(x, y, vx, vy, sideways = 100f, down = 40f, fling = 1000f)

    @Test fun upGetsNoMovementAndDownFollows() {
        assertEquals(0f, toastDragY(-50f), 0f)
        assertEquals(20f, toastDragY(20f), 0f)
    }

    @Test fun aShortDragSpringsBack() {
        assertNull(exit(x = 50f))
        assertNull(exit(y = 20f))
    }

    @Test fun pastTheThresholdItLeavesThatWay() {
        assertEquals(ToastExit.LEFT, exit(x = -120f))
        assertEquals(ToastExit.RIGHT, exit(x = 120f))
        assertEquals(ToastExit.DOWN, exit(y = 50f))
        assertEquals("the longer way wins", ToastExit.DOWN, exit(x = 110f, y = 200f))
    }

    @Test fun aFlingLeavesEvenFromAShortDrag() {
        assertEquals(ToastExit.LEFT, exit(x = -10f, vx = -1500f))
        assertEquals(ToastExit.RIGHT, exit(x = 10f, vx = 1500f))
        assertEquals(ToastExit.DOWN, exit(y = 5f, vy = 1500f))
    }

    @Test fun aFlingUpIsNoFling() {
        assertNull(exit(vy = -3000f))
    }

    @Test fun aFlingMainlyUpIsNoFlingEvenWithSpeedSideways() {
        assertNull(exit(vx = 1500f, vy = -3000f))
        assertNull("nor past a threshold", exit(x = -120f, vx = -1500f, vy = -3000f))
    }

    @Test fun aFlingMainlySidewaysStillLeavesThoughItRises() {
        assertEquals(ToastExit.RIGHT, exit(vx = 3000f, vy = -1500f))
    }

    @Test fun aSlowRiseDoesNotUndoASidewaysDrag() {
        assertEquals(ToastExit.LEFT, exit(x = -120f, vy = -40f))
    }
}
