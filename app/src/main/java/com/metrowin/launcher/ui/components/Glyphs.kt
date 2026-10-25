package com.metrowin.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * The white line glyphs of the Windows Phone stock apps, drawn from path data on a 100x100 grid
 * (the Phone handset is the one exception, on a 24x24 grid, see [PHONE]).
 */
@Composable
fun GlyphIcon(glyph: Glyph, size: Dp, modifier: Modifier = Modifier, color: Color = LocalMetroColors.current.foreground) {
    val spec = remember(glyph) { specOf(glyph) }
    val density = LocalDensity.current
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            spec.parts.forEach { part ->
                val unit = this.size.minDimension / part.grid
                scale(unit, unit, pivot = Offset.Zero) {
                    drawPath(
                        path = part.path,
                        color = color,
                        style = if (part.fill) Fill else Stroke(
                            width = STROKE * part.grid / 100f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
        spec.letter?.let { letter ->
            MetroText(
                text = letter,
                size = with(density) { (size * spec.letterScale).toSp() },
                weight = if (spec.letterBold) FontWeight.SemiBold else FontWeight.Light,
                color = color,
                modifier = Modifier.offset(y = size * spec.letterDy),
            )
        }
    }
}

private const val STROKE = 5.5f

private class Part(data: String, val fill: Boolean, val grid: Float = 100f) {
    val path: Path = PathParser().parsePathString(data).toPath()
}

private class Spec(
    val parts: List<Part>,
    val letter: String? = null,
    val letterScale: Float = 0.5f,
    val letterDy: Float = 0f,
    val letterBold: Boolean = true,
)

private val specs = HashMap<Glyph, Spec>()

private fun specOf(glyph: Glyph): Spec = specs.getOrPut(glyph) { build(glyph) }

private fun stroke(vararg data: String) = data.map { Part(it, fill = false) }
private fun fill(vararg data: String) = data.map { Part(it, fill = true) }
private fun spec(vararg parts: List<Part>, letter: String? = null, letterScale: Float = 0.5f, letterDy: Float = 0f) =
    Spec(parts.flatMap { it }, letter, letterScale, letterDy)

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f): String =
    if (r == 0f) {
        "M$x ${y}h${w}v${h}h${-w}Z"
    } else {
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}" +
            "a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}Z"
    }

/** Eight-tooth gear outline. */
private fun gear(): String {
    val teeth = 8
    val inner = 31f
    val outer = 42f
    val sb = StringBuilder()
    for (i in 0 until teeth) {
        val centre = i * 360f / teeth
        listOf(centre - 14f to inner, centre - 9f to outer, centre + 9f to outer, centre + 14f to inner)
            .forEachIndexed { n, (deg, radius) ->
                val rad = Math.toRadians(deg.toDouble())
                val x = 50f + radius * cos(rad).toFloat()
                val y = 50f + radius * sin(rad).toFloat()
                sb.append(if (i == 0 && n == 0) "M" else "L").append(x).append(' ').append(y)
            }
    }
    return sb.append('Z').toString()
}

/** Material Design "call" handset, Apache 2.0, see THIRD_PARTY_NOTICES.md. 24x24 grid. */
private const val PHONE =
    "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57" +
        ".55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 " +
        "0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"

private fun document() = stroke("M26 10H62L78 26V90H26Z", "M62 10V26H78")

private fun build(glyph: Glyph): Spec = when (glyph) {
    Glyph.Phone -> Spec(listOf(Part(PHONE, fill = false, grid = 24f)))
    Glyph.People -> spec(stroke(circle(50f, 32f, 15f), "M20 88C20 58 80 58 80 88Z"))
    Glyph.Messaging -> spec(stroke(rect(12f, 16f, 76f, 54f, 6f), "M28 70V88L46 70", "M28 34H72", "M28 50H58"))
    Glyph.Mail -> spec(stroke(rect(10f, 22f, 80f, 56f, 4f), "M12 26L50 56L88 26"))
    Glyph.Calendar -> spec(
        stroke(rect(14f, 20f, 72f, 66f, 4f), "M14 40H86", "M34 12V28", "M66 12V28"),
        fill(
            rect(28f, 52f, 10f, 10f), rect(45f, 52f, 10f, 10f), rect(62f, 52f, 10f, 10f),
            rect(28f, 68f, 10f, 10f), rect(45f, 68f, 10f, 10f),
        ),
    )
    Glyph.Camera -> spec(stroke(rect(10f, 30f, 80f, 52f, 6f), "M34 30L40 18H60L66 30", circle(50f, 56f, 16f)))
    Glyph.Photos -> spec(stroke(rect(10f, 18f, 80f, 64f, 4f), "M10 74L34 50L52 66L66 54L90 76", circle(68f, 38f, 7f)))
    Glyph.Music -> spec(stroke(circle(38f, 72f, 12f), "M50 72V18", "M50 18C62 22 78 26 76 42"))
    Glyph.Video -> spec(stroke(circle(50f, 50f, 38f), "M42 34V66L68 50Z"))
    Glyph.Browser -> spec(stroke(circle(50f, 50f, 38f)), letter = "e", letterScale = 0.62f, letterDy = -0.02f)
    Glyph.Maps -> spec(stroke("M50 92C50 92 20 60 20 40A30 30 0 0 1 80 40C80 60 50 92 50 92Z", circle(50f, 40f, 10f)))
    Glyph.Store -> spec(stroke(rect(20f, 34f, 60f, 54f, 3f), "M36 34V28A14 14 0 0 1 64 28V34"))
    Glyph.Settings -> spec(stroke(gear(), circle(50f, 50f, 12f)))
    Glyph.Alarm -> spec(stroke(circle(50f, 56f, 30f), "M50 56V38", "M50 56L64 64", "M14 30L28 16", "M86 30L72 16"))
    Glyph.Calculator -> spec(
        stroke(rect(22f, 10f, 56f, 80f, 4f), rect(32f, 20f, 36f, 16f)),
        fill(
            circle(36f, 52f, 4f), circle(50f, 52f, 4f), circle(64f, 52f, 4f),
            circle(36f, 66f, 4f), circle(50f, 66f, 4f), circle(64f, 66f, 4f),
            circle(36f, 80f, 4f), circle(50f, 80f, 4f), circle(64f, 80f, 4f),
        ),
    )
    Glyph.Files -> spec(stroke("M10 24H38L46 34H90V80H10Z"))
    Glyph.Wallet -> spec(
        stroke(rect(10f, 24f, 80f, 58f, 6f), rect(58f, 44f, 32f, 18f, 4f)),
        fill(circle(70f, 53f, 3.5f)),
    )
    Glyph.Mic -> spec(stroke(rect(38f, 10f, 24f, 46f, 12f), "M26 44A24 24 0 0 0 74 44", "M50 68V88", "M36 88H64"))
    Glyph.Podcasts -> spec(
        stroke("M38 34A18 18 0 0 0 38 58", "M28 26A30 30 0 0 0 28 66", "M62 34A18 18 0 0 1 62 58", "M72 26A30 30 0 0 1 72 66", "M50 58V90"),
        fill(circle(50f, 46f, 7f)),
    )
    Glyph.Cortana -> spec(stroke(circle(50f, 50f, 36f), circle(50f, 50f, 18f)))
    Glyph.Skype -> spec(stroke(circle(50f, 50f, 38f)), letter = "S")
    Glyph.OneDrive -> spec(stroke("M28 76H72A16 16 0 0 0 74 44A24 24 0 0 0 28 42A17 17 0 0 0 28 76Z"))
    Glyph.Xbox -> spec(stroke(circle(50f, 50f, 38f), "M32 30L68 70", "M68 30L32 70"))
    Glyph.Games -> spec(
        stroke(rect(8f, 30f, 84f, 44f, 20f), "M28 42V62", "M18 52H38"),
        fill(circle(66f, 46f, 4f), circle(76f, 58f, 4f)),
    )
    Glyph.Office -> spec(document(), stroke("M38 46H66", "M38 60H66", "M38 74H58"))
    Glyph.Word -> spec(document(), letter = "W", letterScale = 0.34f, letterDy = 0.12f)
    Glyph.Excel -> spec(document(), letter = "X", letterScale = 0.34f, letterDy = 0.12f)
    Glyph.PowerPoint -> spec(document(), letter = "P", letterScale = 0.34f, letterDy = 0.12f)
    Glyph.OneNote -> spec(document(), letter = "N", letterScale = 0.34f, letterDy = 0.12f)
    Glyph.Weather -> spec(stroke("M24 82H66A15 15 0 0 0 66 54A22 22 0 0 0 26 58A12 12 0 0 0 24 82Z", circle(74f, 28f, 10f)))
    Glyph.News -> spec(stroke(rect(12f, 16f, 76f, 68f, 3f), "M24 32H48", "M24 46H48", "M24 60H48", rect(58f, 30f, 18f, 32f)))
    Glyph.Sports -> spec(stroke(circle(50f, 50f, 38f), "M12 50H88", "M50 12V88", "M24 22A40 40 0 0 1 24 78", "M76 22A40 40 0 0 0 76 78"))
    Glyph.Finance -> spec(stroke("M14 14V86H90", "M24 70L44 48L58 62L84 30", "M68 30H84V46"))
    Glyph.Battery -> spec(stroke(rect(10f, 32f, 72f, 36f, 4f)), fill(rect(84f, 44f, 6f, 12f), rect(17f, 39f, 30f, 22f)))
    Glyph.Data -> spec(stroke("M34 82V22", "M20 36L34 22L48 36", "M66 18V78", "M52 64L66 78L80 64"))
    Glyph.Wifi -> spec(
        stroke("M10 40A56 56 0 0 1 90 40", "M24 54A38 38 0 0 1 76 54", "M38 68A20 20 0 0 1 62 68"),
        fill(circle(50f, 82f, 5.5f)),
    )
    Glyph.Bluetooth -> spec(stroke("M28 32L70 68L50 88V12L70 32L28 68"))
    Glyph.Airplane -> spec(stroke("M50 8L58 36L92 58V68L58 58L56 80L68 88V94L50 90L32 94V88L44 80L42 58L8 68V58L42 36Z"))
    Glyph.Moon -> spec(stroke("M62 12A40 40 0 1 0 88 62A32 32 0 0 1 62 12Z"))
    Glyph.Flashlight -> spec(stroke("M34 10H66L58 34H42Z", rect(42f, 34f, 16f, 54f, 3f), "M50 48V62"))
    Glyph.Rotation -> spec(
        stroke(
            rect(32f, 22f, 36f, 56f, 4f),
            "M16 36A36 36 0 0 1 36 10", "M36 10L22 12", "M36 10L34 24",
            "M84 64A36 36 0 0 1 64 90", "M64 90L78 88", "M64 90L66 76",
        ),
    )
    Glyph.Hotspot -> spec(
        stroke(circle(28f, 50f, 9f), circle(72f, 24f, 9f), circle(72f, 76f, 9f), "M36 45L64 29", "M36 55L64 71"),
    )
    Glyph.Vpn -> spec(stroke("M50 8L84 20V48C84 70 68 84 50 92C32 84 16 70 16 48V20Z", "M34 50L46 62L68 38"))
    Glyph.Cast -> spec(
        stroke("M10 72A16 16 0 0 1 28 90", "M10 56A32 32 0 0 1 44 90", "M10 40A48 48 0 0 1 60 90", "M10 26V16H90V84H68"),
        fill(circle(14f, 86f, 4f)),
    )
    Glyph.Brightness -> spec(
        stroke(circle(50f, 50f, 15f), "M50 10V24", "M50 76V90", "M10 50H24", "M76 50H90", "M22 22L32 32", "M68 68L78 78", "M78 22L68 32", "M32 68L22 78"),
    )
    Glyph.Bell -> spec(stroke("M50 10C34 10 26 22 26 40V58L16 70H84L74 58V40C74 22 66 10 50 10Z", "M41 78A9 9 0 0 0 59 78"))
    Glyph.Kids -> spec(stroke(circle(50f, 50f, 38f), "M28 60A24 24 0 0 0 72 60"), fill(circle(36f, 42f, 4.5f), circle(64f, 42f, 4.5f)))
    Glyph.Search -> spec(stroke(circle(42f, 42f, 26f), "M62 62L88 88"))
    Glyph.Theme -> spec(
        stroke("M50 12A38 38 0 1 0 50 88A9 9 0 0 0 55 72A9 9 0 0 1 64 62H74A14 14 0 0 0 88 48A38 36 0 0 0 50 12Z"),
        fill(circle(30f, 48f, 5f), circle(40f, 29f, 5f), circle(61f, 28f, 5f), circle(74f, 42f, 5f)),
    )
    Glyph.Globe -> spec(stroke(circle(50f, 50f, 38f), "M12 50H88", "M50 12C30 30 30 70 50 88", "M50 12C70 30 70 70 50 88"))
    Glyph.Widget -> spec(stroke(rect(14f, 14f, 32f, 32f, 3f), rect(54f, 14f, 32f, 32f, 3f), rect(14f, 54f, 32f, 32f, 3f), rect(54f, 54f, 32f, 32f, 3f)))
    Glyph.Lock -> spec(stroke(rect(22f, 44f, 56f, 44f, 4f), "M34 44V32A16 16 0 0 1 66 32V44"), fill(circle(50f, 66f, 5f)))
    Glyph.Star -> spec(stroke("M50 10L61 38L91 40L68 59L76 88L50 72L24 88L32 59L9 40L39 38Z"))
    Glyph.Folder -> spec(stroke("M10 26H40L48 36H90V80H10Z"), fill(rect(10f, 44f, 80f, 6f, 0f)))
    Glyph.Home -> spec(stroke("M10 48L50 14L90 48", "M22 42V86H78V42", "M42 86V60H58V86"))
    Glyph.Gesture -> spec(stroke(circle(26f, 50f, 11f), "M42 50H90", "M76 36L90 50L76 64"))
    Glyph.Storage -> spec(stroke("M18 26A32 12 0 1 0 82 26A32 12 0 1 0 18 26Z", "M18 26V74A32 12 0 0 0 82 74V26", "M18 50A32 12 0 0 0 82 50"))
}
