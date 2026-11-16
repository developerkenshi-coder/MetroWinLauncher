package com.metrowin.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.MotionTable
import com.metrowin.launcher.ui.anim.Surface
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.anim.turnstile
import com.metrowin.launcher.ui.gesture.BackGestureHandler
import com.metrowin.launcher.ui.gesture.TurnstileBack
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import kotlinx.coroutines.flow.first

/**
 * A list and the page that takes its place ([page] is null while the list shows), the way Windows Phone does it: the list turns away,
 * then the page turns in; Back (the gesture follows the finger) turns the page away and then the list comes back. The list stays composed
 * (its scroll and its state are kept) but is out of reach while it is turned away. A page that handles Back itself in [detail] gets
 * it first, and [onBack] is what the gesture does otherwise: it has to make [page] null. With [animated] false the page simply
 * replaces the list, for the apps that have an option for it. Reduced motion is looked after by the turnstile itself.
 */
@Composable
fun <T : Any> PageSwitch(
    page: T?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    animated: Boolean = true,
    list: @Composable () -> Unit,
    detail: @Composable (T) -> Unit,
) {
    if (!animated) {
        Box(modifier) { if (page != null) detail(page) else list() }
        return
    }
    val reduced = LocalReduceMotion.current
    val timing = MotionTable.timing(Surface.Page, LocalStyle.current.kind, reduced)
    val away = remember { Animatable(if (page == null) 1f else 0f) }
    val listAway = remember { Animatable(if (page == null) 0f else 1f) }
    var detailOn by remember { mutableStateOf(page != null) }
    // The page keeps what it showed while it turns away.
    val kept = remember { arrayOfNulls<Any>(1) }
    if (page != null) kept[0] = page
    val scope = rememberCoroutineScope()
    LaunchedEffect(page != null) {
        if (page != null) {
            listAway.animateTo(1f, metroTween(durationMillis = timing.outMs, easing = Motion.TurnstileOut))
            detailOn = true
        } else {
            detailOn = false
            snapshotFlow { away.value }.first { it >= 1f }
            listAway.animateTo(0f, metroTween(durationMillis = timing.inMs, easing = Motion.TurnstileIn))
        }
    }
    Box(modifier) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { turnstile(listAway.value, reduced) }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (listAway.value > 0f) event.changes.forEach { it.consume() }
                        }
                    }
                }
                .then(if (page != null) Modifier.clearAndSetSemantics { } else Modifier),
        ) { list() }
        TurnstileHost(show = detailOn, modifier = Modifier.fillMaxSize(), away = away) {
            if (page != null) BackGestureHandler { TurnstileBack(away, scope, onBack) }
            @Suppress("UNCHECKED_CAST")
            (kept[0] as T?)?.let { detail(it) }
        }
    }
}
