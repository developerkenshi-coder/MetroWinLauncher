package com.metrowin.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroCollapse
import com.metrowin.launcher.ui.anim.metroExpand
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.util.Locale

/** Windows Phone shows at most four icon buttons; whatever else the bar has goes to its text items. */
internal const val RETRO_BUTTONS = 4

/**
 * The application bar of Windows Phone, as the guidelines draw it: a row of round icon buttons without names and a "…" at the
 * right end. The "…" opens the bar: the names appear under the buttons and the text items (all lowercase) slide up from under
 * them. Touching it again closes it. Nothing else: no gestures, no modes.
 */
@Composable
internal fun RetroBar(buttons: List<AppBarItem>, items: List<AppBarItem>, modifier: Modifier = Modifier) {
    if (buttons.isEmpty() && items.isEmpty()) return
    val colors = LocalMetroColors.current
    var open by remember { mutableStateOf(false) }
    val reduced = LocalReduceMotion.current
    Column(modifier.fillMaxWidth().background(retroBackground()).animateContentSize()) {
        AnimatedVisibility(open && items.isNotEmpty(), enter = metroExpand, exit = metroCollapse) {
            // Five items fit without scrolling, as the guidelines ask; more of them scroll inside this height.
            Column(Modifier.heightIn(max = MENU_ITEM * 5).verticalScroll(rememberScrollState()).testTag("retro:menu")) {
                items.forEachIndexed { index, item ->
                    MetroText(
                        item.label.lowercase(Locale.getDefault()), size = 20.sp, weight = FontWeight.Normal,
                        color = if (item.marked) LocalAccent.current else colors.foreground,
                        modifier = Modifier.fillMaxWidth().testTag("retro:item:$index")
                            .metroPress(onClick = { open = false; item.onClick() })
                            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.Top) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                buttons.forEachIndexed { index, item ->
                    Column(
                        Modifier.testTag("retro:button:$index").metroPress(onClick = item.onClick).padding(horizontal = 8.dp).semantics { contentDescription = item.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.padding(top = (BAR_HEIGHT - BUTTON) / 2).size(BUTTON).border(2.dp, colors.foreground, CircleShape).clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            item.icon?.let { MarkIcon(it, 18.dp) } ?: MetroText(item.symbol, size = 18.sp, weight = FontWeight.Normal)
                        }
                        // The names are under the buttons only while the bar is open.
                        // They slide down from under the buttons as the bar rises.
                        AnimatedVisibility(
                            open,
                            enter = if (reduced) EnterTransition.None else slideInVertically(metroTween(260)) { -it } + expandVertically(metroTween(260)) + fadeIn(metroTween(260)),
                            exit = if (reduced) ExitTransition.None else slideOutVertically(metroTween(180, easing = Motion.Accelerate)) { -it } + shrinkVertically(metroTween(180, easing = Motion.Accelerate)) + fadeOut(metroTween(180)),
                        ) { MetroText(item.label, size = 12.sp, weight = FontWeight.Normal, maxLines = 1, color = colors.foreground, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)) }
                        Box(Modifier.padding(bottom = if (open) 0.dp else (BAR_HEIGHT - BUTTON) / 2))
                    }
                }
            }
            val description = stringResource(if (open) R.string.edit_more_hide else R.string.edit_more_show)
            Box(
                Modifier.size(width = 48.dp, height = BAR_HEIGHT).testTag("retro:more").semantics { contentDescription = description }.metroPress(onClick = { open = !open }),
                contentAlignment = Alignment.Center,
            ) {
                // Three dots drawn, not typed: they do not depend on the font.
                val dots = colors.foreground
                Canvas(Modifier.size(width = 24.dp, height = 6.dp)) {
                    val radius = 2.dp.toPx()
                    repeat(3) { i -> drawCircle(dots, radius, Offset(radius + i * (size.width - 2 * radius) / 2f, size.height / 2f)) }
                }
            }
        }
    }
}

/** The grey of the bar in the theme of the phone (Windows Phone's own, not the accent). */
@Composable
internal fun retroBackground(): Color = if (LocalMetroColors.current.dark) Color(0xFF1F1F1F) else Color(0xFFDDDDDD)

// 72 pixels on a screen 480 wide, as in the guidelines, on the width of a phone of today.
private val BAR_HEIGHT = 56.dp
private val BUTTON = 38.dp
private val MENU_ITEM = 48.dp
