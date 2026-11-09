package com.metrowin.launcher.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val FULL_SIDE = 2048

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

/** What is known of a picture: for the "info" of the viewer. */
data class PhotoInfo(val name: String, val width: Int, val height: Int, val bytes: Long)

private fun details(context: Context, id: Long): PhotoInfo? = runCatching {
    val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.SIZE)
    context.contentResolver.query(PhotoReader.uriOf(id), projection, null, null, null)?.use { c ->
        if (c.moveToFirst()) PhotoInfo(c.getString(0).orEmpty(), c.getInt(1), c.getInt(2), c.getLong(3)) else null
    }
}.getOrNull()

/** The picture itself, as large as the screen can use ([FULL_SIDE] at most), read away from the screen. */
private fun decodeFull(context: Context, id: Long): ImageBitmap? = runCatching {
    val uri = PhotoReader.uriOf(id)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > FULL_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()

/**
 * The photo viewer of Windows Phone: black, the picture fitted whole; pinch and drag zoom and move it (and it glides on when let go),
 * a double tap zooms in and out, a swipe goes to the picture next to it (which is seen coming in as the finger moves), a tap
 * shows or hides the bar with share, delete and info. [photos] is the list in the order of the swipes, newest first.
 */
@Composable
fun PhotoViewer(photos: List<Photo>, start: Int, onClose: () -> Unit, onShare: (Photo) -> Unit, onDelete: (Photo) -> Unit) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pager = rememberPagerState(start.coerceIn(0, photos.lastIndex)) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    BackHandler { if (info) info = false else onClose() }
    // A page swiped away is back at its own size.
    LaunchedEffect(pager.currentPage) { zoomed = false }
    val current = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("photoviewer")) {
        HorizontalPager(
            pager, Modifier.fillMaxSize(), pageSpacing = 16.dp, beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed, key = { photos.getOrNull(it)?.id ?: it },
        ) { page ->
            ZoomableImage(photos[page], active = page == pager.currentPage, onZoomed = { zoomed = it }, onTap = { chrome = !chrome; info = false })
        }
        if (chrome) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                if (info) InfoPanel(current)
                AppBar(
                    listOf(
                        AppBarItem("↗", stringResource(R.string.pv_share)) { onShare(current) },
                        AppBarItem("🗑", stringResource(R.string.pv_delete)) { onDelete(current) },
                        AppBarItem("i", stringResource(R.string.pv_info)) { info = !info },
                    ),
                )
            }
        }
    }
}

@Composable
private fun InfoPanel(photo: Photo) {
    val context = LocalContext.current
    val data by produceState<PhotoInfo?>(null, photo.id) { value = withContext(Dispatchers.IO) { details(context, photo.id) } }
    val colors = LocalMetroColors.current
    Column(Modifier.fillMaxWidth().background(colors.containers.getOrElse(0) { colors.surface }).padding(horizontal = 24.dp, vertical = 12.dp).testTag("photoviewer:info")) {
        data?.let { MetroText(it.name, size = 20.sp, maxLines = 1) }
        MetroText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(photo.takenAt)), size = 16.sp, color = colors.subtle, maxLines = 1)
        if (photo.album.isNotEmpty()) MetroText(photo.album, size = 16.sp, color = colors.subtle, maxLines = 1)
        data?.takeIf { it.width > 0 }?.let {
            MetroText("${it.width} × ${it.height} · ${"%.1f".format(it.bytes / 1_048_576.0)} MB", size = 16.sp, color = colors.subtle, maxLines = 1)
        }
    }
}

/** One picture: a thumbnail at once, then the full picture; pinch, drag (with the glide when let go) and double tap. */
@Composable
private fun ZoomableImage(photo: Photo, active: Boolean, onZoomed: (Boolean) -> Unit, onTap: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val full by produceState<ImageBitmap?>(null, photo.id) { value = withContext(Dispatchers.IO) { decodeFull(context, photo.id) } }
    val shown = full ?: PhotoThumbs.load(photo.id)
    val reduced = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var glide by remember { mutableStateOf<Job?>(null) }
    if (!active && scale != 1f) { scale = 1f; offset = Offset.Zero }

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
        onZoomed(s > 1.001f)
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
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
                            androidx.compose.animation.core.animate(0f, 1f, animationSpec = com.metrowin.launcher.ui.anim.metroTween(260)) { t, _ ->
                                set(fromScale + (toScale - fromScale) * t, fromOffset + (toOffset - fromOffset) * t)
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    glide?.cancel()
                    val tracker = VelocityTracker()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pinching = event.changes.size > 1
                        // One finger on a picture at its own size is a swipe of the pager: left alone.
                        if (pinching || scale > 1.001f) {
                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false) - Offset(size.width / 2f, size.height / 2f)
                            val next = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                            set(next, zoomAbout(offset, centroid, scale, next) + pan)
                            event.changes.forEach { change ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (change.position != change.previousPosition) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
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
        shown?.let {
            Image(
                it, null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
