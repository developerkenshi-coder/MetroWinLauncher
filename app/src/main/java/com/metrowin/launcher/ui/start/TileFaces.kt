package com.metrowin.launcher.ui.start

import com.metrowin.launcher.notifications.LiveCard
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import com.metrowin.launcher.ui.components.AppIcon
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import com.metrowin.launcher.data.WidgetSizing
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Birthdays
import com.metrowin.launcher.data.Checklist
import com.metrowin.launcher.model.TileData
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.LocalWidgetHost
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.TilePeek
import com.metrowin.launcher.ui.anim.LocalTapFeedback
import com.metrowin.launcher.ui.components.AppArt
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onTile
import android.appwidget.AppWidgetHostView
import android.view.View

/** Whether the tiles show the name of their app (the "names on the tiles" setting). */
val LocalTileNames = androidx.compose.runtime.staticCompositionLocalOf { true }

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
    /** The "one less" button of a counter: called with -1. */
    onCounterStep: (Int) -> Unit = {},
) {
    val count = when (item.live) {
        LiveKind.Phone -> maxOf(live.missedCalls, notifications)
        LiveKind.Mail -> maxOf(live.unreadMail, notifications)
        else -> notifications
    }
    // The Iconic template shows the counter big, in the middle; a small tile has no room for that.
    val iconic = style == TileStyle.Iconic && size != TileSize.Small && item.live != LiveKind.Torch
    // The photos a Cycle tile fades through: the latest of the phone, or those of the album the tile stands for.
    val cyclePhotos = when (item.live) {
        LiveKind.Photos -> live.photos
        LiveKind.Album -> (item.data as? TileData.Album)?.let { live.albums[it.bucket] }.orEmpty()
        else -> emptyList()
    }
    val cycle = style == TileStyle.Cycle && cyclePhotos.isNotEmpty() && size != TileSize.Small
    val collection = style == TileStyle.Collection && cyclePhotos.isNotEmpty() && size != TileSize.Small
    val faces = cards.ifEmpty { peek?.let { listOf(LiveCard.Digest(it.title, it.text)) }.orEmpty() }
    // Whatever the theme, a tile is coloured: its text and glyphs are white. A tile has a size of its own, so the text on it
    // grows with the text size of the phone only as far as the tile has room for it.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalMetroColors provides LocalMetroColors.current.onTile(),
        LocalDensity provides Density(density.density, minOf(density.fontScale, MAX_TILE_FONT_SCALE)),
    ) {
        Box(modifier.fillMaxSize()) {
            val widget = item.launch as? Launch.Widget
            when {
                widget != null -> WidgetFace(item, size, width, height, widget.appWidgetId, interactive, onLongClick)
                item.note != null -> NoteFace(item, size, width, height)
                item.targetDay != 0L -> CountdownFace(item, size, width, height)
                item.data is TileData.Counter -> CounterFace(item, size, width, height, item.data.count, onCounterStep)
                item.data is TileData.Checklist -> ChecklistFace(item, size, width, height, item.data.raw)
                item.data is TileData.WorldClock -> WorldClockFace(item, size, width, height, item.data.zone)
                item.data is TileData.Moon -> MoonFace(item, size, width, height)
                item.data is TileData.Place -> PlaceFace(item, size, width, height, item.data.address)
                item.data is TileData.Timer -> TimerFace(item, size, width, height, item.data.state)
                item.data is TileData.Stopwatch -> StopwatchFace(item, size, width, height, item.data.state)
                item.data is TileData.Reminders -> RemindersFace(item, size, width, height, live.reminders)
                item.data is TileData.Steps -> StepsFace(item, size, width, height, live.extras.steps, LocalTileFeatures.current.stepGoal)
                item.data is TileData.AppsRecap -> AppsFace(item, size, width, height, live.extras.apps, live.extras.usageAllowed, seed)
                item.data is TileData.BatteryGraph -> BatteryGraphFace(item, size, width, height, live.extras.battery)
                item.data is TileData.DataUse -> DataUseFace(item, size, width, height, live.extras.dataUsage, live.extras.usageAllowed)
                item.data is TileData.MemoryUse -> MemoryFace(item, size, width, height, live.extras.memory)
                item.data is TileData.SystemMonitor -> SystemFace(item, size, width, height, seed)
                item.data is TileData.Toggle -> ToggleFace(item, size, width, height, live.extras.toggles[item.data.action])
                item.data is TileData.Sun -> SunFace(item, size, width, height, live.weather)
                item.data is TileData.WeatherPlus -> WeatherPlusFace(item, size, width, height, live.weather)
                item.data is TileData.Camera -> CameraFace(item, size, width, height, live.photos.firstOrNull())
                item.data is TileData.Recorder -> NoteLineFace(item, size, width, height, recordingsLine(live.extras.recordings), "recorder:count")
                item.data is TileData.Wallet -> NoteLineFace(item, size, width, height, cardsLine(live.extras.cards), "wallet:count")
                item.data is TileData.WhereAmI -> NoteLineFace(item, size, width, height, live.extras.place, "where:place")
                item.data is TileData.Parking -> NoteLineFace(item, size, width, height, live.extras.parked?.let { parkedLine(it) }, "parking:line")
                item.data is TileData.AnalogClock -> AnalogClockFace(item, size, width, height)
                iconic -> IconicFace(item, size, width, height, count)
                cycle -> CycleFace(item, cyclePhotos, seed)
                collection -> CollectionFace(item, cyclePhotos, size, seed)
                item.data is TileData.Contact ->
                    LinkFace(item, size, width, height, live.extras.messages[item.id], seed) { PinnedBase(item, size, width, height) }
                item.data is TileData.Site ->
                    LinkFace(item, size, width, height, live.extras.sites[item.id]?.title, seed) { PinnedBase(item, size, width, height) }
                else -> when (item.live) {
                    LiveKind.Calendar -> CalendarFace(item, size, width, height, live.nextEvent, live.events, seed)
                    LiveKind.Photos -> PhotosFace(item, size, width, height, live, seed)
                    LiveKind.People -> PeopleFace(item, size, width, height, live, seed)
                    LiveKind.Torch -> TorchFace(item, size, width, height, live.torchOn)
                    LiveKind.Battery -> BatteryFace(item, size, width, height, live.battery, seed)
                    LiveKind.Weather -> WeatherFace(item, size, width, height, live.weather, seed)
                    LiveKind.Storage -> StorageFace(item, size, width, height, live.storage)
                    LiveKind.Alarm -> AlarmFace(item, size, width, height, live.nextAlarm, seed)
                    LiveKind.Birthdays -> BirthdaysFace(item, size, width, height, live.birthdays, seed)
                    LiveKind.Music -> {
                        // The music that plays in any app, then what is going on in the music app itself.
                        val playing = live.nowPlaying?.let { listOf(LiveCard.Playing(it.title, it.artist, it.art, it.playing)) }.orEmpty()
                        val shown = playing + faces.filter { it !is LiveCard.Playing }
                        if (shown.isNotEmpty() && size != TileSize.Small) {
                            CardsFace(item, size, width, height, shown, seed)
                            if (LocalTileFeatures.current.musicControls && live.nowPlaying != null) {
                                MusicControls(live.nowPlaying.playing, Modifier.align(Alignment.BottomEnd))
                            }
                        } else {
                            StaticFace(item, size, width, height)
                        }
                    }
                    LiveKind.Album -> AlbumFace(item, size, width, height, cyclePhotos, seed)
                    LiveKind.Group -> GroupFace(item, size, width, height, (item.data as? TileData.Group)?.let { live.groups[it.groupId] }.orEmpty(), seed)
                    LiveKind.News -> NewsFace(item, size, width, height, live.news, seed)
                    LiveKind.Finance -> FinanceFace(item, size, width, height, live.finance)
                    LiveKind.Phone, LiveKind.Messages, LiveKind.Mail, null ->
                        if (item.photoPath != null) PhotoShortcutFace(item, size, width, height)
                        else if (faces.isNotEmpty()) CardsFace(item, size, width, height, faces, seed)
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
                modifier = Modifier.padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = 32.dp).testTag("note:text"),
            )
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}



/** A counter: the number in large type, and a small button for one less. A touch on the tile (not here) adds one. */
@Composable
private fun CounterFace(item: AppItem, size: TileSize, width: Dp, height: Dp, count: Int, onStep: (Int) -> Unit) {
    val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.34f else 0.42f).toSp() }
    val ink = LocalMetroColors.current.tileContent
    val feedback by rememberUpdatedState(LocalTapFeedback.current)
    Box(Modifier.fillMaxSize()) {
        MetroText(count.toString(), size = digits, modifier = Modifier.align(Alignment.Center).testTag("counter:count"))
        if (count > 0 && size != TileSize.Small) {
            val description = stringResource(R.string.counter_minus)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(36.dp)
                    .testTag("counter:minus")
                    .border(2.dp, ink.copy(alpha = 0.8f))
                    .semantics { contentDescription = description }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            feedback()
                            onStep(-1)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { MetroText("−", size = 22.sp, weight = FontWeight.Normal) }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** A checklist: how many are done, and the first ones still to do. */
@Composable
private fun ChecklistFace(item: AppItem, size: TileSize, width: Dp, height: Dp, raw: String) {
    val items = remember(raw) { Checklist.parse(raw) }
    if (items.isEmpty()) {
        StaticFace(item, size, width, height)
        return
    }
    val (done, total) = Checklist.progress(items)
    Box(Modifier.fillMaxSize()) {
        if (size == TileSize.Small) {
            MetroText("$done/$total", size = 22.sp, modifier = Modifier.align(Alignment.Center).testTag("checklist:progress"))
        } else {
            Column(Modifier.padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = 30.dp)) {
                MetroText(stringResource(R.string.checklist_progress, done, total), size = 30.sp, modifier = Modifier.testTag("checklist:progress"))
                val pending = Checklist.pending(items, if (size == TileSize.Wide) 3 else 4)
                if (pending.isEmpty()) {
                    MetroText(stringResource(R.string.checklist_all_done), size = 16.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("checklist:alldone"))
                } else {
                    pending.forEach { MetroText("· $it", size = 16.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("checklist:pending")) }
                }
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
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
        modifier = modifier.padding(horizontal = FACE_MARGIN, vertical = 4.dp),
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
    Box(
        Modifier.fillMaxSize().then(
            if (!interactive) Modifier else Modifier.pointerInput(Unit) {
                // The widget's own views take the touches, so the long press is read before them (Initial pass) over the whole tile.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val held = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeout
                                if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeout
                            }
                        }
                        false
                    } catch (_: PointerEventTimeoutCancellationException) {
                        true
                    }
                    if (held) {
                        currentLongClick()
                        // Consumed, so the widget gets a cancel and does not take the lift of the finger for a tap.
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        ),
    ) {
        // A tile smaller than what the widget needs would cut it off: the widget is laid out at that size, and shrunk to fit the tile.
        val density = LocalDensity.current.density
        val info = remember(host, appWidgetId) { host.info(appWidgetId) }
        val neededW = info?.let { WidgetSizing.needed((it.minWidth / density).toInt(), (it.minResizeWidth / density).toInt()) } ?: 0
        val neededH = info?.let { WidgetSizing.needed((it.minHeight / density).toInt(), (it.minResizeHeight / density).toInt()) } ?: 0
        val scale = WidgetSizing.fitScale(width.value, height.value, neededW.toFloat(), neededH.toFloat())
        val layoutW = width.value / scale
        val layoutH = height.value / scale
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context -> host.createView(context, appWidgetId) ?: View(context) },
                modifier = if (scale >= 1f) Modifier.fillMaxSize() else Modifier.requiredSize(layoutW.dp, layoutH.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                update = { view ->
                    // Widgets that adapt their layout to their size need to be told it.
                    (view as? AppWidgetHostView)?.updateAppWidgetSize(null, layoutW.toInt(), layoutH.toInt(), layoutW.toInt(), layoutH.toInt())
                },
            )
        }
        if (!interactive) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    }
}


















/** The text of a tile is never drawn bigger than this times its size: past that it does not fit in a small tile. */
private const val MAX_TILE_FONT_SCALE = 1.3f

/** The room the name of a tile takes along the bottom edge. */
internal val NAME_ROOM = 26.dp

/** The space between the edge of a tile and what is written on it, on every face (the 6 of Windows Phone); the name sits in the bottom left corner of it. */
internal val FACE_MARGIN = 6.dp


/** Glyph or icon in the middle, name in the bottom left corner, like every Windows Phone tile. */
@Composable
internal fun StaticFace(item: AppItem, size: TileSize, width: Dp, height: Dp, label: Boolean = true) {
    // As on Windows Phone, the picture of an app can be the tile: its icon fills the tile (a wide tile has it in the middle), with the name over it.
    if (LocalTileFeatures.current.largeIcons && item.glyph == null) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            // On the colour of the icon (the tile takes it), big, with room around it; the name stays small in the corner.
            AppIcon(item, minOf(width, height) * if (size == TileSize.Small) 0.82f else 0.7f, Modifier.align(Alignment.Center))
            if (label && size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        val art = minOf(width, height) * if (size == TileSize.Small) 0.5f else 0.42f
        AppArt(item, art, Modifier.align(Alignment.Center))
        if (label && size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
internal fun TileLabel(label: String, modifier: Modifier = Modifier, color: Color = LocalMetroColors.current.tileContent) {
    if (!LocalTileNames.current) return
    MetroText(
        text = label,
        size = 13.sp,
        weight = FontWeight.Normal,
        color = color,
        // The name of an app: one line (smaller if it must be, then cut: it may be any length).
        maxLines = 1,
        modifier = modifier.padding(start = FACE_MARGIN, end = FACE_MARGIN, bottom = FACE_MARGIN),
    )
}






