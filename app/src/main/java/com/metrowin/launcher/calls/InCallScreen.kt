package com.metrowin.launcher.calls

import com.metrowin.launcher.ui.components.topBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.theme.LocalAccent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.dialer.DialPad
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.delay

private val Green = Color(0xFF2E9E41)
private val Red = Color(0xFFD13438)

/** What the screen of a call can do; each is one line of [CallManager] or of the activity. */
class CallActions(
    val onAnswer: (Int) -> Unit, val onDecline: (Int) -> Unit, val onHangUp: (Int) -> Unit, val onHold: (Int, Boolean) -> Unit,
    val onMute: (Boolean) -> Unit, val onRoute: () -> Unit, val onTone: (Int, Char) -> Unit, val onMerge: (Int) -> Unit, val onAddCall: () -> Unit,
)

/**
 * The screen of a call, as on Windows Phone: who it is at the top, the big square of their picture (the first letter of the name on the
 * accent colour) in the middle, the round buttons of the call and the red button that ends it at the bottom. With a second call, the
 * first waits on hold above.
 */
@Composable
fun InCallScreen(lines: List<CallLine>, audio: CallAudio, actions: CallActions, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val main = lines.firstOrNull { it.ringing } ?: lines.firstOrNull { !it.held } ?: lines.firstOrNull()
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    // Back closes the keypad of the call before it leaves the screen of the call.
    androidx.activity.compose.BackHandler(enabled = keypad) { keypad = false }
    Column(
        modifier.fillMaxSize().pageBackground().topBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (main == null) {
            MetroText(stringResource(R.string.incall_ended), size = 34.sp, modifier = Modifier.padding(top = 40.dp).testTag("incall:ended"))
            return@Column
        }
        Column {
            MetroText(stringResource(R.string.incall_header), size = 13.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(bottom = 6.dp))
            MetroText(
                if (main.inConference) stringResource(R.string.incall_conference) else main.title, size = 40.sp, maxLines = 2, lineHeight = 46.sp,
                modifier = Modifier.testTag("incall:name"),
            )
            if (main.name != null && main.number.isNotEmpty()) MetroText(main.number, size = 20.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroText(stateText(main), size = 24.sp, weight = FontWeight.Normal, color = LocalAccent.current, modifier = Modifier.padding(top = 4.dp).testTag("incall:state"))
            lines.filter { it !== main }.forEach { other ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp).testTag("incall:other:${other.id}")
                        .metroPress(onClick = { if (other.held) actions.onHold(other.id, false) }).litFill(false).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetroText(other.title, size = 20.sp, modifier = Modifier.weight(1f))
                    MetroText(stateText(other), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
        }
        if (keypad && !main.ringing) {
            Column {
                MetroText(typed, size = 30.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("incall:typed"))
                DialPad(onKey = { key ->
                    typed = (typed + key).takeLast(20)
                    actions.onTone(main.id, key)
                }, keyHeight = 56.dp)
                CallButton(stringResource(R.string.incall_hide_keypad), false, "incall:hidekeypad", Modifier.fillMaxWidth().padding(top = 6.dp)) { keypad = false }
            }
        } else {
            // The picture of the caller: a big square, the first letter of the name in it. It gives way when the room is short.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth * 0.62f, maxHeight, 260.dp)
                if (side >= 96.dp) Portrait(main, side)
            }
            if (!main.ringing) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundButton(CallIcon.Mute, stringResource(R.string.incall_mute), audio.muted, "incall:mute") { actions.onMute(!audio.muted) }
                        RoundButton(CallIcon.Keypad, stringResource(R.string.incall_keypad), false, "incall:keypad") { keypad = true }
                        RoundButton(
                            CallIcon.Speaker, stringResource(if (audio.bluetooth) R.string.incall_bluetooth else R.string.incall_speaker), audio.speaker, "incall:speaker",
                        ) { actions.onRoute() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundButton(CallIcon.Hold, stringResource(R.string.incall_hold), main.held, "incall:hold", enabled = main.canHold) { actions.onHold(main.id, !main.held) }
                        RoundButton(CallIcon.Add, stringResource(R.string.incall_add), false, "incall:add") { actions.onAddCall() }
                        RoundButton(CallIcon.Merge, stringResource(R.string.incall_merge), false, "incall:merge", enabled = lines.size > 1 || main.canMerge) { actions.onMerge(main.id) }
                    }
                }
            }
        }
        if (main.ringing) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(stringResource(R.string.incall_decline), Red, "incall:decline", Modifier.weight(1f)) { actions.onDecline(main.id) }
                BigButton(stringResource(R.string.incall_answer), Green, "incall:answer", Modifier.weight(1f)) { actions.onAnswer(main.id) }
            }
        } else {
            BigButton(stringResource(R.string.incall_hang_up), Red, "incall:hangup", Modifier.fillMaxWidth()) { actions.onHangUp(main.id) }
        }
    }
}

/** The big square of the caller: the accent colour, with the first letter of the name, or a person when there is no name. */
@Composable
private fun Portrait(line: CallLine, side: androidx.compose.ui.unit.Dp) {
    val initial = line.name?.firstOrNull { it.isLetter() }?.uppercaseChar()
    Box(Modifier.size(side).background(LocalAccent.current).testTag("incall:portrait"), contentAlignment = Alignment.Center) {
        if (initial != null) MetroText(initial.toString(), size = with(LocalDensity.current) { (side * 0.55f).toSp() }, color = Color.White)
        else GlyphIcon(Glyph.People, side * 0.55f, color = Color.White)
    }
}

@Composable
private fun stateText(line: CallLine): String {
    return when {
        line.ringing -> stringResource(R.string.incall_incoming)
        line.dialing -> stringResource(R.string.incall_calling)
        line.held -> stringResource(R.string.incall_on_hold)
        line.state == android.telecom.Call.STATE_ACTIVE -> elapsed(line.connectedAt)
        else -> stringResource(R.string.incall_ended)
    }
}

/** The time of a call, counting from the moment it was answered. */
@Composable
internal fun elapsed(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = if (since > 0) ((now - since) / 1000).coerceAtLeast(0) else 0
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun CallButton(text: String, on: Boolean, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).testTag(tag).metroPress(onClick = onClick, enabled = enabled).litFill(on),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            text, size = 16.sp, weight = FontWeight.Normal, maxLines = 2, textAlign = TextAlign.Center,
            color = if (on) onAccent() else LocalMetroColors.current.foreground.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

private enum class CallIcon { Mute, Keypad, Speaker, Hold, Add, Merge }

/** A round button of the call, as the buttons of the app bar: a ring with a picture in it and the name under it; filled with the accent while it is on. */
@Composable
private fun RoundButton(icon: CallIcon, label: String, on: Boolean, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val ink = (if (on) onAccent() else colors.foreground).copy(alpha = if (enabled) 1f else 0.4f)
    Column(modifier.width(96.dp).testTag(tag).metroPress(onClick = onClick, enabled = enabled), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).then(if (on) Modifier.background(LocalAccent.current, CircleShape) else Modifier.border(2.dp, ink, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { CallGlyph(icon, ink, struck = on && icon == CallIcon.Mute) }
        MetroText(label, size = 13.sp, weight = FontWeight.Normal, color = colors.subtle.copy(alpha = if (enabled) 1f else 0.5f), maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The pictures of the buttons, drawn in lines like the glyphs of the tiles. */
@Composable
private fun CallGlyph(icon: CallIcon, ink: Color, struck: Boolean) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val line = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            CallIcon.Mute -> {
                drawRoundRect(ink, Offset(w * 0.36f, h * 0.06f), Size(w * 0.28f, h * 0.5f), CornerRadius(w * 0.14f), style = line)
                drawArc(ink, 0f, 180f, false, Offset(w * 0.22f, h * 0.3f), Size(w * 0.56f, h * 0.42f), style = line)
                drawLine(ink, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.92f), line.width, StrokeCap.Round)
                if (struck) drawLine(ink, Offset(w * 0.12f, h * 0.9f), Offset(w * 0.88f, h * 0.1f), line.width, StrokeCap.Round)
            }
            CallIcon.Keypad -> for (row in 0..2) for (col in 0..2) {
                drawRect(ink, Offset(w * (0.1f + col * 0.31f), h * (0.1f + row * 0.31f)), Size(w * 0.18f, h * 0.18f))
            }
            CallIcon.Speaker -> {
                val body = Path().apply {
                    moveTo(w * 0.08f, h * 0.38f); lineTo(w * 0.28f, h * 0.38f); lineTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.5f, h * 0.84f); lineTo(w * 0.28f, h * 0.62f); lineTo(w * 0.08f, h * 0.62f); close()
                }
                drawPath(body, ink, style = line)
                drawArc(ink, -45f, 90f, false, Offset(w * 0.42f, h * 0.3f), Size(w * 0.36f, h * 0.4f), style = line)
                drawArc(ink, -50f, 100f, false, Offset(w * 0.46f, h * 0.12f), Size(w * 0.5f, h * 0.76f), style = line)
            }
            CallIcon.Hold -> {
                drawRect(ink, Offset(w * 0.2f, h * 0.12f), Size(w * 0.2f, h * 0.76f))
                drawRect(ink, Offset(w * 0.6f, h * 0.12f), Size(w * 0.2f, h * 0.76f))
            }
            CallIcon.Add -> {
                drawLine(ink, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.88f), line.width, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), line.width, StrokeCap.Round)
            }
            CallIcon.Merge -> {
                drawCircle(ink, w * 0.26f, Offset(w * 0.34f, h * 0.5f), style = line)
                drawCircle(ink, w * 0.26f, Offset(w * 0.66f, h * 0.5f), style = line)
            }
        }
    }
}

@Composable
private fun BigButton(text: String, color: Color, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(64.dp).testTag(tag).metroPress(onClick = onClick).background(color), contentAlignment = Alignment.Center) {
        MetroText(text, size = 22.sp, weight = FontWeight.Normal, color = Color.White)
    }
}
