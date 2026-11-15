package com.metrowin.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.anim.MotionTable
import com.metrowin.launcher.ui.anim.Surface
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.panel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A dimmed page with [content] in a panel in the middle; touching outside it calls [onCancel]. The panel scrolls by itself when its content is
 * long: never put a vertically scrolling list or column in [content] without a fixed height (it would be measured with no limit and crash).
 * In the classic look the panel swivels in from its top edge, in the others it fades in and grows a little; when closed by a touch outside it
 * goes the same way back before [onCancel] runs (with reduced motion it just appears and goes); a dialog the content itself closes (its own
 * button) just goes, as it is taken off the screen at once. The panel is at most 480 dp wide, in the middle of the page.
 *
 * This is the one dialog of the launcher and of every mini app: do not open a system `AlertDialog` or a bare `Dialog` for a question, a choice
 * or a form. (The only windows of their own are those that need a window: the camera scanner and the card in the Wallet.)
 */
@Composable
fun DialogFrame(onCancel: () -> Unit, modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    val colors = LocalMetroColors.current
    val currentCancel by rememberUpdatedState(onCancel)
    val scope = rememberCoroutineScope()
    val classic = LocalStyle.current.isClassic
    val animated = !LocalReduceMotion.current
    val timing = MotionTable.timing(Surface.Dialog, LocalStyle.current.kind, !animated)
    // 1 while the panel is turned away, 0 when it faces the screen.
    val away = remember { Animatable(if (animated) 1f else 0f) }
    LaunchedEffect(Unit) { if (animated) away.animateTo(0f, metroTween(durationMillis = timing.inMs, easing = Motion.Decelerate)) }
    var leaving by remember { mutableStateOf(false) }
    fun cancel() {
        if (leaving) return
        if (!animated) { currentCancel(); return }
        leaving = true
        scope.launch {
            away.animateTo(1f, metroTween(durationMillis = timing.outMs, easing = Motion.Accelerate))
            currentCancel()
        }
    }
    // Back closes the dialog (and not the page behind it); the keyboard goes with it; the focus moves into the panel, unless a field in it took it already.
    BackHandler { cancel() }
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    val focus = remember { FocusRequester() }
    var focusInside by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!focusInside) runCatching { focus.requestFocus() }
    }
    Box(
        modifier
            .fillMaxSize()
            .drawBehind { drawRect(colors.background.copy(alpha = 0.85f), alpha = 1f - away.value) }
            .pointerInput(Unit) { detectTapGestures { cancel() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                // On a tablet or in landscape the panel does not stretch over the screen: it keeps the width of a phone, in the middle.
                .widthIn(max = MAX_WIDTH + Spacing.Page * 2)
                .fillMaxWidth()
                .padding(horizontal = Spacing.Page)
                .graphicsLayer {
                    if (classic) {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        rotationX = -SWIVEL_DEGREES * away.value
                        cameraDistance = Motion.CAMERA * density
                        alpha = 1f - away.value * away.value
                    } else {
                        // Glass and Droid: the panel fades in and grows a little from the middle.
                        val grow = 1f - POP_FROM * away.value
                        scaleX = grow
                        scaleY = grow
                        alpha = 1f - away.value
                    }
                }
                .panel()
                .pointerInput(Unit) { detectTapGestures { } }
                // TalkBack announces the pane by its title and can dismiss it; Escape does the same on a keyboard.
                .semantics { if (title != null) paneTitle = title; dismiss { cancel(); true } }
                .onPreviewKeyEvent { if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) { cancel(); true } else false }
                .focusRequester(focus)
                .onFocusChanged { focusInside = it.hasFocus }
                .focusable()
                // On a short screen or with big text the panel scrolls: its last button can always be reached.
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The MessageBox of Windows Phone has its title in small capitals above the text; the other looks keep the quiet caption.
            if (title != null) {
                if (LocalStyle.current.isClassic) MetroText(title.uppercase(Locale.getDefault()), size = MetroType.Note, weight = FontWeight.SemiBold)
                else MetroText(title, size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
            CompositionLocalProvider(LocalInDialog provides true) { content() }
        }
    }
}

/**
 * The two buttons that close a dialog that asks for something (a note, a date, a name): the answer in the accent on the left, and the way out
 * beside it. The pickers of dates and times, and the dialogs of Start, all end with this.
 */
@Composable
fun DialogButtons(
    onOk: () -> Unit, onCancel: () -> Unit, okTag: String, cancelTag: String, modifier: Modifier = Modifier,
    okText: String = stringResource(android.R.string.ok),
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.Large)) {
        MetroButton(okText, Modifier.weight(1f).testTag(okTag), role = ButtonRole.Primary, onClick = onOk)
        MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag(cancelTag), onClick = onCancel)
    }
}

private const val SWIVEL_DEGREES = 85f
private const val POP_FROM = 0.08f
private val MAX_WIDTH = 480.dp
