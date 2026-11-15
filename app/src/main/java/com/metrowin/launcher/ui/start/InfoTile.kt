package com.metrowin.launcher.ui.start

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.InfoPriority
import com.metrowin.launcher.data.InfoTileState
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.MiniApps
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPace
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/** Whether a face has anything to say now (a face with nothing is left out of the turn). */
private fun InfoPart.hasData(config: InfoConfig, live: LiveTileData): Boolean = when (this) {
    InfoPart.Time, InfoPart.Calendar -> true
    InfoPart.Alarm -> live.nextAlarm > 0L
    InfoPart.Weather -> live.weather != null
    InfoPart.Battery -> live.battery != null
    InfoPart.Photos -> photosOf(config, live).isNotEmpty()
    InfoPart.Memories -> live.memories.isNotEmpty()
}

private fun photosOf(config: InfoConfig, live: LiveTileData) = if (config.album.isEmpty()) live.photos else live.albums[config.album].orEmpty()

/**
 * The info tile: the faces the user chose, turning one after the other (or alone). Smart: what matters now comes first. Saving battery
 * with the switch on: only the time. A touch opens what the face on show is about (see [openInfoPart]).
 */
@Composable
internal fun InfoFace(item: AppItem, size: TileSize, width: Dp, height: Dp, config: InfoConfig, live: LiveTileData, seed: Int) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000L); now = System.currentTimeMillis() } }
    val saving = LocalPowerSaving.current
    val available = config.parts.filter { it.hasData(config, live) }.ifEmpty { listOf(InfoPart.Time) }
    val hour = remember(now) { Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) }
    val shown = when {
        config.saver && saving -> listOf(InfoPart.Time)
        else -> InfoPriority.order(available, config.smart, now, hour, live.nextEvent?.takeIf { !it.allDay }?.startMillis, live.nextAlarm)
    }
    val locale = Locale.getDefault()
    val timeItem = if (config.zone.isEmpty()) item.copy(label = ClockFormat.date(LocalDate.now(), locale)) else item.copy(label = config.zone.substringAfter('/').replace('_', ' '))
    @Composable
    fun face(part: InfoPart) {
        SideEffect { InfoTileState.show(item.id, part) }
        when (part) {
            InfoPart.Time -> WorldClockFace(timeItem, size, width, height, config.zone.ifEmpty { java.util.TimeZone.getDefault().id })
            InfoPart.Calendar -> CalendarFace(item, size, width, height, live.nextEvent, live.events, seed)
            InfoPart.Alarm -> AlarmFace(item, size, width, height, live.nextAlarm, seed)
            InfoPart.Weather -> WeatherFace(item, size, width, height, live.weather, seed)
            InfoPart.Battery -> BatteryFace(item, size, width, height, live.battery, seed)
            InfoPart.Photos -> CycleFace(item, photosOf(config, live), seed)
            InfoPart.Memories -> CycleFace(item, live.memories, seed)
        }
    }
    if (shown.size == 1) face(shown.first()) else FlipContainer(shown.size, seed, resetKey = shown, pace = config.pace.scale) { index -> face(shown[index % shown.size]) }
}

/**
 * What a touch on an info tile opens: the calendar, the alarms, the photos, the battery screen. False for what has no page of its own (the weather):
 * the caller then opens the choice of the tile.
 */
fun openInfoPart(context: Context, part: InfoPart?): Boolean {
    val intent = when (part) {
        InfoPart.Calendar -> MiniApps.activities["calendar"]?.let { Intent(context, it) }
        InfoPart.Time, InfoPart.Alarm -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        InfoPart.Photos, InfoPart.Memories -> MiniApps.activities["photohub"]?.let { Intent(context, it) }
        InfoPart.Battery -> MiniApps.activities["datasense"]?.let { Intent(context, it) }
        InfoPart.Weather, null -> null
    } ?: return false
    return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/**
 * What the info tile shows and how: a switch for each face (one stays on), the smart and battery-saving switches, how fast it turns, the time
 * zone and the album. Every change is kept at once. [onZone] and [onAlbum] open the pickers; the caller keeps what is picked.
 */
@Composable
fun InfoTileDialog(initial: InfoConfig, onChange: (InfoConfig) -> Unit, onZone: () -> Unit, onAlbum: () -> Unit, onClose: () -> Unit) {
    var config by remember(initial) { mutableStateOf(initial) }
    fun change(next: InfoConfig) {
        config = next
        onChange(next)
    }
    val labels = mapOf(
        InfoPart.Time to R.string.info_time, InfoPart.Calendar to R.string.info_calendar, InfoPart.Alarm to R.string.info_alarm,
        InfoPart.Weather to R.string.info_weather, InfoPart.Battery to R.string.info_battery, InfoPart.Photos to R.string.info_photos,
        InfoPart.Memories to R.string.info_memories,
    )
    DialogFrame(onCancel = onClose, title = stringResource(R.string.info_title)) {
        Column(Modifier.fillMaxWidth().testTag("info:dialog")) {
            InfoPart.entries.forEach { part ->
                MetroToggle(stringResource(labels.getValue(part)), part in config.parts, { change(config.toggled(part)) }, Modifier.testTag("info:${part.name.lowercase()}"))
            }
            MetroText(stringResource(R.string.info_smart_title), size = 18.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 10.dp))
            MetroToggle(stringResource(R.string.info_smart), config.smart, { change(config.copy(smart = it)) }, Modifier.testTag("info:smart"))
            MetroToggle(stringResource(R.string.info_saver), config.saver, { change(config.copy(saver = it)) }, Modifier.testTag("info:saver"))
            MetroText(stringResource(R.string.info_pace), size = 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf(InfoPace.Slow to R.string.info_pace_slow, InfoPace.Normal to R.string.info_pace_normal, InfoPace.Fast to R.string.info_pace_fast).forEach { (pace, label) ->
                    val on = pace == config.pace
                    MetroText(
                        stringResource(label), size = 18.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) LocalAccent.current else LocalMetroColors.current.foreground,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("info:pace:${pace.name.lowercase()}").metroPress(onClick = { change(config.copy(pace = pace)) }).padding(vertical = 12.dp),
                    )
                }
            }
            MetroButton(
                stringResource(R.string.info_zone, config.zone.takeIf { it.isNotEmpty() }?.substringAfter('/')?.replace('_', ' ') ?: stringResource(R.string.info_zone_phone)),
                Modifier.fillMaxWidth().padding(top = 6.dp).testTag("info:zone"), onClick = onZone,
            )
            if (config.zone.isNotEmpty()) MetroText(stringResource(R.string.info_zone_reset), size = 14.sp, modifier = Modifier.testTag("info:zone:reset").metroPress(onClick = { change(config.copy(zone = "")) }).padding(vertical = 10.dp))
            MetroButton(
                stringResource(R.string.info_album, config.albumName.ifEmpty { stringResource(R.string.info_album_latest) }),
                Modifier.fillMaxWidth().padding(top = 6.dp).testTag("info:album"), onClick = onAlbum,
            )
            if (config.album.isNotEmpty()) MetroText(stringResource(R.string.info_album_reset), size = 14.sp, modifier = Modifier.testTag("info:album:reset").metroPress(onClick = { change(config.copy(album = "", albumName = "")) }).padding(vertical = 10.dp))
            MetroText(stringResource(R.string.info_note), size = 13.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            MetroButton(stringResource(R.string.info_done), Modifier.fillMaxWidth().testTag("info:done"), onClick = onClose)
        }
    }
}
