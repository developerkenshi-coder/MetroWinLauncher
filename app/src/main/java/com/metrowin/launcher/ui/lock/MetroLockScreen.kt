package com.metrowin.launcher.ui.lock

import com.metrowin.launcher.ui.components.lockTopPadding
import com.metrowin.launcher.ui.components.rememberTopCutouts
import com.metrowin.launcher.ui.anim.metroTween
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LockIconRow
import com.metrowin.launcher.data.LockStatus
import com.metrowin.launcher.data.QuickBadge
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
import com.metrowin.launcher.calls.elapsed
import com.metrowin.launcher.ui.components.BatteryCell
import com.metrowin.launcher.ui.components.Bolt
import com.metrowin.launcher.ui.components.rememberChargeSweep
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.gestures.ChargeAnnounce
import kotlin.math.PI
import kotlin.math.sin
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
    /** The apps with counters in one smaller row, with a "+N" when they do not all fit. */
    val iconsOneRow: Boolean = false,
    /** The battery fills in sweeps while charging, and a word says so for a moment when the cable goes in. */
    val chargeAnimation: Boolean = true,
)

/** The options the settings give the lock screen. */
fun LauncherSettings.lockOptions() = LockOptions(
    lockShowAlarm, lockShowNotifications, lockShowBattery, lockShowWeather, lockShowEvent,
    showStatus = lockQuickStatus || lockDetailApp.isNotEmpty(), showMusic = lockShowMusic, iconsOneRow = lockIconsOneRow,
    chargeAnimation = chargeAnimation,
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
 * The Windows Phone 8.1 lock screen: the photo full screen, the time big and light at the bottom left with the date under it, then
 * the weather, the alarm and the next event, the last notification told in words, and at the very bottom the row of the apps that
 * have news (their small icon and a counter). Swiping up (with [onUnlock]) lifts it off the screen and leaves; let go early and it
 * drops back with a bounce, and a tap makes it hop, as it did on the phone.
 *
 * The fingerprint is read by Android under this screen. With [sensorY] set (the sensor is under the screen, at that share of the
 * height) a spot is left for the finger: a hole in the picture, ringed, where the icon of Android shows. A change of [unlockSignal]
 * (Android took its lock away by itself, with a finger) lifts the screen off like a swipe does and then calls [onUnlocked].
 */
@Composable
fun MetroLockScreen(
    info: LockInfo,
    photo: ImageBitmap?,
    modifier: Modifier = Modifier,
    options: LockOptions = LockOptions(),
    onUnlock: (() -> Unit)? = null,
    status: LockStatus = LockStatus(),
    media: MediaInfo? = null,
    /** A call that is on: a card with the time, mute and end call, so the call can be handled without leaving the lock screen. */
    call: LockCall? = null,
    /** A fingerprint is saved on the phone and Android takes it now: a line says that it opens it. */
    fingerprint: Boolean = false,
    /** Android asks for the PIN (the fingerprint is not taken now): the line says to swipe up and use it. */
    pin: Boolean = false,
    /** Where the fingerprint sensor is under the screen, as a share of the height from the top; null when it is not under the screen. */
    sensorY: Float? = null,
    /** Draw a fingerprint of ours where the finger goes, over the icon of Android, instead of leaving a hole for it. */
    ownIcon: Boolean = false,
    unlockSignal: Int = 0,
    onUnlocked: () -> Unit = {},
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(1) }
    val hop = with(LocalDensity.current) { HOP.toPx() }
    val time = Instant.ofEpochMilli(info.nowMillis).atZone(zone)
    val currentUnlocked by rememberUpdatedState(onUnlocked)
    val unlockLabel = stringResource(R.string.a11y_unlock)
    LaunchedEffect(unlockSignal) {
        if (unlockSignal > 0) {
            // Android has already let go of its lock: this one leaves faster than a swipe does it, as it is in the way.
            lift.animateTo(-height.toFloat(), metroTween(durationMillis = 130))
            currentUnlocked()
        }
    }

    // What is written on the screen goes up a little ahead of the picture and is gone before the picture is: the picture is what is left to see, and it is the first
    // thing of the launcher that comes out from under it.
    val rise = Modifier.graphicsLayer {
        val done = (-lift.value / height).coerceIn(0f, 1f)
        translationY = lift.value * RISE
        alpha = (1f - done * 1.6f).coerceIn(0f, 1f)
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .testTag("lock:screen")
            .then(if (onUnlock == null) Modifier else Modifier.semantics { contentDescription = unlockLabel; onClick(label = unlockLabel) { scope.launch { lift.animateTo(-height.toFloat(), metroTween(durationMillis = 220)); onUnlock() }; true } })
            // The gestures come before the layer that moves the screen: the finger is followed in the coordinates of the
            // window, not of a screen that is itself moving under it.
            .then(
                if (onUnlock == null) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures {
                                scope.launch {
                                    lift.animateTo(-hop, metroTween(durationMillis = 140))
                                    lift.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                }
                            }
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta -> scope.launch { lift.snapTo((lift.value + delta).coerceIn(-height.toFloat(), 0f)) } },
                            onDragStopped = { velocity ->
                                scope.launch {
                                    if (lift.value < -height * UNLOCK_FRACTION || velocity < -UNLOCK_VELOCITY) {
                                        lift.animateTo(-height.toFloat(), metroTween(durationMillis = 220))
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
            .then(if (sensorY != null) Modifier.sensorSpot(sensorY, ownIcon) else Modifier)
            .background(Color.Black),
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // A shade that grows toward the bottom keeps the white time readable on a bright photo and leaves the top of the picture alone.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))

        // Where the camera is, read from the phone: the battery is kept out from under a hole in the corner, the rest below any hole.
        val holes = rememberTopCutouts()
        val battery = info.battery
        if (options.showBattery && battery != null) {
            ChargeCorner(
                battery, options.chargeAnimation,
                Modifier.align(Alignment.TopEnd).then(rise).lockTopPadding(holes).padding(start = 20.dp, top = 20.dp, bottom = 20.dp, end = 20.dp + holes.right).testTag("lock:battery"),
            )
        }

        val hasStatus = options.showStatus && (status.quick.isNotEmpty() || status.detail != null)
        val playing = media.takeIf { options.showMusic }
        // The room the sensor takes at the bottom: the spot, its ring, and the line under it.
        val spotRadius = maxWidth * SENSOR_R
        val reserve = if (sensorY != null) maxHeight * (1f - sensorY) + spotRadius * 1.3f else 0.dp

        if (onUnlock != null && (fingerprint || pin)) {
            MetroText(
                stringResource(if (pin) R.string.lock_pin_hint else R.string.lock_fingerprint_hint), size = 13.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).then(rise).navigationBarsPadding().padding(bottom = 10.dp).testTag("lock:fingerprint"),
            )
        }
        val clock = @Composable { LockClock(time, is24Hour, locale) }
        val date = @Composable { LockDate(time, locale) }
        val lines = @Composable { LockLines(info, options, zone, is24Hour, locale, countOnly = !hasStatus) }
        if (maxWidth > maxHeight * WIDE_RATIO && (playing != null || hasStatus)) {
            // A screen that is wide and short (a phone on its dock): the time on the left, what is going on on the right.
            Row(
                Modifier.fillMaxSize().then(rise).lockTopPadding(holes).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    Column(Modifier.shrinkToHeight(MIN_FIT), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        clock()
                        date()
                        Spacer(Modifier.height(14.dp))
                        lines()
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (call != null) CallCard(call)
                    if (playing != null) MusicCard(playing)
                    Spacer(Modifier.weight(1f))
                    if (hasStatus) Box(Modifier.shrinkToHeight(MIN_FIT)) { QuickStatus(status, options.iconsOneRow) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().then(rise).lockTopPadding(holes).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = if (playing != null || call != null) 64.dp else 16.dp, bottom = 40.dp + reserve)) {
                if (call != null) CallCard(call)
                if (call != null && playing != null) Spacer(Modifier.height(16.dp))
                if (playing != null) MusicCard(playing)
                Spacer(Modifier.weight(1f))
                // Last to be given room: on a short screen what is written gets smaller instead of being pushed off the screen.
                Column(Modifier.shrinkToHeight(MIN_FIT), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    clock()
                    date()
                    Spacer(Modifier.height(14.dp))
                    lines()
                    if (hasStatus) {
                        Spacer(Modifier.height(14.dp))
                        QuickStatus(status, options.iconsOneRow)
                    }
                }
            }
        }
    }
}

/** The hole where the sensor is, with a ring of ours around it: the icon of Android shows through, and the finger has a place. With [ownIcon], a fingerprint of ours instead of the hole. */
private fun Modifier.sensorSpot(y: Float, ownIcon: Boolean) = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
    drawContent()
    val center = Offset(size.width / 2, size.height * y)
    val radius = size.width * SENSOR_R
    if (ownIcon) {
        // Covers the icon of Android with a fingerprint of ours: three arcs, as a drawn fingertip.
        drawCircle(Color.Black, radius, center)
        for (share in listOf(0.3f, 0.55f, 0.8f)) {
            val r = radius * share
            drawArc(
                Color.White, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(2.dp.toPx()),
            )
        }
    } else {
        drawCircle(Color.Black, radius, center, blendMode = BlendMode.Clear)
    }
    drawCircle(Color.White.copy(alpha = 0.75f), radius * 1.18f, center, style = Stroke(2.dp.toPx()))
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

/**
 * The weather, the alarm and the next event, one under the other. The count of the notifications is a line of its own only when
 * there is no row of icons ([countOnly]) to tell it.
 */
@Composable
private fun LockLines(info: LockInfo, options: LockOptions, zone: ZoneId, is24Hour: Boolean, locale: Locale, countOnly: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val weather = info.weather
        if (options.showWeather && weather != null) {
            // The day on the other face of the line: the high and the low of today.
            val today = weather.days.firstOrNull()
            LockLine(Glyph.Weather, "${weather.temperature}° · ${stringResource(weather.condition.label)}", "lock:weather", 0, today?.let { "↑ ${it.high}°  ↓ ${it.low}°" })
        }
        val alarm = info.nextAlarmMillis
        if (options.showAlarm && alarm != null) {
            LockLine(Glyph.Alarm, LockText.alarm(alarm, info.nowMillis, zone, is24Hour, locale), "lock:alarm", 1)
        }
        val event = info.nextEvent
        if (options.showEvent && event != null) {
            // The event, and how long it is to it.
            val minutes = LockText.minutesTo(event, info.nowMillis)
            LockLine(Glyph.Calendar, LockText.event(event, info.nowMillis, zone, is24Hour, locale), "lock:event", 2, minutes?.let { m ->
                when {
                    m < 1 -> stringResource(R.string.lock_now)
                    m < 60 -> stringResource(R.string.lock_in_minutes, m)
                    else -> stringResource(R.string.lock_in_hours, m / 60)
                }
            })
        }
        if (countOnly && options.showNotifications && info.notifications > 0) {
            LockLine(Glyph.Bell, info.notifications.toString(), "lock:notifications")
        }
    }
}

/** The last notification of the app chosen for the detail, told in words, and under it the apps with news, each with its small icon and a counter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickStatus(status: LockStatus, oneRow: Boolean) {
    Column(Modifier.testTag("lock:status"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        status.detail?.let { line ->
            val context = LocalContext.current
            val label = remember(line.packageName) {
                runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(line.packageName, 0)).toString() }.getOrDefault("")
            }
            Column(Modifier.testTag("lock:detail")) {
                if (label.isNotEmpty()) MetroText(label, size = 15.sp, color = Color.White.copy(alpha = 0.8f), weight = FontWeight.Normal)
                if (line.title.isNotBlank()) MetroText(line.title, size = 22.sp, color = Color.White, maxLines = 1)
                if (line.text.isNotBlank()) MetroText(line.text, size = 17.sp, color = Color.White.copy(alpha = 0.9f), weight = FontWeight.Normal, maxLines = 3)
            }
        }
        if (status.quick.isNotEmpty() && oneRow) {
            // One row, never wider than the screen leaves: the icons shrink to fit and the ones that still do not fit are a "+N".
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fit = LockIconRow.fit(status.quick.map { if (it.count > 0) counter(it.count).length else 0 }, maxWidth.value)
                Row(Modifier.testTag("lock:row"), horizontalArrangement = Arrangement.spacedBy(LockIconRow.GAP_DP.dp), verticalAlignment = Alignment.CenterVertically) {
                    status.quick.take(fit.shown).forEach { QuickBadge(it, fit.iconDp.dp, LockIconRow.textSp(fit.iconDp).sp, Modifier.padding(start = 4.dp)) }
                    if (fit.hidden > 0) {
                        MetroText("+${fit.hidden}", size = LockIconRow.textSp(fit.iconDp).sp, color = Color.White, maxLines = 1, modifier = Modifier.testTag("lock:more"))
                    }
                }
            }
        } else if (status.quick.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                status.quick.forEach { QuickBadge(it, 34.dp, 26.sp, Modifier.padding(start = 8.dp)) }
            }
        }
    }
}

private fun counter(count: Int) = if (count > 99) "99+" else count.toString()

/** An app with news: its small icon and, when it has notifications, how many. */
@Composable
private fun QuickBadge(badge: QuickBadge, iconSize: Dp, textSize: androidx.compose.ui.unit.TextUnit, countPadding: Modifier) {
    Row(Modifier.testTag("lock:badge:${badge.packageName}"), verticalAlignment = Alignment.CenterVertically) {
        val packages = LocalContext.current.packageManager
        // The name of the app, so that the icon says whose news it is.
        val name = remember(badge.packageName) {
            runCatching { packages.getApplicationLabel(packages.getApplicationInfo(badge.packageName, 0)).toString() }.getOrNull()
        }
        val icon = badge.icon
        if (icon != null) {
            // The small icon of a notification is a shape on nothing: drawn white, as the status bar draws it.
            Image(
                icon.asImageBitmap(), contentDescription = name, colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(iconSize).alpha(if (badge.count > 0) 1f else 0.5f).testTag("lock:badge:${badge.packageName}:icon"),
            )
        } else {
            PackageIcon(badge.packageName, iconSize, Modifier.alpha(if (badge.count > 0) 1f else 0.5f), name)
        }
        if (badge.count > 0) {
            MetroText(counter(badge.count), size = textSize, color = Color.White, maxLines = 1, modifier = countPadding.testTag("lock:badge:${badge.packageName}:count"))
        }
    }
}

/** The icon of an app, read once (off the main thread) and kept; nothing while it is not there. */
@Composable
private fun PackageIcon(packageName: String, size: Dp, modifier: Modifier = Modifier, description: String? = null) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.size(size)) {
        icon?.let { Image(it, contentDescription = description, modifier = Modifier.fillMaxSize()) }
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

/** What the lock screen shows of a call that is on, and what it does with it. */
class LockCall(val title: String, val connectedAt: Long, val muted: Boolean, val onMute: () -> Unit, val onHangUp: () -> Unit, val onOpen: () -> Unit)

/** The name and the time of the call (a touch opens the screen of the call), with the buttons to mute it and to end it. */
@Composable
private fun CallCard(call: LockCall, modifier: Modifier = Modifier) {
    Column(modifier.testTag("lock:call"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().testTag("lock:call:open").metroPress(onClick = call.onOpen)) {
            MetroText(call.title.ifEmpty { stringResource(R.string.incall_unknown) }, size = 24.sp, color = Color.White, maxLines = 1)
            MetroText(
                if (call.connectedAt > 0) elapsed(call.connectedAt) else stringResource(R.string.incall_calling), size = 18.sp,
                color = Color.White.copy(alpha = 0.85f), weight = FontWeight.Normal, modifier = Modifier.testTag("lock:call:time"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallChoice(stringResource(R.string.incall_mute), if (call.muted) Color.White else Color.White.copy(alpha = 0.2f), if (call.muted) Color.Black else Color.White, "lock:call:mute", Modifier.weight(1f), call.onMute)
            CallChoice(stringResource(R.string.incall_hang_up), Color(0xFFE51400), Color.White, "lock:call:hangup", Modifier.weight(1f), call.onHangUp)
        }
    }
}

@Composable
private fun CallChoice(text: String, fill: Color, ink: Color, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(52.dp).testTag(tag).metroPress(onClick = onClick).background(fill), contentAlignment = Alignment.Center) {
        MetroText(text, size = 18.sp, color = ink, weight = FontWeight.Normal, maxLines = 1)
    }
}

private enum class Transport { Previous, Play, Pause, Next }

@Composable
private fun TransportButton(kind: Transport, description: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).testTag(tag).semantics { contentDescription = description }.metroPress(onClick = onClick),
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
private fun LockLine(glyph: Glyph, text: String, tag: String, index: Int = 0, detail: String? = null) {
    val reduced = LocalReduceMotion.current
    var other by remember { mutableStateOf(false) }
    // A line with a detail shows it for a moment now and then, the old text sliding up and the new one coming in under it; with reduced motion it stays as it is.
    LaunchedEffect(detail, reduced) {
        if (detail == null || reduced) return@LaunchedEffect
        delay(3_500L + index * 900L)
        while (true) {
            other = true
            delay(2_500L)
            other = false
            delay(5_000L)
        }
    }
    Row(Modifier.featherIn(index, stepMs = 90).padding(top = 4.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(glyph, 24.dp, color = Color.White)
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = if (other && detail != null) detail else text,
            transitionSpec = { (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith (slideOutVertically(tween(240)) { -it / 2 } + fadeOut(tween(200))) },
            label = "lock-line",
        ) { shown -> MetroText(shown, size = 22.sp, color = Color.White) }
    }
}

private const val ICON_PX = 96

/** The radius of the spot for the finger, as a share of the width: what the sensor of a phone with one under the screen takes. */
internal const val SENSOR_R = 0.072f

/** How much further than the finger what is written on the lock screen goes up (the picture goes with the finger). */
private const val RISE = 0.3f

/** How high a tap makes the screen hop. */
private val HOP = 40.dp

/** A screen at least this much wider than it is tall puts the music and the status beside the time. */
private const val WIDE_RATIO = 1.2f

/** How small the lines of the lock screen may be drawn when they do not fit: more than that and they would not be read. */
private const val MIN_FIT = 0.6f
private const val UNLOCK_FRACTION = 0.25f
private const val UNLOCK_VELOCITY = 1_500f

/**
 * The battery in the corner of the lock screen. While charging it fills in sweeps and the bolt pulses (a short sweep now and then, not a loop that
 * runs all the time); a cable plugged in while this is on screen starts the sweep at once and says "charging" for a moment.
 */
@Composable
private fun ChargeCorner(battery: BatteryInfo, animate: Boolean, modifier: Modifier) {
    val plugs by ChargeAnnounce.plugs.collectAsState()
    val firstPlugs = remember { plugs }
    // The battery is read once a minute: a cable plugged in while this is on screen counts at once.
    val charging = battery.state == BatteryState.Charging || plugs != firstPlugs
    val sweep = rememberChargeSweep(charging && animate, plugs)
    val word = remember { Animatable(0f) }
    LaunchedEffect(plugs) {
        if (animate && plugs != firstPlugs) {
            word.animateTo(1f, metroTween(durationMillis = 220))
            delay(ChargeAnnounce.SHOW_MS)
            word.animateTo(0f, metroTween(durationMillis = 400))
        }
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (charging) {
                Bolt(Color.White, Modifier.size(width = 10.dp, height = 16.dp), alpha = { 1f - 0.55f * sin(PI.toFloat() * sweep.value) })
                Spacer(Modifier.width(6.dp))
            }
            BatteryCell(battery, Color.White, Modifier.size(width = 28.dp, height = 15.dp), sweep = { sweep.value })
            Spacer(Modifier.width(8.dp))
            MetroText("${battery.percent}%", size = 18.sp, color = Color.White)
        }
        // Not there at all while it is not said, so it takes no room from what is under the corner.
        val saying by remember { derivedStateOf { word.value > 0f } }
        if (saying) MetroText(
            stringResource(R.string.battery_charging), size = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = word.value },
        )
    }
}
