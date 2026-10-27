package com.metrowin.launcher.ui.theme

import com.metrowin.launcher.ui.anim.MotionSpeed
import com.metrowin.launcher.ui.anim.LocalTiltStrength
import android.app.Activity
import android.content.Context
import android.os.Build
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
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
    /** Text and glyphs on a tile: white on the accent color, but ink on the light containers of Modern Droid. */
    val tileContent: Color = Color.White,
    /** Modern Droid: the colors the tiles take in turn, and the text color that goes on each. Empty in the other themes. */
    val containers: List<Color> = emptyList(),
    val onContainers: List<Color> = emptyList(),
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
fun MetroColors.onTile(content: Color = tileContent) = MetroColors(
    dark = true,
    background = background,
    foreground = content,
    subtle = content.copy(alpha = 0.8f),
    surface = surface,
    inactive = inactive,
    tileContent = content,
    containers = containers,
    onContainers = onContainers,
)

val LocalMetroColors = staticCompositionLocalOf { DarkColors }
val LocalAccent = staticCompositionLocalOf { DefaultAccent }

/** What the accent becomes where the user turned it off: a grey that suits the theme, so that nothing there is coloured. */
@Composable
fun AccentScope(use: Boolean, content: @Composable () -> Unit) {
    if (use) {
        content()
        return
    }
    val grey = if (LocalMetroColors.current.dark) Color(0xFF5C5C5C) else Color(0xFF8C8C8C)
    CompositionLocalProvider(LocalAccent provides grey, content = content)
}

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
    style: ThemeStyle = ThemeStyle.Classic,
    /** For Modern Droid: the palette (the wallpaper's, or made from the accent). */
    droid: DroidPalette? = null,
    /** OLED screens: pure black in the dark theme. */
    oled: Boolean = false,
    /** The size of the text of the launcher: 1 is the size of the phone, 1.3 is 30 percent bigger. */
    textScale: Float = 1f,
    /** Heavier text; the bold text of Android (12 and later) counts too. */
    boldText: Boolean = false,
    /** The font of the phone instead of the one the look brings. */
    systemFont: Boolean = false,
    /** How far a press tilts the tiles and buttons (1 as designed, 0 not at all) and how fast the animations go (2 is twice as fast). */
    tilt: Float = 1f,
    speed: Float = 1f,
    content: @Composable () -> Unit,
) {
    SystemBarIcons(dark)
    SideEffect { MotionSpeed.factor = speed.coerceIn(0.25f, 4f) }
    val adjustment = if (Build.VERSION.SDK_INT >= 31) LocalConfiguration.current.fontWeightAdjustment else 0
    // "Undefined" is a very large number, not a bold setting.
    val systemBold = adjustment != ADJUSTMENT_UNDEFINED && adjustment >= SYSTEM_BOLD_ADJUSTMENT
    val resolved = remember(style, dark, accent, droid, oled) { Themes.resolve(style, dark, accent, droid, oled) }
    val typed = remember(resolved.style, boldText, systemBold, systemFont) { resolved.style.withType(boldText || systemBold, systemFont) }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalAccent provides resolved.accent,
        LocalMetroColors provides if (highContrast) resolved.colors.highContrast() else resolved.colors,
        LocalStyle provides typed,
        LocalOled provides (oled && dark),
        LocalReduceMotion provides reduceMotion,
        LocalHighContrast provides highContrast,
        LocalTiltStrength provides tilt,
        LocalDensity provides density.let { Density(it.density, it.fontScale * textScale) },
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
fun rememberIsDark(settings: LauncherSettings, sunrise: LocalTime? = null, sunset: LocalTime? = null): Boolean {
    val systemDark = isSystemInDarkTheme()
    var minute by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    if (settings.themeMode == ThemeMode.Schedule || settings.themeMode == ThemeMode.Sun) {
        // Only while on screen: coming back after a night, the time is read at once.
        LaunchedWhileStarted(Unit) {
            while (true) {
                minute = LocalTime.now().let { it.hour * 60 + it.minute }
                delay(30_000)
            }
        }
    }
    return ThemeMode.isDark(
        settings.themeMode, systemDark, minute / 60, settings.darkFromHour, settings.darkToHour,
        minuteOfDay = minute, sunriseMinute = sunrise?.let { it.hour * 60 + it.minute }, sunsetMinute = sunset?.let { it.hour * 60 + it.minute },
    )
}

/** The Android setting "bold text" adds 300 to the weights; a smaller adjustment is not that setting. */
private const val SYSTEM_BOLD_ADJUSTMENT = 300

/** The value of Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED (a constant of Android 12, written out so it is no inlined API). */
private const val ADJUSTMENT_UNDEFINED = Int.MAX_VALUE
