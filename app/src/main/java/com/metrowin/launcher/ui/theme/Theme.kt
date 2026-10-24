package com.metrowin.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.metrowin.launcher.R

val MetroBlack = Color(0xFF000000)
val MetroWhite = Color(0xFFFFFFFF)

/** Secondary text. */
val MetroSubtle = Color(0xFF999999)

/** Menus and other surfaces that sit on the black background. */
val MetroSurface = Color(0xFF1F1F1F)

/** Jump list letters that have no apps. */
val MetroInactive = Color(0xFF2B2B2B)

/** The cyan of the Windows Phone 8 accent palette, used as the default accent colour. */
val DefaultAccent = Color(0xFF1BA1E2)

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
fun MetroTheme(accent: Color = DefaultAccent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides accent, content = content)
}
