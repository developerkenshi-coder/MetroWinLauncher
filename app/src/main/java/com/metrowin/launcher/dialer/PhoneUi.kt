package com.metrowin.launcher.dialer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** The green of the call button and the red of the one that ends it: one value for the keypad, the screen of a call and the strip over the launcher. */
val CallGreen = Color(0xFF2E9E41)
val CallRed = Color(0xFFD13438)

/** The way a call of the log went, for the little arrow of its row. */
enum class CallKind { Incoming, Outgoing, Missed }

/** The arrow of a call, drawn in lines: out to the top right, in to the bottom left, and a missed one is an arrow in with a cross. */
@Composable
fun CallKindIcon(kind: CallKind, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val line = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val out = kind == CallKind.Outgoing
        // The shaft goes from corner to corner; the head sits at the end the arrow points to.
        val from = if (out) Offset(w * 0.2f, w * 0.8f) else Offset(w * 0.75f, w * 0.25f)
        val to = if (out) Offset(w * 0.75f, w * 0.25f) else Offset(w * 0.2f, w * 0.8f)
        drawLine(color, from, to, line.width, StrokeCap.Round)
        val corner = if (out) Offset(w * 0.75f, w * 0.25f) else Offset(w * 0.2f, w * 0.8f)
        val dx = if (out) -w * 0.35f else w * 0.35f
        val dy = if (out) w * 0.35f else -w * 0.35f
        drawLine(color, corner, Offset(corner.x + dx, corner.y), line.width, StrokeCap.Round)
        drawLine(color, corner, Offset(corner.x, corner.y + dy), line.width, StrokeCap.Round)
        if (kind == CallKind.Missed) {
            drawLine(color, Offset(w * 0.62f, w * 0.12f), Offset(w * 0.88f, w * 0.38f), line.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.88f, w * 0.12f), Offset(w * 0.62f, w * 0.38f), line.width, StrokeCap.Round)
        }
    }
}

/** The key that takes the last digit away: a label with a cross, drawn in lines (the font of the launcher has no such sign). */
@Composable
fun BackspaceIcon(color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val line = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.34f, h * 0.2f); lineTo(w * 0.92f, h * 0.2f); lineTo(w * 0.92f, h * 0.8f); lineTo(w * 0.34f, h * 0.8f); lineTo(w * 0.06f, h * 0.5f); close()
        }
        drawPath(body, color, style = line)
        drawLine(color, Offset(w * 0.5f, h * 0.38f), Offset(w * 0.74f, h * 0.62f), line.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.74f, h * 0.38f), Offset(w * 0.5f, h * 0.62f), line.width, StrokeCap.Round)
    }
}

/** An empty page: a glyph of the app over the words that say why it is empty. */
@Composable
fun EmptyState(glyph: Glyph, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlyphIcon(glyph, 64.dp, color = LocalMetroColors.current.subtle.copy(alpha = 0.7f))
        PageNote(text, Modifier.padding(top = 12.dp))
    }
}

/** A number as it is read: with the spaces of its country (+39 333 123 4567). What cannot be formatted (a short code, the digits typed so far) stays as it is. */
fun prettyNumber(number: String): String {
    if (number.length < 4 || number.any { it == '*' || it == '#' }) return number
    return runCatching { PhoneNumberUtils.formatNumber(number, Locale.getDefault().country.ifEmpty { "US" }) }.getOrNull() ?: number
}

/** The size of the number on the keypad for [length] characters: it shrinks so that a long one still fits on the line. */
fun numberSize(length: Int, big: Boolean = false): Int = when {
    length <= 10 -> if (big) 44 else 36
    length <= 14 -> if (big) 34 else 30
    length <= 20 -> if (big) 26 else 24
    else -> if (big) 20 else 18
}

/** The pictures of the contacts, kept for the lists: a small cache, so scrolling back does not read them again. */
internal object Avatars {
    private val bitmaps = LruCache<String, ImageBitmap>(64)
    private val byNumber = HashMap<String, String>()

    /** The small picture at [uri]; null when there is none or it cannot be read. Blocking: off the main thread. */
    fun load(context: Context, uri: String): ImageBitmap? {
        bitmaps.get(uri)?.let { return it }
        val bitmap = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull() ?: return null
        bitmaps.put(uri, bitmap)
        return bitmap
    }

    /** The address of the picture of whoever has [number], or null. Blocking. */
    fun uriOf(context: Context, number: String): String? {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return null
        synchronized(byNumber) { byNumber[number] }?.let { return it.ifEmpty { null } }
        val uri = runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        synchronized(byNumber) { byNumber[number] = uri.orEmpty() }
        return uri
    }
}

/**
 * A square for a person in a list, as the tiles are: the picture of the contact when there is one (found by [photoUri], or by [number] when
 * that is null and a number is given), else the first letter of the name on the accent colour.
 */
@Composable
fun ContactAvatar(name: String, modifier: Modifier = Modifier, photoUri: String? = null, number: String? = null, size: Dp = 44.dp) {
    val context = LocalContext.current.applicationContext
    val photo by produceState<ImageBitmap?>(null, photoUri, number) {
        value = withContext(Dispatchers.IO) { (photoUri ?: number?.let { Avatars.uriOf(context, it) })?.let { Avatars.load(context, it) } }
    }
    val initial = name.firstOrNull { it.isLetter() }?.uppercaseChar()
    Box(modifier.size(size).background(LocalAccent.current), contentAlignment = Alignment.Center) {
        val picture = photo
        if (picture != null) Image(picture, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else if (initial != null) MetroText(initial.toString(), size = (size.value * 0.5f).sp, weight = FontWeight.Normal, color = Color.White, textAlign = TextAlign.Center)
        else GlyphIcon(Glyph.People, size * 0.55f, color = Color.White)
    }
}

/** The tone of a key of the keypad, played locally (not into a call), when [on]; null when it is off or the phone gives no generator. */
@Composable
internal fun rememberKeyTones(on: Boolean): ((Char) -> Unit)? {
    if (!on) return null
    val generator = remember { runCatching { ToneGenerator(AudioManager.STREAM_DTMF, TONE_VOLUME) }.getOrNull() }
    DisposableEffect(generator) { onDispose { generator?.release() } }
    return generator?.let { g -> { key: Char -> g.startTone(toneOf(key), TONE_MS) } }
}

private fun toneOf(key: Char) = when (key) {
    '*' -> ToneGenerator.TONE_DTMF_S
    '#' -> ToneGenerator.TONE_DTMF_P
    in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (key - '0')
    else -> ToneGenerator.TONE_DTMF_0
}

private const val TONE_VOLUME = 60
private const val TONE_MS = 90

/** A time as words in the language of the phone ("1 minute, 5 seconds"), for a screen reader: "01:05" is read as digits. */
fun spokenDuration(seconds: Long): String = runCatching {
    val parts = ArrayList<android.icu.util.Measure>(3)
    if (seconds >= 3600) parts += android.icu.util.Measure(seconds / 3600, android.icu.util.MeasureUnit.HOUR)
    if (seconds >= 60) parts += android.icu.util.Measure(seconds / 60 % 60, android.icu.util.MeasureUnit.MINUTE)
    if (seconds % 60 > 0 || parts.isEmpty()) parts += android.icu.util.Measure(seconds % 60, android.icu.util.MeasureUnit.SECOND)
    android.icu.text.MeasureFormat.getInstance(Locale.getDefault(), android.icu.text.MeasureFormat.FormatWidth.WIDE).formatMeasures(*parts.toTypedArray())
}.getOrDefault("$seconds")
