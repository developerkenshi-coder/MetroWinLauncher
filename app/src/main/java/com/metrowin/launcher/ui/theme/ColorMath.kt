package com.metrowin.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Colors in terms of hue, strength and lightness (HSV), for the sliders of the custom color, and in terms of tones for Material. */
object ColorMath {
    /** [hue] 0..360, [saturation] and [value] 0..1, as an opaque ARGB int. */
    fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(f: Float) = (((f + m) * 255f) + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** Hue (0..360), saturation and value (0..1) of an ARGB color. */
    fun argbToHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        val hue = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return floatArrayOf((hue + 360f) % 360f, if (maxC == 0f) 0f else delta / maxC, maxC)
    }

    /** Perceived brightness 0..1 (the weights of the sRGB channels), to choose between dark and white text on a color. */
    fun luminance(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun toColor(argb: Int) = Color(argb)

    /** [color] with its hue turned by [degrees]. */
    fun rotateHue(color: Color, degrees: Float): Color {
        val hsv = argbToHsv(color.toArgbInt())
        return Color(hsvToArgb(hsv[0] + degrees, hsv[1], hsv[2]))
    }
}

/** ARGB int of a [Color], without importing graphics into every caller. */
fun Color.toArgbInt(): Int =
    ((alpha * 255f + 0.5f).toInt() shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()
