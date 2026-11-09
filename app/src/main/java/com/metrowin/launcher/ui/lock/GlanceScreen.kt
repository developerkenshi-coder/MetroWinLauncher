package com.metrowin.launcher.ui.lock

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.LockInfo
import com.metrowin.launcher.data.LockStatus
import com.metrowin.launcher.ui.components.MetroText
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Where on the screen the glance is at a given minute, as the pull towards each edge (−1 to 1): one of eight places that follow one another without ever neighbouring, so that no pixel stays lit. Pure. */
fun glancePlace(epochMinute: Long): Pair<Float, Float> = GLANCE_PLACES[((epochMinute * 3).mod(GLANCE_PLACES.size.toLong())).toInt()]

private val GLANCE_PLACES = listOf(
    -0.9f to -0.8f, 0.9f to 0.2f, -0.6f to 0.8f, 0.7f to -0.6f, -0.9f to 0.0f, 0.5f to 0.9f, 0.9f to -0.9f, -0.4f to -0.2f,
)

/**
 * The glance screen of Windows Phone: black, the time, the date and the small icons of the apps with news, dimmed, in one place that changes
 * every minute (it fades out where it was and in where it goes) so that nothing burns into an OLED panel. A touch leaves it.
 */
@Composable
fun GlanceScreen(info: LockInfo, status: LockStatus, onExit: () -> Unit, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val minute = info.nowMillis / 60_000L
    Box(
        modifier.fillMaxSize().background(Color.Black).testTag("glance:screen").pointerInput(Unit) { detectTapGestures { onExit() } },
    ) {
        AnimatedContent(
            targetState = minute, modifier = Modifier.fillMaxSize(), label = "glance",
            transitionSpec = { fadeIn(tween(700, delayMillis = 400)) togetherWith fadeOut(tween(400)) },
        ) { at ->
            val (x, y) = glancePlace(at)
            val time = Instant.ofEpochMilli(at * 60_000L).atZone(zone)
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                Column(Modifier.align(BiasAlignment(x, y))) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        MetroText(ClockFormat.time(time.toLocalTime(), is24, locale), size = 72.sp, color = DIM, maxLines = 1, modifier = Modifier.testTag("glance:time"))
                        val meridiem = ClockFormat.meridiem(time.toLocalTime(), is24, locale)
                        if (meridiem.isNotEmpty()) MetroText(meridiem, size = 20.sp, color = DIM, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                    }
                    MetroText(ClockFormat.date(time.toLocalDate(), locale), size = 20.sp, color = DIM, maxLines = 1, modifier = Modifier.testTag("glance:date"))
                    if (status.quick.isNotEmpty()) {
                        Row(Modifier.padding(top = 14.dp).testTag("glance:icons"), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            status.quick.forEach { badge ->
                                badge.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(DIM)) }
                                if (badge.count > 0) MetroText(if (badge.count > 99) "99+" else badge.count.toString(), size = 18.sp, color = DIM, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** White, but not much: the glance is a screen that stays on for hours. */
private val DIM = Color.White.copy(alpha = 0.8f)
