package com.metrowin.launcher.photos

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** How far the picture may be moved from the middle, along one axis: the part of it (at [scale]) that does not fit in the [box]. Zero when it fits. */
internal fun panLimit(box: Float, content: Float, scale: Float): Float = max(0f, (content * scale - box) / 2f)

/** The size a picture of [width] by [height] takes when it is fitted whole in a box of [boxWidth] by [boxHeight]. */
internal fun fitSize(width: Float, height: Float, boxWidth: Float, boxHeight: Float): Pair<Float, Float> {
    if (width <= 0f || height <= 0f) return boxWidth to boxHeight
    val k = min(boxWidth / width, boxHeight / height)
    return width * k to height * k
}

/** Where the picture goes when the pinch is at [centroid] (from the middle of the box) and the zoom goes from [from] to [to]: the point under the fingers stays under them. */
internal fun zoomAbout(offset: Offset, centroid: Offset, from: Float, to: Float): Offset = (offset - centroid) * (to / from) + centroid

/** How far a pull down has gone, 0 to 1, when the picture was pulled [pulled] px in a box [height] px tall; at 1 the viewer closes. */
internal fun pullProgress(pulled: Float, height: Float): Float = if (height <= 0f) 0f else (pulled / (height * 0.35f)).coerceIn(0f, 1f)

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/**
 * The photo viewer of Windows Phone: black, the picture fitted whole; pinch and drag zoom and move it (and it glides on when let go),
 * a double tap zooms in and out, a swipe goes to the picture next to it (which is seen coming in as the finger moves), a swipe down closes it,
 * a tap shows or hides the bar with share, favourite, delete and info. [photos] is the list in the order of the swipes, newest first.
 * What the options of the hub switch off ([prefs]) is not drawn; [onFavorite] and [onWallpaper] are null when the activity cannot do them.
 */
@Composable
fun PhotoViewer(
    photos: List<Photo>,
    start: Int,
    onClose: () -> Unit,
    onShare: (Photo) -> Unit,
    onDelete: (Photo) -> Unit,
    from: Rect? = null,
    prefs: PhotoPrefs = rememberPhotoPrefs(),
    onFavorite: ((Photo, Boolean) -> Unit)? = null,
    onWallpaper: ((Photo) -> Unit)? = null,
) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pager = rememberPagerState(start.coerceIn(0, photos.lastIndex)) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var pull by remember { mutableFloatStateOf(0f) }
    // A page swiped away is back at its own size.
    LaunchedEffect(pager.currentPage) { zoomed = false }
    val current = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
    val fx = prefs[PhotoOption.ViewerFx]
    val reduced = LocalReduceMotion.current
    // Opening: the viewer grows out of the small picture it was touched on ([from], in window pixels) and the black comes up behind it;
    // closing goes back into it, as long as the picture on show is still the one that was touched.
    val open = remember { Animatable(if (reduced || from == null) 1f else 0f) }
    var leaving by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) { if (open.value < 1f) open.animateTo(1f, metroTween(durationMillis = 300, easing = Motion.Decelerate)) }
    val startId = remember { photos[start.coerceIn(0, photos.lastIndex)].id }
    val scope = rememberCoroutineScope()
    fun close(instant: Boolean = false) {
        if (leaving) return
        if (instant || from == null || reduced || current.id != startId) { onClose(); return }
        leaving = true
        scope.launch {
            open.animateTo(0f, metroTween(durationMillis = 220, easing = Motion.Accelerate))
            onClose()
        }
    }
    BackHandler { if (info) info = false else close() }

    // The bars of the system go with the bar of the viewer.
    val view = LocalView.current
    val immersive = prefs[PhotoOption.Immersive]
    DisposableEffect(chrome, immersive, playing) {
        val window = view.context.activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (immersive && (!chrome || playing)) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val tone by produceState<Color?>(null, current.id, prefs[PhotoOption.AdaptiveBackground]) {
        value = if (prefs[PhotoOption.AdaptiveBackground]) withContext(Dispatchers.Default) { PhotoThumbs.cached(current)?.let(::toneOf) } else null
    }
    val background by animateColorAsState(tone ?: Color.Black, tween(if (reduced) 0 else 300), label = "viewer-tone")
    val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, tween(if (fx && !reduced) 180 else 0), label = "viewer-chrome")
    var hint by remember { mutableStateOf(prefs[PhotoOption.Hints] && !prefs.hintSeen) }
    LaunchedEffect(Unit) { if (hint) { delay(4_000); hint = false; prefs.hintSeen = true } }

    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("photoviewer").onGloballyPositioned { origin = it.positionInWindow() }
            .drawBehind { drawRect(background, alpha = open.value * (1f - pull * 0.8f)) }
            .graphicsLayer {
                if (from != null && open.value < 1f) {
                    val p = open.value
                    val s = lerp((from.width / size.width).coerceIn(0.05f, 1f), 1f, p)
                    scaleX = s
                    scaleY = s
                    translationX = lerp(from.center.x - origin.x - size.width / 2f, 0f, p)
                    translationY = lerp(from.center.y - origin.y - size.height / 2f, 0f, p)
                }
            },
    ) {
        val wide = maxWidth >= 600.dp
        HorizontalPager(
            pager, Modifier.fillMaxSize(), pageSpacing = 16.dp, beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed, key = { photos.getOrNull(it)?.id ?: it },
        ) { page ->
            ZoomableImage(
                photos[page], active = page == pager.currentPage, onZoomed = { zoomed = it }, onTap = { chrome = !chrome; info = false },
                fx = fx, swipeClose = prefs[PhotoOption.SwipeClose], onPull = { pull = it }, onDismiss = { close(instant = true) },
            )
        }
        if (fx && chromeAlpha > 0f) MetroText(
            "${pager.currentPage + 1} / ${photos.size}", size = 16.sp,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).graphicsLayer { alpha = chromeAlpha }.background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 10.dp, vertical = 2.dp).testTag("photoviewer:counter"),
        )
        if (hint && !playing) MetroText(stringResource(R.string.ph_hint), size = 16.sp, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 44.dp, start = 24.dp, end = 24.dp).background(Color.Black.copy(alpha = 0.6f)).padding(10.dp).testTag("photoviewer:hint"))
        if (chromeAlpha > 0f) {
            if (info && wide) InfoPanel(current, prefs, { playing = true }, onWallpaper, Modifier.align(Alignment.CenterEnd).width(320.dp).graphicsLayer { alpha = chromeAlpha })
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().graphicsLayer { alpha = chromeAlpha }) {
                if (info && !wide) InfoPanel(current, prefs, { playing = true }, onWallpaper, Modifier)
                if (prefs[PhotoOption.Filmstrip]) Filmstrip(photos, pager, prefs)
                PhotoBar(
                    buildList {
                        add(BarItem(PhotoIcon.Share, stringResource(R.string.pv_share), tag = "photoviewer:share") { onShare(current) })
                        if (onFavorite != null && prefs[PhotoOption.FavoriteEdit] && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            add(BarItem(PhotoIcon.Heart, stringResource(if (current.favorite) R.string.ph_fav_remove else R.string.ph_fav_add), on = current.favorite) { onFavorite(current, !current.favorite) })
                        }
                        add(BarItem(PhotoIcon.Trash, stringResource(R.string.pv_delete), tag = "photoviewer:delete") { onDelete(current) })
                        add(BarItem(PhotoIcon.Info, stringResource(R.string.pv_info), on = info, tag = "photoviewer:infobutton") { info = !info })
                    },
                    veil = if (fx) 0.8f else 1f,
                )
            }
        }
        if (playing) Slideshow(photos, pager.currentPage, reduced) { index -> playing = false; scope.launch { pager.scrollToPage(index) } }
    }
}

/** A row of small pictures over the bar: the one on show is framed, a tap goes to another. */
@Composable
private fun Filmstrip(photos: List<Photo>, pager: PagerState, prefs: PhotoPrefs) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { state.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0)) }
    LazyRow(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(vertical = 4.dp).testTag("photoviewer:filmstrip"), state, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
            val here = index == pager.currentPage
            Thumb(photo, Modifier.size(56.dp).then(if (here) Modifier.border(2.dp, LocalAccent.current) else Modifier).metroPress(onClick = { scope.launch { pager.scrollToPage(index) } }), prefs, fill = true)
        }
    }
}

@Composable
private fun InfoPanel(photo: Photo, prefs: PhotoPrefs, onSlideshow: () -> Unit, onWallpaper: ((Photo) -> Unit)?, modifier: Modifier) {
    val context = LocalContext.current
    val data by produceState<PhotoInfo?>(null, photo.id) { value = withContext(Dispatchers.IO) { details(context, photo.id) } }
    val colors = LocalMetroColors.current
    Column(modifier.fillMaxWidth().background(colors.containers.getOrElse(0) { colors.surface }).padding(horizontal = 24.dp, vertical = 12.dp).testTag("photoviewer:info")) {
        data?.let { MetroText(it.name, size = 20.sp, maxLines = 1) }
        MetroText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(photo.takenAt)), size = 16.sp, color = colors.subtle, maxLines = 1)
        if (photo.album.isNotEmpty()) MetroText(photo.album, size = 16.sp, color = colors.subtle, maxLines = 1)
        data?.takeIf { it.width > 0 }?.let {
            MetroText("${it.width} × ${it.height} · ${Formatter.formatFileSize(context, it.bytes)}", size = 16.sp, color = colors.subtle, maxLines = 1)
        }
        data?.camera?.takeIf { it.isNotEmpty() }?.let { MetroText(it, size = 16.sp, color = colors.subtle, maxLines = 1) }
        data?.exposure?.takeIf { it.isNotEmpty() }?.let { MetroText(it, size = 16.sp, color = colors.subtle, maxLines = 1) }
        if (prefs[PhotoOption.Extras]) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroText(stringResource(R.string.ph_slideshow), size = 18.sp, weight = FontWeight.SemiBold, color = LocalAccent.current, modifier = Modifier.metroPress(onClick = onSlideshow).padding(8.dp).testTag("photoviewer:slideshow"))
            if (onWallpaper != null) MetroText(stringResource(R.string.ph_wallpaper), size = 18.sp, weight = FontWeight.SemiBold, color = LocalAccent.current, modifier = Modifier.metroPress(onClick = { onWallpaper(photo) }).padding(8.dp))
        }
    }
}

/** One picture after the other, each melting into the next and drifting slowly closer; a tap (or back) ends it on the picture then on show. */
@Composable
private fun Slideshow(photos: List<Photo>, start: Int, reduced: Boolean, onExit: (Int) -> Unit) {
    var index by remember { mutableIntStateOf(start) }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    LaunchedEffect(Unit) { while (true) { delay(4_500); index = (index + 1) % photos.size } }
    BackHandler { onExit(index) }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { onExit(index) } }.testTag("photoviewer:slides")) {
        Crossfade(index, animationSpec = tween(if (reduced) 0 else 900), label = "slides") { i -> SlideImage(photos[i.coerceIn(0, photos.lastIndex)], reduced) }
    }
}

@Composable
private fun SlideImage(photo: Photo, reduced: Boolean) {
    val context = LocalContext.current.applicationContext
    val full by produceState(FullCache.get(photo.id), photo.id) { if (value == null) value = withContext(Dispatchers.IO) { decodeFull(context, photo.id) }?.also { FullCache.put(photo.id, it) } }
    val shown = full ?: PhotoThumbs.load(photo)
    val drift = remember { Animatable(1f) }
    LaunchedEffect(Unit) { if (!reduced) drift.animateTo(1.08f, tween(5_000, easing = LinearEasing)) }
    shown?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { scaleX = drift.value; scaleY = drift.value }, contentScale = ContentScale.Fit) }
}

/** Two pictures side by side, each with its own zoom, to tell which is better. Back closes. */
@Composable
internal fun PhotoCompare(a: Photo, b: Photo, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color.Black).testTag("photos:compare")) {
        Row(Modifier.weight(1f).fillMaxWidth().statusBarsPadding(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f)) { ZoomableImage(a, active = true, onZoomed = {}, onTap = {}) }
            Box(Modifier.weight(1f)) { ZoomableImage(b, active = true, onZoomed = {}, onTap = {}) }
        }
        PhotoBar(listOf(BarItem(PhotoIcon.Check, stringResource(R.string.ph_done), onClick = onClose)), Modifier.navigationBarsPadding())
    }
}

/**
 * One picture: a thumbnail at once, then the full picture melting in (only the picture on show is read in full, and the last three are kept);
 * pinch (with a little give past the limits), drag (with the glide when let go) and double tap; a pull down closes. Without a touch screen
 * TalkBack gets the actions "zoom in" and "zoom out".
 */
@Composable
internal fun ZoomableImage(
    photo: Photo,
    active: Boolean,
    onZoomed: (Boolean) -> Unit,
    onTap: () -> Unit,
    fx: Boolean = false,
    swipeClose: Boolean = false,
    onPull: (Float) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val full by produceState(FullCache.get(photo.id), photo.id, active) {
        if (value == null && active) value = withContext(Dispatchers.IO) { decodeFull(context, photo.id) }?.also { FullCache.put(photo.id, it) }
    }
    val thumb = PhotoThumbs.load(photo)
    val shown: ImageBitmap? = full ?: thumb
    val reduced = LocalReduceMotion.current
    val melt = fx && !reduced
    val fullAlpha by animateFloatAsState(if (full != null) 1f else 0f, tween(if (melt) 220 else 0), label = "full-in")
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var pulled by remember { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var glide by remember { mutableStateOf<Job?>(null) }
    var lastZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = Offset.Zero; pulled = 0f } }

    fun fitted(): Pair<Float, Float> = fitSize(shown?.width?.toFloat() ?: 0f, shown?.height?.toFloat() ?: 0f, box.width.toFloat(), box.height.toFloat())
    fun clamp(o: Offset, s: Float): Offset {
        val (w, h) = fitted()
        val x = panLimit(box.width.toFloat(), w, s)
        val y = panLimit(box.height.toFloat(), h, s)
        return Offset(o.x.coerceIn(-x, x), o.y.coerceIn(-y, y))
    }
    fun set(s: Float, o: Offset) {
        scale = s
        offset = if (s <= 1f) Offset.Zero else clamp(o, s)
        // Told only when it changes: not at every frame of a pinch.
        val now = s > 1.001f
        if (now != lastZoomed) { lastZoomed = now; onZoomed(now) }
    }
    fun springBack() {
        val to = scale.coerceIn(1f, MAX_ZOOM)
        if (to == scale) return
        val from = scale
        glide = scope.launch { animate(from, to, animationSpec = metroTween(200)) { v, _ -> set(v, offset) } }
    }
    val zoomIn = stringResource(R.string.ph_zoom_in)
    val zoomOut = stringResource(R.string.ph_zoom_out)
    val description = describe(photo)
    val lowest = if (melt) 0.8f else 1f
    val highest = if (melt) MAX_ZOOM * 1.15f else MAX_ZOOM

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(zoomIn) { set((scale * 1.6f).coerceAtMost(MAX_ZOOM), offset); true },
                    CustomAccessibilityAction(zoomOut) { set((scale / 1.6f).coerceAtLeast(1f), offset); true },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { at ->
                        glide?.cancel()
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val toScale = if (scale > 1.01f) 1f else DOUBLE_TAP_ZOOM
                        val toOffset = if (toScale == 1f) Offset.Zero else zoomAbout(Offset.Zero, at - centre, 1f, toScale)
                        if (reduced) return@detectTapGestures set(toScale, toOffset)
                        val fromScale = scale
                        val fromOffset = offset
                        glide = scope.launch {
                            animate(0f, 1f, animationSpec = metroTween(260)) { t, _ ->
                                set(fromScale + (toScale - fromScale) * t, fromOffset + (toOffset - fromOffset) * t)
                            }
                        }
                    },
                )
            }
            .pointerInput(swipeClose, melt, active) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    glide?.cancel()
                    val tracker = VelocityTracker()
                    var decided = false
                    var pulling = false
                    var slack = Offset.Zero
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pinching = event.changes.size > 1
                        // One finger on a picture at its own size is a swipe of the pager: left alone, unless it goes down more than sideways.
                        if (pinching || scale > 1.001f) {
                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false) - Offset(size.width / 2f, size.height / 2f)
                            val next = (scale * zoomChange).coerceIn(lowest, highest)
                            set(next, zoomAbout(offset, centroid, scale, next) + pan)
                            event.changes.forEach { change ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (change.position != change.previousPosition) change.consume()
                            }
                        } else if (swipeClose && active) {
                            val pan = event.calculatePan()
                            if (!decided) {
                                slack += pan
                                if (slack.getDistance() > viewConfiguration.touchSlop) {
                                    decided = true
                                    pulling = slack.y > 0f && abs(slack.y) > 1.5f * abs(slack.x)
                                    if (pulling) pulled = slack.y
                                }
                            } else if (pulling) pulled = (pulled + pan.y).coerceAtLeast(0f)
                            if (pulling) {
                                onPull(pullProgress(pulled, size.height.toFloat()))
                                event.changes.forEach { change ->
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (change.position != change.previousPosition) change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pulling) {
                        val velocity = tracker.calculateVelocity()
                        if (pullProgress(pulled, size.height.toFloat()) >= 0.5f || velocity.y > 1_800f) onDismiss()
                        else {
                            val from = pulled
                            scope.launch { animate(from, 0f, animationSpec = metroTween(200)) { v, _ -> pulled = v; onPull(pullProgress(v, size.height.toFloat())) } }
                        }
                        return@awaitEachGesture
                    }
                    if (scale < 1f || scale > MAX_ZOOM) springBack()
                    val velocity = tracker.calculateVelocity()
                    if (scale > 1.001f && (velocity.x != 0f || velocity.y != 0f)) {
                        val start = Offset(velocity.x, velocity.y)
                        glide = scope.launch {
                            AnimationState(Offset.VectorConverter, offset, start).animateDecay(exponentialDecay<Offset>(frictionMultiplier = 2.2f)) {
                                val moved = clamp(value, scale)
                                offset = moved
                                // Against an edge the glide ends there.
                                if (moved != value) cancelAnimation()
                            }
                        }
                    }
                }
            },
    ) {
        val modifier = Modifier.fillMaxSize().graphicsLayer {
            val k = scale * (1f - 0.15f * pullProgress(pulled, size.height))
            scaleX = k; scaleY = k; translationX = offset.x; translationY = offset.y + pulled
        }
        if (thumb != null && (full == null || fullAlpha < 1f)) Image(thumb, null, modifier, contentScale = ContentScale.Fit)
        full?.let { Image(it, null, modifier.graphicsLayer { alpha = fullAlpha }, contentScale = ContentScale.Fit) }
    }
}
