package com.metrowin.launcher.ui.tutorial

import com.metrowin.launcher.ui.components.Spacing
import com.metrowin.launcher.ui.components.MetroType
import com.metrowin.launcher.ui.components.topBarsPadding
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
import com.metrowin.launcher.data.TutorialLink
import com.metrowin.launcher.data.TutorialStep
import com.metrowin.launcher.ui.settings.SettingsSection
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
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.launch

/**
 * The first-run tour, in the Windows Phone style (small caps header, big lowercase title): one page for each part of
 * the launcher, saying what it does and how to use it. A page that needs a permission asks for it right there, with
 * a button, and says "allowed" once it is granted; nothing is asked for in advance and nothing is required.
 *
 * [met] are the needs that are met now, [denied] the ones Android will not ask about again (the button then
 * takes the user to the settings). [onRequest] asks for one need; [onFinish] leaves the tour. A page that has a setting
 * says where it is and a button opens it ([onOpenLink]); the tour waits where it is and comes back when the settings close.
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
    onOpenLink: (TutorialLink) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val reduced = LocalReduceMotion.current
    val onLast = pager.currentPage >= steps.lastIndex
    Box(
        modifier
            .fillMaxSize()
            .pageBackground()
            // Swallow touches so nothing underneath reacts while the tour is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("tutorial:screen"),
    ) {
        Column(Modifier.fillMaxSize().topBarsPadding().navigationBarsPadding().cutoutSidesPadding()) {
            HorizontalPager(pager, Modifier.weight(1f).testTag("tutorial:pager"), key = { steps[it].id }) { page ->
                Box(Modifier.fillMaxSize().pageTurnstile(pager, page, reduced)) {
                    StepPage(steps[page], met, denied, onRequest, onOpenLink)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // On a wide screen (landscape) everything sits in one line to leave the page as much height as possible;
                // on a narrow one the dots have a line of their own, as ten of them and two buttons would not fit across.
                // A long tour does not get a dot for each page (they would not fit): it says "3 of 25".
                val wide = maxWidth >= WIDE_BAR
                val skip = @Composable {
                    if (!onLast) {
                        Box(Modifier.metroPress(onClick = onFinish).padding(vertical = 10.dp, horizontal = Spacing.Small).testTag("tutorial:skip")) {
                            MetroText(stringResource(R.string.tut_skip), size = MetroType.Lead, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                        }
                    }
                }
                val next = @Composable {
                    MetroButton(stringResource(if (onLast) R.string.tut_done else R.string.tut_next), Modifier.testTag("tutorial:next")) {
                        if (onLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
                val progress = @Composable {
                    if (steps.size > MAX_DOTS) {
                        MetroText(
                            stringResource(R.string.tut_progress, pager.currentPage + 1, steps.size), size = MetroType.Body, weight = FontWeight.Normal,
                            color = LocalMetroColors.current.subtle, modifier = Modifier.testTag("tutorial:progress"),
                        )
                    } else {
                        PageDots(pager, steps.size)
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Page, vertical = Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { skip() }
                        progress()
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { next() }
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) { progress() }
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Page, vertical = Spacing.Large), verticalAlignment = Alignment.CenterVertically) {
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
private fun StepPage(step: TutorialStep, met: Set<Need>, denied: Set<Need>, onRequest: (Need) -> Unit, onOpenLink: (TutorialLink) -> Unit) {
    val colors = LocalMetroColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page).testTag("tutorial:step:${step.id}"),
    ) {
        Spacer(Modifier.height(28.dp))
        MetroText(stringResource(step.label), size = MetroType.Note, weight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 10.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).litFill(true), contentAlignment = Alignment.Center) {
                GlyphIcon(step.glyph, 40.dp, color = onAccent())
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
        step.link?.let { link -> LinkRow(link, onOpenLink) }
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
    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.Large), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 9.dp).size(8.dp).background(LocalAccent.current))
        Spacer(Modifier.width(14.dp))
        MetroText(text, size = 17.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE, lineHeight = 24.sp, modifier = Modifier.weight(1f))
    }
}

/** The setting of a page: where it is in the settings (category › page), and a button that takes you there. */
@Composable
private fun LinkRow(link: TutorialLink, onOpen: (TutorialLink) -> Unit) {
    val label = link.section?.let { name ->
        val section = SettingsSection.valueOf(name)
        val where = if (section.category.pages.size == 1) stringResource(section.category.title)
        else stringResource(R.string.settings_where, stringResource(section.category.title), stringResource(section.title))
        stringResource(R.string.tut_link, where)
    } ?: stringResource(R.string.tut_link_home)
    MetroButton(label, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = Spacing.Large).testTag("tutorial:link")) { onOpen(link) }
}

@Composable
private fun NeedRow(need: Need, met: Boolean, denied: Boolean, onRequest: (Need) -> Unit) {
    val colors = LocalMetroColors.current
    // The words have the whole width; the button, or "allowed", is under them.
    Column(Modifier.fillMaxWidth().padding(top = Spacing.Medium, bottom = Spacing.Large).testTag("tutorial:need:${need.name}")) {
        MetroText(stringResource(need.title), size = 22.sp)
        MetroText(stringResource(need.why), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, lineHeight = 21.sp)
        Spacer(Modifier.height(10.dp))
        if (met) {
            MetroText(stringResource(R.string.need_allowed), size = MetroType.Lead, color = LocalAccent.current, modifier = Modifier.testTag("tutorial:allowed:${need.name}"))
        } else {
            // A permission Android will not ask about again can only be given in the settings of the app.
            val label = if (denied) R.string.need_action_settings else need.action
            MetroButton(stringResource(label), Modifier.testTag("tutorial:allow:${need.name}")) { onRequest(need) }
        }
    }
}

/** More pages than this get a counter ("3 of 25") instead of a dot each. */
private const val MAX_DOTS = 12

/** Width from which the dots, "skip" and "next" fit on one line. */
private val WIDE_BAR = 520.dp
