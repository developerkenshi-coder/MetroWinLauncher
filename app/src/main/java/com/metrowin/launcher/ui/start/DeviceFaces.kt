package com.metrowin.launcher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.toArgbInt
import java.util.Locale




/** The Battery Saver tile: the charge in large type with a bar under it, and on the other face how it is charging. */
@Composable
internal fun BatteryFace(item: AppItem, size: TileSize, width: Dp, height: Dp, battery: BatteryInfo?, seed: Int) {
    if (battery == null) {
        StaticFace(item, size, width, height)
        return
    }
    FlipContainer(if (size == TileSize.Small) 1 else 2, seed) { face ->
        Box(Modifier.fillMaxSize()) {
            if (face == 0) {
                val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.34f else 0.3f).toSp() }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    MetroText("${battery.percent}%", size = digits)
                    if (size != TileSize.Small) BatteryBar(battery.percent, width * 0.46f)
                }
            } else {
                // Above the name of the tile, which sits at the bottom.
                Column(Modifier.align(Alignment.TopStart).padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = 30.dp)) {
                    MetroText(
                        stringResource(
                            when (battery.state) {
                                BatteryState.Charging -> R.string.battery_charging
                                BatteryState.Full -> R.string.battery_full
                                BatteryState.Discharging -> R.string.battery_discharging
                            },
                        ),
                        size = 22.sp,
                    )
                    battery.minutesToFull?.let {
                        MetroText(
                            stringResource(R.string.battery_until_full, BatteryInfo.duration(it)),
                            size = 15.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (LocalTileFeatures.current.batteryEstimate) {
                        battery.minutesLeft?.let {
                            MetroText(
                                stringResource(R.string.battery_time_left, BatteryInfo.duration(it)),
                                size = 15.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 4.dp).testTag("battery:left"),
                            )
                        }
                    }
                }
            }
            if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}


@Composable
internal fun BatteryBar(percent: Int, width: Dp) {
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.size(width, 14.dp).border(2.dp, ink).padding(2.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).background(ink))
    }
}


/** Storage Sense: what is free on the phone in large type, and a bar with how much of it is used. */
@Composable
internal fun StorageFace(item: AppItem, size: TileSize, width: Dp, height: Dp, storage: StorageInfo?) {
    if (storage == null) {
        StaticFace(item, size, width, height)
        return
    }
    val locale = Locale.getDefault()
    Box(Modifier.fillMaxSize()) {
        val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.2f else 0.26f).toSp() }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            MetroText(StorageInfo.format(storage.freeBytes, locale), size = digits, modifier = Modifier.testTag("storage:free"))
            if (size != TileSize.Small) {
                BatteryBar(storage.usedPercent, width * 0.5f)
                MetroText(
                    stringResource(R.string.storage_free_of, StorageInfo.format(storage.totalBytes, locale)),
                    size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}


/** The torch tile: the glyph on the accent colour while the light is off, black on white while it is on. */
@Composable
internal fun TorchFace(item: AppItem, size: TileSize, width: Dp, height: Dp, on: Boolean) {
    val light = LocalMetroColors.current.tileContent
    // Lit, the tile is filled with its text color and the text takes the other one.
    val ink = if (on) (if (ColorMath.luminance(light.toArgbInt()) > 0.5f) Color.Black else Color.White) else light
    Box(Modifier.fillMaxSize().then(if (on) Modifier.background(light) else Modifier)) {
        val art = minOf(width, height) * if (size == TileSize.Small) 0.5f else 0.42f
        GlyphIcon(item.glyph ?: Glyph.Flashlight, art, Modifier.align(Alignment.Center), color = ink)
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart), color = ink)
    }
}
