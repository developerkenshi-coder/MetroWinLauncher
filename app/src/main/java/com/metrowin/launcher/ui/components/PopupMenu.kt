package com.metrowin.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.LocalAccent
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.MotionTable
import com.metrowin.launcher.ui.anim.Surface
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.panel

/**
 * The panel of every pop-up menu of the launcher (the menu of an app, the menu of a row in a mini app, and the next ones): the surface and
 * the frame of the look in use, a fade and a small growth when it opens, and a scroll when the entries do not fit. Fill it with [MenuItem].
 */
@Composable
fun MenuPanel(modifier: Modifier = Modifier, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val reduce = LocalReduceMotion.current
    val shown = remember { Animatable(if (reduce) 1f else 0f) }
    val timing = MotionTable.timing(Surface.Menu, LocalStyle.current.kind, reduce)
    LaunchedEffect(Unit) { if (!reduce) shown.animateTo(1f, metroTween(durationMillis = timing.inMs, easing = Motion.Decelerate)) }
    val order = remember { IntArray(1) }
    CompositionLocalProvider(LocalMenuOrder provides order) {
    Column(
        modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .fillMaxWidth()
            .graphicsLayer {
                val grow = POP_FROM + (1f - POP_FROM) * shown.value
                scaleX = grow
                scaleY = grow
                alpha = shown.value
            }
            .panel()
            .heightIn(max = MENU_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(vertical = Spacing.Medium),
        content = content,
    )
    }
}

/** Counts the entries of the [MenuPanel] as they are first composed, so that each one knows its place in the cascade. Null outside a panel. */
private val LocalMenuOrder = staticCompositionLocalOf<IntArray?> { null }

/** An entry of a [MenuPanel]: at least 48 dp high, so that a finger finds it, and growing with the text when this is made bigger. */
@Composable
fun MenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null) {
    // Windows Phone: the entry that is touched fills with the accent.
    var pressed by remember { mutableStateOf(false) }
    val lit = pressed && LocalStyle.current.isClassic
    // In a panel the entries come in one after the other, not all together.
    val order = LocalMenuOrder.current
    val reduce = LocalReduceMotion.current
    val cascade = remember { Animatable(if (order == null || reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (cascade.value < 1f) {
            kotlinx.coroutines.delay(CASCADE_STEP_MS * (order!![0]++).coerceAtMost(CASCADE_MAX).toLong())
            cascade.animateTo(1f, metroTween(durationMillis = CASCADE_MS, easing = Motion.Decelerate))
        }
    }
    Box(
        modifier.graphicsLayer { alpha = cascade.value; translationY = (1f - cascade.value) * 10.dp.toPx() }.fillMaxWidth().heightIn(min = 48.dp).then(if (lit) Modifier.background(LocalAccent.current) else Modifier)
            .metroPress(onClick = onClick, onPressedChange = { pressed = it }).padding(horizontal = Spacing.Page, vertical = Spacing.Large),
        contentAlignment = Alignment.CenterStart,
    ) {
        MetroText(text, size = MetroType.Subtitle, color = if (lit) onAccent() else color ?: LocalMetroColors.current.foreground)
    }
}

private const val POP_FROM = 0.94f
private const val CASCADE_STEP_MS = 35
private const val CASCADE_MAX = 8
private const val CASCADE_MS = 180
private val MENU_MAX_HEIGHT = 440.dp
