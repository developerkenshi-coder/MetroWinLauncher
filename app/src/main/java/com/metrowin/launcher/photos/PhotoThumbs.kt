package com.metrowin.launcher.photos

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

/** The small pictures of the grids: made by MediaStore (it keeps its own), away from the screen, and kept for the next time. A picture that cannot be read stays a plain square. */
object PhotoThumbs {
    private const val SIDE = 256
    private val cache = object : LruCache<Long, ImageBitmap>(120) {}

    @Suppress("DEPRECATION")
    private fun decode(context: Context, id: Long): ImageBitmap? = runCatching {
        val bitmap: Bitmap? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.loadThumbnail(PhotoReader.uriOf(id), Size(SIDE, SIDE), null)
            else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
        bitmap?.asImageBitmap()
    }.getOrNull()

    @Composable
    fun load(id: Long): ImageBitmap? {
        val context = LocalContext.current.applicationContext
        val bitmap by produceState(cache.get(id), id) {
            if (value == null) value = withContext(Dispatchers.IO) { decode(context, id) }?.also { cache.put(id, it) }
        }
        return bitmap
    }
}
