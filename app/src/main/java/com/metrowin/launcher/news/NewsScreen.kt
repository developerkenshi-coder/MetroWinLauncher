package com.metrowin.launcher.news

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.produceState
import androidx.core.net.toUri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.metrowin.launcher.ui.components.MetroToggle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.HubPager
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.start.ageText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FRESH_MS = 15 * 60_000L

@Composable
private fun topicName(topic: String): String = stringResource(topicRes(topic))

private fun topicRes(topic: String) = when (topic) {
    "WORLD" -> R.string.news_topic_WORLD
    "NATION" -> R.string.news_topic_NATION
    "BUSINESS" -> R.string.news_topic_BUSINESS
    "TECHNOLOGY" -> R.string.news_topic_TECHNOLOGY
    "ENTERTAINMENT" -> R.string.news_topic_ENTERTAINMENT
    "SPORTS" -> R.string.news_topic_SPORTS
    "SCIENCE" -> R.string.news_topic_SCIENCE
    else -> R.string.news_topic_HEALTH
}

@Composable
fun NewsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hub = remember { NewsHub(context) }
    var sources by remember { mutableStateOf<List<NewsSourceDef>?>(null) }
    val data = remember { mutableStateMapOf<String, NewsData>() }
    val failed = remember { mutableStateMapOf<String, Boolean>() }
    var refresh by remember { mutableIntStateOf(0) }
    var managing by remember { mutableStateOf(false) }
    val images = remember { NewsImages(context) }
    var showImages by remember { mutableStateOf(hub.showImages) }
    val topics = NewsSources.TOPICS.associateWith { context.getString(topicRes(it)).substringAfter(": ") }
    LaunchedEffect(Unit) {
        sources = withContext(Dispatchers.IO) {
            val legacy = SettingsStore.of(context).load()
            hub.sources(legacy.newsSource, legacy.newsUrl) { topics[it].orEmpty() }
        }
    }
    val list = sources.orEmpty()
    val pager = rememberPagerState { maxOf(list.size, 1) }
    BackHandler(enabled = managing) { managing = false }

    MiniFrame {
        HubPager(
            stringResource(R.string.stock_news), list.map { it.name }.ifEmpty { listOf(stringResource(R.string.stock_news)) }, pager,
        ) { page ->
            val source = list.getOrNull(page)
            if (source != null) {
                SourcePage(source, hub, images, showImages, data[source.id], failed[source.id] == true, refresh, onLoaded = { data[source.id] = it; failed[source.id] = false }, onFailed = { failed[source.id] = true })
            }
        }
        AppBar(
            listOf(
                AppBarItem("↻", stringResource(R.string.messages_refresh)) { refresh++ },
                AppBarItem("★", stringResource(R.string.nws_tile)) {
                    list.getOrNull(pager.currentPage)?.let {
                        hub.tileSource = it.id
                        Toast.makeText(context, context.getString(R.string.nws_tile_set, it.name), Toast.LENGTH_LONG).show()
                    }
                },
                AppBarItem("⚙", stringResource(R.string.nws_sources)) { managing = true },
            ),
        )
    }
    if (managing) {
        SourcesDialog(
            list, topics,
            onChange = { next -> sources = next; hub.saveSources(next) },
            onRemove = { id -> hub.forget(id); val next = NewsSourceList.remove(list, id); sources = next; hub.saveSources(next); if (hub.tileSource == id) hub.tileSource = "" },
            showImages = showImages,
            onImages = { value -> showImages = value; hub.showImages = value; if (!value) images.clear() },
            onClose = { managing = false },
        )
    }
}

@Composable
private fun SourcePage(
    source: NewsSourceDef,
    hub: NewsHub,
    images: NewsPictures,
    showImages: Boolean,
    shown: NewsData?,
    failed: Boolean,
    refresh: Int,
    onLoaded: (NewsData) -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember(source.id) { mutableStateOf(false) }
    LaunchedEffect(source.id, refresh) {
        if (shown == null) withContext(Dispatchers.IO) { hub.cached(source.id) }?.let(onLoaded)
        val kept = shown ?: withContext(Dispatchers.IO) { hub.cached(source.id) }
        val now = System.currentTimeMillis()
        if (refresh > 0 || kept == null || now - kept.fetchedAt > FRESH_MS) {
            loading = true
            val fetched = withContext(Dispatchers.IO) { hub.fetch(source) }
            loading = false
            if (fetched != null) onLoaded(fetched) else if (kept == null) onFailed()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (loading && shown == null) PageNote(stringResource(R.string.rec_working))
        if (!loading && shown == null && failed) PageNote(stringResource(R.string.nws_offline), Modifier.testTag("nws:offline"))
        NewsList(shown?.items.orEmpty(), images, showImages) { open(context, it.link) }
    }
}

/** The headlines of a source. A feed with pictures is laid out as a page of them (the first headline large, the rest with a small one); one with none (Google News) as a list of headlines. */
@Composable
internal fun NewsList(items: List<NewsItem>, images: NewsPictures, showImages: Boolean, onOpen: (NewsItem) -> Unit) {
    val pictured = showImages && items.any { it.image.isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize().testTag("nws:list")) {
        itemsIndexed(items, key = { _, item -> item.link }) { index, item ->
            if (index == 0) TopStory(item, images, pictured, onOpen) else Headline(item, images, pictured, onOpen)
        }
    }
}

private fun open(context: android.content.Context, link: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)) }
}

/** The line under a headline: who published it in the accent colour, and how long ago. */
@Composable
private fun SourceLine(item: NewsItem, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val age = ageText(item.publishedAt)
    Row(modifier) {
        if (item.source.isNotEmpty()) MetroText(item.source, size = size, weight = FontWeight.Normal, color = LocalAccent.current, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        if (item.source.isNotEmpty() && age != null) MetroText("  ·  ", size = size, weight = FontWeight.Normal, color = colors.subtle)
        if (age != null) MetroText(age, size = size, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
    }
}

/** The first headline: a large picture, when there is one, and the headline big and bold under it. */
@Composable
private fun TopStory(item: NewsItem, images: NewsPictures, pictured: Boolean, onOpen: (NewsItem) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("nws:item").metroPress(onClick = { onOpen(item) }).padding(top = 6.dp, bottom = 18.dp)) {
        if (pictured && item.image.isNotEmpty()) {
            RemoteImage(item.image, images, Modifier.fillMaxWidth().aspectRatio(1.6f).testTag("nws:hero"), maxWidth = 1080)
            Box(Modifier.height(14.dp))
        }
        MetroText(item.title, size = 30.sp, weight = FontWeight.SemiBold, maxLines = 5, lineHeight = 36.sp)
        SourceLine(item, 17.sp, Modifier.padding(top = 6.dp))
    }
}

/** A headline of the list: with a small picture on the left when the feed has them. */
@Composable
private fun Headline(item: NewsItem, images: NewsPictures, pictured: Boolean, onOpen: (NewsItem) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("nws:item").metroPress(onClick = { onOpen(item) }).padding(vertical = 9.dp)) {
        if (pictured) {
            if (item.image.isNotEmpty()) RemoteImage(item.image, images, Modifier.size(width = 100.dp, height = 70.dp).testTag("nws:thumb"), maxWidth = 300)
            else Box(Modifier.size(width = 100.dp, height = 70.dp).background(LocalMetroColors.current.surface))
            Box(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            MetroText(item.title, size = if (pictured) 18.sp else 21.sp, maxLines = if (pictured) 3 else 4, lineHeight = if (pictured) 23.sp else 26.sp, weight = if (pictured) FontWeight.Normal else FontWeight.Light)
            SourceLine(item, 14.sp, Modifier.padding(top = 3.dp))
        }
    }
}

/** A picture fetched off the main thread; the colour of the surface stands in for it until it is there (and when it never comes). */
@Composable
private fun RemoteImage(url: String, images: NewsPictures, modifier: Modifier, maxWidth: Int) {
    val bitmap by produceState<ImageBitmap?>(null, url) { value = withContext(Dispatchers.IO) { images.load(url, maxWidth)?.asImageBitmap() } }
    Box(modifier.background(LocalMetroColors.current.surface)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
internal fun SourcesDialog(
    list: List<NewsSourceDef>,
    topics: Map<String, String>,
    onChange: (List<NewsSourceDef>) -> Unit,
    onRemove: (String) -> Unit,
    showImages: Boolean,
    onImages: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalMetroColors.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    DialogFrame(onCancel = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetroText(stringResource(R.string.nws_sources), size = 22.sp)
            MetroToggle(stringResource(R.string.nws_pictures), showImages, onImages, Modifier.testTag("nws:pictures"))
            list.forEach { source ->
                Row(Modifier.fillMaxWidth().testTag("nws:source"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    MetroText(source.name, size = 19.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    MetroText("✕", size = 20.sp, color = colors.subtle, modifier = Modifier.testTag("nws:remove:${source.id}").metroPress(onClick = { onRemove(source.id) }).padding(8.dp))
                }
            }
            MetroText(stringResource(R.string.nws_add_google), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NewsSources.TOPICS) { topic ->
                    PageChip(topics[topic].orEmpty(), list.any { it.id == "google:$topic" }, "nws:topic:$topic") {
                        if (list.any { it.id == "google:$topic" }) onRemove("google:$topic") else onChange(NewsSourceList.add(list, NewsSourceList.googleTopic(topic, topics[topic].orEmpty())))
                    }
                }
            }
            MetroText(stringResource(R.string.nws_add_feed), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NewsPresets.feeds) { preset ->
                    PageChip(preset.name, list.any { it.url == preset.url }, "nws:preset:${preset.id}") {
                        val existing = list.firstOrNull { it.url == preset.url }
                        if (existing != null) onRemove(existing.id) else onChange(NewsSourceList.add(list, preset))
                    }
                }
            }
            MetroTextField(name, { name = it.take(40) }, stringResource(R.string.nws_name_hint), Modifier.testTag("nws:name"))
            MetroTextField(url, { url = it.take(NewsSources.MAX_URL); bad = false }, stringResource(R.string.news_url_hint), Modifier.testTag("nws:url"))
            if (bad) PageNote(stringResource(R.string.news_url_bad), Modifier.testTag("nws:bad"))
            MetroButton(stringResource(R.string.nws_add), Modifier.fillMaxWidth().testTag("nws:add")) {
                val next = NewsSourceList.addFeed(list, name, url)
                if (next == null) bad = true else { onChange(next); name = ""; url = "" }
            }
        }
    }
}
