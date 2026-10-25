package com.metrowin.launcher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** Where the long-pressed row was on screen (in pixels), so the menu can sit right next to it. */
@Immutable
data class MenuState(val item: AppItem, val top: Float, val height: Float)

/**
 * The menu that opens when an app is long pressed: pin to Start (or unpin) and uninstall. The list
 * dims behind it, the pressed row stays where it was, and the menu opens below it, or above it when
 * the row is in the lower part of the screen.
 */
@Composable
fun AppContextMenu(
    state: MenuState,
    pinned: Boolean,
    onPinToggle: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null when the app cannot be hidden (the launcher's own settings). */
    onHide: (() -> Unit)? = null,
) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(LocalMetroColors.current.background.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { currentDismiss() } },
    ) {
        val rowBottom = state.top + state.height
        val openUp = rowBottom > constraints.maxHeight * OPEN_UP_BELOW

        // Row and menu form one block: it starts at the row when opening downwards, and ends at the
        // bottom of the row when opening upwards.
        Column(
            Modifier.layout { measurable, c ->
                val block = measurable.measure(c)
                val y = if (openUp) rowBottom - block.height else state.top
                layout(block.width, c.maxHeight) {
                    block.place(0, y.toInt().coerceIn(0, (c.maxHeight - block.height).coerceAtLeast(0)))
                }
            },
        ) {
            if (openUp) {
                Menu(state.item, pinned, onPinToggle, onUninstall, onHide)
                AppRowContent(state.item)
            } else {
                AppRowContent(state.item)
                Menu(state.item, pinned, onPinToggle, onUninstall, onHide)
            }
        }
    }
}

@Composable
private fun Menu(item: AppItem, pinned: Boolean, onPinToggle: () -> Unit, onUninstall: () -> Unit, onHide: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().background(LocalMetroColors.current.surface).padding(vertical = 6.dp)) {
        MenuEntry(stringResource(if (pinned) R.string.menu_unpin else R.string.menu_pin), onPinToggle)
        if (onHide != null) MenuEntry(stringResource(R.string.menu_hide), onHide, Modifier.testTag("menu:hide"))
        if (item.removable) MenuEntry(stringResource(R.string.menu_uninstall), onUninstall)
    }
}

@Composable
private fun MenuEntry(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().metroPress(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
        MetroText(text, size = 22.sp)
    }
}

/** A row whose bottom edge is below this fraction of the screen gets its menu above it. */
private const val OPEN_UP_BELOW = 0.6f
