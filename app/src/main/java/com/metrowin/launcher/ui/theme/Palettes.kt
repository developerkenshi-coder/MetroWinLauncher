package com.metrowin.launcher.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/** A ramp of one color from nearly black (tone 0) to nearly white (tone 100), the way Material builds its palettes. */
interface Tonal {
    fun tone(tone: Int): Color
}

/**
 * A ramp made from a seed color: the hue is kept and the tone is the lightness as the eye sees it (the L* of CIE Lab,
 * which is what Material's tones are), so tone 90 text on tone 30 is as readable for yellow as for blue. Not the full HCT
 * of Material, but the same promise about contrast; the strength is eased at the ends so the lightest and darkest
 * tones are not neon.
 */
class SeedTonal(private val hue: Float, private val saturation: Float) : Tonal {
    override fun tone(tone: Int): Color {
        val t = tone.coerceIn(0, 100)
        if (t == 0) return Color.Black
        if (t == 100) return Color.White
        val h = ((hue % 360f) + 360f) % 360f
        val ease = 0.35f + 0.65f * (1f - abs(t - 50) / 50f)
        val s = (saturation * ease).coerceIn(0f, 1f)
        // Lightness grows with tone for a fixed hue and strength, so a bisection finds the one that gives this tone.
        var low = 0f
        var high = 1f
        repeat(18) {
            val mid = (low + high) / 2f
            if (lightness(Color.hsl(h, s, mid)) < t) low = mid else high = mid
        }
        return Color.hsl(h, s, (low + high) / 2f)
    }

    /** L* of CIE Lab, 0 (black) to 100 (white). */
    private fun lightness(color: Color): Float {
        fun linear(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        val y = 0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)
        val f = if (y > 216f / 24389f) y.toDouble().pow(1.0 / 3.0).toFloat() else (24389f / 27f * y + 16f) / 116f
        return 116f * f - 16f
    }
}

/** The ramps Android makes from the wallpaper, which come in a few fixed shades (0, 10, 50, 100, 200 … 1000). */
class SystemTonal(private val shade: (Int) -> Color) : Tonal {
    override fun tone(tone: Int): Color = shade(SystemShades.forTone(tone))
}

object SystemShades {
    val all = listOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)

    /** The system shade closest to a Material tone: tone 90 is shade 100, tone 40 is shade 600, tone 98 is shade 10. */
    fun forTone(tone: Int): Int {
        val wanted = 1000 - tone.coerceIn(0, 100) * 10
        return all.minBy { abs(it - wanted) }
    }
}

/** The five ramps that Material You colors are built from. */
@Immutable
class DroidPalette(val accent1: Tonal, val accent2: Tonal, val accent3: Tonal, val neutral1: Tonal, val neutral2: Tonal)

object Palettes {
    /** A palette made from [seed]: the accent ramp, a quiet cousin, a hue turned a little for the third, and tinted greys. */
    fun fromSeed(seed: Color): DroidPalette {
        val hsv = ColorMath.argbToHsv(seed.toArgbInt())
        val hue = hsv[0]
        val saturation = hsv[1].coerceIn(0.25f, 0.9f)
        return DroidPalette(
            accent1 = SeedTonal(hue, saturation),
            accent2 = SeedTonal(hue, saturation * 0.35f),
            accent3 = SeedTonal(hue + 60f, saturation * 0.6f),
            neutral1 = SeedTonal(hue, 0.06f),
            neutral2 = SeedTonal(hue, 0.12f),
        )
    }

    /** The palette Android makes from the wallpaper (Android 12 and later), or null on an older phone. */
    fun fromSystem(context: Context): DroidPalette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return systemPalette(context)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun systemPalette(context: Context): DroidPalette {
        fun ramp(resource: (Int) -> Int) = SystemTonal { shade -> Color(context.getColor(resource(shade))) }
        return DroidPalette(ramp(::accent1), ramp(::accent2), ramp(::accent3), ramp(::neutral1), ramp(::neutral2))
    }

    /** The colors of Modern Droid in light or dark: surfaces from the neutral ramps, three tonal containers for the tiles. */
    fun colors(palette: DroidPalette, dark: Boolean): MetroColors {
        val a = listOf(palette.accent1, palette.accent2, palette.accent3)
        return if (dark) {
            MetroColors(
                dark = true,
                background = palette.neutral1.tone(6),
                foreground = palette.neutral1.tone(90),
                subtle = palette.neutral2.tone(80),
                surface = palette.neutral1.tone(20),
                inactive = palette.neutral1.tone(30),
                tileContent = palette.neutral1.tone(90),
                containers = a.map { it.tone(30) },
                onContainers = a.map { it.tone(90) },
            )
        } else {
            MetroColors(
                dark = false,
                background = palette.neutral1.tone(98),
                foreground = palette.neutral1.tone(10),
                subtle = palette.neutral2.tone(30),
                surface = palette.neutral1.tone(94),
                inactive = palette.neutral1.tone(90),
                tileContent = palette.neutral1.tone(10),
                containers = a.map { it.tone(90) },
                onContainers = a.map { it.tone(10) },
            )
        }
    }

    /** The main color of Modern Droid: bright enough on a dark surface, deep enough on a light one. */
    fun accent(palette: DroidPalette, dark: Boolean): Color = palette.accent1.tone(if (dark) 80 else 40)

    private fun accent1(shade: Int): Int = when (shade) {
        0 -> android.R.color.system_accent1_0
        10 -> android.R.color.system_accent1_10
        50 -> android.R.color.system_accent1_50
        100 -> android.R.color.system_accent1_100
        200 -> android.R.color.system_accent1_200
        300 -> android.R.color.system_accent1_300
        400 -> android.R.color.system_accent1_400
        500 -> android.R.color.system_accent1_500
        600 -> android.R.color.system_accent1_600
        700 -> android.R.color.system_accent1_700
        800 -> android.R.color.system_accent1_800
        900 -> android.R.color.system_accent1_900
        1000 -> android.R.color.system_accent1_1000
        else -> android.R.color.system_accent1_500
    }

    private fun accent2(shade: Int): Int = when (shade) {
        0 -> android.R.color.system_accent2_0
        10 -> android.R.color.system_accent2_10
        50 -> android.R.color.system_accent2_50
        100 -> android.R.color.system_accent2_100
        200 -> android.R.color.system_accent2_200
        300 -> android.R.color.system_accent2_300
        400 -> android.R.color.system_accent2_400
        500 -> android.R.color.system_accent2_500
        600 -> android.R.color.system_accent2_600
        700 -> android.R.color.system_accent2_700
        800 -> android.R.color.system_accent2_800
        900 -> android.R.color.system_accent2_900
        1000 -> android.R.color.system_accent2_1000
        else -> android.R.color.system_accent2_500
    }

    private fun accent3(shade: Int): Int = when (shade) {
        0 -> android.R.color.system_accent3_0
        10 -> android.R.color.system_accent3_10
        50 -> android.R.color.system_accent3_50
        100 -> android.R.color.system_accent3_100
        200 -> android.R.color.system_accent3_200
        300 -> android.R.color.system_accent3_300
        400 -> android.R.color.system_accent3_400
        500 -> android.R.color.system_accent3_500
        600 -> android.R.color.system_accent3_600
        700 -> android.R.color.system_accent3_700
        800 -> android.R.color.system_accent3_800
        900 -> android.R.color.system_accent3_900
        1000 -> android.R.color.system_accent3_1000
        else -> android.R.color.system_accent3_500
    }

    private fun neutral1(shade: Int): Int = when (shade) {
        0 -> android.R.color.system_neutral1_0
        10 -> android.R.color.system_neutral1_10
        50 -> android.R.color.system_neutral1_50
        100 -> android.R.color.system_neutral1_100
        200 -> android.R.color.system_neutral1_200
        300 -> android.R.color.system_neutral1_300
        400 -> android.R.color.system_neutral1_400
        500 -> android.R.color.system_neutral1_500
        600 -> android.R.color.system_neutral1_600
        700 -> android.R.color.system_neutral1_700
        800 -> android.R.color.system_neutral1_800
        900 -> android.R.color.system_neutral1_900
        1000 -> android.R.color.system_neutral1_1000
        else -> android.R.color.system_neutral1_500
    }

    private fun neutral2(shade: Int): Int = when (shade) {
        0 -> android.R.color.system_neutral2_0
        10 -> android.R.color.system_neutral2_10
        50 -> android.R.color.system_neutral2_50
        100 -> android.R.color.system_neutral2_100
        200 -> android.R.color.system_neutral2_200
        300 -> android.R.color.system_neutral2_300
        400 -> android.R.color.system_neutral2_400
        500 -> android.R.color.system_neutral2_500
        600 -> android.R.color.system_neutral2_600
        700 -> android.R.color.system_neutral2_700
        800 -> android.R.color.system_neutral2_800
        900 -> android.R.color.system_neutral2_900
        1000 -> android.R.color.system_neutral2_1000
        else -> android.R.color.system_neutral2_500
    }
}
