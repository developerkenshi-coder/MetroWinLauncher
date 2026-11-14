package com.metrowin.launcher.photos

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The small pictures of the grids: made by MediaStore (it keeps its own), away from the screen, and kept for the next time. A picture that
 * cannot be read stays a plain square. The cache is counted in bytes (an eighth of the memory of the app, 24 MB at most), keyed by the picture
 * and the moment it was last changed, so an edited picture does not keep its old thumbnail.
 */
object PhotoThumbs {
    private const val SIDE = 256

    private data class Key(val id: Long, val modified: Long)

    internal fun budget(maxMemory: Long): Int = minOf(24L * 1024 * 1024, maxMemory / 8).toInt()

    private val cache = object : LruCache<Key, ImageBitmap>(budget(Runtime.getRuntime().maxMemory())) {
        override fun sizeOf(key: Key, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** The phone is short of memory: half of the cache goes, or all of it at the worst levels. */
    fun trim(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) cache.evictAll()
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) cache.trimToSize(cache.size() / 2)
    }

    @Suppress("DEPRECATION")
    private fun decode(context: Context, id: Long): ImageBitmap? = runCatching {
        val bitmap: Bitmap? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.loadThumbnail(PhotoReader.uriOf(id), Size(SIDE, SIDE), null)
            else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
        bitmap?.asImageBitmap()
    }.getOrNull()

    fun cached(photo: Photo): ImageBitmap? = cache.get(Key(photo.id, photo.modified))

    @Composable
    fun load(photo: Photo): ImageBitmap? {
        val context = LocalContext.current.applicationContext
        val key = Key(photo.id, photo.modified)
        val bitmap by produceState(cache.get(key), key) {
            if (value == null) value = withContext(Dispatchers.IO) { decode(context, key.id) }?.also { cache.put(key, it) }
        }
        return bitmap
    }
}
