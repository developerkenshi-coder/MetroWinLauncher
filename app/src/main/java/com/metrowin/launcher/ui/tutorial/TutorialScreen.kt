package com.metrowin.launcher.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Need
import com.metrowin.launcher.data.Tutorial
import com.metrowin.launcher.data.TutorialStep
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.ui.gesture.PageDots
import com.metrowin.launcher.ui.gesture.pageTurnstile
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.TileWhite
import kotlinx.coroutines.launch

/**
 * The first-run tour, in the Windows Phone style (small caps header, big lowercase title): one page for each part of
 * the launcher, saying what it does and how to use it. A page that needs a permission asks for it right there, with
 * a button, and says "allowed" once it is granted; nothing is asked for in advance and nothing is required.
 *
 * [met] are the needs that are met now, [denied] the ones Android will not ask about again (the button then
 * takes the user to the settings). [onRequest] asks for one need; [onFinish] leaves the tour.
 */
@Composable
fun TutorialScreen(
    pager: PagerState,
    met: Set<Need>,
    onRequest: (Need) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    denied: Set<Need> = emptySet(),
    steps: List<TutorialStep> = Tutorial.steps,
) {
    val scope = rememberCoroutineScope()
    val reduced = LocalReduceMotion.current
    val onLast = pager.currentPage >= steps.lastIndex
    Box(
        modifier
            .fillMaxSize()
            .background(LocalMetroColors.current.background)
            // Swallow touches so nothing underneath reacts while the tour is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("tutorial:screen"),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().cutoutSidesPadding()) {
            HorizontalPager(pager, Modifier.weight(1f).testTag("tutorial:pager"), key = { steps[it].id }) { page ->
                Box(Modifier.fillMaxSize().pageTurnstile(pager, page, reduced)) {
                    StepPage(steps[page], met, denied, onRequest)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // On a wide screen (landscape) everything sits in one line to leave the page as much height as possible;
                // on a narrow one the dots have a line of their own, as ten of them and two buttons would not fit across.
                val wide = maxWidth >= WIDE_BAR
                val skip = @Composable {
                    if (!onLast) {
                        Box(Modifier.metroPress(onClick = onFinish).padding(vertical = 10.dp, horizontal = 4.dp).testTag("tutorial:skip")) {
                            MetroText(stringResource(R.string.tut_skip), size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                        }
                    }
                }
                val next = @Composable {
                    MetroButton(stringResource(if (onLast) R.string.tut_done else R.string.tut_next), Modifier.testTag("tutorial:next")) {
                        if (onLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { skip() }
                        PageDots(pager, steps.size)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { next() }
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) { PageDots(pager, steps.size) }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { skip() }
                            next()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPage(step: TutorialStep, met: Set<Need>, denied: Set<Need>, onRequest: (Need) -> Unit) {
    val colors = LocalMetroColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).testTag("tutorial:step:${step.id}"),
    ) {
        Spacer(Modifier.height(28.dp))
        MetroText(stringResource(step.label), size = 14.sp, weight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 10.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).background(LocalAccent.current), contentAlignment = Alignment.Center) {
                GlyphIcon(step.glyph, 40.dp, color = TileWhite)
            }
            Spacer(Modifier.width(16.dp))
            MetroText(
                stringResource(step.title), size = 38.sp, color = LocalAccent.current, maxLines = 3, lineHeight = 42.sp,
                modifier = Modifier.weight(1f).testTag("tutorial:title"),
            )
        }
        MetroText(stringResource(step.intro), size = 17.sp, color = colors.subtle, maxLines = Int.MAX_VALUE, lineHeight = 24.sp)
        Spacer(Modifier.height(18.dp))
        step.tips.forEach { tip -> Tip(stringResource(tip)) }
        if (step.needs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            step.needs.forEach { need -> NeedRow(need, need in met, need in denied, onRequest) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A line of advice, with a small square of the accent colour in front, like the bullets of Windows Phone. */
@Composable
private fun Tip(text: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 9.dp).size(8.dp).background(LocalAccent.current))
        Spacer(Modifier.width(14.dp))
        MetroText(text, size = 17.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE, lineHeight = 24.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NeedRow(need: Need, met: Boolean, denied: Boolean, onRequest: (Need) -> Unit) {
    val colors = LocalMetroColors.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).testTag("tutorial:need:${need.name}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            MetroText(stringResource(need.title), size = 22.sp, maxLines = 2)
            MetroText(stringResource(need.why), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, lineHeight = 21.sp)
        }
        Spacer(Modifier.width(14.dp))
        if (met) {
            MetroText(stringResource(R.string.need_allowed), size = 18.sp, color = LocalAccent.current, modifier = Modifier.testTag("tutorial:allowed:${need.name}"))
        } else {
            // A permission Android will not ask about again can only be given in the settings of the app.
            val label = if (denied) R.string.need_action_settings else need.action
            MetroButton(stringResource(label), Modifier.testTag("tutorial:allow:${need.name}")) { onRequest(need) }
        }
    }
}

/** Width from which the dots, "skip" and "next" fit on one line. */
private val WIDE_BAR = 520.dp
