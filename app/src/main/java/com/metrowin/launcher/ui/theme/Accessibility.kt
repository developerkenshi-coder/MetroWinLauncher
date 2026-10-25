package com.metrowin.launcher.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Screens fade instead of swinging, and nothing tilts, flips or cycles by itself. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Frames on tiles, solid tiles, and no grey text. */
val LocalHighContrast = staticCompositionLocalOf { false }

object AccessibilityPolicy {
    /** On by the setting, or when the user has removed the animations of Android altogether. */
    fun reduceMotion(setting: Boolean, animatorDurationScale: Float): Boolean = setting || animatorDurationScale <= 0f

    /** The animation scale of Android, 1 when it cannot be read. 0 is "animations off". */
    fun systemAnimatorScale(context: Context): Float =
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
}

/** The same colours with no grey text and surfaces that stand out more from the background. */
fun MetroColors.highContrast() = MetroColors(
    dark = dark,
    background = background,
    foreground = foreground,
    subtle = foreground,
    surface = if (dark) Color(0xFF333333) else Color(0xFFCCCCCC),
    inactive = if (dark) Color(0xFF4D4D4D) else Color(0xFFB3B3B3),
)
