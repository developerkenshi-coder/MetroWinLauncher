package com.metrowin.launcher.ui.pages

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.pageBackground

/**
 * A page of the launcher that fills the screen, the way the Forecast page does: a small heading, the title in large light type
 * and the content below, on the page background of the look in use. It swallows the touches, so nothing underneath reacts.
 */
@Composable
fun PageFrame(header: String, title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .pageBackground()
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .cutoutSidesPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        MetroText(header, size = 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 28.dp))
        MetroText(title, size = 34.sp, maxLines = 2, lineHeight = 40.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp).testTag("page:title"))
        content()
    }
}

/** A small choice among several, lit when it is the one in use: a length, a time, a source. */
@Composable
fun PageChip(text: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.testTag(tag).metroPress(onClick = onClick).litFill(selected).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, size = 16.sp, weight = FontWeight.Normal, color = if (selected) onAccent() else LocalMetroColors.current.foreground)
    }
}

/** A line of small subdued text under something. */
@Composable
fun PageNote(text: String, modifier: Modifier = Modifier) {
    MetroText(text, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = Int.MAX_VALUE, lineHeight = 19.sp, modifier = modifier)
}

internal val SpacedRows = Arrangement.spacedBy(12.dp)
