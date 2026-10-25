package com.metrowin.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.model.TileSize
import kotlin.math.max

/**
 * What changes between the looks besides the colors: the corners of tiles and controls, the space between tiles, and
 * the type. The colors come from [MetroColors]; the surfaces (flat, frosted, tonal) from the modifiers below.
 */
@Immutable
class MetroStyle(
    val kind: ThemeStyle,
    val tileShape: Shape,
    val smallTileShape: Shape,
    val tileGap: Dp,
    /** Buttons and text fields. */
    val controlShape: Shape,
    /** Menus, dialogs and bars. */
    val cardShape: Shape,
    /** The square behind an icon in the app list, and the small squares of the edit bars. */
    val squareShape: Shape,
    /** Bars and menus that rise from the bottom edge. */
    val sheetShape: Shape,
    val font: FontFamily,
    private val weights: Map<FontWeight, FontWeight>,
) {
    val isClassic: Boolean get() = kind == ThemeStyle.Classic

    /** The weight to use where the Classic type asks for [requested]: Windows Phone's Light is a thin type, the others are not. */
    fun weight(requested: FontWeight): FontWeight = weights[requested] ?: requested

    fun tileShapeFor(size: TileSize): Shape = if (size == TileSize.Small) smallTileShape else tileShape
}

val ClassicStyle = MetroStyle(
    kind = ThemeStyle.Classic,
    tileShape = RectangleShape, smallTileShape = RectangleShape, tileGap = 6.dp,
    controlShape = RectangleShape, cardShape = RectangleShape, squareShape = RectangleShape, sheetShape = RectangleShape,
    font = MetroFont, weights = emptyMap(),
)

val GlassStyle = MetroStyle(
    kind = ThemeStyle.Glass,
    tileShape = RoundedCornerShape(26.dp), smallTileShape = RoundedCornerShape(14.dp), tileGap = 9.dp,
    controlShape = RoundedCornerShape(50), cardShape = RoundedCornerShape(26.dp), squareShape = RoundedCornerShape(13.dp),
    sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    font = FontFamily.SansSerif,
    weights = mapOf(FontWeight.Light to FontWeight.Normal, FontWeight.Normal to FontWeight.Medium),
)

val DroidStyle = MetroStyle(
    kind = ThemeStyle.Droid,
    tileShape = RoundedCornerShape(28.dp), smallTileShape = RoundedCornerShape(18.dp), tileGap = 8.dp,
    controlShape = RoundedCornerShape(50), cardShape = RoundedCornerShape(28.dp), squareShape = RoundedCornerShape(14.dp),
    sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    font = FontFamily.SansSerif,
    weights = mapOf(FontWeight.Light to FontWeight.Normal, FontWeight.SemiBold to FontWeight.Medium),
)

fun styleOf(kind: ThemeStyle): MetroStyle = when (kind) {
    ThemeStyle.Classic -> ClassicStyle
    ThemeStyle.Glass -> GlassStyle
    ThemeStyle.Droid -> DroidStyle
}

val LocalStyle = staticCompositionLocalOf { ClassicStyle }

/** The dark theme is the OLED black (see [oled]). */
val LocalOled = staticCompositionLocalOf { false }

/** The color and the text color of tile number [index]: the accent and white, or in Modern Droid the containers in turn. */
fun MetroColors.tileColor(index: Int, accent: Color): Color = if (containers.isEmpty()) accent else containers[index.mod(containers.size)]

fun MetroColors.tileInk(index: Int): Color = if (onContainers.isEmpty()) tileContent else onContainers[index.mod(onContainers.size)]

/** The text of a field the user types in: the type of the look, in the light weight of Classic. */
@Composable
fun fieldTextStyle(color: Color, size: androidx.compose.ui.unit.TextUnit): androidx.compose.ui.text.TextStyle {
    val style = LocalStyle.current
    return androidx.compose.ui.text.TextStyle(color = color, fontSize = size, fontFamily = style.font, fontWeight = style.weight(FontWeight.Light))
}

// --- surfaces

/** Frosted glass: a translucent veil, an optional tint, a highlight from the top left, a little shade below and a light rim. */
@Composable
fun Modifier.glass(shape: Shape, tint: Color? = null, tintAlpha: Float = 0.38f, veil: Float? = null): Modifier {
    val dark = LocalMetroColors.current.dark
    val veilAlpha = veil ?: if (dark) 0.10f else 0.45f
    return this.clip(shape).drawBehind {
        drawRect(Color.White.copy(alpha = veilAlpha))
        if (tint != null) drawRect(tint.copy(alpha = tintAlpha))
        drawRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.26f else 0.50f), Color.Transparent),
                start = Offset.Zero, end = Offset(size.width * 0.75f, size.height * 0.75f),
            ),
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.14f else 0.06f)),
                startY = size.height * 0.55f, endY = size.height,
            ),
        )
        drawOutline(
            shape.createOutline(size, layoutDirection, this),
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.38f)),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ),
            style = Stroke(width = 2.4.dp.toPx()),
        )
    }
}

/** The surface of a tile of color [color] in the look in use. */
@Composable
fun Modifier.tileSurface(color: Color, shape: Shape, classicColor: Color = color): Modifier = when (LocalStyle.current.kind) {
    ThemeStyle.Classic -> this.background(classicColor)
    ThemeStyle.Glass -> this.glass(shape, tint = color)
    ThemeStyle.Droid -> this.clip(shape).background(color)
}

/** Menus, bars and dialogs: a flat grey square, a sheet of glass, or a tonal rounded card. */
@Composable
fun Modifier.panel(shape: Shape? = null): Modifier {
    val style = LocalStyle.current
    val colors = LocalMetroColors.current
    val cardShape = shape ?: style.cardShape
    return when (style.kind) {
        ThemeStyle.Classic -> this.background(colors.surface)
        ThemeStyle.Glass -> this.glass(cardShape, veil = if (colors.dark) 0.14f else 0.62f)
        ThemeStyle.Droid -> this.clip(cardShape).background(colors.surface)
    }
}

/** A frame of [width] around the control, square in Classic and rounded in the other looks (or in [shape]). */
@Composable
fun Modifier.frame(width: Dp, color: Color, shape: Shape? = null): Modifier {
    val s = shape ?: LocalStyle.current.squareShape
    return this.clip(s).border(width, color, s)
}

/** A bar or menu that rises from the bottom edge, in the surface of the look. */
@Composable
fun Modifier.sheet(): Modifier = this.panel(LocalStyle.current.sheetShape)

/**
 * A small control that is lit in the accent color or switched off in a quiet grey: the buttons of the Action Center, the
 * size buttons of the edit bar, the letters of the jump list.
 */
@Composable
fun Modifier.litFill(lit: Boolean, shape: Shape = LocalStyle.current.squareShape): Modifier {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    return when (LocalStyle.current.kind) {
        ThemeStyle.Classic -> this.background(if (lit) accent else colors.inactive)
        ThemeStyle.Glass ->
            if (lit) this.glass(shape, tint = accent, tintAlpha = 0.72f) else this.glass(shape, veil = if (colors.dark) 0.08f else 0.40f)
        ThemeStyle.Droid -> this.clip(shape).background(if (lit) accent else colors.inactive)
    }
}

/** The text and glyph color on a [litFill] that is lit, or on a plain square of the accent color. */
@Composable
fun onAccent(): Color {
    if (LocalStyle.current.isClassic) return TileWhite
    // The other looks have pale accents too (the light tones of Modern Droid), so the ink follows the brightness.
    return if (ColorMath.luminance(LocalAccent.current.toArgbInt()) > 0.55f) Color(0xFF111111) else Color.White
}

/** The square (or rounded square) behind an icon, clipped to the shape of the look. */
@Composable
fun Modifier.squareClip(): Modifier = this.clip(LocalStyle.current.squareShape)

/** The background of a whole page: black or white, the aurora of Modern Glass, or the tonal surface of Modern Droid. */
@Composable
fun Modifier.pageBackground(): Modifier {
    val style = LocalStyle.current
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val oled = LocalOled.current
    return when (style.kind) {
        // The pools of light reach past the page that draws them, so the page clips them to itself.
        ThemeStyle.Glass -> this.clipToBounds().drawBehind { drawAurora(accent, colors.dark, oled && colors.dark) }
        else -> this.background(colors.background)
    }
}

/**
 * The backdrop of Modern Glass: a deep (or pale) base with soft pools of light, the accent color and two turns of its
 * hue. It is drawn the same way on every page, so a page laid over another looks like the same backdrop.
 */
fun DrawScope.drawAurora(accent: Color, dark: Boolean, oled: Boolean = false) {
    val w = size.width
    val h = size.height
    val reach = max(w, h)
    // On an OLED screen the base is black and the light is a faint glow, so most of the pixels stay off.
    drawRect(if (oled) Color.Black else if (dark) Color(0xFF0B1022) else Color(0xFFEEF2FF))
    val glow = if (oled) 0.22f else 1f
    fun pool(x: Float, y: Float, radius: Float, color: Color, alpha: Float) {
        val center = Offset(w * x, h * y)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * glow), Color.Transparent), center = center, radius = radius), radius, center)
    }
    pool(0.15f, 0.12f, reach * 0.75f, accent, if (dark) 0.55f else 0.45f)
    pool(0.95f, 0.35f, reach * 0.65f, ColorMath.rotateHue(accent, 70f), if (dark) 0.40f else 0.40f)
    pool(0.25f, 0.85f, reach * 0.70f, ColorMath.rotateHue(accent, -70f), if (dark) 0.40f else 0.35f)
    pool(0.90f, 0.95f, reach * 0.55f, ColorMath.rotateHue(accent, 150f), if (dark) 0.30f else 0.30f)
}

/** A copy of [image] with the detail gone, as a frosted pane shows it: shrunk a lot and stretched back. */
fun frosted(image: ImageBitmap, factor: Int = 14): ImageBitmap {
    val source = image.asAndroidBitmap()
    val small = source.scale((source.width / factor).coerceAtLeast(2), (source.height / factor).coerceAtLeast(2), true)
    return small.scale(source.width, source.height, true).asImageBitmap()
}

/** Colors of Modern Glass in the dark and the light. */
object GlassColors {
    val dark = MetroColors(
        dark = true, background = Color(0xFF0B1022), foreground = Color(0xFFFFFFFF), subtle = Color(0xFFB4BBD6),
        surface = Color(0xFF1C2342), inactive = Color(0xFF2B3358), tileContent = Color(0xFFFFFFFF),
    )
    val light = MetroColors(
        dark = false, background = Color(0xFFEEF2FF), foreground = Color(0xFF111527), subtle = Color(0xFF58607A),
        surface = Color(0xFFFFFFFF), inactive = Color(0xFFDCE2F5), tileContent = Color(0xFF14182A),
    )
}

/**
 * The same colors for an OLED screen: the background is black (not the dark grey or navy of the look), and the surfaces
 * that sit on it (menus, bars, the grey of a switched-off button) are half way to black, so that little more than the
 * tiles and the text is lit.
 */
fun MetroColors.oled(): MetroColors = MetroColors(
    dark = dark,
    background = Color.Black,
    foreground = foreground,
    subtle = subtle,
    surface = lerp(surface, Color.Black, 0.5f),
    inactive = lerp(inactive, Color.Black, 0.35f),
    tileContent = tileContent,
    containers = containers,
    onContainers = onContainers,
)

/** The colors and the main color of a look. */
@Immutable
class ResolvedTheme(val colors: MetroColors, val accent: Color, val style: MetroStyle)

object Themes {
    /**
     * [accent] is the color the user chose. In Modern Droid the main color comes from [droid] (the wallpaper's palette or
     * one made from the accent), so [droid] must be given for it.
     */
    fun resolve(kind: ThemeStyle, dark: Boolean, accent: Color, droid: DroidPalette?, oled: Boolean = false): ResolvedTheme {
        val resolved = when (kind) {
            ThemeStyle.Classic -> ResolvedTheme(if (dark) DarkColors else LightColors, accent, ClassicStyle)
            ThemeStyle.Glass -> ResolvedTheme(if (dark) GlassColors.dark else GlassColors.light, accent, GlassStyle)
            ThemeStyle.Droid -> {
                val palette = droid ?: Palettes.fromSeed(accent)
                ResolvedTheme(Palettes.colors(palette, dark), Palettes.accent(palette, dark), DroidStyle)
            }
        }
        // OLED black only exists in the dark: in the light theme there is nothing to switch off.
        return if (oled && dark) ResolvedTheme(resolved.colors.oled(), resolved.accent, resolved.style) else resolved
    }
}

