package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.AppShortcut
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.MenuState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The menu of a long-pressed app stays in the visible area: the navigation bar at the bottom never covers its entries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class MenuInsetsTest : ScreenshotBase() {
    private val app = AppItem("a", "Alpha", Launch.Action("x"), Glyph.entries[5], removable = true)
    private val shortcuts = (1..4).map { AppShortcut("s$it", "com.example.alpha", "Shortcut $it", android.os.Process.myUserHandle()) }
    private val navBar = 150 // px

    private fun menuAt(top: Float) {
        show(size = DpSize(360.dp, 760.dp)) {
            AppContextMenu(
                state = MenuState(app, top = top, height = 190f), pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {},
                modifier = Modifier.fillMaxSize(), onHide = {}, onAddToFolder = {}, shortcuts = shortcuts, onShortcut = {},
                onRename = {}, onResetName = {}, onInfo = {}, locked = false, onToggleLock = {}, onPinShortcut = {}, onStore = {}, onShare = {},
                bottomInset = navBar,
            )
        }
        advance(500)
    }

    private fun assertAboveTheBar() {
        val root = compose.onRoot().fetchSemanticsNode().size.height
        val panel = compose.onNodeWithTag("menu:panel").fetchSemanticsNode()
        val bottom = panel.positionInRoot.y + panel.size.height
        assertTrue("the menu ends at $bottom, the bar starts at ${root - navBar}", bottom <= root - navBar + 0.5f)
        assertTrue("the menu is still there", panel.size.height > 200)
    }

    @Test fun aRowInTheLowerPartOpensItsMenuAboveTheBar() {
        menuAt(top = 1900f)
        assertAboveTheBar()
    }

    @Test fun aRowInTheUpperPartOpensItsMenuAboveTheBarToo() {
        menuAt(top = 300f)
        assertAboveTheBar()
    }

    @Test fun aRowOnTheBarItselfIsMovedUp() {
        menuAt(top = 2150f)
        assertAboveTheBar()
    }
}
