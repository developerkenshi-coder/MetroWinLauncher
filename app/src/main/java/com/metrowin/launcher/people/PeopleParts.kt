package com.metrowin.launcher.people

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The thumbnails kept by their weight in bytes (not by how many: a few big photos no longer push out dozens of small ones). */
private val photos = object : LruCache<String, Bitmap>(PHOTO_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private const val PHOTO_CACHE_BYTES = 12 * 1024 * 1024
private const val THUMB_PX = 192

/** The small photo of a contact at [uri], read off the main thread at the size of a tile, not of the file. */
@Composable
fun rememberPhoto(uri: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(uri?.let(photos::get)?.asImageBitmap(), uri) {
        if (uri != null && value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { PeopleRepo.decodeSampled({ context.contentResolver.openInputStream(android.net.Uri.parse(uri)) }, THUMB_PX) }.getOrNull()
            }?.also { photos.put(uri, it) }?.asImageBitmap()
        }
    }
    return bitmap
}

/** The tile of the look in use: a square in Classic, softly rounded in Glass and Droid. */
@Composable
fun tileShape(): Shape = LocalStyle.current.squareShape

/** A contact as a tile: its photo, or its first letter on the accent colour, and the name over the foot of it when [label]. */
@Composable
fun PhotoTile(name: String, photo: ImageBitmap?, modifier: Modifier = Modifier, label: Boolean = true, letterSize: Int = 40) {
    val shape = tileShape()
    Box(modifier.aspectRatio(1f).clip(shape).background(LocalAccent.current)) {
        MetroText(PeopleBook.avatar(name), size = letterSize.sp, color = onAccent(), modifier = Modifier.align(Alignment.Center))
        // The photo fades in over the letter, instead of jumping in.
        val alpha by androidx.compose.animation.core.animateFloatAsState(if (photo != null) 1f else 0f, tween(if (LocalReduceMotion.current) 0 else 220), label = "photo")
        if (photo != null) Image(photo, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentScale = ContentScale.Crop)
        // The scrim stays dark whatever the theme: the white name has to be read over any photo.
        if (label) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))).padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            MetroText(name, size = 16.sp, weight = FontWeight.Normal, maxLines = 2, minScale = 0.7f, color = Color.White)
        }
    }
}

/** Grey rows with a pulse, for the list while the contacts are read. */
@Composable
fun SkeletonRows(count: Int = 7) {
    val colors = LocalMetroColors.current
    val pulse = if (LocalReduceMotion.current) 0.35f else rememberInfiniteTransition(label = "skeleton").animateFloat(0.2f, 0.45f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse").value
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(colors.foreground.copy(alpha = pulse)))
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(width = (120 + it * 18 % 70).dp, height = 16.dp).background(colors.foreground.copy(alpha = pulse)))
                    Box(Modifier.size(width = 80.dp, height = 11.dp).background(colors.foreground.copy(alpha = pulse * 0.7f)))
                }
            }
        }
    }
}

/** A big picture and a line under it, for a page with nothing in it. */
@Composable
fun EmptyState(glyph: PeopleGlyph, text: String, modifier: Modifier = Modifier, tag: String = "") {
    val colors = LocalMetroColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp).let { if (tag.isEmpty()) it else it.testTag(tag) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PeopleIcon(glyph, 72.dp, color = colors.subtle.copy(alpha = 0.5f))
        MetroText(text, size = 18.sp, color = colors.subtle, maxLines = Int.MAX_VALUE, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** A round button with a 2 dp edge, like the ones of the bars of Windows Phone; it is a button for TalkBack, and reads its [label]. */
@Composable
fun Round(glyph: PeopleGlyph, label: String, modifier: Modifier = Modifier, on: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Column(
        modifier.metroPress(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 10.dp, vertical = 4.dp).semantics(mergeDescendants = true) { role = Role.Button; contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).border(2.dp, if (on) accent else colors.foreground, CircleShape).clip(CircleShape).background(if (on) accent else Color.Transparent), contentAlignment = Alignment.Center) {
            PeopleIcon(glyph, 24.dp, color = if (on) onAccent() else colors.foreground)
        }
        MetroText(label, size = 12.sp, weight = FontWeight.Normal, modifier = Modifier.padding(top = 4.dp), maxLines = 1, minScale = 0.7f)
    }
}

/** The small accent caption over a block of a card. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    MetroText(text, size = 16.sp, color = LocalAccent.current, maxLines = 1, modifier = modifier.padding(top = 18.dp, bottom = 4.dp))
}

/** A panel over the whole page, with the page dimmed behind it; a touch outside closes it. */
@Composable
fun Sheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalMetroColors.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier.fillMaxWidth().heightIn(max = 560.dp).background(colors.background).navigationBarsPadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        ) { content() }
    }
}

/** A photo over the whole page that two fingers zoom and move; a touch on the dark closes it. */
@Composable
fun FullPhoto(photo: ImageBitmap, name: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(photo, name, Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
    }
}

private const val MAX_ZOOM = 5f

/** A thin bar at the right edge that shows where the list is, over its length; it only shows while the list is longer than the page. */
fun Modifier.scrollIndicator(state: LazyListState, color: Color): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    if (total > visible && visible > 0) {
        val height = (size.height * visible / total).coerceAtLeast(INDICATOR_MIN_PX)
        val top = (size.height - height) * (state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
        drawRect(color, Offset(size.width - INDICATOR_WIDTH_PX, top), Size(INDICATOR_WIDTH_PX, height))
    }
}

private const val INDICATOR_MIN_PX = 40f
private const val INDICATOR_WIDTH_PX = 5f

/** A line along the foot of a row of a list, when the dividers are on. */
fun Modifier.divider(on: Boolean, color: Color): Modifier = if (!on) this else drawWithContent {
    drawContent()
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1f)
}

/** The row of the right height for a thumb, with its text for TalkBack read as one thing. */
fun Modifier.listRow(description: String): Modifier = heightIn(min = 56.dp).fillMaxWidth().semantics(mergeDescendants = true) { role = Role.Button; contentDescription = description }
