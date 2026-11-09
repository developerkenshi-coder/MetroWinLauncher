package com.metrowin.launcher.mail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The biggest picture that is fetched only to make a small one of it: a larger file stays an icon until the user opens it. */
private const val THUMB_MAX_BYTES = 4_000_000L
private const val THUMB_PX = 160

/** The power of two to shrink a picture of [longest] pixels by so it stays at least [target] pixels long. */
internal fun thumbSample(longest: Int, target: Int = THUMB_PX): Int {
    var sample = 1
    while (longest / (sample * 2) >= target) sample *= 2
    return sample
}

/**
 * The square in front of an attachment: a small picture of it when it is an image of a size that is worth fetching (the message does not
 * carry it, so the file is fetched through [fetch] once, away from the screen), else the paper clip. A file that cannot be read stays a clip.
 */
@Composable
fun AttachmentThumb(file: MailAttachment, fetch: suspend (Int) -> Result<Triple<String, String, ByteArray>>) {
    val wanted = file.mime.startsWith("image/") && file.size <= THUMB_MAX_BYTES
    val thumb by produceState<ImageBitmap?>(null, file.index) {
        if (!wanted) return@produceState
        value = fetch(file.index).getOrNull()?.let { (_, _, bytes) ->
            withContext(Dispatchers.Default) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = thumbSample(maxOf(bounds.outWidth, bounds.outHeight)) }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }
        }
    }
    val image = thumb
    if (image != null) Image(image, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
    else Box(Modifier.size(if (wanted) 56.dp else 24.dp).background(if (wanted) LocalAccent.current.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) { MetroText("📎", size = 18.sp) }
}
