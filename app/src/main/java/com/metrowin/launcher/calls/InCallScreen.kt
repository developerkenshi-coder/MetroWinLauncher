package com.metrowin.launcher.calls

import com.metrowin.launcher.ui.components.MarkIcon
import com.metrowin.launcher.ui.components.Mark
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.dialer.CallGreen
import com.metrowin.launcher.dialer.CallRed
import com.metrowin.launcher.dialer.DialPad
import com.metrowin.launcher.dialer.PhonePrefs
import com.metrowin.launcher.dialer.prettyNumber
import com.metrowin.launcher.dialer.spokenDuration
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.flipIn
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.components.FontScaleAtMost
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.DarkColors
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.pageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the screen of a call can do; each is one line of [CallManager] or of the activity. */
class CallActions(
    val onAnswer: (Int) -> Unit, val onDecline: (Int) -> Unit, val onHangUp: (Int) -> Unit, val onHold: (Int, Boolean) -> Unit,
    val onMute: (Boolean) -> Unit, val onRoute: () -> Unit, val onTone: (Int, Char) -> Unit, val onMerge: (Int) -> Unit, val onAddCall: () -> Unit,
    /** Declines with a text message to the caller. */
    val onDeclineText: (Int, String) -> Unit = { _, _ -> },
    val onEndAndAnswer: (Int) -> Unit = {},
    val onSplit: (Int) -> Unit = {},
    val onSwap: (Int) -> Unit = {},
    /** The places the sound can go, and the choice of one. */
    val outputs: () -> List<AudioOutput> = { emptyList() },
    val onOutput: (AudioOutput) -> Unit = {},
)

/**
 * The screen of a call, as on Windows Phone: who it is at the top, the big square of their picture (the first letter of the name on the
 * accent colour) in the middle, the round buttons of the call and the red button that ends it at the bottom. With a second call, the
 * first waits on hold above. On a short screen the whole thing scrolls (and the picture goes), on a wide one the picture and the buttons
 * are side by side.
 */
@Composable
fun InCallScreen(lines: List<CallLine>, audio: CallAudio, actions: CallActions, modifier: Modifier = Modifier, prefs: PhonePrefs? = null) {
    val context = LocalContext.current
    val settings = prefs ?: remember { PhonePrefs(context) }
    val main = CallLines.main(lines)
    // With a photo of the caller the screen is that photo under a veil, and the ink is white whatever the theme.
    val photo = rememberCallerPhoto(main?.number.orEmpty())
    val colors = if (photo != null) DarkColors else LocalMetroColors.current
    // Kept over a turn of the screen: the keypad stays open and what was sent stays written.
    var keypad by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var outputs by remember { mutableStateOf<List<AudioOutput>?>(null) }
    var texts by remember { mutableStateOf(false) }
    // Back closes the keypad of the call before it leaves the screen of the call.
    BackHandler(enabled = keypad) { keypad = false }
    val accent = LocalAccent.current
    CompositionLocalProvider(LocalMetroColors provides colors) {
        Box(modifier.fillMaxSize().then(if (photo == null) Modifier.pageBackground() else Modifier)) {
            if (photo != null) {
                Image(photo, null, Modifier.fillMaxSize().testTag("incall:photo"), contentScale = ContentScale.Crop)
                // A veil that is light in the middle, where the face is, and dark where the words and the buttons are.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.4f to Color.Black.copy(alpha = 0.3f), 0.6f to Color.Black.copy(alpha = 0.3f), 1f to Color.Black.copy(alpha = 0.78f))))
            } else if (!settings.bigText) {
                // Without a photo the screen takes a little of the accent colour at the top.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to accent.copy(alpha = 0.3f), 0.45f to Color.Transparent)))
            }
            if (main == null) {
                Ended(Modifier.topBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp))
                return@Box
            }
            BoxWithConstraints(Modifier.fillMaxSize().topBarsPadding().navigationBarsPadding()) {
                val wide = maxWidth >= WIDE && maxWidth > maxHeight
                val compact = !wide && maxHeight < COMPACT
                val big = settings.bigText
                val header: @Composable () -> Unit = { Header(main, lines, actions, compact, big) }
                val body: @Composable (Modifier) -> Unit = { m ->
                    Middle(main, photo != null, settings, m, wide)
                }
                val controls: @Composable () -> Unit = {
                    if (keypad && !main.ringing) {
                        KeypadBlock(main, typed, { typed = it }, actions, settings, compact) { keypad = false }
                    } else if (!main.ringing) {
                        Controls(main, lines, audio, actions, settings, keypad) { which ->
                            when (which) {
                                Control.Keypad -> keypad = true
                                Control.Route -> {
                                    val list = if (settings.audioPicker) actions.outputs() else emptyList()
                                    if (list.size > 2) outputs = list else actions.onRoute()
                                }
                            }
                        }
                    }
                }
                val bottom: @Composable () -> Unit = { Bottom(main, lines, actions, settings) { texts = true } }
                when {
                    wide -> Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            header()
                            if (!(keypad && !main.ringing)) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { body(Modifier) }
                        }
                        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { controls() } }
                            bottom()
                        }
                    }
                    compact -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        header()
                        controls()
                        bottom()
                    }
                    else -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        header()
                        if (keypad && !main.ringing) controls() else {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { body(Modifier) }
                            controls()
                        }
                        bottom()
                    }
                }
            }
            outputs?.let { list ->
                DialogFrame(onCancel = { outputs = null }, title = stringResource(R.string.incall_audio)) {
                    list.forEach { output ->
                        Row(
                            Modifier.fillMaxWidth().testTag("incall:output:${output.kind.name}").metroPress(onClick = { actions.onOutput(output); outputs = null }).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MetroText(outputLabel(output), size = 20.sp, color = if (output.active) accent else LocalMetroColors.current.foreground, modifier = Modifier.weight(1f))
                            if (output.active) MarkIcon(Mark.Dot, 10.dp, color = accent)
                        }
                    }
                }
            }
            if (texts && main.ringing) {
                val offered = remember { settings.declineTexts ?: emptyList() }
                val shown = offered.ifEmpty { stringArrayOf(context) }.filter { it.isNotBlank() }
                DialogFrame(onCancel = { texts = false }, title = stringResource(R.string.incall_decline_with)) {
                    shown.forEach { text ->
                        Row(Modifier.fillMaxWidth().testTag("incall:text").metroPress(onClick = { texts = false; actions.onDeclineText(main.id, text) }).padding(vertical = 10.dp)) {
                            MetroText(text, size = 18.sp, weight = FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

private fun stringArrayOf(context: android.content.Context) = listOf(R.string.incall_text_busy, R.string.incall_text_later, R.string.incall_text_driving).map(context::getString)

@Composable
private fun outputLabel(output: AudioOutput) = when (output.kind) {
    OutputKind.Ear -> stringResource(R.string.incall_output_ear)
    OutputKind.Speaker -> stringResource(R.string.incall_speaker)
    OutputKind.Wired -> stringResource(R.string.incall_output_wired)
    OutputKind.Bluetooth -> output.name.ifBlank { stringResource(R.string.incall_bluetooth) }
}

/** "Call ended" comes in from the side and fades in, rather than being there from the first frame. */
@Composable
private fun Ended(modifier: Modifier) {
    val reduced = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, metroTween(durationMillis = 360, easing = Motion.Decelerate)) }
    MetroText(
        stringResource(R.string.incall_ended), size = 34.sp,
        modifier = modifier.padding(top = 40.dp).graphicsLayer { alpha = enter.value; translationX = (1f - enter.value) * -48.dp.toPx() }.testTag("incall:ended"),
    )
}

private enum class Control { Keypad, Route }

/** Who it is, and in what state; with a conference its parts, and a second call under it. */
@Composable
private fun Header(main: CallLine, lines: List<CallLine>, actions: CallActions, compact: Boolean, big: Boolean) {
    val colors = LocalMetroColors.current
    Column {
        MetroText(stringResource(R.string.incall_header), size = 13.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetroText(
                if (main.inConference) stringResource(R.string.incall_conference) else main.title.ifEmpty { stringResource(R.string.incall_unknown) }, size = if (compact) 30.sp else if (big) 44.sp else 40.sp,
                maxLines = 2, lineHeight = if (compact) 34.sp else 46.sp, modifier = Modifier.weight(1f).testTag("incall:name"),
            )
            main.sim?.let { SimBadge(it) }
        }
        if (main.name != null && main.number.isNotEmpty()) MetroText(prettyNumber(main.number), size = 20.sp, weight = FontWeight.Normal, color = colors.subtle)
        val state = stateText(main)
        // The state of the call is said to TalkBack as a state, and the time of a call is read as words ("1 minute 5 seconds"), not as digits.
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(main)
            if (main.state == android.telecom.Call.STATE_ACTIVE) CallTimer(main.connectedAt, if (big) 34 else 28, Modifier.testTag("incall:state"))
            else MetroText(state, size = 24.sp, weight = FontWeight.Normal, color = LocalAccent.current, modifier = Modifier.semantics { stateDescription = state }.testTag("incall:state"))
        }
        if (main.inConference) {
            lines.filter { it.parentId == main.id }.forEach { part ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).testTag("incall:part:${part.id}").litFill(false).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetroText(part.title.ifEmpty { stringResource(R.string.incall_unknown) }, size = 18.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    if (part.canSplit) SmallButton(stringResource(R.string.incall_split), "incall:split:${part.id}") { actions.onSplit(part.id) }
                    SmallButton(stringResource(R.string.incall_hang_up_short), "incall:partend:${part.id}") { actions.onHangUp(part.id) }
                }
            }
        }
        lines.filter { it !== main && !it.child }.forEach { other ->
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp).testTag("incall:other:${other.id}")
                    .metroPress(onClick = { if (other.held) actions.onHold(other.id, false) }).litFill(false).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetroText(other.title.ifEmpty { stringResource(R.string.incall_unknown) }, size = 20.sp, modifier = Modifier.weight(1f))
                MetroText(stateText(other), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
        }
    }
}

/** On hold and in a conference are drawn as a mark before the words, not only as the words. */
@Composable
private fun Badge(line: CallLine) {
    if (!line.held && !line.inConference) return
    val accent = LocalAccent.current
    Canvas(Modifier.padding(end = 8.dp).size(22.dp)) {
        val w = size.width
        if (line.held) {
            drawRect(accent, Offset(w * 0.2f, w * 0.12f), Size(w * 0.2f, w * 0.76f))
            drawRect(accent, Offset(w * 0.6f, w * 0.12f), Size(w * 0.2f, w * 0.76f))
        } else {
            val ring = Stroke(width = 2.dp.toPx())
            drawCircle(accent, w * 0.26f, Offset(w * 0.34f, w * 0.5f), style = ring)
            drawCircle(accent, w * 0.26f, Offset(w * 0.66f, w * 0.5f), style = ring)
        }
    }
}

@Composable
private fun SimBadge(slot: Int) {
    MetroText(
        "SIM $slot", size = 12.sp, weight = FontWeight.SemiBold, color = onAccent(),
        modifier = Modifier.padding(start = 8.dp).background(LocalAccent.current).padding(horizontal = 6.dp, vertical = 2.dp).testTag("incall:sim"),
    )
}

@Composable
private fun SmallButton(text: String, tag: String, onClick: () -> Unit) {
    MetroText(text, size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.testTag(tag).metroPress(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp))
}

/** The picture of the caller, and while the phone rings the rings that spread from it. */
@Composable
private fun Middle(main: CallLine, hasPhoto: Boolean, prefs: PhonePrefs, modifier: Modifier, wide: Boolean) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val room = if (wide) minOf(maxWidth, maxHeight) else maxHeight
        val side = minOf(maxWidth * 0.62f, room, 260.dp)
        if (hasPhoto || side < MIN_PORTRAIT) return@BoxWithConstraints
        if (main.ringing && prefs.ringAnimation && !LocalReduceMotion.current) Rings(side)
        Portrait(main, side)
    }
}

/** Three rings that grow from the picture and fade, one after the other, for as long as it rings. */
@Composable
private fun Rings(side: Dp) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "rings")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Restart), label = "phase")
    Canvas(Modifier.size(side * 1.5f).testTag("incall:rings")) {
        val base = side.toPx() / 2
        repeat(3) { i ->
            val t = (phase + i / 3f) % 1f
            drawCircle(accent.copy(alpha = (1f - t) * 0.5f), base * (1f + t * 0.5f), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** The photo of whoever is on the line (read from the contacts by the number, away from the screen), or null when there is none or it cannot be read. */
@Composable
private fun rememberCallerPhoto(number: String): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val photo by produceState<ImageBitmap?>(null, number) {
        value = if (number.isEmpty()) null else withContext(Dispatchers.IO) {
            runCatching {
                val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver.query(lookup, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?.let { uri -> context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return photo
}

/** The big square of the caller: the accent colour, with the first letter of the name, or a person when there is no name. */
@Composable
private fun Portrait(line: CallLine, side: Dp) {
    val initial = line.name?.firstOrNull { it.isLetter() }?.uppercaseChar()
    Box(Modifier.size(side).background(LocalAccent.current).testTag("incall:portrait"), contentAlignment = Alignment.Center) {
        if (initial != null) MetroText(initial.toString(), size = with(LocalDensity.current) { (side * 0.55f).toSp() }, color = Color.White)
        else GlyphIcon(Glyph.People, side * 0.55f, color = Color.White)
    }
}

@Composable
private fun stateText(line: CallLine): String = when {
    line.ringing -> stringResource(R.string.incall_incoming)
    line.dialing -> stringResource(R.string.incall_calling)
    line.held -> stringResource(R.string.incall_on_hold)
    line.state == android.telecom.Call.STATE_ACTIVE -> elapsed(line.connectedAt)
    else -> stringResource(R.string.incall_ended)
}

/** The seconds of a call so far, counting from the moment it was answered; ticks once a second. */
@Composable
internal fun rememberElapsedSeconds(since: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return if (since > 0) ((now - since) / 1000).coerceAtLeast(0) else 0
}

internal fun clockText(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)

/** The time of a call, counting from the moment it was answered. */
@Composable
internal fun elapsed(since: Long): String = clockText(rememberElapsedSeconds(since))

/**
 * The time of a call as digits of one width each, so the line does not shake as they change, and read as words by TalkBack. A new second
 * is not announced by itself: a screen reader tells the time when it is asked.
 */
@Composable
private fun CallTimer(since: Long, size: Int, modifier: Modifier = Modifier) {
    val seconds = rememberElapsedSeconds(since)
    val spoken = spokenDuration(seconds)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically) {
        clockText(seconds).forEach { c ->
            val cell = if (c == ':') (size * 0.3f).dp else (size * 0.58f).dp
            Box(Modifier.width(cell), contentAlignment = Alignment.Center) { MetroText(c.toString(), size = size.sp, weight = FontWeight.Light, color = LocalAccent.current, maxLines = 1, minScale = 1f) }
        }
    }
}

/** The keys of the call, with the number that was sent and the way back to the buttons. */
@Composable
private fun KeypadBlock(main: CallLine, typed: String, onTyped: (String) -> Unit, actions: CallActions, prefs: PhonePrefs, compact: Boolean, onHide: () -> Unit) {
    // The keypad rises from the bottom and fades in.
    val reduced = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, metroTween(durationMillis = 320, easing = Motion.Decelerate)) }
    Column(Modifier.graphicsLayer { translationY = (1f - enter.value) * 96.dp.toPx(); alpha = enter.value }) {
        MetroText(typed, size = 30.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("incall:typed"))
        DialPad(onKey = { key ->
            onTyped((typed + key).takeLast(20))
            actions.onTone(main.id, key)
        }, keyHeight = if (compact) 48.dp else 56.dp, big = prefs.bigText)
        CallButton(stringResource(R.string.incall_hide_keypad), false, "incall:hidekeypad", Modifier.fillMaxWidth().padding(top = 6.dp), onClick = onHide)
    }
}

/** The round buttons of the call, in two rows, and under them what a conference or a waiting call can do. */
@Composable
private fun Controls(main: CallLine, lines: List<CallLine>, audio: CallAudio, actions: CallActions, prefs: PhonePrefs, keypad: Boolean, onControl: (Control) -> Unit) {
    val size = if (prefs.carMode) 88.dp else 64.dp
    val width = if (prefs.carMode) 112.dp else 96.dp
    // The labels sit under round buttons of a fixed size: they are not made bigger than 1.2 times, so DE and FR words still fit on two lines.
    FontScaleAtMost(1.2f) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(CallIcon.Mute, stringResource(R.string.incall_mute), audio.muted, "incall:mute", size, width, Modifier.flipIn(0, keypad)) { actions.onMute(!audio.muted) }
                RoundButton(CallIcon.Keypad, stringResource(R.string.incall_keypad), false, "incall:keypad", size, width, Modifier.flipIn(1, keypad)) { onControl(Control.Keypad) }
                RoundButton(
                    CallIcon.Speaker, stringResource(if (audio.bluetooth) R.string.incall_bluetooth else R.string.incall_speaker), audio.speaker, "incall:speaker", size, width, Modifier.flipIn(2, keypad),
                ) { onControl(Control.Route) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(CallIcon.Hold, stringResource(R.string.incall_hold), main.held, "incall:hold", size, width, Modifier.flipIn(3, keypad), enabled = main.canHold) { actions.onHold(main.id, !main.held) }
                RoundButton(CallIcon.Add, stringResource(R.string.incall_add), false, "incall:add", size, width, Modifier.flipIn(4, keypad)) { actions.onAddCall() }
                RoundButton(CallIcon.Merge, stringResource(R.string.incall_merge), false, "incall:merge", size, width, Modifier.flipIn(5, keypad), enabled = lines.count { !it.child } > 1 || main.canMerge) { actions.onMerge(main.id) }
            }
            if (main.canSwap) CallButton(stringResource(R.string.incall_swap), false, "incall:swap", Modifier.fillMaxWidth()) { actions.onSwap(main.id) }
        }
    }
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
private fun RoundButton(icon: CallIcon, label: String, on: Boolean, tag: String, size: Dp, width: Dp, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val ink = (if (on) onAccent() else colors.foreground).copy(alpha = if (enabled) 1f else 0.4f)
    Column(modifier.width(width).testTag(tag).metroPress(onClick = onClick, enabled = enabled), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).then(if (on) Modifier.background(LocalAccent.current, CircleShape) else Modifier.border(2.dp, ink, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { CallGlyph(icon, ink, struck = on && icon == CallIcon.Mute) }
        MetroText(label, size = 13.sp, weight = FontWeight.Normal, color = colors.subtle.copy(alpha = if (enabled) 1f else 0.5f), maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
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

/** What ends the call, or answers it: the buttons (or the slider), and when another call is on, the way to end it and answer this one. */
@Composable
private fun Bottom(main: CallLine, lines: List<CallLine>, actions: CallActions, prefs: PhonePrefs, onMessage: () -> Unit) {
    val height = if (prefs.carMode) 96.dp else 64.dp
    if (main.ringing) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val other = CallLines.toEnd(lines)
            if (other != null) CallButton(stringResource(R.string.incall_end_answer), false, "incall:endanswer", Modifier.fillMaxWidth()) { actions.onEndAndAnswer(main.id) }
            if (prefs.declineMessage) CallButton(stringResource(R.string.incall_decline_with), false, "incall:message", Modifier.fillMaxWidth(), onClick = onMessage)
            if (prefs.swipeAnswer) {
                AnswerSlider({ actions.onAnswer(main.id) }, { actions.onDecline(main.id) })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(stringResource(R.string.incall_decline), CallRed, "incall:decline", height, Modifier.weight(1f)) { actions.onDecline(main.id) }
                    BigButton(stringResource(R.string.incall_answer), CallGreen, "incall:answer", height, Modifier.weight(1f)) { actions.onAnswer(main.id) }
                }
            }
        }
    } else {
        BigButton(stringResource(R.string.incall_hang_up), CallRed, "incall:hangup", height, Modifier.fillMaxWidth()) { actions.onHangUp(main.id) }
    }
}

@Composable
private fun BigButton(text: String, color: Color, tag: String, height: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(height).testTag(tag).metroPress(onClick = onClick).background(color), contentAlignment = Alignment.Center) {
        MetroText(text, size = 22.sp, weight = FontWeight.Normal, color = Color.White)
    }
}

private val WIDE = 700.dp
private val COMPACT = 560.dp
private val MIN_PORTRAIT = 64.dp
