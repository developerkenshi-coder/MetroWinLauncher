package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Whatever can be touched can be hit with a finger: 48 dp, even where the drawing is smaller. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class TouchTargetTest : ScreenshotBase() {
    @Test
    fun aSmallPressedThingIsHitWithin48dpOfItsCentre() {
        var clicks = 0
        show { Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(20.dp).testTag("tiny").metroPress(onClick = { clicks++ })) } }
        // 20 dp wide: 14 dp outside its edge is still inside the 48 dp square around it.
        compose.onNodeWithTag("tiny").performTouchInput { click(Offset(-14.dp.toPx(), height / 2f)) }
        assertEquals(1, clicks)
        // And 30 dp outside is not: the touch goes to what is behind.
        compose.onNodeWithTag("tiny").performTouchInput { click(Offset(-30.dp.toPx(), height / 2f)) }
        assertEquals(1, clicks)
    }

    @Test
    fun everyButtonOfTheApplicationBarIsAtLeast48dp() {
        show { AppBar(List(5) { AppBarItem("＋", "Item $it") {} }, menu = listOf(AppBarItem("", "Menu") {})) }
        val buttons = compose.onAllNodes(hasClickAction())
        repeat(buttons.fetchSemanticsNodes().size) { i ->
            buttons[i].assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }
}
