package com.metrowin.launcher.ui.apps

import com.metrowin.launcher.ui.theme.panel
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.data.AppShortcut
import com.metrowin.launcher.ui.theme.LocalAccent
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
    /** Null when there is no folder on Start to put the app in. */
    onAddToFolder: (() -> Unit)? = null,
    /** What the app itself offers on a long press ("new message", "incognito tab"), and what to do with one. */
    shortcuts: List<AppShortcut> = emptyList(),
    onShortcut: (AppShortcut) -> Unit = {},
    /** Null when the app cannot be renamed. */
    onRename: (() -> Unit)? = null,
    /** Null when the app still has its own name. */
    onResetName: (() -> Unit)? = null,
    /** Null when the app has no "app info" screen (the launcher's own pages). */
    onInfo: (() -> Unit)? = null,
    /** Null when the app cannot be locked; otherwise whether it asks for the screen lock now. */
    locked: Boolean? = null,
    onToggleLock: () -> Unit = {},
    /** Puts an app's shortcut on Start as a tile; null when the menu cannot (the shortcut stays a plain entry). */
    onPinShortcut: ((AppShortcut) -> Unit)? = null,
) {
    val entries = MenuEntries(
        pinned, onPinToggle, onUninstall, onHide, onAddToFolder, shortcuts, onShortcut, onRename, onResetName, onInfo, locked, onToggleLock, onPinShortcut,
    )
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
                Menu(state.item, entries)
                AppRowContent(state.item)
            } else {
                AppRowContent(state.item)
                Menu(state.item, entries)
            }
        }
    }
}

/** What the menu offers and does, gathered so the menu itself stays short. */
private class MenuEntries(
    val pinned: Boolean,
    val onPinToggle: () -> Unit,
    val onUninstall: () -> Unit,
    val onHide: (() -> Unit)?,
    val onAddToFolder: (() -> Unit)?,
    val shortcuts: List<AppShortcut>,
    val onShortcut: (AppShortcut) -> Unit,
    val onRename: (() -> Unit)?,
    val onResetName: (() -> Unit)?,
    val onInfo: (() -> Unit)?,
    val locked: Boolean?,
    val onToggleLock: () -> Unit,
    val onPinShortcut: ((AppShortcut) -> Unit)?,
)

@Composable
private fun Menu(item: AppItem, e: MenuEntries) {
    // Long menus scroll: ten entries and the pressed row do not always fit on a small screen.
    Column(
        Modifier.fillMaxWidth().panel().heightIn(max = MENU_MAX_HEIGHT).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
    ) {
        e.shortcuts.forEach { shortcut ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MenuEntry(shortcut.label, { e.onShortcut(shortcut) }, Modifier.weight(1f).testTag("menu:shortcut:${shortcut.id}"), color = LocalAccent.current)
                if (e.onPinShortcut != null) {
                    val description = stringResource(R.string.menu_pin_shortcut)
                    Box(
                        Modifier
                            .testTag("menu:shortcutpin:${shortcut.id}")
                            .semantics { contentDescription = description }
                            .metroPress(onClick = { e.onPinShortcut.invoke(shortcut) })
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) { MetroText("+", size = 21.sp, color = LocalAccent.current) }
                }
            }
        }
        MenuEntry(stringResource(if (e.pinned) R.string.menu_unpin else R.string.menu_pin), e.onPinToggle)
        if (e.onAddToFolder != null) MenuEntry(stringResource(R.string.menu_add_to_folder), e.onAddToFolder, Modifier.testTag("menu:folder"))
        if (e.onRename != null) MenuEntry(stringResource(R.string.menu_rename), e.onRename, Modifier.testTag("menu:rename"))
        if (e.onResetName != null) MenuEntry(stringResource(R.string.menu_reset_name), e.onResetName, Modifier.testTag("menu:resetname"))
        if (e.locked != null) {
            MenuEntry(stringResource(if (e.locked) R.string.menu_unlock else R.string.menu_lock), e.onToggleLock, Modifier.testTag("menu:lock"))
        }
        if (e.onInfo != null) MenuEntry(stringResource(R.string.menu_info), e.onInfo, Modifier.testTag("menu:info"))
        if (e.onHide != null) MenuEntry(stringResource(R.string.menu_hide), e.onHide, Modifier.testTag("menu:hide"))
        if (item.removable) MenuEntry(stringResource(R.string.menu_uninstall), e.onUninstall)
    }
}

@Composable
private fun MenuEntry(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null) {
    Box(modifier.fillMaxWidth().metroPress(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp)) {
        MetroText(text, size = 21.sp, color = color ?: LocalMetroColors.current.foreground)
    }
}

/** A row whose bottom edge is below this fraction of the screen gets its menu above it. */
private const val OPEN_UP_BELOW = 0.6f

private val MENU_MAX_HEIGHT = 440.dp
