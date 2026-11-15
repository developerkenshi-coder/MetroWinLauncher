package com.metrowin.launcher

import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.ui.components.statusBarSolid
import com.metrowin.launcher.ui.theme.DarkColors
import com.metrowin.launcher.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarColorsTest {
    @Test fun solidBarIsBlackInTheDarkAndTheSurfaceGreyInTheLight() {
        assertEquals(Color.Black, DarkColors.statusBarSolid())
        assertEquals(Color(0xFFE6E6E6), LightColors.statusBarSolid())
    }
}
