package com.metrowin.launcher.ui.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.components.AppArt
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.MetroBlack
import com.metrowin.launcher.ui.theme.MetroWhite
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** What a tile looks like. Static for most apps, a flipping live tile for the ones in [LiveKind]. */
@Composable
fun TileFace(
    item: AppItem,
    size: TileSize,
    width: Dp,
    height: Dp,
    live: LiveTileData,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (item.live) {
            LiveKind.Calendar -> CalendarFace(item, size, width, height, live.nextEvent, seed)
            LiveKind.Photos -> PhotosFace(item, size, width, height, live, seed)
            LiveKind.People -> PeopleFace(item, size, width, height, live, seed)
            LiveKind.Phone -> StaticFace(item, size, width, height, badge = live.missedCalls.takeIf { it > 0 })
            null -> StaticFace(item, size, width, height)
        }
    }
}

/** Glyph or icon in the middle, name in the bottom left corner, like every Windows Phone tile. */
@Composable
private fun StaticFace(item: AppItem, size: TileSize, width: Dp, height: Dp, badge: Int? = null) {
    Box(Modifier.fillMaxSize()) {
        val art = minOf(width, height) * if (size == TileSize.Small) 0.5f else 0.42f
        AppArt(item, art, Modifier.align(Alignment.Center))
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        if (badge != null) {
            MetroText(
                text = badge.toString(),
                size = if (size == TileSize.Small) 16.sp else 30.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TileLabel(label: String, modifier: Modifier = Modifier, color: Color = MetroWhite) {
    MetroText(
        text = label,
        size = 13.sp,
        weight = FontWeight.Normal,
        color = color,
        modifier = modifier.padding(start = 9.dp, end = 9.dp, bottom = 7.dp),
    )
}

@Composable
private fun CalendarFace(item: AppItem, size: TileSize, width: Dp, height: Dp, event: LiveEvent?, seed: Int) {
    val today = rememberToday()
    val faces = if (event != null && size != TileSize.Small) 2 else 1
    FlipContainer(faces, seed) { face ->
        Box(Modifier.fillMaxSize()) {
            if (face == 0 || event == null) {
                val numberSize = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.46f else 0.4f).toSp() }
                Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 2.dp)) {
                    MetroText(today.dayOfMonth.toString(), size = numberSize)
                    if (size != TileSize.Small) {
                        MetroText(
                            today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            size = 15.sp,
                            weight = FontWeight.Normal,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
                if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
            } else {
                Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp, end = 10.dp)) {
                    MetroText(describeWhen(event, today), size = 14.sp, weight = FontWeight.Normal)
                    MetroText(
                        event.title,
                        size = 22.sp,
                        maxLines = 3,
                        lineHeight = 26.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TileLabel(item.label, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
private fun PhotosFace(item: AppItem, size: TileSize, width: Dp, height: Dp, live: LiveTileData, seed: Int) {
    val photos = live.photos
    FlipContainer(if (size == TileSize.Small) 1 else 1 + photos.size, seed) { face ->
        if (face == 0 || photos.isEmpty()) {
            StaticFace(item, size, width, height)
        } else {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = photos[(face - 1) % photos.size],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
private fun PeopleFace(item: AppItem, size: TileSize, width: Dp, height: Dp, live: LiveTileData, seed: Int) {
    val people = live.people
    FlipContainer(if (size == TileSize.Small) 1 else 1 + people.size, seed) { face ->
        if (face == 0 || people.isEmpty()) {
            StaticFace(item, size, width, height)
        } else {
            val person = people[(face - 1) % people.size]
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = person.photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                PhotoLabel(person.name, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/** Name over a photo: a soft shade at the bottom keeps it readable on any picture. */
@Composable
private fun PhotoLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(Brush.verticalGradient(listOf(Color.Transparent, MetroBlack.copy(alpha = 0.55f)))),
    ) {
        TileLabel(text, Modifier.padding(top = 18.dp))
    }
}

/** The current date, kept right across midnight. */
@Composable
private fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            LocalDate.now().let { if (it != today) today = it }
        }
    }
    return today
}

@Composable
private fun describeWhen(event: LiveEvent, today: LocalDate): String {
    val start = Instant.ofEpochMilli(event.startMillis).atZone(ZoneId.systemDefault())
    val day = when (start.toLocalDate()) {
        today -> stringResource(R.string.live_today)
        today.plusDays(1) -> stringResource(R.string.live_tomorrow)
        else -> start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    if (event.allDay) return day
    return "$day ${start.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}"
}
