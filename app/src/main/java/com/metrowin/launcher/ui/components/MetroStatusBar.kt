package com.metrowin.launcher.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Path
import com.metrowin.launcher.data.CellState
import com.metrowin.launcher.data.CellWatcher
import com.metrowin.launcher.data.Ringer
import com.metrowin.launcher.data.StatusIcons
import com.metrowin.launcher.data.StatusItem
import com.metrowin.launcher.data.StatusReading
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.metrowin.launcher.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.notifications.NotificationHub
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * The room the Windows Phone status bar takes at the top, 0 while it is not shown. [topBarsPadding] leaves this much room, so that
 * the pages start under the bar.
 */
val LocalMetroStatusBar = compositionLocalOf { 0.dp }

/** How high the bar is: as the status bar of Android would be, or the camera hole if that is lower. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun metroStatusBarHeight(): Dp {
    val density = LocalDensity.current
    val top = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).getTop(density)
    return with(density) { top.toDp() }.coerceAtLeast(MIN_BAR)
}

private val MIN_BAR = 24.dp

/**
 * The last broadcast of any of [actions] (null before the first), received while this is composed: a change is on the bar as the phone says
 * it, not at the next poll. Each broadcast is a new value, so what is keyed on it starts again. A sticky one (the battery) answers at once.
 */
@Composable
fun rememberBroadcast(vararg actions: String): Intent? {
    val context = LocalContext.current
    var last by remember { mutableStateOf<Intent?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                last = intent
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply { actions.forEach(::addAction) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return last
}

/** What changes the icons of the toggles and the Wi-Fi, so they follow the phone at once; the poll below stays as a net for what sends nothing. */
private val RADIO_ACTIONS = arrayOf(
    WifiManager.RSSI_CHANGED_ACTION, WifiManager.NETWORK_STATE_CHANGED_ACTION, WifiManager.WIFI_STATE_CHANGED_ACTION,
    Intent.ACTION_AIRPLANE_MODE_CHANGED, BluetoothAdapter.ACTION_STATE_CHANGED, AudioManager.RINGER_MODE_CHANGED_ACTION,
    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED, PowerManager.ACTION_POWER_SAVE_MODE_CHANGED, LocationManager.MODE_CHANGED_ACTION,
    AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED, @Suppress("DEPRECATION") ConnectivityManager.CONNECTIVITY_ACTION,
)

/**
 * The status bar of Windows Phone: the notifications on the left, and on the right what the phone is doing (airplane mode, hotspot, VPN,
 * location, Bluetooth, alarm, silent, do not disturb, battery saver), the mobile signal with the kind of network (5G, 4G, 3G, E), the Wi-Fi,
 * the battery with its percentage and the time, thin and white over the page. [hidden] are the ids of [StatusItem] the user turned off.
 * What it shows is what the phone says now: the signal comes from the phone as it changes, the rest is read every few seconds.
 */
@Composable
fun MetroStatusBar(
    battery: BatteryInfo?,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    /** Where the bar is the window of a service, over other apps, the launcher's insets say nothing: it is told how high it is, and it has a background to cover the bar of Android. */
    height: Dp = metroStatusBarHeight(),
    background: Color = Color.Transparent,
    hidden: Set<String> = emptySet(),
    /** What a screen reader's "activate" does on the bar: the pull down from the top edge has no other way for someone who cannot swipe. */
    onOpenActionCenter: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // The polls run only while the bar is on screen (started): with the screen off, as over the lock screen, they stop and the phone can sleep; back, they read at once.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val radioChange = rememberBroadcast(*RADIO_ACTIONS)
    val reading by produceState(StatusReading(), radioChange) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = withContext(Dispatchers.IO) { StatusIcons.read(context) }
                delay(RADIO_REFRESH_MS)
            }
        }
    }
    val cell by produceState(CellState()) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val watcher = CellWatcher(context) { value = it }
            watcher.start()
            try {
                while (true) {
                    watcher.poll()
                    delay(RADIO_REFRESH_MS)
                }
            } finally {
                watcher.stop()
            }
        }
    }
    // The minute is also told by the phone: a pause in the coroutine's clock (the phone asleep) or a change of the time or the zone does not leave it wrong.
    val clockChange = rememberBroadcast(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    val clock by produceState(LocalTime.now().truncatedTo(ChronoUnit.MINUTES), clockChange) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Every second, and the state only changes with the minute: a coroutine that wakes early or late, a clock that jumps (network time)
            // or a minute broadcast that is late never leave it wrong for more than a second.
            while (true) {
                value = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                delay(1_000L - System.currentTimeMillis() % 1_000L)
            }
        }
    }
    val notifications by NotificationHub.entries.collectAsState()
    val is24 = DateFormat.is24HourFormat(context)
    fun on(item: StatusItem) = item.id !in hidden
    val holes = rememberTopCutouts(height)
    val notificationsPart: @Composable () -> Unit = {
        if (on(StatusItem.Notifications) && notifications.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Bell, 14.dp, color = color)
                Spacer(Modifier.width(3.dp))
                val said = stringResource(R.string.a11y_sb_notifications, notifications.size)
                MetroText(notifications.size.toString(), size = 12.sp, color = color, maxLines = 1, modifier = Modifier.testTag("statusbar:notifications").semantics { contentDescription = said })
            }
        }
    }
    val togglesPart: @Composable () -> Unit = {
        if (on(StatusItem.Airplane) && reading.airplane) Said(R.string.a11y_sb_airplane) { GlyphIcon(Glyph.Airplane, 13.dp, color = color) }
        if (on(StatusItem.Hotspot) && reading.hotspot) Said(R.string.a11y_sb_hotspot) { GlyphIcon(Glyph.Hotspot, 13.dp, color = color) }
        if (on(StatusItem.Vpn) && reading.vpn) Said(R.string.a11y_sb_vpn) { GlyphIcon(Glyph.Vpn, 12.dp, color = color) }
        if (on(StatusItem.Location) && reading.location) Said(R.string.a11y_sb_location) { PinIcon(color) }
        if (on(StatusItem.Bluetooth) && reading.bluetooth) Said(R.string.a11y_sb_bluetooth) { GlyphIcon(Glyph.Bluetooth, 12.dp, color = color) }
        if (on(StatusItem.Alarm) && reading.alarm) Said(R.string.a11y_sb_alarm) { GlyphIcon(Glyph.Alarm, 13.dp, color = color) }
        if (on(StatusItem.Sound) && reading.ringer != Ringer.Normal) {
            Said(if (reading.ringer == Ringer.Vibrate) R.string.ringer_vibrate else R.string.ringer_silent) { SoundIcon(reading.ringer == Ringer.Vibrate, color) }
        }
        if (on(StatusItem.Dnd) && reading.dnd) Said(R.string.a11y_sb_dnd) { GlyphIcon(Glyph.Moon, 12.dp, color = color) }
    }
    val radiosPart: @Composable () -> Unit = {
        if (on(StatusItem.Signal) && !reading.airplane && cell.level >= 0) Said(R.string.a11y_sb_signal, cell.level) { Bars(cell.level, 4, color) }
        if (on(StatusItem.Network) && !reading.airplane && cell.level >= 0) cell.label?.let {
            MetroText(it, size = 12.sp, color = color, maxLines = 1, modifier = Modifier.testTag("statusbar:network"))
        }
        if (on(StatusItem.Wifi)) reading.wifi?.let { Said(R.string.a11y_sb_wifi) { WifiFan(it, color) } }
    }
    val batteryPart: @Composable () -> Unit = {
        if (on(StatusItem.Saver) && reading.saver) Said(R.string.a11y_sb_saver) { SaverPlus(color) }
        if (on(StatusItem.BatteryPercent)) battery?.let {
            val said = stringResource(R.string.a11y_sb_battery, it.percent)
            MetroText("${it.percent}%", size = 12.sp, color = color, maxLines = 1, modifier = Modifier.testTag("statusbar:battery").semantics { contentDescription = said })
        }
        if (on(StatusItem.Battery)) battery?.let {
            if (it.state == BatteryState.Charging) Said(R.string.a11y_sb_charging) { Bolt(color) }
            BatteryCell(it, color)
        }
        if (on(StatusItem.Time)) MetroText(
            ClockFormat.time(clock, is24, java.util.Locale.getDefault()), size = 13.sp, color = color, maxLines = 1,
            modifier = Modifier.testTag("statusbar:time"),
        )
    }
    val gap = Arrangement.spacedBy(7.dp)
    val openLabel = stringResource(R.string.gact_action_center)
    Row(
        modifier.fillMaxWidth().background(background).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).height(height)
            .padding(start = 10.dp + holes.left, end = 10.dp + holes.right)
            .testTag("statusbar")
            .then(if (onOpenActionCenter == null) Modifier else Modifier.semantics { onClick(label = openLabel) { onOpenActionCenter(); true } }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (holes.hasCenter) {
            // A camera in the middle: what is on the left stays on its left, the rest on its right, and none of it is under the glass.
            val room = (holes.centerStart!! - 10.dp - holes.left).coerceAtLeast(0.dp)
            Row(Modifier.width(room), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = gap) {
                notificationsPart(); togglesPart(); radiosPart()
            }
            Spacer(Modifier.width(holes.centerEnd!! - holes.centerStart))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End)) {
                batteryPart()
            }
        } else {
            notificationsPart()
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = gap) {
                togglesPart(); radiosPart(); batteryPart()
            }
        }
    }
}

private const val RADIO_REFRESH_MS = 4_000L

/** An icon with no text of its own, named for a screen reader. */
@Composable
private fun Said(@StringRes text: Int, vararg args: Any, content: @Composable () -> Unit) {
    val said = stringResource(text, *args)
    Box(Modifier.semantics { contentDescription = said }) { content() }
}

/** Rising bars, [level] of [count] of them filled and the rest faint, as the signal of Windows Phone. */
@Composable
private fun Bars(level: Int, count: Int, color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
        val bar = size.width / (count * 2 - 1)
        for (i in 0 until count) {
            val h = size.height * (i + 1) / count
            drawRect(
                color = if (i < level.coerceAtMost(count)) color else color.copy(alpha = 0.3f),
                topLeft = Offset(i * 2 * bar, size.height - h), size = Size(bar, h),
            )
        }
    }
}

/** The Wi-Fi: four arcs and a dot, as many of the arcs lit as the signal has bars (0 to 4, as the bar of Android counts them). */
@Composable
private fun WifiFan(level: Int, color: Color) {
    Canvas(Modifier.size(width = 15.dp, height = 12.dp)) {
        val stroke = 1.4.dp.toPx()
        val base = Offset(size.width / 2, size.height)
        drawCircle(color, radius = stroke, center = base)
        for (arc in 1..4) {
            val r = size.height * arc / 4f
            drawArc(
                color = if (arc <= level) color else color.copy(alpha = 0.3f),
                startAngle = 225f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(base.x - r, base.y - r), size = Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
        }
    }
}

/** The place pin of the location: a drop in a thin line with a dot in it. */
@Composable
private fun PinIcon(color: Color) {
    Canvas(Modifier.size(width = 10.dp, height = 13.dp)) {
        val stroke = 1.3.dp.toPx()
        val r = size.width * 0.4f
        val centre = Offset(size.width / 2, r + stroke)
        val drop = Path().apply {
            moveTo(size.width / 2, size.height)
            lineTo(centre.x - r * 0.9f, centre.y + r * 0.55f)
            arcTo(androidx.compose.ui.geometry.Rect(centre.x - r, centre.y - r, centre.x + r, centre.y + r), 150f, 240f, false)
            lineTo(size.width / 2, size.height)
        }
        drawPath(drop, color, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(color, radius = stroke, center = centre)
    }
}

/** Silent: a bell struck through; vibrate: a phone with a line on each side. */
@Composable
private fun SoundIcon(vibrate: Boolean, color: Color) {
    if (!vibrate) {
        Box(Modifier.size(13.dp), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Bell, 12.dp, color = color)
            Canvas(Modifier.fillMaxSize()) {
                drawLine(color, Offset(size.width * 0.1f, size.height * 0.9f), Offset(size.width * 0.9f, size.height * 0.1f), 1.4.dp.toPx())
            }
        }
    } else {
        Canvas(Modifier.size(width = 13.dp, height = 13.dp)) {
            val stroke = 1.2.dp.toPx()
            drawRoundRect(color, Offset(size.width * 0.3f, size.height * 0.08f), Size(size.width * 0.4f, size.height * 0.84f), CornerRadius(1.5.dp.toPx()), Stroke(stroke))
            drawLine(color, Offset(size.width * 0.12f, size.height * 0.3f), Offset(size.width * 0.12f, size.height * 0.7f), stroke)
            drawLine(color, Offset(size.width * 0.88f, size.height * 0.3f), Offset(size.width * 0.88f, size.height * 0.7f), stroke)
        }
    }
}

/** A plus: the battery saver is on. */
@Composable
private fun SaverPlus(color: Color) {
    Canvas(Modifier.size(9.dp)) {
        val stroke = 1.4.dp.toPx()
        drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke)
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
    }
}

/** A small lightning bolt: the battery is charging. */
@Composable
private fun Bolt(color: Color) {
    Canvas(Modifier.size(width = 7.dp, height = 11.dp)) {
        val w = size.width
        val h = size.height
        val bolt = Path().apply {
            moveTo(w * 0.65f, 0f)
            lineTo(w * 0.05f, h * 0.58f)
            lineTo(w * 0.45f, h * 0.58f)
            lineTo(w * 0.35f, h)
            lineTo(w * 0.95f, h * 0.38f)
            lineTo(w * 0.55f, h * 0.38f)
            close()
        }
        drawPath(bolt, color)
    }
}

/** The battery lying down with its nub; filled to the charge, red when it is low. */
@Composable
internal fun BatteryCell(battery: BatteryInfo, color: Color, modifier: Modifier = Modifier.size(width = 20.dp, height = 11.dp)) {
    Canvas(modifier) {
        val stroke = 1.2.dp.toPx()
        val nub = 2.dp.toPx()
        val body = size.width - nub
        drawRoundRect(color, size = Size(body, size.height), cornerRadius = CornerRadius(1.dp.toPx()), style = Stroke(stroke))
        drawRect(color, topLeft = Offset(body, size.height * 0.3f), size = Size(nub, size.height * 0.4f))
        val inner = body - stroke * 4
        val fill = inner * battery.percent / 100f
        val low = battery.percent <= 15 && battery.state == BatteryState.Discharging
        drawRect(
            if (low) Color(0xFFE51400) else color,
            topLeft = Offset(stroke * 2, stroke * 2), size = Size(fill, size.height - stroke * 4),
        )
    }
}
