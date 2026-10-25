package com.metrowin.launcher.ui.lock

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.LockInfo
import com.metrowin.launcher.data.LockInfoSource
import com.metrowin.launcher.data.LockText
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What the lock screen shows besides the time and the date. */
data class LockOptions(val showAlarm: Boolean = true, val showNotifications: Boolean = true, val showBattery: Boolean = true)

/** The lock screen's information, read now and again when the minute changes. The notifications follow the hub. */
@Composable
fun rememberLockInfo(): LockInfo {
    val context = LocalContext.current.applicationContext
    val source = remember { LockInfoSource(context) }
    var info by remember { mutableStateOf(source.read()) }
    LaunchedWhileStarted(source) {
        while (true) {
            info = source.read()
            delay(LockText.untilNextMinute(info.nowMillis) + 50)
        }
    }
    val notifications by NotificationHub.entries.collectAsState()
    return info.copy(notifications = notifications.size)
}

/**
 * The Windows Phone lock screen: a photo, the time big and light at the bottom, the date under it, and the next
 * alarm, the notifications and the battery in small lines. Swiping up (with [onUnlock]) lifts it off the screen and
 * leaves; let go early and it drops back with a bounce.
 */
@Composable
fun MetroLockScreen(
    info: LockInfo,
    photo: ImageBitmap?,
    modifier: Modifier = Modifier,
    options: LockOptions = LockOptions(),
    onUnlock: (() -> Unit)? = null,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(1) }
    val time = Instant.ofEpochMilli(info.nowMillis).atZone(zone)

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .testTag("lock:screen")
            // The drag comes before the layer that moves the screen: the finger is followed in the coordinates of the
            // window, not of a screen that is itself moving under it.
            .then(
                if (onUnlock == null) {
                    Modifier
                } else {
                    Modifier.draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> scope.launch { lift.snapTo((lift.value + delta).coerceIn(-height.toFloat(), 0f)) } },
                        onDragStopped = { velocity ->
                            scope.launch {
                                if (lift.value < -height * UNLOCK_FRACTION || velocity < -UNLOCK_VELOCITY) {
                                    lift.animateTo(-height.toFloat(), tween(durationMillis = 220))
                                    onUnlock()
                                } else {
                                    lift.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                }
                            }
                        },
                    )
                },
            )
            .graphicsLayer { translationY = lift.value }
            .background(Color.Black),
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // A shade at the bottom keeps the white time readable on a bright photo.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        }

        val battery = info.battery
        if (options.showBattery && battery != null) {
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(20.dp).testTag("lock:battery"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Battery, 22.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                MetroText("${battery.percent}%", size = 18.sp, color = Color.White)
            }
        }

        Column(
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                MetroText(
                    ClockFormat.time(time.toLocalTime(), is24Hour, locale), size = 104.sp, color = Color.White,
                    modifier = Modifier.testTag("lock:time"),
                )
                val meridiem = ClockFormat.meridiem(time.toLocalTime(), is24Hour, locale)
                if (meridiem.isNotEmpty()) {
                    MetroText(meridiem, size = 26.sp, color = Color.White, modifier = Modifier.padding(start = 6.dp, bottom = 16.dp))
                }
            }
            MetroText(
                ClockFormat.date(time.toLocalDate(), locale), size = 26.sp, color = Color.White, weight = FontWeight.Normal,
                modifier = Modifier.testTag("lock:date"),
            )
            Spacer(Modifier.height(18.dp))
            val alarm = info.nextAlarmMillis
            if (options.showAlarm && alarm != null) {
                LockLine(Glyph.Alarm, LockText.alarm(alarm, info.nowMillis, zone, is24Hour, locale), "lock:alarm")
            }
            if (options.showNotifications && info.notifications > 0) {
                LockLine(Glyph.Bell, info.notifications.toString(), "lock:notifications")
            }
        }
    }
}

@Composable
private fun LockLine(glyph: Glyph, text: String, tag: String) {
    Row(Modifier.padding(top = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(glyph, 24.dp, color = Color.White)
        Spacer(Modifier.width(12.dp))
        MetroText(text, size = 22.sp, color = Color.White)
    }
}

private const val UNLOCK_FRACTION = 0.25f
private const val UNLOCK_VELOCITY = 1_500f

