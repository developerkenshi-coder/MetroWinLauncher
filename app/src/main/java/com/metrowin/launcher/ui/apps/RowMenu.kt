package com.metrowin.launcher.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.panel
import kotlin.math.roundToInt

/** Where a row is on the screen, in pixels of the window; it is kept up to date by [anchor], and read when the row is long pressed. */
class RowBounds {
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f
}

fun Modifier.anchor(bounds: RowBounds): Modifier = onGloballyPositioned {
    val p = it.positionInWindow()
    bounds.left = p.x; bounds.top = p.y; bounds.width = it.size.width.toFloat(); bounds.height = it.size.height.toFloat()
}

class MenuAction(val label: String, val onClick: () -> Unit)

/**
 * The context menu of Windows Phone for a row of a list in a mini app: the page dims, the row stays where it was, and the actions are
 * under it (above it when the row is low on the screen). One host per screen ([RowMenuHost], in the page turn of the mini apps).
 */
class RowMenuState {
    internal class Open(val bounds: RowBounds, val actions: List<MenuAction>, val row: @Composable () -> Unit)

    internal var open by mutableStateOf<Open?>(null)

    /** Shows the menu for the row at [bounds]; [row] draws it again over the dimmed page. */
    fun show(bounds: RowBounds, actions: List<MenuAction>, row: @Composable () -> Unit) {
        open = Open(RowBounds().also { it.left = bounds.left; it.top = bounds.top; it.width = bounds.width; it.height = bounds.height }, actions, row)
    }

    fun dismiss() { open = null }
}

val LocalRowMenu = staticCompositionLocalOf { RowMenuState() }

@Composable
fun RowMenuHost(state: RowMenuState, modifier: Modifier = Modifier) {
    val open = state.open ?: return
    key(open) { BackHandler { state.dismiss() } }
    val bottomInset = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    BoxWithConstraints(
        modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }
            .background(LocalMetroColors.current.background.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { state.dismiss() } },
    ) {
        val b = open.bounds
        val rowTop = b.top - origin.y
        val rowBottom = rowTop + b.height
        val openUp = rowBottom > constraints.maxHeight * OPEN_UP_BELOW
        val rowDp = with(LocalDensity.current) { b.width.toDp() }
        Column(
            Modifier
                .offset { IntOffset((b.left - origin.x).roundToInt(), 0) }
                .width(rowDp)
                .layout { measurable, c ->
                    val limit = (c.maxHeight - bottomInset).coerceAtLeast(0)
                    val block = measurable.measure(c.copy(minHeight = 0, maxHeight = limit))
                    val y = if (openUp) rowBottom - block.height else rowTop
                    layout(block.width, c.maxHeight) { block.place(0, y.toInt().coerceIn(0, (limit - block.height).coerceAtLeast(0))) }
                },
        ) {
            val menu = @Composable {
                Column(Modifier.testTag("rowmenu:panel").fillMaxWidth().panel().heightIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    open.actions.forEachIndexed { i, action ->
                        Box(Modifier.fillMaxWidth().testTag("rowmenu:$i").metroPress(onClick = { state.dismiss(); action.onClick() }).padding(horizontal = 24.dp, vertical = 12.dp)) {
                            MetroText(action.label, size = 21.sp)
                        }
                    }
                }
            }
            if (openUp) { menu(); open.row() } else { open.row(); menu() }
        }
    }
}

private const val OPEN_UP_BELOW = 0.6f
