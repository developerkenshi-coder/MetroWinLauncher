package com.metrowin.launcher.wallet

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MarkIcon
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/** The size to read the picture of the camera at: the largest that is no bigger than [maxWidth] by [maxHeight] (a bigger one only slows the reading down). Pure, so it is tested. */
internal fun bestSize(sizes: List<Pair<Int, Int>>, maxWidth: Int = 1280, maxHeight: Int = 960): Pair<Int, Int>? =
    sizes.filter { it.first <= maxWidth && it.second <= maxHeight }.maxByOrNull { it.first.toLong() * it.second } ?: sizes.minByOrNull { it.first.toLong() * it.second }

/** The code the camera sees: reads one frame in a few, off the main thread, until [onFound] is called with what it read. */
private class FrameReader(private val onFound: (String, CodeFormat) -> Unit) {
    private val reader = MultiFormatReader().apply { setHints(Wallet.readHints()) }
    private var last = 0L
    private val main = Handler(Looper.getMainLooper())

    fun read(image: android.media.Image) {
        val now = System.currentTimeMillis()
        if (now - last < FRAME_GAP_MS) return
        last = now
        val plane = image.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
        val found = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))) }.getOrNull()
        reader.reset()
        val format = found?.let { CodeFormat.of(it.barcodeFormat) } ?: return
        main.post { onFound(found.text, format) }
    }

    private companion object {
        const val FRAME_GAP_MS = 120L
    }
}

/** What the back camera can do, asked once and off the main thread. */
private class CameraInfo(
    val id: String, val analysis: Pair<Int, Int>, val preview: Pair<Int, Int>, val sensor: Int,
    val flash: Boolean, val maxZoom: Float, val active: Rect?, val focusRegions: Boolean,
)

private sealed interface Setup {
    data object Loading : Setup
    data object None : Setup
    class Ready(val info: CameraInfo) : Setup
}

private fun cameraInfo(manager: CameraManager): CameraInfo? = runCatching {
    val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: return null
    val c = manager.getCameraCharacteristics(id)
    val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val analysis = bestSize(map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty().map { it.width to it.height }) ?: return null
    val ratio = analysis.first.toFloat() / analysis.second
    val preview = bestSize(map?.getOutputSizes(SurfaceTexture::class.java).orEmpty().filter { kotlin.math.abs(it.width.toFloat() / it.height - ratio) < 0.02f }.map { it.width to it.height }) ?: return null
    CameraInfo(
        id, analysis, preview, c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90, c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
        minOf(c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f, MAX_ZOOM), c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
        (c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0) > 0,
    )
}.getOrNull()

private const val MAX_ZOOM = 4f

/**
 * One open camera. It can be closed and opened again (the phone goes to the background, the surface is lost), and a camera that opens after
 * it was closed is closed at once: each [open] has a number and the answers of an older one are ignored, so a camera never stays open with nobody to use it.
 */
private class CameraSession(
    private val manager: CameraManager, private val info: CameraInfo, private val handler: Handler, private val reader: Surface, private val onFailed: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var preview: Surface? = null
    private var torch = false
    private var zoom = 1f
    private var focus: MeteringRectangle? = null

    @SuppressLint("MissingPermission") // asked by the caller: CodeScanDialog
    @Synchronized
    fun open(surface: Surface) {
        close()
        val mine = ++generation
        preview = surface
        try {
            manager.openCamera(info.id, Opened(mine), handler)
        } catch (_: Exception) {
            fail(mine)
        }
    }

    @Synchronized
    fun close() {
        generation++
        runCatching { device?.close() }
        runCatching { preview?.release() }
        device = null
        session = null
        preview = null
    }

    @Synchronized
    fun setTorch(on: Boolean) { torch = on; apply() }

    @Synchronized
    fun setZoom(value: Float) { zoom = value.coerceIn(1f, info.maxZoom); apply() }

    /** Focus and meter on the point (u, v) of the picture as it is shown, 0 to 1 from the top left; [turn] is how far (in degrees, clockwise) the sensor picture is turned to be shown. */
    @Synchronized
    fun focusAt(u: Float, v: Float, turn: Int) {
        val (su, sv) = when (turn) {
            90 -> v to 1 - u
            180 -> 1 - u to 1 - v
            270 -> 1 - v to u
            else -> u to v
        }
        val active = info.active
        if (info.focusRegions && active != null) {
            val half = (minOf(active.width(), active.height()) * 0.06f).toInt()
            val x = (active.left + su * active.width()).toInt().coerceIn(active.left + half, active.right - half)
            val y = (active.top + sv * active.height()).toInt().coerceIn(active.top + half, active.bottom - half)
            focus = MeteringRectangle(x - half, y - half, 2 * half, 2 * half, MeteringRectangle.METERING_WEIGHT_MAX - 1)
        }
        apply()
        val s = session ?: return
        runCatching { s.capture(request(trigger = true)?.build() ?: return, null, handler) }
    }

    private fun request(trigger: Boolean): CaptureRequest.Builder? {
        val camera = device ?: return null
        val surface = preview ?: return null
        return camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface); addTarget(reader)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            if (trigger) set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            if (torch) set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            info.active?.takeIf { zoom > 1f }?.let { a ->
                val w = (a.width() / zoom).toInt()
                val h = (a.height() / zoom).toInt()
                set(CaptureRequest.SCALER_CROP_REGION, Rect(a.centerX() - w / 2, a.centerY() - h / 2, a.centerX() + w / 2, a.centerY() + h / 2))
            }
            focus?.let { set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(it)); set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(it)) }
        }
    }

    @Synchronized
    private fun apply() {
        val s = session ?: return
        runCatching { s.setRepeatingRequest(request(trigger = false)?.build() ?: return, null, handler) }
    }

    private fun fail(mine: Int) {
        synchronized(this) { if (mine != generation) return }
        main.post(onFailed)
    }

    private inner class Opened(private val mine: Int) : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) = synchronized(this@CameraSession) {
            val surface = preview
            if (mine != generation || surface == null) {
                camera.close()
                return
            }
            device = camera
            @Suppress("DEPRECATION")
            runCatching { camera.createCaptureSession(listOf(surface, reader), Configured(mine), handler) }.onFailure { camera.close(); fail(mine) }
            Unit
        }

        override fun onDisconnected(camera: CameraDevice) { camera.close(); fail(mine) }
        override fun onError(camera: CameraDevice, error: Int) { camera.close(); fail(mine) }
        override fun onClosed(camera: CameraDevice) = synchronized(this@CameraSession) { if (device === camera) device = null }
    }

    private inner class Configured(private val mine: Int) : CameraCaptureSession.StateCallback() {
        override fun onConfigured(s: CameraCaptureSession) = synchronized(this@CameraSession) {
            if (mine != generation) {
                s.close()
                return
            }
            session = s
            apply()
        }

        override fun onConfigureFailed(s: CameraCaptureSession) { s.close(); fail(mine) }
    }
}

/** What a TextureView needs to show the picture of the camera upright when the phone is turned across (the picture comes turned to the sensor). */
private fun transform(view: TextureView, width: Int, height: Int, size: Pair<Int, Int>, rotation: Int) {
    if (rotation != Surface.ROTATION_90 && rotation != Surface.ROTATION_270) return
    val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val bufferRect = RectF(0f, 0f, size.second.toFloat(), size.first.toFloat())
    bufferRect.offset(viewRect.centerX() - bufferRect.centerX(), viewRect.centerY() - bufferRect.centerY())
    val matrix = Matrix().apply { setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL) }
    val scale = maxOf(height.toFloat() / size.second, width.toFloat() / size.first)
    matrix.postScale(scale, scale, viewRect.centerX(), viewRect.centerY())
    matrix.postRotate(90f * (rotation - 2), viewRect.centerX(), viewRect.centerY())
    view.setTransform(matrix)
}

/** The live view of the back camera, with the frame to put a code in; [onFound] gets the first code that can be read. */
@Composable
private fun CameraCodeView(extras: Boolean, motion: Boolean, onFound: (String, CodeFormat) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(CameraManager::class.java) }
    // The list of cameras and their sizes is asked off the main thread.
    val setup by produceState<Setup>(Setup.Loading) { value = withContext(Dispatchers.IO) { cameraInfo(manager)?.let { Setup.Ready(it) } ?: Setup.None } }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    when (val s = setup) {
        Setup.Loading -> Box(modifier)
        Setup.None -> Box(modifier, contentAlignment = Alignment.Center) { MetroText(stringResource(R.string.wal_scan_no_camera), size = 18.sp, color = Color.White, maxLines = 4, modifier = Modifier.padding(24.dp)) }
        is Setup.Ready -> if (failed) {
            Column(modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                MetroText(stringResource(R.string.wal_scan_error), size = 18.sp, color = Color.White, maxLines = 4)
                MetroButton(stringResource(R.string.wal_scan_retry), Modifier.testTag("wal:scan:retry")) { failed = false; attempt++ }
            }
        } else key(attempt) { CameraRunning(s.info, manager, extras, motion, { failed = true }, onFound, modifier) }
    }
}

@Composable
private fun CameraRunning(info: CameraInfo, manager: CameraManager, extras: Boolean, motion: Boolean, onFailed: () -> Unit, onFound: (String, CodeFormat) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val found by rememberUpdatedState(onFound)
    val failed by rememberUpdatedState(onFailed)
    @Suppress("DEPRECATION") val rotation = (context.getSystemService(android.view.WindowManager::class.java).defaultDisplay).rotation
    // The picture comes in the way the sensor sits: across, for most phones; the view is turned to the way the phone is held.
    val portrait = (info.sensor % 180 == 90) == (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
    val ratio = if (portrait) info.preview.second.toFloat() / info.preview.first else info.preview.first.toFloat() / info.preview.second
    val turn = (info.sensor - rotation * 90 + 360) % 360
    val thread = remember { HandlerThread("wallet-scan").apply { start() } }
    val handler = remember { Handler(thread.looper) }
    val imageReader = remember { ImageReader.newInstance(info.analysis.first, info.analysis.second, ImageFormat.YUV_420_888, 2) }
    val frames = remember { FrameReader { text, format -> found(text, format) } }
    val session = remember { CameraSession(manager, info, handler, imageReader.surface) { failed() } }
    val texture = remember { AtomicReference<SurfaceTexture?>() }
    var torch by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var ring by remember { mutableStateOf<Offset?>(null) }
    DisposableEffect(Unit) {
        imageReader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { frames.read(it) } }, handler)
        onDispose {
            session.close()
            imageReader.close()
            thread.quitSafely()
        }
    }
    // Out of sight the camera is given back; when the screen is there again it is opened anew.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session.close() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { texture.get()?.let { session.open(Surface(it)) } }
    LaunchedEffect(ring) { if (ring != null) { delay(700); ring = null } }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).then(
                if (!extras) Modifier else Modifier
                    .pointerInput(Unit) { detectTapGestures { o -> ring = o; session.focusAt(o.x / size.width, o.y / size.height, turn) } }
                    .pointerInput(Unit) { detectTransformGestures { _, _, change, _ -> zoom = (zoom * change).coerceIn(1f, info.maxZoom); session.setZoom(zoom) } },
            ),
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(t: SurfaceTexture, width: Int, height: Int) {
                                texture.set(t)
                                t.setDefaultBufferSize(info.preview.first, info.preview.second)
                                transform(this@apply, width, height, info.preview, rotation)
                                session.open(Surface(t))
                            }
                            override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, width: Int, height: Int) = transform(this@apply, width, height, info.preview, rotation)
                            override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean {
                                texture.set(null)
                                session.close()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(t: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            ring?.let { o -> Canvas(Modifier.fillMaxSize()) { drawCircle(Color.White, 28.dp.toPx(), o, style = Stroke(2.dp.toPx())) } }
        }
        ScanFrame(motion)
        if (extras && info.flash) {
            val label = stringResource(R.string.wal_scan_torch)
            MetroText(
                label, size = 18.sp, color = if (torch) Color.Black else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp).testTag("wal:scan:torch")
                    .background(if (torch) Color.White else Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { torch = !torch; session.setTorch(torch) } }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** The frame to put a code in: four corners in the accent colour, and, when the animations are on, a line that sweeps it. */
@Composable
private fun ScanFrame(motion: Boolean) {
    val accent = LocalAccent.current
    val sweep = if (motion && !LocalReduceMotion.current) rememberInfiniteTransition(label = "laser").animateFloat(0.08f, 0.92f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "y").value else null
    Canvas(Modifier.size(SCAN_FRAME)) {
        val arm = 28.dp.toPx()
        val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Square)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(accent, Offset(x, y), Offset(x + dx * arm, y), stroke.width, stroke.cap)
            drawLine(accent, Offset(x, y), Offset(x, y + dy * arm), stroke.width, stroke.cap)
        }
        corner(0f, 0f, 1f, 1f); corner(size.width, 0f, -1f, 1f); corner(0f, size.height, 1f, -1f); corner(size.width, size.height, -1f, -1f)
        sweep?.let { drawLine(accent.copy(alpha = 0.8f), Offset(8.dp.toPx(), size.height * it), Offset(size.width - 8.dp.toPx(), size.height * it), 2.dp.toPx()) }
    }
}

/** The mark of a code that was read: a ring that swells and a tick inside. */
@Composable
private fun FoundMark(motion: Boolean) {
    val accent = LocalAccent.current
    val still = LocalReduceMotion.current
    val grow = remember { Animatable(if (motion && !still) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
    Box(Modifier.size(64.dp).scale(grow.value).border(3.dp, accent, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) { MarkIcon(Mark.Check, 34.dp, color = accent) }
}

/**
 * Adding a card by pointing the camera at its code: a full-screen view asks for the camera (once), shows what it sees with a frame, and
 * when a code is read shows it again as the wallet would draw it (so the user can see it is the right one) with "use" and "scan again".
 */
@Composable
fun CodeScanDialog(onUse: (String, CodeFormat) -> Unit, onCancel: () -> Unit, secure: Boolean = true, extras: Boolean = true, motion: Boolean = true) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val haptic = LocalHapticFeedback.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var result by remember { mutableStateOf<Pair<String, CodeFormat>?>(null) }
    DisposableEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA); onDispose { } }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogWindowSetup(secure)
        BackHandler { onCancel() }
        // Black behind the camera; the page of the launcher behind the questions and the answer.
        val live = allowed && result == null
        Box(Modifier.fillMaxSize().background(if (live) Color.Black else colors.background).testTag("wal:scan")) {
            when {
                !allowed -> Column(Modifier.align(Alignment.Center).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetroText(stringResource(R.string.wal_scan_why), size = 18.sp, maxLines = 5)
                    MetroButton(stringResource(R.string.photos_allow), Modifier.testTag("wal:scan:allow")) { ask.launch(Manifest.permission.CAMERA) }
                }
                result == null -> {
                    CameraCodeView(extras, motion, { text, format -> if (extras) haptic.performHapticFeedback(HapticFeedbackType.LongPress); result = text to format }, Modifier.fillMaxSize())
                    MetroText(stringResource(R.string.wal_scan_hint), size = 18.sp, color = Color.White, weight = FontWeight.Normal, maxLines = 3, modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(32.dp))
                }
                else -> Column(Modifier.align(Alignment.Center).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val (text, format) = result!!
                    FoundMark(motion)
                    MetroText(stringResource(R.string.wal_scan_found), size = 24.sp)
                    // The code as the card will show it, to compare with the one in the hand.
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                        CodeImage(text, format, Modifier.fillMaxWidth().testTag("wal:scan:preview"))
                        MetroText(text, size = 16.sp, weight = FontWeight.Normal, color = Color.Black, maxLines = 3, modifier = Modifier.padding(top = 8.dp))
                    }
                    MetroText(formatName(format), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                    MetroButton(stringResource(R.string.wal_scan_use), Modifier.fillMaxWidth().testTag("wal:scan:use")) { onUse(Wallet.completed(text, format), format) }
                    MetroButton(stringResource(R.string.wal_scan_again), Modifier.fillMaxWidth().testTag("wal:scan:again")) { result = null }
                }
            }
        }
    }
}
