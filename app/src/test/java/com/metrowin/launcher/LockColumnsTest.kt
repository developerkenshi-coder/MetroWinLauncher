package com.metrowin.launcher

import com.metrowin.launcher.ui.lock.lockTwoColumns
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockColumnsTest {
    @Test fun onItsSideOrOnATablet() {
        assertTrue(lockTwoColumns(915f, 412f))
        assertTrue(lockTwoColumns(1280f, 800f))
    }

    @Test fun uprightOrNearlySquareKeepsOneColumn() {
        assertFalse(lockTwoColumns(360f, 800f))
        assertFalse(lockTwoColumns(800f, 700f))
    }
}
