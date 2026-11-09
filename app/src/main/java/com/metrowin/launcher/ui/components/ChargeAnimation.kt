package com.metrowin.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.gestures.ChargeAnnounce
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.panel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/** One sweep of the filling of the battery, and the rest between two: the screen wakes for the sweep only, not all the time. */
private const val SWEEP_MS = 1_600
private const val REST_MS = 4_400L

/**
 * 0 to 1 while the phone [charging] fills its battery in sweeps: a sweep, a rest, another sweep. It starts again at once when [restart]
 * changes (a cable just plugged in), runs only while the screen is on (the window started) and is 0 for good when motion is reduced.
 * Read it in a draw block, so a frame redraws the battery and nothing else.
 */
@Composable
fun rememberChargeSweep(charging: Boolean, restart: Any? = null): State<Float> {
    val reduced = LocalReduceMotion.current
    val sweep = remember { Animatable(0f) }
    val run = charging && !reduced
    LaunchedWhileStarted(run to restart) {
        if (!run) {
            sweep.snapTo(0f)
            return@LaunchedWhileStarted
        }
        while (true) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(SWEEP_MS, easing = LinearEasing))
            sweep.snapTo(0f)
            delay(REST_MS)
        }
    }
    return sweep.asState()
}

/**
 * The banner of a cable just plugged in: a flat panel that slides in from the top with the bolt, the percentage and what the phone
 * is doing, a bar that fills to the charge, then goes by itself. [topInset] as in `ToastBanner`. With reduced motion it only fades.
 */
@Composable
fun ChargeBanner(
    battery: BatteryInfo,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp? = null,
) {
    val reduced = LocalReduceMotion.current
    val currentDone by rememberUpdatedState(onDone)
    val shown = remember { Animatable(0f) }
    val fill = remember { Animatable(if (reduced) 1f else 0f) }
    var height by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        coroutineScope {
            if (!reduced) launch { delay(150); fill.animateTo(1f, metroTween(durationMillis = 900)) }
            shown.animateTo(1f, metroTween(durationMillis = if (reduced) 80 else IN_MS, easing = Motion.Decelerate))
            delay(ChargeAnnounce.SHOW_MS - IN_MS - OUT_MS)
            shown.animateTo(0f, metroTween(durationMillis = if (reduced) 80 else OUT_MS, easing = Motion.Accelerate))
        }
        currentDone()
    }
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val said = stringResource(R.string.a11y_sb_charging) + ", " + stringResource(R.string.a11y_sb_battery, battery.percent)
    Column(
        modifier
            .then(if (topInset != null) Modifier.padding(top = topInset) else Modifier.topBarsPadding())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .graphicsLayer {
                if (reduced) alpha = shown.value else translationY = -(1f - shown.value) * (height + 80.dp.toPx())
            }
            .panel()
            .testTag("charge:banner")
            .semantics { contentDescription = said }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Bolt(accent, Modifier.size(width = 18.dp, height = 28.dp))
            Spacer(Modifier.width(14.dp))
            MetroText("${battery.percent}%", size = 34.sp, maxLines = 1, modifier = Modifier.testTag("charge:percent"))
            Spacer(Modifier.width(14.dp))
            val left = battery.minutesToFull?.let { stringResource(R.string.battery_until_full, BatteryInfo.duration(it)) }
            val state = stringResource(R.string.battery_charging)
            MetroText(if (left == null) state else "$state · $left", size = 15.sp, color = colors.subtle, maxLines = 2, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            drawRect(colors.subtle.copy(alpha = 0.3f))
            drawRect(accent, size = Size(size.width * battery.percent / 100f * fill.value, size.height))
        }
    }
}

private const val IN_MS = 260
private const val OUT_MS = 200
