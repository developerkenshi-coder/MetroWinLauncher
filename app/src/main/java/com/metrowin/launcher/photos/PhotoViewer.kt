package com.metrowin.launcher.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.util.lerp
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The size, in pixels, that a picture is read down to for the viewer: enough for a phone screen zoomed in a few times. */
private const val VIEW_SIDE = 2048

/** What a pinch can reach, and what a double tap zooms to. */
private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** A picture's power of two to read it by, so that its longer side is not much more than [max]. */
internal fun sampleFor(width: Int, height: Int, max: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= max) sample *= 2
    return sample
}

/** How far a zoomed picture can be dragged along an axis of [size] pixels at [zoom] times: the edge of the picture stays at the edge of the screen. */
internal fun panLimit(size: Float, zoom: Float): Float = ((zoom - 1f) * size / 2f).coerceAtLeast(0f)

private fun decode(context: Context, id: Long): ImageBitmap? = runCatching {
    val uri = PhotoReader.uriOf(id)
    val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
        // This one also turns the picture the way it was taken.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.setTargetSampleSize(sampleFor(info.size.width, info.size.height, VIEW_SIDE))
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, VIEW_SIDE) }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
    bitmap?.asImageBitmap()
}.getOrNull()

/**
 * A picture over the hub, black behind it: it grows out of the small picture it was touched on ([from], in window pixels) and goes back into
 * it. Two fingers zoom it and drag it, a double tap zooms in and out, a long press hands it to the gallery of the phone ([onGallery]).
 */
@Composable
internal fun PhotoViewer(photo: Photo, from: Rect, onClose: () -> Unit, onGallery: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val reduced = LocalReduceMotion.current
    val open = remember { Animatable(if (reduced) 1f else 0f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!reduced) open.animateTo(1f, metroTween(durationMillis = 300, easing = Motion.Decelerate)) }
    fun close() {
        if (leaving) return
        leaving = true
        scope.launch {
            if (!reduced) open.animateTo(0f, metroTween(durationMillis = 220, easing = Motion.Accelerate))
            onClose()
        }
    }
    BackHandler { close() }
    val thumb = PhotoThumbs.load(photo.id)
    val full by produceState<ImageBitmap?>(null, photo.id) { value = withContext(Dispatchers.IO) { decode(context, photo.id) } }
    val zoom = remember { Animatable(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("photos:viewer").onGloballyPositioned { origin = it.positionInWindow() }
            .drawBehind { drawRect(Color.Black, alpha = open.value) },
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        fun clamp(p: Offset, z: Float) = Offset(p.x.coerceIn(-panLimit(width, z), panLimit(width, z)), p.y.coerceIn(-panLimit(height, z), panLimit(height, z)))
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, drag, change, _ ->
                        val z = (zoom.value * change).coerceIn(1f, MAX_ZOOM)
                        scope.launch { zoom.snapTo(z) }
                        pan = clamp(pan + drag, z)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            val target = if (zoom.value > 1.05f) 1f else DOUBLE_TAP_ZOOM
                            if (target == 1f) pan = Offset.Zero
                            scope.launch { zoom.animateTo(target, metroTween(durationMillis = 220)) }
                        },
                        onLongPress = { onGallery() },
                    )
                }
                .graphicsLayer {
                    // From the small picture to the whole screen: the same picture, scaled about its middle and moved from where the small one was.
                    val p = open.value
                    val start = (from.width / width).coerceIn(0.05f, 1f)
                    val s = lerp(start, 1f, p) * zoom.value
                    scaleX = s
                    scaleY = s
                    translationX = lerp(from.center.x - origin.x - width / 2f, 0f, p) + pan.x
                    translationY = lerp(from.center.y - origin.y - height / 2f, 0f, p) + pan.y
                    alpha = 0.2f + 0.8f * p
                },
        ) {
            // The small picture shows at once; the big one replaces it when it is read.
            (full ?: thumb)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
    }
}
