package com.metrowin.launcher.ui.start

import com.metrowin.launcher.ui.components.DialogFrame
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroToggle

/**
 * The info tile: the time, the next events of the calendar and the latest photos, any of them or all together. With more than one it turns
 * from one to the next, as the other live tiles do. What has nothing to say (no photo read, none allowed) is left out of the turn.
 */
@Composable
internal fun InfoFace(item: AppItem, size: TileSize, width: Dp, height: Dp, config: InfoConfig, live: LiveTileData, seed: Int) {
    val parts = config.parts.filter { it != InfoPart.Photos || live.photos.isNotEmpty() }.ifEmpty { listOf(InfoPart.Time) }
    @Composable
    fun part(which: InfoPart) {
        when (which) {
            InfoPart.Time -> WorldClockFace(item, size, width, height, java.util.TimeZone.getDefault().id)
            InfoPart.Calendar -> CalendarFace(item, size, width, height, live.nextEvent, live.events, seed)
            InfoPart.Photos -> CycleFace(item, live.photos, seed)
        }
    }
    if (parts.size == 1) part(parts.first()) else FlipContainer(parts.size, seed) { face -> part(parts[face % parts.size]) }
}

/** What the info tile shows: a switch for each of the time, the calendar and the photos (one stays on). Every change is kept at once. */
@Composable
fun InfoTileDialog(initial: InfoConfig, onChange: (InfoConfig) -> Unit, onClose: () -> Unit) {
    var config by remember { mutableStateOf(initial) }
    fun toggle(part: InfoPart) {
        config = config.toggled(part)
        onChange(config)
    }
    DialogFrame(onCancel = onClose, title = stringResource(R.string.info_title)) {
        Column(Modifier.fillMaxWidth().testTag("info:dialog")) {
            MetroToggle(stringResource(R.string.info_time), config.time, { toggle(InfoPart.Time) }, Modifier.testTag("info:time"))
            MetroToggle(stringResource(R.string.info_calendar), config.calendar, { toggle(InfoPart.Calendar) }, Modifier.testTag("info:calendar"))
            MetroToggle(stringResource(R.string.info_photos), config.photos, { toggle(InfoPart.Photos) }, Modifier.testTag("info:photos"))
            MetroText(stringResource(R.string.info_note), size = 13.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            MetroButton(stringResource(R.string.info_done), Modifier.fillMaxWidth().testTag("info:done"), onClick = onClose)
        }
    }
}
