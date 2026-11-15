package com.metrowin.launcher.ui.lock

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/** The shade under the lines of the lock screen: lighter on a dark photo, stronger on a bright one, so the white text is always read. */
object LockVeil {
    private const val SIDE = 24

    /** Share of the picture, from the top, that is not read: the text lives in the lower part. */
    private const val SKIP_TOP = 0.35f
    const val MIN_ALPHA = 0.3f
    const val MAX_ALPHA = 0.8f

    /** What the shade was before it followed the photo. */
    const val DEFAULT_ALPHA = 0.6f

    /** Mean luma (0..1) of the rows from [firstRow] down of a [width]-wide grid of ARGB pixels; 0.5 when there is nothing to read. */
    fun luma(pixels: IntArray, width: Int, firstRow: Int): Float {
        val from = firstRow * width
        if (width <= 0 || from >= pixels.size) return 0.5f
        var sum = 0.0
        for (i in from until pixels.size) {
            val p = pixels[i]
            sum += 0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)
        }
        return (sum / (pixels.size - from) / 255.0).toFloat()
    }

    /** The strength of the shade for a photo of mean [luma]: [MIN_ALPHA] up to a luma of 0.25, [MAX_ALPHA] from 0.7 up. */
    fun alpha(luma: Float): Float {
        val t = ((luma - 0.25f) / 0.45f).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    /** The strength of the shade for [photo]: read on a small copy, so it costs little; the usual one if the picture cannot be read. */
    fun alpha(photo: ImageBitmap): Float = runCatching {
        val source = photo.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        val small = Bitmap.createScaledBitmap(source, SIDE, SIDE, true)
        val pixels = IntArray(SIDE * SIDE)
        small.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)
        alpha(luma(pixels, SIDE, (SIDE * SKIP_TOP).toInt()))
    }.getOrDefault(DEFAULT_ALPHA)
}
