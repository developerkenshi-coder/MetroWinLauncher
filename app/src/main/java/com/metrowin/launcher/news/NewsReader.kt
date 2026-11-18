package com.metrowin.launcher.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.ageText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The reading page of one article: its title, then only its text and (when the pictures of the app are on) its main pictures, at the size the
 * user chose. [load] is blocking, it runs off the main thread, once per open and once more per retry. Nothing here runs scripts or draws a web view.
 */
@Composable
internal fun NewsReader(
    item: NewsItem,
    images: NewsPictures,
    opts: NewsOpts,
    now: Long,
    load: (NewsItem) -> ReadResult,
    onSize: (Int) -> Unit,
    onBrowser: () -> Unit,
    onShare: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<ReadResult?>(null, item.link, attempt) {
        value = null
        value = withContext(Dispatchers.IO) { load(item) }
    }
    val colors = LocalMetroColors.current
    val scale = opts.readPercent / 100f
    Column(Modifier.fillMaxSize().testTag("nws:reader")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroButton("A−", Modifier.testTag("nws:read:smaller")) { onSize(opts.readStep(-1)) }
            MetroText("${opts.readPercent}%", size = 17.sp, color = colors.subtle, modifier = Modifier.testTag("nws:read:size"))
            MetroButton("A+", Modifier.testTag("nws:read:larger")) { onSize(opts.readStep(1)) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MetroText(
                        item.title, size = (26 * scale).sp, weight = FontWeight.Normal, lineHeight = (32 * scale).sp,
                        modifier = Modifier.semantics { heading() }.testTag("nws:read:title"),
                    )
                    MetroText(listOfNotNull(item.source.ifEmpty { null }, ageText(item.publishedAt, now)).joinToString("  ·  "), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
            when (val r = state) {
                null -> item { MetroProgressBar(Modifier.padding(vertical = 12.dp).testTag("nws:read:loading")) }
                is ReadResult.Failed -> item {
                    Column(Modifier.testTag("nws:read:failed"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PageNote(failureText(r.why, r.code))
                        MetroButton(stringResource(R.string.nws_retry), Modifier.testTag("nws:read:retry")) { attempt++ }
                    }
                }
                ReadResult.Empty -> item { PageNote(stringResource(R.string.nws_read_empty), Modifier.testTag("nws:read:empty")) }
                is ReadResult.Ok -> items(r.blocks.filter { it !is ReadBlock.Pic || opts.images }) { block ->
                    when (block) {
                        is ReadBlock.Head -> MetroText(
                            block.text, size = (20 * scale).sp, weight = FontWeight.SemiBold, lineHeight = (26 * scale).sp,
                            modifier = Modifier.padding(top = 6.dp).semantics { heading() }.testTag("nws:read:head"),
                        )
                        is ReadBlock.Para -> MetroText(block.text, size = (17 * scale).sp, weight = FontWeight.Normal, lineHeight = (26 * scale).sp, modifier = Modifier.testTag("nws:read:para"))
                        is ReadBlock.Pic -> RemoteImage(block.url, images, Modifier.fillMaxWidth().aspectRatio(1.6f).testTag("nws:read:pic"), maxWidth = 1080, dim = opts.dimImages)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetroButton(stringResource(R.string.nws_read_browser), Modifier.weight(1f).testTag("nws:read:browser")) { onBrowser() }
            MetroButton(stringResource(R.string.nws_share), Modifier.weight(1f).testTag("nws:read:share")) { onShare() }
        }
    }
}
