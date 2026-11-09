package com.metrowin.launcher.wallet

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent

/** The size to read the picture of the camera at: the largest that is no bigger than [maxWidth] by [maxHeight] (a bigger one only slows the reading down). Pure, so it is tested. */
internal fun bestSize(sizes: List<Pair<Int, Int>>, maxWidth: Int = 1280, maxHeight: Int = 960): Pair<Int, Int>? =
    sizes.filter { it.first <= maxWidth && it.second <= maxHeight }.maxByOrNull { it.first.toLong() * it.second } ?: sizes.minByOrNull { it.first.toLong() * it.second }

/** The code the camera sees: reads one frame in a few, off the main thread, until [onFound] is called with what it read. */
private class FrameReader(private val onFound: (String, CodeFormat) -> Unit) {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to CodeFormat.entries.map { it.zxing }))
    }
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

/** The live view of the back camera, with the frame to put a code in; [onFound] gets the first code that can be read. */
@SuppressLint("MissingPermission") // asked by the caller: CodeScanDialog
@Composable
private fun CameraCodeView(onFound: (String, CodeFormat) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val found by rememberUpdatedState(onFound)
    val manager = remember { context.getSystemService(CameraManager::class.java) }
    val id = remember { manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } }
    val characteristics = remember { id?.let(manager::getCameraCharacteristics) }
    val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val analysis = remember { bestSize(map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty().map { it.width to it.height }) }
    val preview = remember {
        val ratio = analysis?.let { it.first.toFloat() / it.second }
        bestSize(map?.getOutputSizes(SurfaceTexture::class.java).orEmpty().filter { ratio == null || kotlin.math.abs(it.width.toFloat() / it.height - ratio) < 0.02f }.map { it.width to it.height })
    }
    if (id == null || analysis == null || preview == null) {
        Box(modifier, contentAlignment = Alignment.Center) { MetroText(stringResource(R.string.wal_scan_no_camera), size = 18.sp, color = Color.White, maxLines = 4, modifier = Modifier.padding(24.dp)) }
        return
    }
    val sensor = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    @Suppress("DEPRECATION") val rotation = (context.getSystemService(android.view.WindowManager::class.java).defaultDisplay).rotation
    // The picture comes in the way the sensor sits: across, for most phones; the view is turned to the way the phone is held.
    val portrait = (sensor % 180 == 90) == (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
    val ratio = if (portrait) preview.second.toFloat() / preview.first else preview.first.toFloat() / preview.second
    val thread = remember { HandlerThread("wallet-scan").apply { start() } }
    val handler = remember { Handler(thread.looper) }
    var device by remember { mutableStateOf<CameraDevice?>(null) }
    val imageReader = remember { ImageReader.newInstance(analysis.first, analysis.second, ImageFormat.YUV_420_888, 2) }
    val frames = remember { FrameReader { text, format -> found(text, format) } }
    DisposableEffect(Unit) {
        imageReader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { frames.read(it) } }, handler)
        onDispose {
            runCatching { device?.close() }
            imageReader.close()
            thread.quitSafely()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            texture.setDefaultBufferSize(preview.first, preview.second)
                            transform(this@apply, width, height, preview, rotation)
                            open(ctx, manager, id, Surface(texture), imageReader.surface, handler) { device = it }
                        }
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = transform(this@apply, width, height, preview, rotation)
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
        )
        // The frame: four corners in the accent colour, where the code goes.
        Box(Modifier.size(260.dp).border(2.dp, LocalAccent.current))
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

@SuppressLint("MissingPermission")
private fun open(context: Context, manager: CameraManager, id: String, preview: Surface, reader: Surface, handler: Handler, onOpen: (CameraDevice) -> Unit) {
    runCatching {
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                onOpen(camera)
                val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(preview); addTarget(reader)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
                @Suppress("DEPRECATION")
                camera.createCaptureSession(listOf(preview, reader), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) { runCatching { session.setRepeatingRequest(request.build(), null, handler) } }
                    override fun onConfigureFailed(session: CameraCaptureSession) = camera.close()
                }, handler)
            }
            override fun onDisconnected(camera: CameraDevice) = camera.close()
            override fun onError(camera: CameraDevice, error: Int) = camera.close()
        }, handler)
    }
}

/**
 * Adding a card by pointing the camera at its code: a full-screen view asks for the camera (once), shows what it sees with a frame, and
 * when a code is read shows it again as the wallet would draw it (so the user can see it is the right one) with "use" and "scan again".
 */
@Composable
fun CodeScanDialog(onUse: (String, CodeFormat) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var result by remember { mutableStateOf<Pair<String, CodeFormat>?>(null) }
    DisposableEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA); onDispose { } }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { onCancel() }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("wal:scan")) {
            when {
                !allowed -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetroText(stringResource(R.string.wal_scan_why), size = 18.sp, color = Color.White, maxLines = 5)
                    MetroButton(stringResource(R.string.photos_allow), Modifier.testTag("wal:scan:allow")) { ask.launch(Manifest.permission.CAMERA) }
                }
                result == null -> {
                    CameraCodeView({ text, format -> result = text to format }, Modifier.fillMaxSize())
                    MetroText(stringResource(R.string.wal_scan_hint), size = 18.sp, color = Color.White, weight = FontWeight.Normal, maxLines = 3, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
                }
                else -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val (text, format) = result!!
                    MetroText(stringResource(R.string.wal_scan_found), size = 24.sp, color = Color.White)
                    // The code as the card will show it, to compare with the one in the hand.
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                        CodeImage(text, format, Modifier.fillMaxWidth().testTag("wal:scan:preview"))
                        MetroText(text, size = 16.sp, weight = FontWeight.Normal, color = Color.Black, maxLines = 3, modifier = Modifier.padding(top = 8.dp))
                    }
                    MetroText(format.name, size = 14.sp, weight = FontWeight.Normal, color = Color.White.copy(alpha = 0.7f))
                    MetroButton(stringResource(R.string.wal_scan_use), Modifier.fillMaxWidth().testTag("wal:scan:use")) { onUse(text, format) }
                    MetroButton(stringResource(R.string.wal_scan_again), Modifier.fillMaxWidth().testTag("wal:scan:again")) { result = null }
                }
            }
        }
    }
}
