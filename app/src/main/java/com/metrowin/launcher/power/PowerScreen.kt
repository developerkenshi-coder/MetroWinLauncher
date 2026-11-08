package com.metrowin.launcher.power

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The curtain at the top follows the finger; past [POWER_OFF_AT] of the screen it is let go and the phone switches off, short of it it springs back. */
private const val POWER_OFF_AT = 0.4f

/** `svc power shutdown` returns at once when it works; past this it is stuck and the screen says so. */
private const val SHUTDOWN_TIMEOUT_MS = 10_000L

@Composable
fun PowerScreen(finish: () -> Unit) {
    var height by remember { mutableStateOf(1) }
    val drag = remember { Animatable(0f) }
    var going by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rest = with(LocalDensity.current) { 140.dp.toPx() }
    Box(
        Modifier.fillMaxSize().pageBackground().onSizeChanged { height = it.height }.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (drag.value > height * POWER_OFF_AT && !going) {
                            going = true
                            val result = withContext(Dispatchers.IO) { ShizukuShell.run(arrayOf("svc", "power", "shutdown"), timeoutMs = SHUTDOWN_TIMEOUT_MS) }
                            // A phone that really switches off never gets here; failing or timing out, the curtain goes back up with a reason.
                            if (!result.ok) {
                                going = false
                                Toast.makeText(context, R.string.pw_failed, Toast.LENGTH_LONG).show()
                                drag.animateTo(0f)
                            }
                        } else if (!going) drag.animateTo(0f)
                    }
                },
                onDragCancel = { scope.launch { if (!going) drag.animateTo(0f) } },
            ) { _, amount -> if (!going) scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) } }
        },
    ) {
        Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (rest + drag.value).toDp() }).background(LocalAccent.current), contentAlignment = Alignment.BottomStart) {
            MetroText(stringResource(if (going) R.string.pw_going else R.string.pw_swipe), size = 28.sp, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(24.dp))
        }
        if (!going) {
            MetroButton(stringResource(R.string.pw_restart), Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(24.dp)) {
                ShizukuShell.async(listOf(arrayOf("svc", "power", "reboot")))
                finish()
            }
        }
    }
}
