package com.metrowin.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap

/**
 * The wallpaper of Android that the launcher puts behind everything it does not draw: the screen of the recent apps shows it behind the
 * cards, and it would be the wallpaper of the launcher the phone came with. With MetroWin as the home app it is the photo of Start (as
 * Start draws it) or, with no photo, the colour of the pages. Pure but for the drawing, so the placement is tested with numbers.
 */
object SystemWallpaper {
    /** The veil Start lays over its photo, as an alpha of black out of 255 (0.12). */
    private const val VEIL = 31

    /** Where a photo of [photoW] x [photoH] goes to cover [width] x [height] zoomed by [zoom] percent and kept at [x], [y] percent: the scale and the corner. */
    data class Placement(val scale: Float, val left: Float, val top: Float)

    fun fillPlacement(photoW: Int, photoH: Int, width: Int, height: Int, zoom: Int, x: Int, y: Int): Placement {
        val scale = maxOf(width.toFloat() / photoW, height.toFloat() / photoH) * zoom.coerceIn(PhotoFraming.MIN_ZOOM, PhotoFraming.MAX_ZOOM) / 100f
        // 0 keeps the left (top) edge of the photo in view, 100 the right (bottom) one: the same alignment as on Start.
        val left = -(photoW * scale - width) * x.coerceIn(0, 100) / 100f
        val top = -(photoH * scale - height) * y.coerceIn(0, 100) / 100f
        return Placement(scale, left, top)
    }

    /** The picture of the wallpaper: [photo] placed as [fit] says over [background], or only [background] when there is no photo. */
    fun render(photo: Bitmap?, fit: PhotoFit, zoom: Int, x: Int, y: Int, background: Int, width: Int, height: Int): Bitmap {
        val out = createBitmap(width, height)
        val canvas = Canvas(out)
        canvas.drawColor(background)
        if (photo == null) return out
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val pw = photo.width
        val ph = photo.height
        when (fit) {
            PhotoFit.Fill -> {
                val p = fillPlacement(pw, ph, width, height, zoom, x, y)
                canvas.drawBitmap(photo, Matrix().apply { postScale(p.scale, p.scale); postTranslate(p.left, p.top) }, paint)
            }
            PhotoFit.Fit -> {
                val scale = minOf(width.toFloat() / pw, height.toFloat() / ph)
                canvas.drawBitmap(photo, Matrix().apply { postScale(scale, scale); postTranslate((width - pw * scale) / 2f, (height - ph * scale) / 2f) }, paint)
            }
            PhotoFit.Stretch -> canvas.drawBitmap(photo, Matrix().apply { postScale(width.toFloat() / pw, height.toFloat() / ph) }, paint)
            PhotoFit.Center -> canvas.drawBitmap(photo, (width - pw) / 2f, (height - ph) / 2f, paint)
            PhotoFit.Tile -> canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = BitmapShader(photo, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) })
        }
        canvas.drawColor(VEIL shl 24)
        return out
    }

    /** What the picture is made of, so it is made again only when one of these changes. */
    fun key(settings: LauncherSettings, dark: Boolean, width: Int, height: Int): String =
        listOf(settings.backgroundRevision, settings.startPhotoFit.code, settings.startPhotoZoom, settings.startPhotoX, settings.startPhotoY, dark, width, height).joinToString(":")

    /** The size of the screen: the picture is drawn for it, as Start is; Android crops it as it needs to (some phones want it wider for a parallax). */
    fun size(context: Context): Pair<Int, Int> = context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }

    /** Sets [picture] as the wallpaper of Android (not the one of the lock screen). False when the phone does not allow it. */
    fun apply(context: Context, picture: Bitmap): Boolean = runCatching {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return false
        manager.setBitmap(picture, null, true, WallpaperManager.FLAG_SYSTEM)
        true
    }.getOrDefault(false)

    /** Gives the wallpaper back to the one Android has by itself. */
    fun clear(context: Context) {
        runCatching { WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_SYSTEM) }
    }
}
