package com.metrowin.launcher

import androidx.compose.ui.geometry.Offset
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.ui.gesture.SwipeDirection
import com.metrowin.launcher.ui.gesture.TwoFingerSwipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFingerSwipeTest {
    private val a = Offset(300f, 800f)
    private val b = Offset(700f, 800f)

    private fun swipe(dyA: Float, dyB: Float, dxA: Float = 0f, dxB: Float = 0f) =
        TwoFingerSwipe.direction(a, b, a + Offset(dxA, dyA), b + Offset(dxB, dyB), thresholdPx = 100f)

    @Test
    fun twoFingersMovingDownTogetherSwipeDown() {
        assertEquals(SwipeDirection.Down, swipe(150f, 140f))
    }

    @Test
    fun twoFingersMovingUpTogetherSwipeUp() {
        assertEquals(SwipeDirection.Up, swipe(-150f, -220f))
    }

    @Test
    fun bothFingersHaveToGoFarEnough() {
        assertNull("only one finger went far", swipe(300f, 20f))
        assertNull("nobody went far", swipe(40f, 50f))
        assertEquals(SwipeDirection.Down, swipe(100f, 100f)) // exactly the threshold counts
    }

    @Test
    fun fingersGoingOppositeWaysAreNotASwipe() {
        assertNull(swipe(200f, -200f))
    }

    @Test
    fun aPinchOrAnAngledSwipeIsNotASwipe() {
        assertNull("spreading apart", swipe(10f, 10f, dxA = -300f, dxB = 300f))
        assertNull("more sideways than down", swipe(150f, 150f, dxA = 300f, dxB = 300f))
        assertNull("one finger mostly sideways", swipe(150f, 150f, dxA = 0f, dxB = 400f))
    }

    // --- the screen lock service

    private val pkg = "com.metrowin.launcher"
    private val cls = "com.metrowin.launcher.gestures.LockAccessibilityService"

    @Test
    fun theServiceIsFoundInTheListAndroidKeeps() {
        val list = "com.example.other/.Service:com.metrowin.launcher/com.metrowin.launcher.gestures.LockAccessibilityService"
        assertTrue(LockAccessibilityService.isListed(list, pkg, cls))
        assertTrue("the short form of the name", LockAccessibilityService.isListed("com.metrowin.launcher/.gestures.LockAccessibilityService", pkg, cls))
    }

    @Test
    fun anotherServiceOrAnEmptyListIsNotIt() {
        assertFalse(LockAccessibilityService.isListed("", pkg, cls))
        assertFalse(LockAccessibilityService.isListed("com.example.other/.Service", pkg, cls))
        assertFalse("a service of another app with the same class name", LockAccessibilityService.isListed("com.other/com.metrowin.launcher.gestures.LockAccessibilityService", pkg, cls))
        assertFalse("junk in the list", LockAccessibilityService.isListed("nonsense::/x:", pkg, cls))
    }
}
