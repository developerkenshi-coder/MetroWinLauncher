package com.metrowin.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Loads app icons as flat squares, the shape of every Metro tile and app list entry. */
class IconLoader(context: Context) {
    private val pm = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val owner = Process.myUserHandle()
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun clear() = cache.evictAll()

    /** Null for entries that have no icon of their own (stock apps drawn with a glyph). */
    suspend fun load(item: AppItem, sizePx: Int): Bitmap? {
        val activity = item.launch as? Launch.Activity ?: return null
        val key = "${item.id}@$sizePx"
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                render(iconOf(activity), sizePx).also { if (item.isWork) drawWorkBadge(it) }
            }.getOrNull()
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private fun iconOf(activity: Launch.Activity): Drawable =
        if (activity.user == owner) {
            pm.getActivityIcon(activity.component)
        } else {
            launcherApps.getActivityList(activity.component.packageName, activity.user)
                .first { it.componentName == activity.component }
                .getIcon(0)
        }

    /** The blue briefcase corner that Android puts on work profile apps. */
    private fun drawWorkBadge(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val size = bitmap.width.toFloat()
        val badge = size * WORK_BADGE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WORK_BLUE }
        canvas.drawRect(size - badge, size - badge, size, size, paint)
        paint.apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = badge * 0.09f }
        val pad = badge * 0.22f
        canvas.drawRect(size - badge + pad, size - badge + pad * 1.9f, size - pad, size - pad, paint)
        canvas.drawRect(size - badge * 0.62f, size - badge + pad, size - badge * 0.38f, size - badge + pad * 1.9f, paint)
    }

    private fun render(drawable: Drawable, size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            // An adaptive icon is 108dp wide and only the centre 72dp is guaranteed to show. Draw the
            // layers across that centre without the system mask: square, like a Metro tile.
            val full = (size * ADAPTIVE_CANVAS / ADAPTIVE_VISIBLE).roundToInt()
            val offset = -(full - size) / 2
            listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
                layer.setBounds(offset, offset, offset + full, offset + full)
                layer.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        return bitmap
    }

    private companion object {
        const val CACHE_BYTES = 16 * 1024 * 1024
        const val ADAPTIVE_CANVAS = 108f
        const val ADAPTIVE_VISIBLE = 72f
        const val WORK_BADGE = 0.36f
        const val WORK_BLUE = 0xFF1A73E8.toInt()
    }
}
