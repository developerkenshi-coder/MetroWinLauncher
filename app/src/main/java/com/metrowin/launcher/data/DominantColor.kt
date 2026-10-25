package com.metrowin.launcher.data

/**
 * Picks the accent colour that suits a picture. It works on plain pixel arrays, so it can be unit tested
 * without Android bitmaps.
 */
object DominantColor {
    private const val HUE_BUCKETS = 12
    private const val MIN_SATURATION = 0.25f
    private const val MIN_VALUE = 0.2f

    /** A colour has to cover this much of the picture (in weight) to count: a few stray pixels do not make a theme. */
    private const val MIN_COVERAGE = 0.01f

    /**
     * The colour the picture is about, as ARGB, or null when it has none worth a theme (grey, black, white).
     * Pixels are weighted by how saturated and bright they are, grouped by hue, and the heaviest hue wins:
     * a grey photo with a little green in it is green, not grey.
     */
    fun of(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        val weight = FloatArray(HUE_BUCKETS)
        val red = FloatArray(HUE_BUCKETS)
        val green = FloatArray(HUE_BUCKETS)
        val blue = FloatArray(HUE_BUCKETS)
        for (pixel in pixels) {
            if (pixel ushr 24 < 128) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (max == 0) continue
            val value = max / 255f
            val saturation = (max - min).toFloat() / max
            if (saturation < MIN_SATURATION || value < MIN_VALUE) continue
            val w = saturation * value
            val bucket = (hue(r, g, b, max, min) / (360f / HUE_BUCKETS)).toInt().coerceIn(0, HUE_BUCKETS - 1)
            weight[bucket] += w
            red[bucket] += w * r
            green[bucket] += w * g
            blue[bucket] += w * b
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return null
        if (weight[best] / pixels.size < MIN_COVERAGE) return null
        val total = weight[best]
        return (0xFF shl 24) or
            ((red[best] / total).toInt() shl 16) or
            ((green[best] / total).toInt() shl 8) or
            (blue[best] / total).toInt()
    }

    private fun hue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
        val delta = (max - min).toFloat()
        if (delta == 0f) return 0f
        val h = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f
        return if (h < 0f) h + 360f else h
    }

    /** Index in [accents] (ARGB colours) of the one closest to [argb], by the "redmean" approximation of how the eye sees colour. */
    fun nearestIndex(argb: Int, accents: List<Int>): Int {
        require(accents.isNotEmpty())
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return accents.indices.minBy { index ->
            val c = accents[index]
            val cr = (c shr 16) and 0xFF
            val cg = (c shr 8) and 0xFF
            val cb = c and 0xFF
            val mean = (r + cr) / 2
            val dr = r - cr
            val dg = g - cg
            val db = b - cb
            (((512 + mean) * dr * dr) shr 8) + 4 * dg * dg + (((767 - mean) * db * db) shr 8)
        }
    }
}
