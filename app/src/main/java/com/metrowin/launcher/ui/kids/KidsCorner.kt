package com.metrowin.launcher.ui.kids

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.onAccent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape

/** The title of the Kid's Corner, with the way out for a grown-up. */
@Composable
fun KidsHeader(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MetroText(stringResource(R.string.kids_title), size = 38.sp, modifier = Modifier.weight(1f))
        Box(Modifier.testTag("kids:exit").metroPress(onClick = onExit).padding(horizontal = 10.dp, vertical = 12.dp)) {
            MetroText(stringResource(R.string.kids_exit), size = 17.sp, weight = FontWeight.Normal, color = LocalAccent.current)
        }
    }
}

/**
 * A square that is ticked when [checked]. Classic, as the CheckBox of Windows Phone: 24 dp, a 3 px outline, and the tick in the ink on the
 * page (no fill). The other looks fill the square with the accent.
 */
@Composable
fun CheckSquare(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val style = LocalStyle.current
    val shape = style.squareShape
    val ink = LocalMetroColors.current.foreground
    val tick = if (style.isClassic) ink else onAccent()
    val filled = checked && !style.isClassic
    Box(
        modifier
            .size(if (style.isClassic) 24.dp else 28.dp)
            .clip(shape)
            .border(if (style.isClassic) 2.25.dp else 2.dp, if (filled) accent else ink, shape)
            .background(if (filled) accent else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (checked && style.isClassic) {
            // The mark of Windows Phone: a heavy tick, 23 x 21 px = 17 x 16 dp.
            Canvas(Modifier.size(17.dp, 16.dp)) {
                val tickPath = Path().apply {
                    listOf(0f to 123f, 39f to 93f, 124f to 164f, 256f to 18f, 295f to 49f, 124f to 240f).forEachIndexed { i, (x, y) ->
                        val px = x / 295f * size.width
                        val py = y / 240f * size.height
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(tickPath, tick)
            }
        } else if (checked) {
            Canvas(Modifier.size(14.dp)) {
                val stroke = 2.4.dp.toPx()
                drawLine(tick, Offset(0f, size.height * 0.55f), Offset(size.width * 0.38f, size.height * 0.95f), stroke, StrokeCap.Round)
                drawLine(tick, Offset(size.width * 0.38f, size.height * 0.95f), Offset(size.width, size.height * 0.1f), stroke, StrokeCap.Round)
            }
        }
    }
}
