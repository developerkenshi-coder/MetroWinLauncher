package com.metrowin.launcher.ui.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.components.AppArt
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.TileWhite
import com.metrowin.launcher.ui.theme.onTile
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * What a tile looks like. Static for most apps, a flipping live tile for the ones in [LiveKind].
 * The counter in the corner does not flip with the face: it is the number of missed calls on the
 * Phone tile, and the number of notifications of the app on any tile.
 */
@Composable
fun TileFace(
    item: AppItem,
    size: TileSize,
    width: Dp,
    height: Dp,
    live: LiveTileData,
    seed: Int,
    notifications: Int,
    modifier: Modifier = Modifier,
) {
    val count = if (item.live == LiveKind.Phone) maxOf(live.missedCalls, notifications) else notifications
    // Whatever the theme, a tile is coloured: its text and glyphs are white.
    CompositionLocalProvider(LocalMetroColors provides LocalMetroColors.current.onTile()) {
        Box(modifier.fillMaxSize()) {
            when (item.live) {
                LiveKind.Calendar -> CalendarFace(item, size, width, height, live.nextEvent, seed)
                LiveKind.Photos -> PhotosFace(item, size, width, height, live, seed)
                LiveKind.People -> PeopleFace(item, size, width, height, live, seed)
                LiveKind.Phone, null -> StaticFace(item, size, width, height)
            }
            CountBadge(count, size, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The number in the top right corner of a tile. */
@Composable
fun CountBadge(count: Int, size: TileSize, modifier: Modifier = Modifier) {
    if (count <= 0) return
    MetroText(
        text = if (count > 99) "99+" else count.toString(),
        size = if (size == TileSize.Small) 16.sp else 30.sp,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * A folder: the first apps it holds as small squares, its name in the corner. An open folder has a
 * bar along the bottom, and the apps it holds follow it on Start.
 */
@Composable
fun FolderFace(
    folder: Folder,
    items: List<AppItem>,
    expanded: Boolean,
    size: TileSize,
    width: Dp,
    height: Dp,
    notifications: Int,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalMetroColors provides LocalMetroColors.current.onTile()) {
        Box(modifier.fillMaxSize()) {
            val shown = items.take(4)
            val side = minOf(width, height)
            when (size) {
                TileSize.Wide -> Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { shown.forEach { MiniApp(it, side * 0.34f) } }
                else -> {
                    val mini = side * if (size == TileSize.Small) 0.34f else 0.3f
                    Column(
                        Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(if (size == TileSize.Small) 3.dp else 6.dp),
                    ) {
                        shown.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(if (size == TileSize.Small) 3.dp else 6.dp)) {
                                pair.forEach { MiniApp(it, mini) }
                            }
                        }
                    }
                }
            }
            if (size != TileSize.Small) {
                TileLabel(folder.name.ifBlank { stringResource(R.string.folder_default_name) }, Modifier.align(Alignment.BottomStart))
            }
            if (expanded) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(TileWhite))
            CountBadge(notifications, size, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun MiniApp(item: AppItem, side: Dp) {
    Box(Modifier.size(side).background(TileWhite.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        AppArt(item, side * 0.74f)
    }
}

/** Glyph or icon in the middle, name in the bottom left corner, like every Windows Phone tile. */
@Composable
private fun StaticFace(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    Box(Modifier.fillMaxSize()) {
        val art = minOf(width, height) * if (size == TileSize.Small) 0.5f else 0.42f
        AppArt(item, art, Modifier.align(Alignment.Center))
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun TileLabel(label: String, modifier: Modifier = Modifier, color: Color = TileWhite) {
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
        modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
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
