package com.metrowin.launcher.news

import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Feeds
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.HubPager
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

/** At most this many sources are read at once: the pager can have several pages going and a slow line should not be shared by all of them. */
private val gate = Semaphore(2)

private const val ALL = "local:all"
private const val SAVED = "local:saved"

private fun topicRes(topic: String): Int? = when (topic) {
    "WORLD" -> R.string.nws_topic_short_WORLD
    "NATION" -> R.string.nws_topic_short_NATION
    "BUSINESS" -> R.string.nws_topic_short_BUSINESS
    "TECHNOLOGY" -> R.string.nws_topic_short_TECHNOLOGY
    "ENTERTAINMENT" -> R.string.nws_topic_short_ENTERTAINMENT
    "SPORTS" -> R.string.nws_topic_short_SPORTS
    "SCIENCE" -> R.string.nws_topic_short_SCIENCE
    "HEALTH" -> R.string.nws_topic_short_HEALTH
    else -> null
}

private sealed interface NewsDialog {
    data object Sources : NewsDialog

    data object Options : NewsDialog

    class Actions(val item: NewsItem) : NewsDialog
}

/** Everything a page of headlines needs from the screen. */
private class NewsEnv(
    val hub: NewsHub,
    val images: NewsPictures,
    val opts: NewsOpts,
    val read: Set<String>,
    val now: Long,
    val query: String,
    /** The refreshes already taken up, by source and number: a page that is composed again does not read again for a refresh it already did. */
    val handled: MutableSet<Pair<String, Int>>,
    val onOpen: (NewsItem) -> Unit,
    val onLong: (NewsItem) -> Unit,
)

@Composable
fun NewsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hub = remember { NewsHub(context) }
    var sources by remember { mutableStateOf<List<NewsSourceDef>?>(null) }
    val data = remember { mutableStateMapOf<String, NewsData>() }
    val failed = remember { mutableStateMapOf<String, NewsResult.Failed>() }
    val handled = remember { HashSet<Pair<String, Int>>() }
    var refresh by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<NewsDialog?>(null) }
    val images = remember { NewsImages(context) }
    var opts by remember { mutableStateOf(hub.options) }
    var read by remember { mutableStateOf<Set<String>>(emptySet()) }
    var saved by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var tile by remember { mutableStateOf(hub.tileSource) }
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    // The ages under the headlines move on by themselves.
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
    val topics = NewsSources.TOPICS.associateWith { topicRes(it)?.let(context::getString) ?: it }
    LaunchedEffect(Unit) {
        val (list, kept, later) = withContext(Dispatchers.IO) {
            val legacy = SettingsStore.of(context).load()
            Triple(hub.sources(legacy.newsSource, legacy.newsUrl) { topics[it].orEmpty() }, hub.readLinks(), hub.saved())
        }
        read = kept
        saved = later
        sources = list
    }
    val list = sources.orEmpty()
    val allName = stringResource(R.string.nws_all)
    val savedName = stringResource(R.string.nws_saved)
    val pages = list + listOfNotNull(
        if (opts.allPage && list.isNotEmpty()) NewsSourceDef(ALL, allName, "local") else null,
        if (saved.isNotEmpty()) NewsSourceDef(SAVED, savedName, "local") else null,
    )
    val pager = rememberPagerState { maxOf(pages.size, 1) }
    BackHandler(enabled = dialog != null || query != null) { if (dialog != null) dialog = null else query = null }

    // The headlines of every source together, for the last page: what is kept, no new request.
    val stamp = data.values.sumOf { it.fetchedAt }
    val everything by produceState(emptyList<NewsItem>(), list, opts.allPage, stamp) {
        value = if (!opts.allPage) emptyList() else withContext(Dispatchers.IO) {
            NewsRows.merge(list.map { s -> s.name to (hub.cached(s.id)?.items.orEmpty()) })
        }
    }

    fun open(item: NewsItem) {
        openLink(context, item.link)
        if (opts.trackRead) scope.launch { read = withContext(Dispatchers.IO) { hub.setRead(item.link, true) } }
    }

    val env = NewsEnv(
        hub, images, opts, read, now, query.orEmpty(), handled,
        onOpen = ::open,
        onLong = { dialog = NewsDialog.Actions(it) },
    )

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(NewsOpml.encode(list).toByteArray()) } }.isSuccess
            }
            MetroToast.show(context, context.getString(if (ok) R.string.nws_opml_saved else R.string.nws_opml_error))
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { readBounded(context, uri) }.getOrNull() }
            if (text == null) { MetroToast.show(context, context.getString(R.string.nws_opml_error)); return@launch }
            var next = list
            var added = 0
            NewsOpml.parse(text).forEach { (name, url) -> NewsSourceList.addFeed(next, name, url)?.let { next = it; added++ } }
            if (added == 0) MetroToast.show(context, context.getString(R.string.nws_opml_none))
            else {
                sources = next
                withContext(Dispatchers.IO) { hub.saveSources(next) }
                MetroToast.show(context, context.getString(R.string.nws_opml_done, added))
            }
        }
    }

    MiniFrame {
        HubPager(
            stringResource(R.string.stock_news),
            pages.map { (if (it.id == tile) "★ " else "") + it.name }.ifEmpty { listOf(stringResource(R.string.stock_news)) },
            pager,
        ) { page ->
            val source = pages.getOrNull(page)
            when (source?.id) {
                null -> Unit
                ALL -> LocalPage(everything, env)
                SAVED -> LocalPage(saved, env)
                else -> SourcePage(source, env, data[source.id], failed[source.id], refresh,
                    onLoaded = { data[source.id] = it; failed.remove(source.id) },
                    onFailed = { failed[source.id] = it },
                    onRetry = { refresh++ })
            }
        }
        query?.let { text ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp).imePadding()) {
                MetroTextField(text, { query = it.take(60) }, stringResource(R.string.nws_search_hint), Modifier.testTag("nws:search"))
            }
        }
        val current = pages.getOrNull(pager.currentPage)
        AppBar(
            listOf(
                AppBarItem("↻", stringResource(R.string.messages_refresh)) { refresh++ },
                AppBarItem("★", stringResource(R.string.nws_tile)) {
                    list.firstOrNull { it.id == current?.id }?.let {
                        hub.tileSource = it.id
                        tile = it.id
                        MetroToast.show(context, context.getString(R.string.nws_tile_set, it.name))
                    }
                }.marked(current != null && current.id == tile),
                AppBarItem("⚙", stringResource(R.string.nws_sources)) { dialog = NewsDialog.Sources },
            ),
            menu = listOf(
                AppBarItem("≡", stringResource(R.string.nws_options)) { dialog = NewsDialog.Options },
                AppBarItem("⌕", stringResource(R.string.nws_search)) { query = if (query == null) "" else null },
                AppBarItem("⇧", stringResource(R.string.nws_export)) { exporter.launch("metrowin-news.opml") },
                AppBarItem("⇩", stringResource(R.string.nws_import)) { importer.launch(arrayOf("*/*")) },
            ),
        )
    }
    when (val d = dialog) {
        null -> Unit
        NewsDialog.Sources -> SourcesDialog(
            list, topics,
            onChange = { next -> sources = next; hub.saveSources(next) },
            onRemove = { id ->
                hub.forget(id)
                val next = NewsSourceList.remove(list, id)
                sources = next
                hub.saveSources(next)
                if (hub.tileSource == id) { hub.tileSource = ""; tile = "" }
            },
            showImages = opts.images,
            onImages = { value -> opts = opts.copy(images = value).also { hub.options = it }; if (!value) images.clear() },
            onClose = { dialog = null },
            tile = tile,
            presets = NewsPresets.ordered(Locale.getDefault().country),
            onFind = if (opts.discover) ({ typed -> findFeed(typed, opts.anySite) }) else null,
        )
        NewsDialog.Options -> NewsOptionsDialog(opts, onChange = { next -> opts = next; hub.options = next }, onClose = { dialog = null })
        is NewsDialog.Actions -> NewsActionsDialog(
            d.item, images, opts.images, saved = saved.any { it.link == d.item.link }, read = d.item.link in read, trackRead = opts.trackRead, now = now,
            onOpen = { dialog = null; open(d.item) },
            onShare = { dialog = null; share(context, d.item) },
            onSave = { scope.launch { saved = withContext(Dispatchers.IO) { hub.toggleSaved(d.item) } }; dialog = null },
            onRead = { scope.launch { read = withContext(Dispatchers.IO) { hub.setRead(d.item.link, d.item.link !in read) } }; dialog = null },
            onClose = { dialog = null },
        )
    }
}

/** A page of the app that is not a source: the saved headlines, or all the sources together. */
@Composable
private fun LocalPage(items: List<NewsItem>, env: NewsEnv) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) { NewsBody(items, true, false, null, env, {}) }
}

@Composable
private fun SourcePage(
    source: NewsSourceDef,
    env: NewsEnv,
    shown: NewsData?,
    failure: NewsResult.Failed?,
    refresh: Int,
    onLoaded: (NewsData) -> Unit,
    onFailed: (NewsResult.Failed) -> Unit,
    onRetry: () -> Unit,
) {
    var loading by remember(source.id) { mutableStateOf(false) }
    var fresh by remember(source.id) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(source.id, refresh) {
        // A refresh is taken up once, by the first page that sees it: coming back to a page does not read it again.
        val forced = refresh > 0 && env.handled.add(source.id to refresh)
        val kept = shown ?: withContext(Dispatchers.IO) { env.hub.cached(source.id) }?.also(onLoaded)
        fresh = withContext(Dispatchers.IO) { env.hub.fresh(source.id) }
        if (forced || kept == null || System.currentTimeMillis() - kept.fetchedAt > env.opts.freshMs) {
            loading = true
            val result = gate.withPermit { withContext(Dispatchers.IO) { env.hub.load(source, force = forced) } }
            loading = false
            when (result) {
                is NewsResult.Ok -> { onLoaded(result.data); fresh = withContext(Dispatchers.IO) { env.hub.fresh(source.id) } }
                is NewsResult.Failed -> onFailed(result)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (loading) MetroProgressBar(Modifier.padding(vertical = 6.dp).testTag("nws:loading"))
        NewsBody(shown?.items.orEmpty(), shown != null, loading, failure, env, onRetry, fresh)
    }
}

/** The page under a pivot title: a skeleton while the first read goes, the problem and a retry when there is nothing to show, else the headlines. */
@Composable
private fun NewsBody(
    items: List<NewsItem>,
    hasData: Boolean,
    loading: Boolean,
    failure: NewsResult.Failed?,
    env: NewsEnv,
    onRetry: () -> Unit,
    fresh: Set<String> = emptySet(),
) {
    when {
        loading && !hasData -> NewsSkeleton()
        !hasData && failure != null -> Problem(failure, onRetry, Modifier.testTag("nws:offline"), big = true)
        else -> {
            if (failure != null && !loading) {
                // What is on the page is the last copy; say why it is not newer.
                Problem(failure, onRetry, Modifier.padding(bottom = 6.dp).testTag("nws:stale"), big = false)
            }
            val shown = NewsRows.search(items, env.query)
            if (hasData && shown.isEmpty()) PageNote(stringResource(if (env.query.isBlank()) R.string.nws_empty else R.string.nws_empty_search), Modifier.testTag("nws:empty"))
            NewsList(shown, env.images, env.opts.images, NewsView(env.opts, env.read, fresh, env.now, env.query.trim()), env.onLong, env.onOpen)
        }
    }
}

@Composable
private fun Problem(failure: NewsResult.Failed, onRetry: () -> Unit, modifier: Modifier, big: Boolean) {
    val code = failure.code
    val text = when (failure.why) {
        NewsFailure.Offline -> stringResource(R.string.nws_offline)
        NewsFailure.Http -> stringResource(R.string.nws_err_http, code)
        NewsFailure.Parse -> stringResource(R.string.nws_err_parse)
        NewsFailure.Blocked -> stringResource(R.string.nws_err_blocked)
        NewsFailure.TooBig -> stringResource(R.string.nws_err_toobig)
        NewsFailure.Metered -> stringResource(R.string.nws_err_metered)
        NewsFailure.Address -> stringResource(R.string.nws_err_address)
    }
    if (big) {
        Column(modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val ink = LocalMetroColors.current.subtle
            // A circle with a bar across: nothing could be read.
            Canvas(Modifier.size(56.dp)) {
                drawCircle(ink, size.minDimension / 2 - 2.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
                drawLine(ink, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.8f, size.height * 0.2f), strokeWidth = 3.dp.toPx())
            }
            PageNote(text)
            MetroButton(stringResource(R.string.nws_retry), Modifier.testTag("nws:retry")) { onRetry() }
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            PageNote(stringResource(R.string.nws_stale) + " " + text)
            MetroButton(stringResource(R.string.nws_retry), Modifier.testTag("nws:retry")) { onRetry() }
        }
    }
}

/** Opens [link] in the browser, when it is a web page (http or https); nothing else is ever opened. */
private fun openLink(context: Context, link: String) {
    val safe = Feeds.safeLink(link) ?: return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, safe.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)) }
}

private fun share(context: Context, item: NewsItem) {
    val safe = Feeds.safeLink(item.link) ?: return
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${item.title}\n$safe")
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** The text of the file at [uri], or null when it is longer than 256 KB (an OPML of feeds is far shorter). */
private fun readBounded(context: Context, uri: android.net.Uri): String? = context.contentResolver.openInputStream(uri)?.use { input ->
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        if (out.size() + n > 256 * 1024) return null
        out.write(buffer, 0, n)
    }
    out.toString(Charsets.UTF_8.name())
}

/**
 * The address of a feed for what the user typed: the address itself when it is a feed, else the first feed that the web page there
 * offers and that can be read. Reads the network (a few requests), so only when the user asks for it with the option on.
 */
private suspend fun findFeed(typed: String, anySite: Boolean): String? = withContext(Dispatchers.IO) {
    val page = NewsSources.customUrl(typed) ?: return@withContext null
    val reply = NewsHttp.get(page, NewsSources.hosts(page), anySite) as? NewsReply.Body ?: return@withContext null
    if (Feeds.parse(reply.text) != null) return@withContext page
    val pageHost = runCatching { java.net.URI(page).host }.getOrNull()
    Feeds.feedLinks(reply.text, page).firstOrNull { candidate ->
        val host = runCatching { java.net.URI(candidate).host }.getOrNull() ?: return@firstOrNull false
        // The page decides these addresses, not the user: only public hosts (or the page's own).
        (host == pageHost || NewsHttp.isPublicHost(host)) &&
            (NewsHttp.get(candidate, NewsSources.hosts(candidate), anySite) as? NewsReply.Body)?.let { Feeds.parse(it.text) } != null
    }
}
