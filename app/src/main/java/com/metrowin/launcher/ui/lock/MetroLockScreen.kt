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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LockStatus
import com.metrowin.launcher.data.LockStatusBuilder
import com.metrowin.launcher.notifications.MediaInfo
import com.metrowin.launcher.notifications.NotificationControl
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.LockInfo
import com.metrowin.launcher.data.LockInfoSource
import com.metrowin.launcher.data.LockText
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.shrinkToHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What the lock screen shows besides the time and the date. */
data class LockOptions(
    val showAlarm: Boolean = true,
    val showNotifications: Boolean = true,
    val showBattery: Boolean = true,
    val showWeather: Boolean = true,
    val showEvent: Boolean = true,
    /** The apps with their counters (and the detailed status, when [LockStatus] has one). */
    val showStatus: Boolean = false,
    /** The music card with its buttons, when something plays. */
    val showMusic: Boolean = false,
)

/** The options the settings give the lock screen. */
fun LauncherSettings.lockOptions() = LockOptions(
    lockShowAlarm, lockShowNotifications, lockShowBattery, lockShowWeather, lockShowEvent,
    showStatus = lockQuickStatus || lockDetailApp.isNotEmpty(), showMusic = lockShowMusic,
)

/**
 * The lock screen's information, read now and again when the minute changes. The notifications follow the hub; [hiddenApps] are
 * the apps whose notifications the user keeps off the lock screen.
 */
@Composable
fun rememberLockInfo(hiddenApps: Set<String> = emptySet()): LockInfo {
    val context = LocalContext.current.applicationContext
    val source = remember { LockInfoSource(context) }
    var info by remember { mutableStateOf(source.read()) }
    LaunchedWhileStarted(source) {
        while (true) {
            // The calendar is a query: it is asked off the main thread.
            info = withContext(Dispatchers.IO) { source.read(withEvent = true) }
            delay(LockText.untilNextMinute(info.nowMillis) + 50)
        }
    }
    val notifications by NotificationHub.entries.collectAsState()
    return info.copy(notifications = if (hiddenApps.isEmpty()) notifications.size else notifications.count { it.packageName !in hiddenApps })
}

/** The quick status and the detailed status of the lock screen, from the notifications that are up and the settings. */
@Composable
fun rememberLockStatus(settings: LauncherSettings): LockStatus {
    val context = LocalContext.current
    val entries by NotificationHub.entries.collectAsState()
    return remember(entries, settings) {
        LockStatusBuilder.build(
            NotificationControl.forLockScreen(entries, settings), settings.lockQuickStatus, settings.lockQuickApps, settings.lockDetailApp,
            settings.liveHideText, context.packageName,
        )
    }
}

/** The apps whose notifications stay off the lock screen. */
fun LauncherSettings.hiddenOnLock(): Set<String> = if (notifControl) notifHideLock else emptySet()

/**
 * The Windows Phone lock screen: a photo, the time big and light at the bottom, the date under it, and the next
 * alarm, the notifications and the battery in small lines. Swiping up (with [onUnlock]) lifts it off the screen and
 * leaves; let go early and it drops back with a bounce.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetroLockScreen(
    info: LockInfo,
    photo: ImageBitmap?,
    modifier: Modifier = Modifier,
    options: LockOptions = LockOptions(),
    onUnlock: (() -> Unit)? = null,
    status: LockStatus = LockStatus(),
    media: MediaInfo? = null,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(1) }
    val time = Instant.ofEpochMilli(info.nowMillis).atZone(zone)

    BoxWithConstraints(
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

        val hasStatus = options.showStatus && (status.quick.isNotEmpty() || status.detail != null)
        val playing = media.takeIf { options.showMusic }
        val clock = @Composable { modifier: Modifier -> LockClock(time, is24Hour, locale, modifier) }
        val date = @Composable { LockDate(time, locale) }
        val lines = @Composable { LockLines(info, options, zone, is24Hour, locale) }
        when {
            // Just the time and what goes with it, at the bottom. Last to be given room: on a short screen the big time gets smaller
            // instead of pushing the lines under it off the screen.
            !hasStatus && playing == null -> Column(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                clock(Modifier.weight(1f, fill = false))
                date()
                Spacer(Modifier.height(18.dp))
                lines()
            }
            // A screen that is wide and short (a phone on its dock): the time on the left, what is going on on the right.
            maxWidth > maxHeight * WIDE_RATIO -> Row(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    Column(Modifier.shrinkToHeight(MIN_FIT), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        clock(Modifier)
                        date()
                        Spacer(Modifier.height(18.dp))
                        lines()
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (playing != null) MusicCard(playing)
                    Spacer(Modifier.weight(1f))
                    if (hasStatus) Box(Modifier.shrinkToHeight(MIN_FIT)) { QuickStatus(status) }
                }
            }
            // A tall screen: the music at the top, and the rest from the bottom, drawn smaller when it does not fit under the music.
            else -> Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = if (playing != null) 64.dp else 16.dp, bottom = 40.dp),
            ) {
                if (playing != null) MusicCard(playing)
                Spacer(Modifier.weight(1f))
                Column(Modifier.shrinkToHeight(MIN_FIT), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    clock(Modifier)
                    date()
                    if (hasStatus) {
                        Spacer(Modifier.height(14.dp))
                        QuickStatus(status)
                    }
                    Spacer(Modifier.height(18.dp))
                    lines()
                }
            }
        }
    }
}

/** The time, big and light, with AM or PM next to it on a 12 hour clock. */
@Composable
private fun LockClock(time: java.time.ZonedDateTime, is24Hour: Boolean, locale: Locale, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        MetroText(
            ClockFormat.time(time.toLocalTime(), is24Hour, locale), size = 104.sp, color = Color.White, maxLines = 1,
            modifier = Modifier.weight(1f, fill = false).testTag("lock:time"),
        )
        val meridiem = ClockFormat.meridiem(time.toLocalTime(), is24Hour, locale)
        if (meridiem.isNotEmpty()) {
            MetroText(meridiem, size = 26.sp, color = Color.White, modifier = Modifier.padding(start = 6.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun LockDate(time: java.time.ZonedDateTime, locale: Locale) {
    MetroText(
        ClockFormat.date(time.toLocalDate(), locale), size = 26.sp, color = Color.White, weight = FontWeight.Normal,
        modifier = Modifier.testTag("lock:date"),
    )
}

/** The alarm, the notifications, the weather and the next event, side by side when the screen is wide and one under the other when it is narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockLines(info: LockInfo, options: LockOptions, zone: ZoneId, is24Hour: Boolean, locale: Locale) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        val alarm = info.nextAlarmMillis
        if (options.showAlarm && alarm != null) {
            LockLine(Glyph.Alarm, LockText.alarm(alarm, info.nowMillis, zone, is24Hour, locale), "lock:alarm")
        }
        if (options.showNotifications && info.notifications > 0) {
            LockLine(Glyph.Bell, info.notifications.toString(), "lock:notifications")
        }
        val weather = info.weather
        if (options.showWeather && weather != null) {
            LockLine(Glyph.Weather, "${weather.temperature}° · ${stringResource(weather.condition.label)}", "lock:weather")
        }
        val event = info.nextEvent
        if (options.showEvent && event != null) {
            LockLine(Glyph.Calendar, LockText.event(event, info.nowMillis, zone, is24Hour, locale), "lock:event")
        }
    }
}

/** The apps with a counter, one after the other, and under them the last notification of the app chosen for the detail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickStatus(status: LockStatus) {
    Column(Modifier.testTag("lock:status"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (status.quick.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                status.quick.forEach { badge ->
                    Row(Modifier.testTag("lock:badge:${badge.packageName}"), verticalAlignment = Alignment.CenterVertically) {
                        PackageIcon(badge.packageName, 38.dp, Modifier.alpha(if (badge.count > 0) 1f else 0.5f))
                        if (badge.count > 0) {
                            MetroText(
                                if (badge.count > 99) "99+" else badge.count.toString(), size = 26.sp, color = Color.White,
                                modifier = Modifier.padding(start = 8.dp).testTag("lock:badge:${badge.packageName}:count"),
                            )
                        }
                    }
                }
            }
        }
        status.detail?.let { line ->
            val context = LocalContext.current
            val label = remember(line.packageName) {
                runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(line.packageName, 0)).toString() }.getOrDefault("")
            }
            Column(Modifier.testTag("lock:detail")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PackageIcon(line.packageName, 20.dp)
                    if (label.isNotEmpty()) MetroText(label, size = 15.sp, color = Color.White.copy(alpha = 0.8f), weight = FontWeight.Normal, modifier = Modifier.padding(start = 8.dp))
                }
                if (line.title.isNotBlank()) MetroText(line.title, size = 22.sp, color = Color.White, maxLines = 1)
                if (line.text.isNotBlank()) MetroText(line.text, size = 17.sp, color = Color.White.copy(alpha = 0.9f), weight = FontWeight.Normal, maxLines = 3)
            }
        }
    }
}

/** The icon of an app, read once (off the main thread) and kept; nothing while it is not there. */
@Composable
private fun PackageIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.size(size)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** The song that plays: its cover, its name and artist, and the buttons that go back, pause or play, and skip. */
@Composable
private fun MusicCard(media: MediaInfo, modifier: Modifier = Modifier) {
    Row(modifier.testTag("lock:music"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            val art = media.art
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().testTag("lock:music:art"))
            } else {
                GlyphIcon(Glyph.Music, 36.dp, color = Color.White)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            MetroText(media.title, size = 20.sp, color = Color.White, maxLines = 2, modifier = Modifier.testTag("lock:music:title"))
            if (media.artist.isNotBlank()) MetroText(media.artist, size = 15.sp, color = Color.White.copy(alpha = 0.85f), weight = FontWeight.Normal, maxLines = 2)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransportButton(Transport.Previous, stringResource(R.string.lock_music_previous), "lock:music:previous") { media.previous() }
                TransportButton(
                    if (media.playing) Transport.Pause else Transport.Play,
                    stringResource(if (media.playing) R.string.lock_music_pause else R.string.lock_music_play),
                    if (media.playing) "lock:music:pause" else "lock:music:play",
                ) { media.playPause() }
                TransportButton(Transport.Next, stringResource(R.string.lock_music_next), "lock:music:next") { media.next() }
            }
        }
    }
}

private enum class Transport { Previous, Play, Pause, Next }

@Composable
private fun TransportButton(kind: Transport, description: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).testTag(tag).semantics { contentDescription = description }.metroPress(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val white = Color.White
            fun triangle(left: Float, right: Float, pointRight: Boolean) {
                val path = Path().apply {
                    if (pointRight) {
                        moveTo(left, 0f); lineTo(right, h / 2f); lineTo(left, h)
                    } else {
                        moveTo(right, 0f); lineTo(left, h / 2f); lineTo(right, h)
                    }
                    close()
                }
                drawPath(path, white)
            }
            when (kind) {
                Transport.Play -> triangle(w * 0.15f, w * 0.95f, true)
                Transport.Pause -> {
                    drawRect(white, Offset(w * 0.12f, 0f), Size(w * 0.28f, h))
                    drawRect(white, Offset(w * 0.6f, 0f), Size(w * 0.28f, h))
                }
                Transport.Next -> {
                    triangle(0f, w * 0.72f, true)
                    drawRect(white, Offset(w * 0.8f, 0f), Size(w * 0.16f, h))
                }
                Transport.Previous -> {
                    drawRect(white, Offset(w * 0.04f, 0f), Size(w * 0.16f, h))
                    triangle(w * 0.28f, w, false)
                }
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

private const val ICON_PX = 96

/** A screen at least this much wider than it is tall puts the music and the status beside the time. */
private const val WIDE_RATIO = 1.2f

/** How small the lines of the lock screen may be drawn when they do not fit: more than that and they would not be read. */
private const val MIN_FIT = 0.6f
private const val UNLOCK_FRACTION = 0.25f
private const val UNLOCK_VELOCITY = 1_500f

