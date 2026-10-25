package com.metrowin.launcher.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ThemeMode
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Text and glyphs on a tile are white in every theme, as on Windows Phone. */
val TileWhite = Color(0xFFFFFFFF)

/** The cyan of the Windows Phone 8 accent palette, used as the default accent colour. */
val DefaultAccent = Color(0xFF1BA1E2)

/**
 * The 20 accent colours of Windows Phone 8, in the order of its colour picker. Microsoft only
 * published the table as an image, so these come from transcriptions of it and may be a shade off.
 */
val MetroAccents: List<Color> = listOf(
    Color(0xFFA4C400), // lime
    Color(0xFF60A917), // green
    Color(0xFF008A00), // emerald
    Color(0xFF00ABA9), // teal
    Color(0xFF1BA1E2), // cyan
    Color(0xFF0050EF), // cobalt
    Color(0xFF6A00FF), // indigo
    Color(0xFFAA00FF), // violet
    Color(0xFFF472D0), // pink
    Color(0xFFD80073), // magenta
    Color(0xFFA20025), // crimson
    Color(0xFFE51400), // red
    Color(0xFFFA6800), // orange
    Color(0xFFF0A30A), // amber
    Color(0xFFD8C100), // yellow
    Color(0xFF825A2C), // brown
    Color(0xFF6D8764), // olive
    Color(0xFF647687), // steel
    Color(0xFF76608A), // mauve
    Color(0xFF7A3B3F), // sienna
)

/** The two Windows Phone backgrounds: dark (the default) and light. */
@Immutable
class MetroColors(
    val dark: Boolean,
    val background: Color,
    val foreground: Color,
    /** Secondary text. */
    val subtle: Color,
    /** Menus and other surfaces that sit on the background. */
    val surface: Color,
    /** Jump list letters that have no apps, and switched-off toggles. */
    val inactive: Color,
)

val DarkColors = MetroColors(
    dark = true,
    background = Color(0xFF000000),
    foreground = Color(0xFFFFFFFF),
    subtle = Color(0xFF999999),
    surface = Color(0xFF1F1F1F),
    inactive = Color(0xFF2B2B2B),
)

val LightColors = MetroColors(
    dark = false,
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF000000),
    subtle = Color(0xFF666666),
    surface = Color(0xFFE6E6E6),
    inactive = Color(0xFFD6D6D6),
)

/** The colours for what is drawn on a coloured tile: white text and glyphs, in either theme. */
fun MetroColors.onTile() = MetroColors(
    dark = true,
    background = background,
    foreground = TileWhite,
    subtle = TileWhite.copy(alpha = 0.8f),
    surface = surface,
    inactive = inactive,
)

val LocalMetroColors = staticCompositionLocalOf { DarkColors }
val LocalAccent = staticCompositionLocalOf { DefaultAccent }

/**
 * Open Sans stands in for Segoe WP, which is proprietary. It has the same humanist shapes and the
 * Light weight that gives Windows Phone its look.
 */
val MetroFont = FontFamily(
    Font(R.font.opensans_light, FontWeight.Light),
    Font(R.font.opensans_regular, FontWeight.Normal),
    Font(R.font.opensans_semibold, FontWeight.SemiBold),
)

@Composable
fun MetroTheme(
    accent: Color = DefaultAccent,
    dark: Boolean = true,
    reduceMotion: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    SystemBarIcons(dark)
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalAccent provides accent,
        LocalMetroColors provides if (highContrast) colors.highContrast() else colors,
        LocalReduceMotion provides reduceMotion,
        LocalHighContrast provides highContrast,
        content = content,
    )
}

/** Dark icons in the status and navigation bars on the light theme, light icons on the dark one. */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Whether the launcher is dark now: fixed, as Android's theme, or by the hour. Only the schedule needs a
 * clock, and it checks twice a minute, so the switch happens within seconds of the hour.
 */
@Composable
fun rememberIsDark(settings: LauncherSettings): Boolean {
    val systemDark = isSystemInDarkTheme()
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    if (settings.themeMode == ThemeMode.Schedule) {
        LaunchedEffect(Unit) {
            while (true) {
                hour = LocalTime.now().hour
                delay(30_000)
            }
        }
    }
    return ThemeMode.isDark(settings.themeMode, systemDark, hour, settings.darkFromHour, settings.darkToHour)
}
