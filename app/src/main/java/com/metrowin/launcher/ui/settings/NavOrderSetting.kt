package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.metrowin.launcher.R
import com.metrowin.launcher.data.NavButtons
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.frame
import kotlin.math.roundToInt

/**
 * The navigation bar in miniature: a button dragged onto another place swaps the two, and the real bar follows (see [NavButtons]). TalkBack
 * gets "move left" and "move right" on each button instead of the drag.
 */
@Composable
fun NavOrderSetting(env: SettingsEnv) {
    val colors = LocalMetroColors.current
    val label = stringResource(R.string.gest_navbar_order)
    val context = LocalContext.current
    val store = remember { SettingsStore.of(context) }
    var order by remember { mutableStateOf(store.navOrder()) }
    val names = listOf(R.string.nav_btn_back, R.string.nav_btn_start, R.string.nav_btn_search).map { stringResource(it) }
    val moveLeft = stringResource(R.string.nav_move_left)
    val moveRight = stringResource(R.string.nav_move_right)
    val swap = { a: Int, b: Int ->
        if (a != b) {
            val places = order.toCharArray()
            val kept = places[a]
            places[a] = places[b]
            places[b] = kept
            order = String(places)
            store.setNavOrder(order)
            // The bar over the apps is drawn again with the new order.
            LockAccessibilityService.syncNavBar()
        }
    }
    // The place being dragged from (-1: none) and where the finger is along the bar.
    var from by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var placePx by remember { mutableFloatStateOf(1f) }
    WithInfo("gest.navorder", label, Modifier.settingAnchor("gest.navorder").testTag("gestures:navorder")) { Column {
        SettingLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(64.dp)
                .frame(2.dp, colors.foreground)
                .onSizeChanged { placePx = (it.width / 3f).coerceAtLeast(1f) }
                .pointerInput(order) {
                    detectDragGestures(
                        onDragStart = { start -> from = (start.x / placePx).toInt().coerceIn(0, 2); dragX = start.x },
                        onDrag = { change, amount -> change.consume(); dragX = (dragX + amount.x).coerceIn(0f, 3 * placePx) },
                        onDragEnd = { swap(from, (dragX / placePx).toInt().coerceIn(0, 2)); from = -1 },
                        onDragCancel = { from = -1 },
                    )
                },
        ) {
            for (place in 0..2) {
                val button = order[place] - '0'
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .zIndex(if (place == from) 1f else 0f)
                        .offset { IntOffset(if (place == from) (dragX - (place + 0.5f) * placePx).roundToInt() else 0, 0) }
                        .semantics {
                            contentDescription = names[button]
                            customActions = buildList {
                                if (place > 0) add(CustomAccessibilityAction(moveLeft) { swap(place, place - 1); true })
                                if (place < 2) add(CustomAccessibilityAction(moveRight) { swap(place, place + 1); true })
                            }
                        }
                        .testTag("gestures:navorder:$place"),
                    contentAlignment = Alignment.Center,
                ) { NavGlyph(button, colors.foreground) }
            }
        }
        SettingHint(stringResource(R.string.gest_navbar_order_hint), Modifier.padding(top = 2.dp))
    } }
}

/** One button of the bar (0 back, 1 the Windows logo, 2 search), drawn like the real one in [com.metrowin.launcher.gestures.NavBarOverlay]. */
@Composable
private fun NavGlyph(button: Int, color: Color) {
    // shortcut: the shapes are written twice (here with Compose, there with android.graphics); upgrade when a third place needs them.
    Canvas(Modifier.size(44.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val s = size.height * 0.25f
        val line = Stroke(2.2.dp.toPx(), cap = StrokeCap.Square)
        when (button) {
            0 -> {
                drawLine(color, Offset(cx + s, cy), Offset(cx - s, cy), line.width, StrokeCap.Square)
                val head = Path().apply {
                    moveTo(cx - 0.25f * s, cy - 0.75f * s)
                    lineTo(cx - s, cy)
                    lineTo(cx - 0.25f * s, cy + 0.75f * s)
                }
                drawPath(head, color, style = line)
            }
            1 -> {
                val gap = 0.28f * s
                val pane = s - gap / 2
                for (row in 0..1) for (col in 0..1) drawRect(color, Offset(cx - s + col * (pane + gap), cy - s + row * (pane + gap)), Size(pane, pane))
            }
            else -> {
                drawCircle(color, 0.8f * s, Offset(cx - 0.25f * s, cy - 0.25f * s), style = line)
                drawLine(color, Offset(cx + 0.35f * s, cy + 0.35f * s), Offset(cx + s, cy + s), line.width, StrokeCap.Square)
            }
        }
    }
}
