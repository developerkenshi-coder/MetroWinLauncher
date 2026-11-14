package com.metrowin.launcher.news

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.arrival
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.start.ageText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** What a list of headlines needs besides the headlines: the options, what was opened, what is new, the time (it moves on, so the ages do), and the word searched for. */
data class NewsView(
    val opts: NewsOpts = NewsOpts(),
    val read: Set<String> = emptySet(),
    val fresh: Set<String> = emptySet(),
    val now: Long = System.currentTimeMillis(),
    val query: String = "",
)

private fun sized(base: Int, scale: Float): TextUnit = (base * scale).sp

/**
 * The headlines of a source. A feed with pictures is laid out as a page of them (the first headline large, the rest with a small one); one
 * with none (Google News) as a list of headlines. On a wide screen the headlines after the first go two by two. A touch opens a headline,
 * a long touch asks [onLong] for what else can be done with it.
 */
@Composable
internal fun NewsList(
    items: List<NewsItem>,
    images: NewsPictures,
    showImages: Boolean,
    view: NewsView = NewsView(),
    onLong: (NewsItem) -> Unit = {},
    onOpen: (NewsItem) -> Unit,
) {
    val opts = view.opts
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP
    val withPictures = showImages && !opts.compact && items.any { it.image.isNotEmpty() }
    // Once a page of pictures, a page of pictures: the rows keep their size when a refresh brings or loses a picture.
    val seen = remember { booleanArrayOf(false) }
    if (withPictures) seen[0] = true
    val pictured = seen[0] && showImages && !opts.compact
    val distinct = remember(items) { items.distinctBy { it.link } }
    val rows = remember(distinct, view.now / 60_000L, opts.days, wide) { NewsRows.build(distinct, view.now, opts.days, wide) }
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("nws:list"), state = listState) {
        itemsIndexed(rows, key = { i, row -> when (row) { is NewsRow.Day -> "d$i"; is NewsRow.One -> row.item.link; is NewsRow.Two -> row.a.link } }) { i, row ->
            when (row) {
                is NewsRow.Day -> DayLine(row, view)
                is NewsRow.One -> Box(Modifier.featherRow(i)) { Story(row.item, row.index, images, pictured, wide, view, onOpen, onLong) }
                is NewsRow.Two -> Row(Modifier.featherRow(i), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.weight(1f)) { Story(row.a, row.aIndex, images, pictured, wide, view, onOpen, onLong) }
                    Box(Modifier.weight(1f)) { Story(row.b, row.bIndex, images, pictured, wide, view, onOpen, onLong) }
                }
            }
        }
    }
}

private const val WIDE_DP = 600

@Composable
private fun DayLine(row: NewsRow.Day, view: NewsView) {
    val text = when (row.ago) {
        0L -> stringResource(R.string.nws_day_today)
        1L -> stringResource(R.string.nws_day_yesterday)
        else -> remember(row.at) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(row.at)) }
    }
    MetroText(
        text, size = sized(14, view.opts.scale), weight = FontWeight.SemiBold, color = LocalAccent.current,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp).semantics { heading() }.testTag("nws:day"),
    )
}

@Composable
private fun Story(item: NewsItem, index: Int, images: NewsPictures, pictured: Boolean, wide: Boolean, view: NewsView, onOpen: (NewsItem) -> Unit, onLong: (NewsItem) -> Unit) {
    val opts = view.opts
    val isRead = opts.trackRead && item.link in view.read
    val isNew = opts.markNew && item.link in view.fresh
    val state = when { isNew -> stringResource(R.string.nws_new); isRead -> stringResource(R.string.nws_read); else -> null }
    val press = Modifier
        .semantics { state?.let { stateDescription = it } }
        .metroPress(onClick = { onOpen(item) }, onLongClick = { onLong(item) }, longClickLabel = stringResource(R.string.nws_options))
    val colour = if (isRead) LocalMetroColors.current.subtle else LocalMetroColors.current.foreground
    val k = opts.scale
    val tight = if (opts.compact) 4.dp else 9.dp
    if (index == 0) {
        Column(Modifier.fillMaxWidth().testTag("nws:item").then(press).padding(top = 6.dp, bottom = if (opts.compact) 8.dp else 18.dp)) {
            if (pictured && item.image.isNotEmpty()) {
                RemoteImage(item.image, images, Modifier.fillMaxWidth().aspectRatio(if (wide) 2.6f else 1.6f).testTag("nws:hero"), maxWidth = 1080, dim = opts.dimImages)
                Box(Modifier.height(14.dp))
            }
            MetroText(item.title, size = sized(30, k), weight = FontWeight.SemiBold, maxLines = 5, lineHeight = sized(36, k), color = colour, highlight = view.query)
            SourceLine(item, sized(17, k), view, isNew, Modifier.padding(top = 6.dp))
        }
    } else {
        Row(Modifier.fillMaxWidth().testTag("nws:item").then(press).padding(vertical = tight)) {
            if (pictured) {
                // The box stands where the picture will be, so the row does not change its height when the picture comes.
                if (item.image.isNotEmpty()) RemoteImage(item.image, images, Modifier.size(width = 100.dp, height = 70.dp).testTag("nws:thumb"), maxWidth = 300, dim = opts.dimImages)
                else Box(Modifier.size(width = 100.dp, height = 70.dp).background(LocalMetroColors.current.surface))
                Box(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                MetroText(
                    item.title, size = sized(if (pictured) 18 else 21, k), maxLines = if (pictured) 3 else 4, lineHeight = sized(if (pictured) 23 else 26, k),
                    weight = if (pictured) FontWeight.Normal else FontWeight.Light, color = colour, highlight = view.query,
                )
                SourceLine(item, sized(14, k), view, isNew, Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** The line under a headline: a mark when it is new, who published it in the accent colour, and how long ago (it moves on with [NewsView.now]). */
@Composable
private fun SourceLine(item: NewsItem, size: TextUnit, view: NewsView, isNew: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val age = ageText(item.publishedAt, view.now)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isNew) Box(Modifier.padding(end = 8.dp).size(8.dp).background(LocalAccent.current).clearAndSetSemantics { })
        if (item.source.isNotEmpty()) MetroText(item.source, size = size, weight = FontWeight.Normal, color = LocalAccent.current, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        // The dot between the two is only for the eyes: a screen reader would say "dot".
        if (item.source.isNotEmpty() && age != null) MetroText("  ·  ", size = size, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.clearAndSetSemantics { })
        if (age != null) MetroText(age, size = size, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
    }
}

private sealed interface Picture {
    data object Loading : Picture

    class Ready(val image: ImageBitmap) : Picture

    data object Failed : Picture
}

/** A picture fetched off the main thread: a pulse while it comes, a crossed box when it never does. */
@Composable
internal fun RemoteImage(url: String, images: NewsPictures, modifier: Modifier, maxWidth: Int, dim: Boolean = false) {
    val picture by produceState<Picture>(Picture.Loading, url) {
        value = withContext(Dispatchers.IO) { images.load(url, maxWidth)?.asImageBitmap() }?.let { Picture.Ready(it) } ?: Picture.Failed
    }
    val colors = LocalMetroColors.current
    // A decoration: the headline next to it says what the story is.
    Box(modifier.background(colors.surface).clearAndSetSemantics { }) {
        when (val p = picture) {
            Picture.Loading -> Box(Modifier.fillMaxSize().pulse())
            is Picture.Ready -> Image(
                p.image, null, Modifier.fillMaxSize().arrival(), contentScale = ContentScale.Crop,
                colorFilter = if (dim && colors.background.luminance() < 0.5f) ColorFilter.tint(Color.Black.copy(alpha = 0.3f), BlendMode.SrcAtop) else null,
            )
            Picture.Failed -> Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                val stroke = Stroke(width = 2.dp.toPx())
                drawRect(colors.subtle, Offset.Zero, Size(size.width, size.height), style = stroke)
                drawLine(colors.subtle, Offset.Zero, Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
                drawLine(colors.subtle, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

/** A slow pulse of the foreground colour over a box that waits for its content; still when the user asked for less motion. */
@Composable
internal fun Modifier.pulse(): Modifier {
    val ink = LocalMetroColors.current.foreground
    if (LocalReduceMotion.current) return background(ink.copy(alpha = 0.08f))
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(0.04f, 0.14f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    return background(ink.copy(alpha = alpha))
}

/** Rows of grey bars where the headlines will be, while the first read of a source is on its way. */
@Composable
internal fun NewsSkeleton(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.rec_working)
    Column(modifier.fillMaxWidth().testTag("nws:skeleton").clearAndSetSemantics { contentDescription = label }) {
        repeat(6) { i ->
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.fillMaxWidth(0.95f).height(if (i == 0) 22.dp else 16.dp).pulse())
                Box(Modifier.fillMaxWidth(0.7f - 0.1f * (i % 3)).height(if (i == 0) 22.dp else 16.dp).pulse())
                Box(Modifier.fillMaxWidth(0.3f).height(11.dp).pulse())
            }
        }
    }
}
