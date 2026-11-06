package com.metrowin.launcher.files

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The small pictures of the tile view: decoded away from the screen at a size that fits a tile, and kept for the next time. */
object FilesThumbs {
    private const val SIDE = 192
    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    /** Whether [file] is one the phone can show as a picture; a file that cannot be decoded gives null at [load] all the same. */
    fun isPicture(file: File): Boolean = file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

    private fun decode(file: File): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIDE && bounds.outHeight / (sample * 2) >= SIDE) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()

    @Composable
    fun load(file: File): ImageBitmap? {
        val key = file.path + "|" + file.lastModified()
        val bitmap by produceState(cache.get(key), key) {
            if (value == null) value = withContext(Dispatchers.IO) { decode(file) }?.also { cache.put(key, it) }
        }
        return bitmap
    }
}
