package com.metrowin.launcher.ui.mini

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.components.pageSide

/**
 * The only padding at the sides of the page of a mini app: [base] (24 dp, the design of the stock apps) less or more as the settings
 * say ("compact", "normal", "wide"), and never so much that the page is too narrow. Use it in place of `padding(horizontal = 24.dp)`.
 * [MiniFrame] already clears the bars, the camera hole and the width of a tablet: this is only the margin inside.
 */
@Composable
fun Modifier.miniPageSide(base: Dp = 24.dp): Modifier = padding(horizontal = pageSide(base))
