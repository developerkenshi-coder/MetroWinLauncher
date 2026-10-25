package com.metrowin.launcher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.R
import com.metrowin.launcher.data.WidgetProvider
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.TileWhite
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size

/** The widgets that the apps of the phone offer, one under the other. Touching one adds it to Start. */
@Composable
fun WidgetPicker(providers: List<WidgetProvider>, onPick: (WidgetProvider) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .cutoutSidesPadding(),
    ) {
        MetroText(
            stringResource(R.string.widgets_header), size = 14.sp, weight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp),
        )
        MetroText(
            stringResource(R.string.widgets_title), size = 40.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp),
        )
        if (providers.isEmpty()) {
            MetroText(
                stringResource(R.string.widgets_none), size = 17.sp, weight = FontWeight.Normal, color = colors.subtle,
                maxLines = Int.MAX_VALUE, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(providers, key = { it.component.flattenToString() }) { provider ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag("widget:${provider.component.flattenToShortString()}")
                            .metroPress(onClick = { onPick(provider) })
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).background(LocalAccent.current), contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.Widget, 30.dp, color = TileWhite)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            MetroText(provider.label.ifBlank { provider.appLabel }, size = 21.sp)
                            MetroText(provider.appLabel, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                        }
                    }
                }
            }
        }
    }
}
