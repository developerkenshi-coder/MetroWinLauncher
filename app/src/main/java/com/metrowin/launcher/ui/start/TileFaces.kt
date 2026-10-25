package com.metrowin.launcher.ui.start

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import com.metrowin.launcher.notifications.LiveCards
import com.metrowin.launcher.notifications.LiveCard
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.NextAlarm
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.data.Countdown
import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.LocalWidgetHost
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.TilePeek
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import com.metrowin.launcher.ui.components.AppArt
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.toArgbInt
import com.metrowin.launcher.ui.theme.onTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.appwidget.AppWidgetHostView
import android.graphics.BitmapFactory
import android.view.View
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
    style: TileStyle = TileStyle.Standard,
    /** The latest notification of the app, when the tile is allowed to flip to it. */
    peek: TilePeek? = null,
    /** What is going on in the app (conversations, pictures, music, progress), one face each, when the tile may show it. */
    cards: List<LiveCard> = emptyList(),
    /** False while tiles are being edited: a widget then lets the touch through to the tile instead of using it. */
    interactive: Boolean = true,
    /** A long press on a widget (which keeps the touches of its own area) selects the tile for editing. */
    onLongClick: () -> Unit = {},
) {
    val count = if (item.live == LiveKind.Phone) maxOf(live.missedCalls, notifications) else notifications
    // The Iconic template shows the counter big, in the middle; a small tile has no room for that.
    val iconic = style == TileStyle.Iconic && size != TileSize.Small && item.live != LiveKind.Torch
    val cycle = style == TileStyle.Cycle && item.live == LiveKind.Photos && size != TileSize.Small && live.photos.isNotEmpty()
    val faces = cards.ifEmpty { peek?.let { listOf(LiveCard.Digest(it.title, it.text)) }.orEmpty() }
    // Whatever the theme, a tile is coloured: its text and glyphs are white.
    CompositionLocalProvider(LocalMetroColors provides LocalMetroColors.current.onTile()) {
        Box(modifier.fillMaxSize()) {
            val widget = item.launch as? Launch.Widget
            when {
                widget != null -> WidgetFace(item, size, width, height, widget.appWidgetId, interactive, onLongClick)
                item.note != null -> NoteFace(item, size, width, height)
                item.targetDay != 0L -> CountdownFace(item, size, width, height)
                iconic -> IconicFace(item, size, width, height, count)
                cycle -> CycleFace(item, live.photos, seed)
                else -> when (item.live) {
                    LiveKind.Calendar -> CalendarFace(item, size, width, height, live.nextEvent, seed)
                    LiveKind.Photos -> PhotosFace(item, size, width, height, live, seed)
                    LiveKind.People -> PeopleFace(item, size, width, height, live, seed)
                    LiveKind.Torch -> TorchFace(item, size, width, height, live.torchOn)
                    LiveKind.Battery -> BatteryFace(item, size, width, height, live.battery, seed)
                    LiveKind.Weather -> WeatherFace(item, size, width, height, live.weather, seed)
                    LiveKind.Storage -> StorageFace(item, size, width, height, live.storage)
                    LiveKind.Alarm -> AlarmFace(item, size, width, height, live.nextAlarm)
                    LiveKind.Music -> {
                        // The music that plays in any app, then what is going on in the music app itself.
                        val playing = live.nowPlaying?.let { listOf(LiveCard.Playing(it.title, it.artist, it.art, it.playing)) }.orEmpty()
                        val shown = playing + faces.filter { it !is LiveCard.Playing }
                        if (shown.isNotEmpty() && size != TileSize.Small) CardsFace(item, size, width, height, shown, seed)
                        else StaticFace(item, size, width, height)
                    }
                    LiveKind.Phone, LiveKind.Messages, null ->
                        if (item.photoPath != null) PhotoShortcutFace(item, size, width, height)
                        else if (faces.isNotEmpty() && size != TileSize.Small) CardsFace(item, size, width, height, faces, seed)
                        else StaticFace(item, size, width, height)
                }
            }
            if (!iconic) CountBadge(count, size, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** A note: what was written on it, as much as fits. A small tile only has the mark of a note. */
@Composable
private fun NoteFace(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    val text = item.note.orEmpty()
    Box(Modifier.fillMaxSize()) {
        if (size == TileSize.Small || text.isBlank()) {
            AppArt(item, minOf(width, height) * if (size == TileSize.Small) 0.5f else 0.42f, Modifier.align(Alignment.Center))
        } else {
            MetroText(
                text, size = 17.sp, weight = FontWeight.Normal, maxLines = if (size == TileSize.Wide) 4 else 6, lineHeight = 22.sp,
                modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 32.dp).testTag("note:text"),
            )
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The days left to a date in large light type, and what they are to: "62 days to Christmas". It counts up after the date. */
@Composable
private fun CountdownFace(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    var today by remember { mutableStateOf(LocalDate.now()) }
    // Once a day, just after midnight, and only while the launcher is on screen.
    LaunchedWhileStarted(Unit) {
        while (true) {
            val now = java.time.LocalDateTime.now()
            today = now.toLocalDate()
            delay(java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000L)
        }
    }
    val days = Countdown.daysLeft(item.targetDay, today)
    val number = kotlin.math.abs(days).coerceAtMost(99_999L).toInt()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            if (days == 0L) {
                MetroText(stringResource(R.string.cd_today), size = if (size == TileSize.Small) 16.sp else 36.sp, modifier = Modifier.testTag("countdown:days"))
            } else {
                MetroText(number.toString(), size = if (size == TileSize.Small) 26.sp else 56.sp, modifier = Modifier.testTag("countdown:days"))
                if (size != TileSize.Small) {
                    MetroText(
                        pluralStringResource(if (days > 0) R.plurals.cd_to else R.plurals.cd_since, number, number, item.label),
                        size = 15.sp, weight = FontWeight.Normal, maxLines = 2, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        if (days == 0L && size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** What the weather is like now besides the temperature: what is felt, the wind and the humidity. */
@Composable
private fun WeatherDetailsFace(weather: WeatherData) {
    val details = weather.details
    val unit = stringResource(if (weather.fahrenheit) R.string.unit_mph else R.string.unit_kmh)
    val lines = listOfNotNull(
        details.feelsLike?.let { stringResource(R.string.weather_feels_like, it) },
        details.wind?.let { stringResource(R.string.weather_wind_line, it, unit) },
        details.humidity?.let { stringResource(R.string.weather_humidity_line, it) },
        details.rainChance?.let { stringResource(R.string.weather_rain_line, it) },
    ).take(3)
    Column(Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { MetroText(it, size = 19.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("weather:detail")) }
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
            // More than four apps: the fourth square says how many are in the folder beyond the three shown.
            val more = (items.size - 3).takeIf { items.size > 4 }
            val side = minOf(width, height)
            when (size) {
                TileSize.Wide -> Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { shown.forEachIndexed { index, app -> MiniApp(app, side * 0.34f, more.takeIf { index == 3 }) } }
                else -> {
                    val mini = side * if (size == TileSize.Small) 0.34f else 0.3f
                    Column(
                        Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(if (size == TileSize.Small) 3.dp else 6.dp),
                    ) {
                        shown.withIndex().chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(if (size == TileSize.Small) 3.dp else 6.dp)) {
                                pair.forEach { (index, app) -> MiniApp(app, mini, more.takeIf { index == 3 }) }
                            }
                        }
                    }
                }
            }
            if (size != TileSize.Small) {
                TileLabel(folder.name.ifBlank { stringResource(R.string.folder_default_name) }, Modifier.align(Alignment.BottomStart))
            }
            if (expanded) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(LocalMetroColors.current.tileContent))
            CountBadge(notifications, size, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun MiniApp(item: AppItem, side: Dp, more: Int? = null) {
    Box(Modifier.size(side).background(LocalMetroColors.current.tileContent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        if (more != null) {
            MetroText("+$more", size = with(LocalDensity.current) { (side * 0.42f).toSp() }, modifier = Modifier.testTag("folder:more"))
        } else {
            AppArt(item, side * 0.74f)
        }
    }
}

/**
 * Windows Phone's Iconic template: the icon on the left and the counter in large type on the right (no
 * counter, just the icon). The name is in the corner as on every tile.
 */
@Composable
private fun IconicFace(item: AppItem, size: TileSize, width: Dp, height: Dp, count: Int) {
    Box(Modifier.fillMaxSize()) {
        val art = minOf(width, height) * 0.36f
        AppArt(item, art, Modifier.align(Alignment.CenterStart).padding(start = width * 0.12f))
        if (count > 0) {
            val digits = with(LocalDensity.current) { (height * if (size == TileSize.Wide) 0.34f else 0.3f).toSp() }
            MetroText(
                text = if (count > 99) "99+" else count.toString(),
                size = digits,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = width * 0.1f),
            )
        }
        TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** Windows Phone's Cycle template: photos fading into one another, one after the other. */
@Composable
private fun CycleFace(item: AppItem, photos: List<ImageBitmap>, seed: Int) {
    var index by remember { mutableIntStateOf(0) }
    val reduced = LocalReduceMotion.current
    val saving = LocalPowerSaving.current
    LaunchedWhileStarted(photos.size to (reduced || saving)) {
        // With reduced motion, or while saving battery, the tile keeps its photo.
        if (photos.size < 2 || reduced || saving) return@LaunchedWhileStarted
        // Tiles start at different moments, so the whole screen does not change at once.
        delay(1_000L + seed.mod(5) * 700L)
        while (true) {
            delay(CYCLE_MS)
            index = (index + 1) % photos.size
        }
    }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = index % photos.size, animationSpec = tween(durationMillis = 900), label = "cycle") { shown ->
            Image(bitmap = photos[shown], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

private const val CYCLE_MS = 4_500L

/**
 * A widget: the view Android gives for it, filling the tile. Where there is no widget host (previews, tests) or the
 * widget's app was removed, the tile shows the glyph and the name instead.
 */
@Composable
@Suppress("DEPRECATION") // updateAppWidgetSize(Bundle, ...) is the form that works on every version we support
private fun WidgetFace(
    item: AppItem, size: TileSize, width: Dp, height: Dp, appWidgetId: Int, interactive: Boolean, onLongClick: () -> Unit,
) {
    val host = LocalWidgetHost.current
    val available = remember(host, appWidgetId) { host?.info(appWidgetId) != null }
    if (host == null || !available) {
        Box(Modifier.fillMaxSize()) {
            StaticFace(item, size, width, height)
            if (host != null) {
                MetroText(
                    stringResource(R.string.widget_unavailable), size = 12.sp, weight = FontWeight.Normal,
                    color = LocalMetroColors.current.tileContent.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }
        return
    }
    val currentLongClick by rememberUpdatedState(onLongClick)
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                (host.createView(context, appWidgetId) ?: View(context)).also { view ->
                    view.setOnLongClickListener {
                        currentLongClick()
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Widgets that adapt their layout to their size need to be told it.
                (view as? AppWidgetHostView)?.updateAppWidgetSize(null, width.value.toInt(), height.value.toInt(), width.value.toInt(), height.value.toInt())
            },
        )
        if (!interactive) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    }
}

/** The tile of a contact: the person's photo over the whole tile, the name on it. Until the photo is read, the glyph. */
@Composable
private fun PhotoShortcutFace(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    val path = item.photoPath
    val photo by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    val bitmap = photo
    if (bitmap == null) {
        StaticFace(item, size, width, height)
    } else {
        Box(Modifier.fillMaxSize()) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (size != TileSize.Small) PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}

/**
 * The tile of an app with something going on: its usual face, then one face for each thing (a conversation, a picture, the
 * song, a download), turning over one after the other.
 */
@Composable
private fun CardsFace(item: AppItem, size: TileSize, width: Dp, height: Dp, cards: List<LiveCard>, seed: Int) {
    val shown = cards.take(LiveCards.MAX_CARDS)
    FlipContainer(1 + shown.size, seed) { face ->
        if (face == 0) {
            StaticFace(item, size, width, height)
        } else {
            Box(Modifier.fillMaxSize().testTag("card:${shown[face - 1]::class.simpleName}")) {
                when (val card = shown[face - 1]) {
                    is LiveCard.Message -> MessageCard(card, size)
                    is LiveCard.Digest -> DigestCard(card)
                    is LiveCard.Picture -> PictureCard(card)
                    is LiveCard.Progress -> ProgressCard(card, width)
                    is LiveCard.Playing -> PlayingCard(card, size, height)
                }
                TileLabel(item.label, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/** Someone wrote: their picture (when there is one), their name, the chat it came in, and the last line. */
@Composable
private fun MessageCard(card: LiveCard.Message, size: TileSize) {
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize()) {
    if (card.more > 0) {
        MetroText(
            "+${card.more}", size = 15.sp, color = ink.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp).testTag("card:more"),
        )
    }
    Row(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = if (card.more > 0) 38.dp else 10.dp, bottom = 26.dp)) {
        card.avatar?.let { avatar ->
            val side = if (size == TileSize.Wide) 64.dp else 44.dp
            Image(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(side).testTag("card:avatar"))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            MetroText(card.sender, size = 20.sp, maxLines = 1)
            card.conversation?.let { chat ->
                MetroText(chat, size = 13.sp, weight = FontWeight.Normal, maxLines = 1, color = ink.copy(alpha = 0.75f))
            }
            if (card.text.isNotBlank()) {
                MetroText(
                    card.text, size = 15.sp, weight = FontWeight.Normal, maxLines = 3, lineHeight = 19.sp,
                    color = ink.copy(alpha = 0.9f), modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
    }
}

/** A notification said in a title and a line, with the picture of who it is from. A dot says it is going on now. */
@Composable
private fun DigestCard(card: LiveCard.Digest) {
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp, end = 10.dp)) {
            card.avatar?.let { avatar ->
                Image(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).testTag("card:avatar"))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                if (card.title.isNotBlank()) MetroText(card.title, size = 20.sp, maxLines = 2, lineHeight = 24.sp)
                if (card.text.isNotBlank()) {
                    MetroText(
                        card.text, size = 15.sp, weight = FontWeight.Normal, maxLines = 3, lineHeight = 19.sp,
                        color = ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        if (card.live) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(9.dp).background(ink, CircleShape).testTag("card:live"))
    }
}

/** A notification with a picture: the picture fills the tile, with its words in a shade at the bottom. */
@Composable
private fun PictureCard(card: LiveCard.Picture) {
    Box(Modifier.fillMaxSize()) {
        Image(card.picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (card.caption.isNotBlank()) {
            // Its words sit in a shade over the picture, clear of the name of the app at the bottom.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .padding(start = 9.dp, end = 9.dp, top = 22.dp, bottom = 28.dp),
            ) {
                MetroText(card.caption, size = 14.sp, weight = FontWeight.Normal, maxLines = 2, lineHeight = 18.sp, color = Color.White)
            }
        }
    }
}

/** A download, an upload: what it is and how far along, as a bar and a number. */
@Composable
private fun ProgressCard(card: LiveCard.Progress, width: Dp) {
    val ink = LocalMetroColors.current.tileContent
    Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 32.dp)) {
        MetroText(card.title, size = 18.sp, maxLines = 2, lineHeight = 22.sp)
        Spacer(Modifier.weight(1f))
        if (card.percent != null) {
            MetroText("${card.percent}%", size = 34.sp)
            BatteryBar(card.percent, width * 0.7f)
        } else {
            MetroText("…", size = 34.sp, color = ink.copy(alpha = 0.85f))
        }
    }
}

/** The song: its cover (when there is one), its name and its artist, and whether it plays. */
@Composable
private fun PlayingCard(card: LiveCard.Playing, size: TileSize, height: Dp) {
    val ink = LocalMetroColors.current.tileContent
    Row(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 26.dp)) {
        card.art?.let { art ->
            val side = (height * 0.55f).coerceAtMost(if (size == TileSize.Wide) 96.dp else 72.dp)
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(side).testTag("card:art"))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            MetroText(card.title, size = 20.sp, maxLines = 2, lineHeight = 24.sp)
            if (card.artist.isNotBlank()) {
                MetroText(card.artist, size = 15.sp, weight = FontWeight.Normal, maxLines = 1, color = ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 3.dp))
            }
        }
        PlayMark(card.playing, ink)
    }
}

/** A triangle when the music plays, two bars when it is paused. */
@Composable
private fun PlayMark(playing: Boolean, color: Color) {
    Canvas(Modifier.size(18.dp).testTag(if (playing) "card:playing" else "card:paused")) {
        if (playing) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.05f)
                lineTo(size.width * 0.95f, size.height * 0.5f)
                lineTo(size.width * 0.15f, size.height * 0.95f)
                close()
            }
            drawPath(path, color)
        } else {
            drawRect(color, androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.05f), androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.9f))
            drawRect(color, androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.05f), androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.9f))
        }
    }
}

/** The Battery Saver tile: the charge in large type with a bar under it, and on the other face how it is charging. */
@Composable
private fun BatteryFace(item: AppItem, size: TileSize, width: Dp, height: Dp, battery: BatteryInfo?, seed: Int) {
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
                Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp, end = 10.dp)) {
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
                            size = 15.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 4.dp), maxLines = 2,
                        )
                    }
                }
            }
            if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun BatteryBar(percent: Int, width: Dp) {
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.size(width, 14.dp).border(2.dp, ink).padding(2.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).background(ink))
    }
}

/** Storage Sense: what is free on the phone in large type, and a bar with how much of it is used. */
@Composable
private fun StorageFace(item: AppItem, size: TileSize, width: Dp, height: Dp, storage: StorageInfo?) {
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

/** The Alarms tile: when the next alarm rings, as a time in large type and the day under it. No alarm: just the mark. */
@Composable
private fun AlarmFace(item: AppItem, size: TileSize, width: Dp, height: Dp, triggerMillis: Long) {
    val next = remember(triggerMillis) { NextAlarm.describe(triggerMillis, System.currentTimeMillis()) }
    if (next == null) {
        StaticFace(item, size, width, height)
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val locale = Locale.getDefault()
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    Box(Modifier.fillMaxSize()) {
        val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.26f else 0.3f).toSp() }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                MetroText(ClockFormat.time(next.time, is24, locale), size = digits, modifier = Modifier.testTag("alarm:time"))
                val meridiem = ClockFormat.meridiem(next.time, is24, locale)
                if (meridiem.isNotEmpty() && size != TileSize.Small) {
                    MetroText(meridiem, size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                }
            }
            if (size != TileSize.Small) {
                MetroText(
                    when (val day = next.day) {
                        NextAlarm.Day.Today -> stringResource(R.string.live_today)
                        NextAlarm.Day.Tomorrow -> stringResource(R.string.live_tomorrow)
                        is NextAlarm.Day.Weekday -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    },
                    size = 15.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("alarm:day"),
                )
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The weather tile: the temperature in large type with the weather under it, and on the other face the next days. */
@Composable
private fun WeatherFace(item: AppItem, size: TileSize, width: Dp, height: Dp, weather: WeatherData?, seed: Int) {
    if (weather == null) {
        StaticFace(item, size, width, height)
        return
    }
    val withDays = size != TileSize.Small && weather.days.size >= 2
    val withDetails = size != TileSize.Small && !weather.details.isEmpty
    val faces = 1 + (if (withDays) 1 else 0) + (if (withDetails) 1 else 0)
    FlipContainer(faces, seed) { face ->
        Box(Modifier.fillMaxSize()) {
            if (face == 2 || (face == 1 && !withDays)) {
                WeatherDetailsFace(weather)
            } else if (face == 0) {
                val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.46f else 0.38f).toSp() }
                Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 2.dp)) {
                    MetroText("${weather.temperature}°", size = digits)
                    if (size != TileSize.Small) {
                        MetroText(
                            stringResource(weather.condition.label),
                            size = 16.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            } else {
                val shown = weather.days.drop(1).take(if (size == TileSize.Wide) 3 else 2)
                val rows: @Composable (DayForecast) -> Unit = { day ->
                    Column(Modifier.padding(end = 18.dp)) {
                        MetroText(
                            day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f),
                        )
                        MetroText("${day.high}° ${day.low}°", size = 20.sp)
                    }
                }
                if (size == TileSize.Wide) {
                    Row(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 12.dp)) { shown.forEach { rows(it) } }
                } else {
                    Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp)) { shown.forEach { rows(it) } }
                }
            }
            if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** The torch tile: the glyph on the accent colour while the light is off, black on white while it is on. */
@Composable
private fun TorchFace(item: AppItem, size: TileSize, width: Dp, height: Dp, on: Boolean) {
    val light = LocalMetroColors.current.tileContent
    // Lit, the tile is filled with its text color and the text takes the other one.
    val ink = if (on) (if (ColorMath.luminance(light.toArgbInt()) > 0.5f) Color.Black else Color.White) else light
    Box(Modifier.fillMaxSize().then(if (on) Modifier.background(light) else Modifier)) {
        val art = minOf(width, height) * if (size == TileSize.Small) 0.5f else 0.42f
        GlyphIcon(item.glyph ?: Glyph.Flashlight, art, Modifier.align(Alignment.Center), color = ink)
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart), color = ink)
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
private fun TileLabel(label: String, modifier: Modifier = Modifier, color: Color = LocalMetroColors.current.tileContent) {
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
    LaunchedWhileStarted(Unit) {
        while (true) {
            // Coming back after a night off screen, the date is right at once.
            LocalDate.now().let { if (it != today) today = it }
            delay(60_000)
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
