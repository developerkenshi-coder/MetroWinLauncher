package com.metrowin.launcher

import com.metrowin.launcher.ui.components.WindowClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowClassTest {
    @Test fun limits() {
        assertEquals(WindowClass.Compact, WindowClass.of(599f))
        assertEquals(WindowClass.Medium, WindowClass.of(600f))
        assertEquals(WindowClass.Medium, WindowClass.of(839f))
        assertEquals(WindowClass.Expanded, WindowClass.of(840f))
    }

    @Test fun wideFromMedium() {
        assertFalse(WindowClass.Compact.isWide)
        assertTrue(WindowClass.Medium.isWide)
        assertTrue(WindowClass.Expanded.isWide)
    }
}
