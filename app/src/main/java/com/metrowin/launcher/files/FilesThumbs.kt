package com.metrowin.launcher.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The small pictures of the tile view and of the list: decoded away from the screen at a size that fits, and kept for the next time. */
object FilesThumbs {
    private const val SIDE = 192
    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    /** Whether [file] is one the phone can show as a picture; a file that cannot be decoded gives null at [load] all the same. */
    fun isPicture(file: File): Boolean = file.extension.lowercase() in FileKinds.DECODABLE

    fun isVideo(file: File): Boolean = FileKinds.isVideo(file.name)

    fun isApk(file: File): Boolean = file.extension.equals("apk", true)

    fun isPdf(file: File): Boolean = file.extension.equals("pdf", true)

    private fun decode(file: File, side: Int): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()

    /** A frame of a video, as the phone gives it. */
    private fun frame(file: File): ImageBitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            retriever.getFrameAtTime(-1L)?.let { Bitmap.createScaledBitmap(it, SIDE, (SIDE * it.height / it.width.coerceAtLeast(1)).coerceAtLeast(1), true) }?.asImageBitmap()
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun apkIcon(context: android.content.Context, file: File): ImageBitmap? = runCatching {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.path, 0)?.applicationInfo ?: return null
        info.sourceDir = file.path
        info.publicSourceDir = file.path
        val drawable = info.loadIcon(pm)
        val bitmap = Bitmap.createBitmap(SIDE / 2, SIDE / 2, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(android.graphics.Canvas(bitmap))
        bitmap.asImageBitmap()
    }.getOrNull()

    /** The first page of a pdf, [width] pixels wide. */
    private fun pdfPage(file: File, width: Int): ImageBitmap? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(width, (width * page.height / page.width.coerceAtLeast(1)).coerceIn(1, width * 3), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
        }
    }.getOrNull()

    /**
     * The small copy of [file]: a picture; with [rich] also a frame of a video or the icon of an apk. [side] is the size of a picture
     * asked for (a preview asks for more than a tile).
     */
    @Composable
    fun load(file: File, rich: Boolean = false, side: Int = SIDE): ImageBitmap? {
        val context = LocalContext.current
        val key = file.path + "|" + file.lastModified() + "|" + side + (if (rich) "r" else "")
        val bitmap by produceState(cache.get(key), key) {
            if (value == null) value = withContext(Dispatchers.IO) {
                when {
                    isPicture(file) -> decode(file, side)
                    rich && isVideo(file) -> frame(file)
                    rich && isApk(file) -> apkIcon(context, file)
                    else -> null
                }
            }?.also { cache.put(key, it) }
        }
        return bitmap
    }

    /** The first page of the pdf [file], for a preview. */
    @Composable
    fun page(file: File, width: Int = 480): ImageBitmap? {
        val key = file.path + "|" + file.lastModified() + "|pdf" + width
        val bitmap by produceState(cache.get(key), key) {
            if (value == null) value = withContext(Dispatchers.IO) { pdfPage(file, width) }?.also { cache.put(key, it) }
        }
        return bitmap
    }

    /** A picture as big as a screen, for the viewer. */
    fun large(file: File): ImageBitmap? = decode(file, 1600)
}
