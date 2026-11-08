package com.metrowin.launcher.data

import kotlin.math.abs

/** Turns an icon into translucent glass: the picture is kept as a pale, see-through shape, so what lies behind the tile shows. Plain pixel arrays, so it can be unit tested. */
object GlassIcon {
    private const val TINT = 0.3f
    private const val OPACITY = 0.85f
    private const val SHINE = 0.4f
    private const val SHINE_HEIGHT = 0.55f

    /** An icon whose border is this opaque is a filled square (a legacy icon): its fill is the background to drop. */
    private const val OPAQUE_BORDER = 0.9f

    /** Colour distance (0..255) under which a pixel is the background, and over which it is the picture. */
    private const val BG_NEAR = 24
    private const val BG_FAR = 96

    fun of(pixels: IntArray, width: Int, height: Int): IntArray {
        require(pixels.size == width * height)
        val bg = filledBackground(pixels, width, height)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            var a = (p ushr 24) / 255f
            if (bg != null) a *= ((distance(p, bg) - BG_NEAR).toFloat() / (BG_FAR - BG_NEAR)).coerceIn(0f, 1f)
            if (a == 0f) return@IntArray 0
            // Lit from above: the top of the glass is a little whiter.
            val shine = SHINE * (1f - (i / width).toFloat() / (height * SHINE_HEIGHT)).coerceAtLeast(0f)
            fun channel(shift: Int): Int {
                val c = (p shr shift) and 0xFF
                val glass = 255 * (1f - TINT) + c * TINT
                return (glass + (255 - glass) * shine).toInt().coerceIn(0, 255)
            }
            ((a * OPACITY * 255).toInt().coerceIn(0, 255) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }

    /** The colour (RGB) of the border when the icon is a filled square, else null. */
    private fun filledBackground(pixels: IntArray, width: Int, height: Int): Int? {
        var n = 0
        var opaque = 0
        var r = 0L
        var g = 0L
        var b = 0L
        for (y in 0 until height) for (x in 0 until width) {
            if (x > 1 && x < width - 2 && y > 1 && y < height - 2) continue
            val p = pixels[y * width + x]
            n++
            if (p ushr 24 >= 250) {
                opaque++
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
        }
        if (n == 0 || opaque < n * OPAQUE_BORDER) return null
        return ((r / opaque).toInt() shl 16) or ((g / opaque).toInt() shl 8) or (b / opaque).toInt()
    }

    private fun distance(p: Int, bg: Int): Int =
        (abs(((p shr 16) and 0xFF) - ((bg shr 16) and 0xFF)) + abs(((p shr 8) and 0xFF) - ((bg shr 8) and 0xFF)) + abs((p and 0xFF) - (bg and 0xFF))) / 3
}
