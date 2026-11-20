package com.metrowin.launcher.systemsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Where [SystemSettingsScreen] shows the dialog of a page: over the whole screen, outside the scrolling column that holds the pages. */
internal class DialogSlot {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

internal val LocalDialogSlot = staticCompositionLocalOf<DialogSlot?> { null }

/**
 * A dialog of a page of the settings. `DialogFrame` fills its parent, and the parent of a page scrolls (it has no height limit): inside the page
 * it crashed the app ("measured with an infinity maximum height"). So the page hands the dialog to the screen, which shows it outside the scroll.
 * Without a screen to hand it to (a test of one page) the dialog is drawn where it is called.
 */
@Composable
internal fun PageDialog(content: @Composable () -> Unit) {
    val slot = LocalDialogSlot.current
    if (slot == null) {
        content()
        return
    }
    val latest by rememberUpdatedState(content)
    val mine: @Composable () -> Unit = remember { { latest() } }
    DisposableEffect(slot) {
        slot.content = mine
        onDispose { if (slot.content === mine) slot.content = null }
    }
}
