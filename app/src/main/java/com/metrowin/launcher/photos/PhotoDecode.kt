package com.metrowin.launcher.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** The largest side of the picture the viewer keeps in memory. */
internal const val FULL_SIDE = 2048

/** The power of two by which a picture of [width] by [height] is shrunk so that its longest side is at most [side]. */
internal fun sampleFor(width: Int, height: Int, side: Int = FULL_SIDE): Int {
    var sample = 1
    while (max(width, height) / sample > side) sample *= 2
    return sample
}

/** What is known of a picture, for the "info" of the viewer. [camera], [exposure] and the like come from the EXIF and are empty when the picture has none. */
data class PhotoInfo(
    val name: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val mime: String = "",
    val camera: String = "",
    val exposure: String = "",
)

internal fun details(context: Context, id: Long): PhotoInfo? = runCatching {
    val uri = PhotoReader.uriOf(id)
    val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE)
    val base = context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
        if (c.moveToFirst()) PhotoInfo(c.getString(0).orEmpty(), c.getInt(1), c.getInt(2), c.getLong(3), c.getString(4).orEmpty()) else null
    } ?: return@runCatching null
    val exif = runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull() ?: return@runCatching base
    base.copy(camera = exifCamera(exif), exposure = exifExposure(exif))
}.getOrNull()

private fun exifCamera(exif: ExifInterface): String =
    listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL)).map { it.trim() }.distinct().joinToString(" ")

/** "f/1.8 · 1/120 s · ISO 100 · 5.4 mm", the parts the picture has. */
private fun exifExposure(exif: ExifInterface): String = exposureText(
    exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0), exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0),
    exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0), exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0),
)

internal fun exposureText(fNumber: Double, seconds: Double, iso: Int, focalMm: Double): String = listOfNotNull(
    fNumber.takeIf { it > 0 }?.let { "f/%.1f".format(java.util.Locale.getDefault(), it) },
    seconds.takeIf { it > 0 }?.let { if (it >= 1) "%.1f s".format(java.util.Locale.getDefault(), it) else "1/${(1 / it).roundToInt()} s" },
    iso.takeIf { it > 0 }?.let { "ISO $it" },
    focalMm.takeIf { it > 0 }?.let { "%.1f mm".format(java.util.Locale.getDefault(), it) },
).joinToString(" · ")

/**
 * The picture itself, as large as the screen can use ([FULL_SIDE] at most), read away from the screen, turned upright by its EXIF orientation.
 * From Android 9 the system decoder does the whole job in one pass over the stream (and reads HEIC too); before it the stream is opened twice.
 */
internal fun decodeFull(context: Context, id: Long, side: Int = FULL_SIDE): ImageBitmap? = runCatching {
    val uri = PhotoReader.uriOf(id)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > side) {
                val k = side.toFloat() / longest
                decoder.setTargetSize((info.size.width * k).roundToInt().coerceAtLeast(1), (info.size.height * k).roundToInt().coerceAtLeast(1))
            }
        }.asImageBitmap()
    } else legacyDecode(context, uri, side)?.asImageBitmap()
}.getOrNull()

private fun legacyDecode(context: Context, uri: Uri, side: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, side) }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val orientation = runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrNull()
    val matrix = matrixFor(orientation ?: ExifInterface.ORIENTATION_NORMAL) ?: return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** The turn and mirror that bring a picture with this EXIF [orientation] upright; null when it is already. */
internal fun matrixFor(orientation: Int): Matrix? = Matrix().takeIf {
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> it.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> it.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> it.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> it.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> it.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { it.postRotate(90f); it.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { it.postRotate(270f); it.postScale(-1f, 1f) }
        else -> return@takeIf false
    }
    true
}

/** The last few full pictures, so going back to one does not read it again; counted in pictures because they are all about the same size. */
internal object FullCache {
    private val cache = LruCache<Long, ImageBitmap>(3)
    fun get(id: Long): ImageBitmap? = cache.get(id)
    fun put(id: Long, bitmap: ImageBitmap) { cache.put(id, bitmap) }
    fun clear() = cache.evictAll()
}

/** The main tone of a picture, darkened until white text reads on it; null when it cannot be told. */
internal fun toneOf(bitmap: ImageBitmap): Color? = runCatching {
    val small = Bitmap.createScaledBitmap(bitmap.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false), 1, 1, true).getPixel(0, 0)
    Color(small).let { Color(it.red * 0.35f, it.green * 0.35f, it.blue * 0.35f) }
}.getOrNull()

/**
 * Copies of the pictures without their metadata (place, camera, date), in the cache folder the launcher shares from, for sharing; the copies of an
 * earlier sharing are removed first. PNG stays PNG (it may be transparent), the rest becomes JPEG. Throws when a copy cannot be made: nothing is shared then.
 */
internal fun strippedCopies(context: Context, photos: List<Photo>): List<Uri> {
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    folder.listFiles { f -> f.name.startsWith("photo-") }?.forEach { it.delete() }
    return photos.map { photo ->
        val uri = PhotoReader.uriOf(photo.id)
        val bitmap = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        } else legacyDecode(context, uri, 4096)) ?: error("no picture")
        val png = photo.mime == "image/png"
        val file = File(folder, "photo-${photo.id}.${if (png) "png" else "jpg"}")
        file.outputStream().use { bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
