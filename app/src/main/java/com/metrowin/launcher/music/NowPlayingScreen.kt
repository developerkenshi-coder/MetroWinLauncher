package com.metrowin.launcher.music

import com.metrowin.launcher.ui.components.PageSwitch
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.data.DominantColor
import com.metrowin.launcher.notifications.MediaInfo
import com.metrowin.launcher.notifications.MediaSessions
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.centeredContent
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.start.Transport
import com.metrowin.launcher.ui.start.TransportButton
import com.metrowin.launcher.ui.theme.DarkColors
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.navigationBarsPadding
import kotlin.math.abs
import kotlin.math.min

/** The cover of a song as the player gave it, whole, with the colour it is about worked out once and only when asked. */
class Cover(val bitmap: Bitmap) {
    val image: ImageBitmap by lazy { bitmap.asImageBitmap() }
    val accent: Color? by lazy {
        runCatching {
            val small = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
            val pixels = IntArray(24 * 24).also { small.getPixels(it, 0, 24, 0, 0, 24, 24) }
            DominantColor.of(pixels)?.let(::Color)
        }.getOrNull()
    }
}

// The last covers: a player that sends the song first and the picture after (or drops the picture for a moment) does not make it flicker.
private val covers = LruCache<String, Cover>(3)

internal fun coverOf(media: MediaInfo, meta: MediaMetadata?): Cover? {
    val key = "${media.packageName}|${media.title}|${media.artist}"
    val bitmap = runCatching {
        meta?.let { it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) }
    }.getOrNull()
    val kept = covers.get(key)
    if (bitmap == null) return kept
    return kept?.takeIf { it.bitmap === bitmap } ?: Cover(bitmap).also { covers.put(key, it) }
}

/** What the player says now, read once for each change it makes (not at every redraw); a session that has closed gives nothing instead of an error. */
private class Snap(controller: MediaController) {
    val state: PlaybackState? = runCatching { controller.playbackState }.getOrNull()
    val meta: MediaMetadata? = runCatching { controller.metadata }.getOrNull()
    val queue = runCatching { controller.queue }.getOrNull().orEmpty()
    val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val abilities = Abilities(state?.actions ?: 0L)
}

/** The media sessions of the phone, and a count that moves at every change any of them reports. */
private class Sessions(val list: List<MediaController>, val version: Int)

@Composable
private fun rememberSessions(): Sessions {
    val context = LocalContext.current.applicationContext
    var sessions by remember { mutableStateOf(Sessions(emptyList(), 0)) }
    LifecycleStartEffect(Unit) {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = MediaSessions.component(context)
        var watched = emptyList<MediaController>()
        fun bump() { sessions = Sessions(watched, sessions.version + 1) }
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = bump()
            override fun onMetadataChanged(metadata: MediaMetadata?) = bump()
            override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = bump()
            override fun onSessionDestroyed() = bump()
        }
        fun watch(list: List<MediaController>?) {
            watched.forEach { runCatching { it.unregisterCallback(callback) } }
            watched = list.orEmpty().onEach { runCatching { it.registerCallback(callback) } }
            bump()
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        runCatching { manager?.addOnActiveSessionsChangedListener(listener, component) }
        watch(runCatching { manager?.getActiveSessions(component) }.getOrNull())
        onStopOrDispose {
            runCatching { manager?.removeOnActiveSessionsChangedListener(listener) }
            watched.forEach { runCatching { it.unregisterCallback(callback) } }
            watched = emptyList()
        }
    }
    return sessions
}

private fun hasAccess(context: Context) = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun appLabel(context: Context, pkg: String) =
    runCatching { context.packageManager.let { it.getApplicationLabel(it.getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(pkg)

private fun openSource(context: Context, media: MediaInfo) {
    val opened = runCatching { media.controller.sessionActivity?.send() }.map { it != null }.getOrDefault(false)
    if (!opened) runCatching { context.packageManager.getLaunchIntentForPackage(media.packageName)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/** The colours of the page: the cover's own colours go over the whole page, so the text is the light one there. */
private class Look(
    val ink: Color,
    val faint: Color,
    val accent: Color,
    val pageCover: Boolean,
    val wide: Boolean,
    val maxW: Dp,
    val maxH: Dp,
    val opts: NpOptions,
)

/**
 * Now Playing: the cover of the song across the whole page (darker toward the text, which is laid over it), the title and the artist
 * large, a bar of where the song is (a touch or a drag seeks), the transport buttons, and the queue of the player with the song that
 * plays marked. Needs the notification access, which the launcher asks for anyway; without it, or without music, it says so. The
 * options of the app (menu "…") are kept in preferences of its own.
 */
@Composable
fun NowPlayingScreen() {
    val context = LocalContext.current
    val store = remember { NpStore(context) }
    var opts by remember { mutableStateOf(store.load()) }
    fun change(o: NpOptions) { opts = o; store.save(o) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    var sleepMin by rememberSaveable { mutableIntStateOf(0) }
    val sessions = rememberSessions()
    val media = remember(sessions, pick) {
        val chosen = sessions.list.firstOrNull { it.packageName == pick }
        listOfNotNull(chosen, MediaSessions.best(sessions.list)).firstNotNullOfOrNull(MediaSessions::info)
    }
    val snap = remember(media) { media?.let { Snap(it.controller) } }
    val cover = remember(media) { media?.let { coverOf(it, snap?.meta) } }
    var access by remember { mutableStateOf(hasAccess(context)) }
    LifecycleResumeEffect(Unit) { access = hasAccess(context); onPauseOrDispose { } }

    // The sleep timer lives as long as this page does.
    val live by rememberUpdatedState(media)
    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            live?.let { runCatching { it.controller.transportControls.pause() } }
            sleepMin = 0
        }
    }
    var listened by remember { mutableStateOf(emptyList<Listened>()) }
    LaunchedEffect(opts.history) { listened = if (opts.history) store.listened() else emptyList() }
    LaunchedEffect(opts.history, media?.title, media?.artist, media?.playing) {
        val m = media
        if (opts.history && m != null && m.playing && m.title.isNotBlank()) {
            listened = addListened(store.listened(), Listened(System.currentTimeMillis(), m.title, m.artist)).also { store.saveListened(it) }
        }
    }
    BackHandler(showOptions) { showOptions = false }

    val accent = if (opts.accentFromCover) cover?.accent ?: LocalAccent.current else LocalAccent.current
    val pageCover = cover != null && opts.coverPage && !showOptions
    val sleepText = if (sleepMin == 0) stringResource(R.string.np_sleep_off) else stringResource(R.string.np_sleep, "$sleepMin min")
    // Over the cover the page is dark whatever the theme: its text, buttons and switches use the dark colours there.
    CompositionLocalProvider(LocalAccent provides accent, LocalMetroColors provides if (pageCover) DarkColors else LocalMetroColors.current) {
        val colors = LocalMetroColors.current
        BoxWithConstraints(Modifier.fillMaxSize().pageBackground()) {
            val wide = maxWidth > maxHeight
            if (pageCover && cover != null) Backdrop(cover, opts, wide, if (opts.accentFromCover) accent else null)
            val look = Look(
                ink = if (pageCover) Color.White else colors.foreground,
                faint = if (opts.contrast) (if (pageCover) Color.White else colors.foreground) else if (pageCover) Color.White.copy(alpha = 0.7f) else colors.subtle,
                accent = accent, pageCover = pageCover, wide = wide, maxW = maxWidth, maxH = maxHeight, opts = opts,
            )
            val frame = Modifier.fillMaxSize().topBarsPadding().navigationBarsPadding().cutoutSidesPadding()
            Column(if (pageCover && wide) frame else frame.centeredContent()) {
                PageSwitch(
                    page = true.takeIf { showOptions }, onBack = { showOptions = false }, modifier = Modifier.weight(1f).fillMaxWidth(),
                    list = {
                        if (media == null || snap == null) Empty(access, look)
                        else Player(media, snap, cover, look, sessions.list, { pick = it }, listened)
                    },
                    detail = { Options(opts, ::change, look) },
                )
                AppBar(
                    buildList { if (media != null) add(AppBarItem("↗", stringResource(R.string.np_open_source)) { openSource(context, media) }) },
                    Modifier.background(colors.background),
                    menu = buildList {
                        add(AppBarItem("⚙", stringResource(R.string.np_options)) { showOptions = !showOptions })
                        add(AppBarItem("☾", sleepText) { sleepMin = following(SLEEP_MINUTES, sleepMin) })
                        add(AppBarItem("≡", stringResource(R.string.np_queue_size, opts.queueLimit)) { change(opts.copy(queueLimit = following(QUEUE_LIMITS, opts.queueLimit))) })
                        if (opts.history) add(AppBarItem("✕", stringResource(R.string.np_history_clear)) { store.forgetListened(); listened = emptyList() })
                    },
                )
            }
        }
    }
}

/** The cover over the whole page, with its veil: dark toward the text, or flat where the text is beside the cover (or the contrast is asked); tinted with the cover's own colour when asked. */
@Composable
private fun Backdrop(cover: Cover, opts: NpOptions, wide: Boolean, tint: Color?) {
    val reduced = LocalReduceMotion.current
    Crossfade(cover, animationSpec = if (reduced) snap() else tween(400), label = "np:backdrop") { c ->
        Box(Modifier.fillMaxSize()) {
            Image(c.image, null, Modifier.fillMaxSize().then(if (opts.blur) Modifier.blur(28.dp) else Modifier), contentScale = ContentScale.Crop)
            val veil = when {
                opts.contrast -> SolidColor(Color.Black.copy(alpha = 0.92f))
                opts.blur || wide -> SolidColor(Color.Black.copy(alpha = 0.62f))
                else -> Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 0.3f to Color.Black.copy(alpha = 0.45f), 0.45f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black.copy(alpha = 0.88f))
            }
            Box(Modifier.fillMaxSize().background(veil))
            if (tint != null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.3f), Color.Transparent))))
        }
    }
}

/** Nothing plays, or the launcher cannot see it: in the second case the way to turn the access on. */
@Composable
private fun Empty(access: Boolean, look: Look) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("np:empty"), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        NoteIcon(look.faint)
        MetroText(stringResource(if (access) R.string.np_nothing else R.string.np_access_title), size = 22.sp, color = look.faint, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(top = 16.dp))
        if (!access) {
            MetroText(stringResource(R.string.np_access_steps), size = 16.sp, weight = FontWeight.Normal, color = look.faint, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            MetroButton(stringResource(R.string.np_access_button), Modifier.testTag("np:access")) {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}

/** A music note in a ring, drawn. */
@Composable
private fun NoteIcon(color: Color) {
    Canvas(Modifier.size(96.dp)) {
        val s = size.width
        drawCircle(color.copy(alpha = 0.5f), s / 2 - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
        val head = s * 0.11f
        drawCircle(color, head, Offset(s * 0.42f, s * 0.68f))
        drawLine(color, Offset(s * 0.42f + head, s * 0.68f), Offset(s * 0.42f + head, s * 0.28f), 3.dp.toPx())
        drawLine(color, Offset(s * 0.42f + head, s * 0.28f), Offset(s * 0.66f, s * 0.40f), 3.dp.toPx())
    }
}

@Composable
private fun Options(opts: NpOptions, set: (NpOptions) -> Unit, look: Look) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp).testTag("np:options")) {
        MetroText(stringResource(R.string.np_options_title).uppercase(), size = 14.sp, weight = FontWeight.SemiBold, color = look.faint)
        MetroText(stringResource(R.string.np_sleep_note), size = 14.sp, weight = FontWeight.Normal, color = look.faint, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        MetroToggle(stringResource(R.string.np_opt_cover_page), opts.coverPage, onChange = { set(opts.copy(coverPage = it)) })
        MetroToggle(stringResource(R.string.np_opt_blur), opts.blur, onChange = { set(opts.copy(blur = it)) })
        MetroToggle(stringResource(R.string.np_opt_accent), opts.accentFromCover, onChange = { set(opts.copy(accentFromCover = it)) })
        MetroToggle(stringResource(R.string.np_opt_disc), opts.disc, onChange = { set(opts.copy(disc = it)) })
        MetroToggle(stringResource(R.string.np_opt_swipe), opts.swipe, onChange = { set(opts.copy(swipe = it)) })
        MetroToggle(stringResource(R.string.np_opt_volume), opts.volume, onChange = { set(opts.copy(volume = it)) })
        MetroToggle(stringResource(R.string.np_opt_queue_covers), opts.queueCovers, onChange = { set(opts.copy(queueCovers = it)) })
        MetroToggle(stringResource(R.string.np_opt_car), opts.car, onChange = { set(opts.copy(car = it)) })
        MetroToggle(stringResource(R.string.np_opt_contrast), opts.contrast, onChange = { set(opts.copy(contrast = it)) })
        MetroToggle(stringResource(R.string.np_opt_history), opts.history, onChange = { set(opts.copy(history = it)) })
    }
}

@Composable
private fun Player(media: MediaInfo, snap: Snap, cover: Cover?, look: Look, players: List<MediaController>, onPick: (String) -> Unit, listened: List<Listened>) {
    val opts = look.opts
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    // Where the cover is the page, its place here is left empty (or a disc is shown there); a big text makes the cover smaller.
    val coverShown = cover != null && (!look.pageCover || opts.disc)
    val skip = if (opts.swipe) Modifier.swipeToSkip(media, snap.abilities) else Modifier
    if (look.wide) {
        Row(Modifier.fillMaxSize()) {
            val side = (min(look.maxW.value / 2 - 32, look.maxH.value - 140) / fontScale).coerceAtLeast(96f).dp
            Box(Modifier.weight(1f).fillMaxHeight().then(skip), contentAlignment = Alignment.Center) { if (coverShown) CoverBlock(cover!!, side, opts.disc, media.playing) }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) { Controls(media, snap, look, players, onPick, listened) }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
            val room = if (opts.car) 0.15f else if (look.pageCover) 0.3f else 0.42f
            val side = (min(look.maxW.value - 48, look.maxH.value * room) / fontScale).dp
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(side).then(skip), contentAlignment = Alignment.Center) { if (coverShown && !opts.car) CoverBlock(cover!!, side, opts.disc, media.playing) }
            Controls(media, snap, look, players, onPick, listened)
        }
    }
}

/** A swipe sideways on the cover goes to the next song, the other way to the one before. */
private fun Modifier.swipeToSkip(media: MediaInfo, ab: Abilities) = pointerInput(media.controller, ab.next, ab.previous) {
    var moved = 0f
    val limit = 80.dp.toPx()
    detectHorizontalDragGestures(onDragStart = { moved = 0f }, onDragCancel = { moved = 0f }, onDragEnd = {
        if (moved < -limit && ab.next) media.next() else if (moved > limit && ab.previous) media.previous()
    }) { _, delta -> moved += delta }
}

/** The cover, whole and square in the shape of the look, or a disc that turns while the song plays (and the motion is not reduced). */
@Composable
private fun CoverBlock(cover: Cover, side: Dp, disc: Boolean, playing: Boolean) {
    val spin = if (disc && playing && !LocalReduceMotion.current) {
        rememberInfiniteTransition(label = "np:disc").animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "np:angle")
    } else null
    Image(
        cover.image, stringResource(R.string.np_cover),
        Modifier.size(side).graphicsLayer { rotationZ = spin?.value ?: 0f }.clip(if (disc) CircleShape else LocalStyle.current.squareShape).testTag("np:cover"),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun Controls(media: MediaInfo, snap: Snap, look: Look, players: List<MediaController>, onPick: (String) -> Unit, listened: List<Listened>) {
    val context = LocalContext.current
    val opts = look.opts
    val ab = snap.abilities
    val reduced = LocalReduceMotion.current
    val label = remember(media.packageName) { appLabel(context, media.packageName) }
    val icon = remember(media.packageName) { runCatching { context.packageManager.getApplicationIcon(media.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Image(icon, null, Modifier.padding(end = 8.dp).size(20.dp))
        if (media.playing) MetroText("●", size = 14.sp, color = look.accent, modifier = Modifier.padding(end = 6.dp))
        MetroText("${stringResource(R.string.np_now).uppercase()} · $label", size = 14.sp, weight = FontWeight.SemiBold, color = look.faint, maxLines = 1)
    }
    if (players.size > 1) {
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            players.map { it.packageName }.distinct().forEach { pkg ->
                MetroText(appLabel(context, pkg), size = 16.sp, color = if (pkg == media.packageName) look.accent else look.faint, maxLines = 1, modifier = Modifier.metroPress(onClick = { onPick(pkg) }).semantics { contentDescription = "${context.getString(R.string.np_other_player)} $pkg" })
            }
        }
    }
    val title = media.title.ifBlank { media.artist }
    Crossfade(title to media.artist, animationSpec = if (reduced) snap() else tween(300), label = "np:song") { _ ->
        Column {
            MetroText(title, size = if (opts.car) 44.sp else 34.sp, weight = if (opts.contrast) FontWeight.SemiBold else FontWeight.Light, color = look.ink, maxLines = 2, lineHeight = if (opts.car) 50.sp else 40.sp, modifier = Modifier.padding(top = 16.dp).testTag("np:title"))
            if (media.title.isNotBlank() && media.artist.isNotBlank()) MetroText(media.artist, size = if (opts.car) 28.sp else 22.sp, color = look.faint, maxLines = 1, modifier = Modifier.testTag("np:artist"))
        }
    }
    Progress(media, snap, look)
    val big = if (opts.car) 96.dp else 64.dp
    val ring = LocalStyle.current.isClassic
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Dimmed(ab.previous) { TransportButton(Transport.Previous, "np:prev", look.ink, stringResource(R.string.np_previous), big, ring) { if (ab.previous) media.previous() } }
        Crossfade(media.playing, animationSpec = if (reduced) snap() else tween(200), label = "np:toggle") { playing ->
            TransportButton(if (playing) Transport.Pause else Transport.Play, "np:toggle", look.ink, stringResource(if (playing) R.string.np_pause else R.string.np_play), big, ring) { media.playPause() }
        }
        Dimmed(ab.next) { TransportButton(Transport.Next, "np:next", look.ink, stringResource(R.string.np_next), big, ring) { if (ab.next) media.next() } }
    }
    Extras(media, snap, look)
    if (!opts.car) {
        Queue(media.controller, snap, look)
        if (opts.history && listened.isNotEmpty()) {
            MetroText(stringResource(R.string.np_history_title), size = 18.sp, color = look.faint, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            listened.take(20).forEach { MetroText(if (it.artist.isBlank()) it.title else "${it.title} — ${it.artist}", size = 16.sp, weight = FontWeight.Normal, color = look.ink, maxLines = 1, modifier = Modifier.padding(vertical = 3.dp).testTag("np:history")) }
        }
    }
}

/** A control the player does not offer is shown faint and does nothing. */
@Composable
private fun Dimmed(enabled: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.alpha(if (enabled) 1f else 0.35f).semantics { if (!enabled) disabled() }) { content() }
}

/** The bar of the song: a touch seeks, a drag seeks where it is let go (the time under the finger shown while it moves), the time left in place of the length on a touch of it. */
@Composable
private fun Progress(media: MediaInfo, snap: Snap, look: Look) {
    val duration = snap.duration
    val state = snap.state
    val buffering = state?.state == PlaybackState.STATE_BUFFERING
    // The position is worked out again twice a second while the song plays, from what the player last said: only this bar redraws.
    var now by remember(snap) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(snap, media.playing) { while (media.playing) { delay(500); now = SystemClock.elapsedRealtime() } }
    if (buffering) MetroText(stringResource(R.string.np_buffering), size = 14.sp, weight = FontWeight.Normal, color = look.accent, modifier = Modifier.padding(top = 8.dp).testTag("np:buffering"))
    if (duration <= 0) return
    val at = state?.let { playbackPosition(it.position, it.lastPositionUpdateTime.takeIf { t -> t > 0 } ?: now, now, it.playbackSpeed, media.playing, duration) } ?: 0L
    val seekable = snap.abilities.seek
    var drag by remember { mutableStateOf<Float?>(null) }
    var left by rememberSaveable { mutableStateOf(false) }
    val shown = drag?.let { (duration * it).toLong() } ?: at
    val fraction = drag ?: (at.toFloat() / duration).coerceIn(0f, 1f)
    fun seek(ms: Long) { runCatching { media.controller.transportControls.seekTo(ms) } }
    // Windows Phone slider: a track of 9 dp and a block of 9 x 18 dp.
    val classic = LocalStyle.current.isClassic
    val thick = if (classic) 9.dp else if (look.opts.contrast || look.opts.car) 8.dp else 4.dp
    val positionLabel = stringResource(R.string.np_position)
    Box(
        Modifier.padding(top = 10.dp).fillMaxWidth().height(48.dp).testTag("np:seek").semantics { contentDescription = "$positionLabel ${mmss(shown)} / ${mmss(duration)}" }
            .then(
                if (!seekable) Modifier else Modifier
                    .pointerInput(duration) { detectTapGestures { p -> seek(seekTarget(duration, p.x, size.width.toFloat())) } }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures(
                            onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = { drag?.let { seek((duration * it).toLong()) }; drag = null },
                            onDragCancel = { drag = null },
                        ) { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
                    },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = thick.toPx()
            val y = (size.height - h) / 2
            drawRect(look.faint.copy(alpha = 0.35f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, h))
            drawRect(look.accent, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width * fraction, h))
            if (seekable && classic) drawRect(look.ink, Offset((size.width - 9.dp.toPx()) * fraction, size.height / 2 - 9.dp.toPx()), androidx.compose.ui.geometry.Size(9.dp.toPx(), 18.dp.toPx()))
            else if (seekable) drawCircle(look.accent, if (drag != null) 11.dp.toPx() else 7.dp.toPx(), Offset(size.width * fraction, size.height / 2))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MetroText(mmss(shown), size = 15.sp, weight = FontWeight.Normal, color = if (drag != null) look.accent else look.faint)
        MetroText(
            if (left) remaining(shown, duration) else mmss(duration), size = 15.sp, weight = FontWeight.Normal, color = look.faint,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.np_remaining)) { left = !left },
        )
    }
}

/** Volume, like, speed and the extra actions the player offers (shuffle, repeat…): only what exists. */
@Composable
private fun Extras(media: MediaInfo, snap: Snap, look: Look) {
    val c = media.controller
    val ab = snap.abilities
    val canRate = runCatching { c.ratingType }.getOrDefault(0) == Rating.RATING_HEART && ab.rate
    val rating = if (canRate) snap.meta?.getRating(MediaMetadata.METADATA_KEY_USER_RATING) else null
    val speed = snap.state?.playbackSpeed ?: 1f
    val custom = snap.state?.customActions.orEmpty().take(6)
    val volume = look.opts.volume && !look.opts.car
    if (!volume && !canRate && !(ab.speed && Build.VERSION.SDK_INT >= 29) && custom.isEmpty()) return
    Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (volume) {
            val down = stringResource(R.string.np_volume_down)
            val up = stringResource(R.string.np_volume_up)
            MetroButton("−", Modifier.semantics { contentDescription = down }.testTag("np:vol:down")) { runCatching { c.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) } }
            MetroButton("+", Modifier.semantics { contentDescription = up }.testTag("np:vol:up")) { runCatching { c.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) } }
        }
        if (canRate) {
            val liked = rating?.hasHeart() == true
            val text = stringResource(if (liked) R.string.np_unlike else R.string.np_like)
            MetroButton(if (liked) "♥" else "♡", Modifier.semantics { contentDescription = text }.testTag("np:like")) { runCatching { c.transportControls.setRating(Rating.newHeartRating(!liked)) } }
        }
        if (ab.speed && Build.VERSION.SDK_INT >= 29) {
            val next = following(SPEEDS, SPEEDS.minBy { abs(it - speed) })
            MetroButton(stringResource(R.string.np_speed, "${"%.2f".format(speed).trimEnd('0').trimEnd('.', ',')}×"), Modifier.testTag("np:speed")) { runCatching { c.transportControls.setPlaybackSpeed(next) } }
        }
        custom.forEach { action -> MetroButton(action.name.toString(), Modifier.testTag("np:custom")) { runCatching { c.transportControls.sendCustomAction(action, null) } } }
    }
}

/** The queue of the player: what is next, the song that plays in the accent colour and marked at the side, small covers; a touch jumps to it; a search when it is long; it folds. Nothing when the player keeps none. */
@Composable
private fun Queue(controller: MediaController, snap: Snap, look: Look) {
    if (snap.queue.size < 2) return
    val playing = snap.state?.activeQueueItemId ?: -1L
    val icons = remember(snap) { snap.queue.associate { it.queueId to it.description.iconBitmap } }
    val entries = remember(snap) {
        snap.queue.map { item ->
            val title = item.description.title?.toString().orEmpty()
            val subtitle = item.description.subtitle?.toString().orEmpty()
            // A song without a title is still a row: its subtitle stands for it, or a dash.
            if (title.isBlank()) QueueEntry(item.queueId, subtitle.ifBlank { "—" }, "") else QueueEntry(item.queueId, title, subtitle)
        }
    }
    var open by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf("") }
    val rows = queueRows(entries, look.opts.queueLimit, filter)
    val collapse = stringResource(R.string.np_collapse)
    MetroText("${stringResource(R.string.np_queue)} (${entries.size})", size = 18.sp, color = look.faint, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp).clickable(role = Role.Button, onClickLabel = collapse) { open = !open }.testTag("np:queue:header"))
    if (!open) return
    if (entries.size >= 8) MetroTextField(filter, { filter = it }, stringResource(R.string.np_queue_search), Modifier.padding(bottom = 6.dp).testTag("np:queue:search"))
    val scroll = rememberScrollState()
    var currentTop by remember { mutableIntStateOf(0) }
    // The queue scrolls inside its own box, to the song that plays, without moving the page.
    LaunchedEffect(playing, currentTop) { scroll.scrollTo(currentTop) }
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(scroll)) {
        rows.forEach { row ->
            val current = row.id == playing
            val icon = if (look.opts.queueCovers) icons[row.id] else null
            Row(
                Modifier.fillMaxWidth()
                    .then(if (current) Modifier.onGloballyPositioned { currentTop = it.positionInParent().y.toInt() } else Modifier)
                    .metroPress(onClick = { if (snap.abilities.jump) runCatching { controller.transportControls.skipToQueueItem(row.id) } })
                    .padding(vertical = 6.dp).testTag("np:queue"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(32.dp).background(if (current) look.accent else Color.Transparent))
                if (icon != null) Image(icon.asImageBitmap(), null, Modifier.padding(start = 8.dp).size(40.dp), contentScale = ContentScale.Crop) else Box(Modifier.width(8.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    MetroText(row.title, size = 20.sp, color = if (current) look.accent else look.ink, maxLines = 1)
                    if (row.subtitle.isNotBlank()) MetroText(row.subtitle, size = 14.sp, weight = FontWeight.Normal, color = look.faint, maxLines = 1)
                }
            }
        }
    }
}
