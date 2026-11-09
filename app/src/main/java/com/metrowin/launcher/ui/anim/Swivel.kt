package com.metrowin.launcher.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The swivel of Windows Phone when a row of a list is touched: the row turns away like a card before the next page opens. One per list;
 * [open] runs the turn of the row named by its key and then what opens. Without motion (or outside the classic style) it opens at once.
 */
class RowSwivel internal constructor(private val scope: CoroutineScope, private val animated: Boolean) {
    internal val progress = Animatable(0f)
    internal var turning by mutableStateOf<Any?>(null)

    fun open(key: Any, then: () -> Unit) {
        if (!animated || turning != null) { then(); return }
        scope.launch {
            turning = key
            progress.snapTo(0f)
            progress.animateTo(1f, metroTween(durationMillis = TURN_MS, easing = Motion.Accelerate))
            then()
            // The page is over the list by now; the row is back in place for when it is shown again.
            turning = null
            progress.snapTo(0f)
        }
    }

    private companion object {
        const val TURN_MS = 200
        const val DEGREES = 80f
    }

    internal fun degrees() = DEGREES * progress.value
}

@Composable
fun rememberRowSwivel(): RowSwivel {
    val scope = rememberCoroutineScope()
    val animated = !LocalReduceMotion.current && LocalStyle.current.isClassic
    return remember(scope, animated) { RowSwivel(scope, animated) }
}

/** The row with this [key] turns away while the swivel runs; the others stay. */
fun Modifier.swivel(swivel: RowSwivel, key: Any): Modifier = graphicsLayer {
    if (swivel.turning == key) {
        transformOrigin = TransformOrigin(0.5f, 0.5f)
        rotationX = swivel.degrees()
        cameraDistance = Motion.CAMERA * density
        alpha = 1f - swivel.progress.value * 0.6f
    }
}
