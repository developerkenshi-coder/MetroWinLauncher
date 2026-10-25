package com.metrowin.launcher.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Keeps content out of a camera cutout on the left or right side, which is where it is in landscape. The system bars
 * on the sides are already left out by `navigationBarsPadding`; the top is left to `statusBarsPadding`.
 */
@Composable
fun Modifier.cutoutSidesPadding(): Modifier = windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
