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

/** A square that is ticked in the accent colour when [checked]. */
@Composable
fun CheckSquare(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val style = LocalStyle.current
    val shape = if (style.isClassic) RectangleShape else RoundedCornerShape(8.dp)
    val tick = onAccent()
    Box(
        modifier
            .size(28.dp)
            .clip(shape)
            .border(2.dp, if (checked) accent else LocalMetroColors.current.foreground, shape)
            .background(if (checked) accent else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(14.dp)) {
                val stroke = 2.4.dp.toPx()
                drawLine(tick, Offset(0f, size.height * 0.55f), Offset(size.width * 0.38f, size.height * 0.95f), stroke, StrokeCap.Round)
                drawLine(tick, Offset(size.width * 0.38f, size.height * 0.95f), Offset(size.width, size.height * 0.1f), stroke, StrokeCap.Round)
            }
        }
    }
}
