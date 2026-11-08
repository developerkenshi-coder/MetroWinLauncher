package com.metrowin.launcher.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.SystemStats
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import com.metrowin.launcher.ui.components.MetroText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private const val REFRESH_MS = 5_000L

/**
 * The System tile: the power the battery moves and how hot it is on one face, the memory and the signal on the other (the tile turns over, as the
 * live tiles do). It reads for itself while the launcher is on screen and stands still while the battery is spared.
 */
@Composable
internal fun SystemFace(item: AppItem, size: TileSize, width: Dp, height: Dp, seed: Int) {
    val context = LocalContext.current
    val saving = LocalPowerSaving.current
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    LaunchedWhileStarted(saving) {
        while (true) {
            stats = withContext(Dispatchers.IO) { SystemStats.read(context) }
            if (saving) return@LaunchedWhileStarted
            delay(REFRESH_MS)
        }
    }
    val s = stats
    if (s == null) {
        StaticFace(item, size, width, height)
        return
    }
    val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.26f else 0.3f).toSp() }
    FlipContainer(if (size == TileSize.Small) 1 else 2, seed) { face ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (face == 0) {
                    MetroText(s.watts?.let { String.format(Locale.getDefault(), "%.1f W", it) } ?: "—", size = digits, maxLines = 1, modifier = Modifier.testTag("system:watts"))
                    if (size != TileSize.Small) {
                        MetroText(
                            listOfNotNull(
                                stringResource(if (s.charging) R.string.tile_sys_charging else R.string.tile_sys_battery),
                                s.tempC?.let { String.format(Locale.getDefault(), "%.1f °C", it) },
                            ).joinToString(" · "),
                            size = 14.sp, weight = FontWeight.Normal,
                        )
                    }
                } else {
                    MetroText("${s.ramPercent} %", size = digits, maxLines = 1, modifier = Modifier.testTag("system:ram"))
                    BatteryBar(s.ramPercent, width * 0.5f)
                    MetroText(
                        listOfNotNull(s.wifiDbm?.let { "Wi-Fi $it dBm" }, s.mobileDbm?.let { "${stringResource(R.string.tile_sys_mobile)} $it dBm" }).joinToString(" · ").ifEmpty { stringResource(R.string.tile_sys_ram) },
                        size = 13.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}
