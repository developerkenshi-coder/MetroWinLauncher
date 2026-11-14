package com.metrowin.launcher.news

import com.metrowin.launcher.data.Prefs
import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.data.NewsCodec
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsEdition
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.Feeds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * A place the news are read from: a [code] of [NewsSources] (Google News top stories or one of its topics) or a feed at [url]
 * (RSS or Atom). [name] is what the page is called.
 */
data class NewsSourceDef(val id: String, val name: String, val code: String, val url: String = "")

/** Feeds that are offered to add with a touch. The address is the one the publisher gives for its own feed. */
object NewsPresets {
    val feeds: List<NewsSourceDef> = listOf(
        NewsSourceDef("preset:bbc", "BBC News", NewsSources.CUSTOM, "https://feeds.bbci.co.uk/news/world/rss.xml"),
        NewsSourceDef("preset:guardian", "The Guardian", NewsSources.CUSTOM, "https://www.theguardian.com/world/rss"),
        NewsSourceDef("preset:ansa", "ANSA", NewsSources.CUSTOM, "https://www.ansa.it/sito/ansait_rss.xml"),
        NewsSourceDef("preset:ilpost", "Il Post", NewsSources.CUSTOM, "https://www.ilpost.it/feed/"),
        NewsSourceDef("preset:lemonde", "Le Monde", NewsSources.CUSTOM, "https://www.lemonde.fr/rss/une.xml"),
        NewsSourceDef("preset:spiegel", "DER SPIEGEL", NewsSources.CUSTOM, "https://www.spiegel.de/schlagzeilen/index.rss"),
        NewsSourceDef("preset:elpais", "EL PAÍS", NewsSources.CUSTOM, "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada"),
        NewsSourceDef("preset:ars", "Ars Technica", NewsSources.CUSTOM, "https://feeds.arstechnica.com/arstechnica/index"),
        NewsSourceDef("preset:hn", "Hacker News", NewsSources.CUSTOM, "https://hnrss.org/frontpage"),
        NewsSourceDef("preset:npr", "NPR News", NewsSources.CUSTOM, "https://feeds.npr.org/1001/rss.xml"),
        NewsSourceDef("preset:tagesschau", "tagesschau", NewsSources.CUSTOM, "https://www.tagesschau.de/index~rss2.xml"),
        NewsSourceDef("preset:france24", "France 24", NewsSources.CUSTOM, "https://www.france24.com/fr/rss"),
        NewsSourceDef("preset:lefigaro", "Le Figaro", NewsSources.CUSTOM, "https://www.lefigaro.fr/rss/figaro_actualites.xml"),
        NewsSourceDef("preset:corriere", "Corriere della Sera", NewsSources.CUSTOM, "https://xml2.corriereobjects.it/rss/homepage.xml"),
        NewsSourceDef("preset:repubblica", "la Repubblica", NewsSources.CUSTOM, "https://www.repubblica.it/rss/homepage/rss2.0.xml"),
        NewsSourceDef("preset:publico", "Público", NewsSources.CUSTOM, "https://feeds.feedburner.com/PublicoRSS"),
        NewsSourceDef("preset:folha", "Folha de S.Paulo", NewsSources.CUSTOM, "https://feeds.folha.uol.com.br/emcimadahora/rss091.xml"),
        NewsSourceDef("preset:dw", "Deutsche Welle", NewsSources.CUSTOM, "https://rss.dw.com/rdf/rss-en-all"),
    )

    /** The countries a preset is from; a preset that is not here is for everyone. */
    private val regions = mapOf(
        "preset:bbc" to "GB", "preset:guardian" to "GB", "preset:ansa" to "IT", "preset:ilpost" to "IT", "preset:corriere" to "IT", "preset:repubblica" to "IT",
        "preset:lemonde" to "FR", "preset:france24" to "FR", "preset:lefigaro" to "FR", "preset:spiegel" to "DE", "preset:tagesschau" to "DE",
        "preset:elpais" to "ES", "preset:publico" to "PT", "preset:folha" to "BR", "preset:npr" to "US",
    )

    /** [feeds] with the ones of [country] (an ISO code such as "IT") first, the rest in their order. */
    fun ordered(country: String): List<NewsSourceDef> {
        val here = country.uppercase(Locale.ROOT)
        return feeds.sortedBy { if (regions[it.id] == here) 0 else 1 }
    }
}

/** The sources of the news app, and what is done with a list of them. Pure, so it is tested with words. */
object NewsSourceList {
    const val MAX = 24

    fun googleTop(): NewsSourceDef = NewsSourceDef("google", "Google News", NewsSources.GOOGLE_TOP)

    fun ansa(): NewsSourceDef = NewsPresets.feeds.first { it.id == "preset:ansa" }

    /** [list] with ANSA right after Google News, for the lists saved before ANSA was a default; unchanged when it is there or the list is full. */
    fun withAnsa(list: List<NewsSourceDef>): List<NewsSourceDef> {
        val ansa = ansa()
        if (list.size >= MAX || list.any { it.id == ansa.id || it.url == ansa.url }) return list
        val at = list.indexOfFirst { it.id == googleTop().id } + 1
        return list.take(at) + ansa + list.drop(at)
    }

    fun googleTopic(topic: String, name: String): NewsSourceDef = NewsSourceDef("google:$topic", name, NewsSources.topic(topic))

    /**
     * The list the app starts with: Google News, ANSA, and the source the News tile was set to in the settings before this app took the choice
     * ([legacyCode] and [legacyUrl]), so nothing the user chose is lost.
     */
    fun initial(legacyCode: String, legacyUrl: String, topicName: (String) -> String): List<NewsSourceDef> {
        val list = arrayListOf(googleTop(), ansa())
        val code = NewsSources.normalize(legacyCode)
        NewsSources.topicOf(code)?.let { list += googleTopic(it, topicName(it)) }
        if (code == NewsSources.CUSTOM) NewsSources.customUrl(legacyUrl)?.let { url -> list += NewsSourceDef("custom:" + url.hashCode(), java.net.URI(url).host.removePrefix("www."), NewsSources.CUSTOM, url) }
        return list
    }

    /** [list] with a feed of the user's, or null when [url] is not a good address (see [NewsSources.customUrl]), it is already there or the list is full. */
    fun addFeed(list: List<NewsSourceDef>, name: String, url: String): List<NewsSourceDef>? {
        val good = NewsSources.customUrl(url) ?: return null
        if (list.size >= MAX || list.any { it.url.equals(good, ignoreCase = true) }) return null
        val label = name.trim().take(40).ifEmpty { java.net.URI(good).host.removePrefix("www.") }
        return list + NewsSourceDef("custom:" + UUID.randomUUID(), label, NewsSources.CUSTOM, good)
    }

    fun add(list: List<NewsSourceDef>, source: NewsSourceDef): List<NewsSourceDef> =
        if (list.size >= MAX || list.any { it.id == source.id || (source.url.isNotEmpty() && it.url == source.url) }) list else list + source

    /** Without [id]; the list is never left empty (Google News stays when it would be). */
    fun remove(list: List<NewsSourceDef>, id: String): List<NewsSourceDef> = list.filter { it.id != id }.ifEmpty { listOf(googleTop()) }

    /** [list] with the source at [from] moved by [by] places (-1 up, 1 down); unchanged when it would leave the list. */
    fun move(list: List<NewsSourceDef>, from: Int, by: Int): List<NewsSourceDef> {
        val to = from + by
        if (from !in list.indices || to !in list.indices) return list
        return list.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun encode(list: List<NewsSourceDef>): String = JSONArray().also { array ->
        list.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("code", it.code).put("url", it.url)) }
    }.toString()

    fun decode(json: String?): List<NewsSourceDef> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until minOf(array.length(), MAX)).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val code = NewsSources.normalize(o.optString("code"))
                val url = o.optString("url")
                if (code == NewsSources.CUSTOM && NewsSources.customUrl(url) == null) return@runCatching null
                NewsSourceDef(o.getString("id").take(80), o.getString("name").take(40), code, if (code == NewsSources.CUSTOM) url else "")
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
}

/** The options of the news app. Everything that reaches the network, costs data, or keeps a trace of what was read starts off. */
data class NewsOpts(
    /** Fetch and show the pictures of the headlines (each one is a request to the site that has it). */
    val images: Boolean = false,
    /** Follow a redirect of a feed to another public site (a CDN), not only to the same host. */
    val anySite: Boolean = false,
    /** Read the feeds by themselves only on a connection that is not metered; the refresh button still reads. */
    val unmeteredOnly: Boolean = false,
    /** Keep up to [Feeds.MAX_ITEMS_MORE] headlines of a source instead of [Feeds.MAX_ITEMS]. */
    val moreHeadlines: Boolean = false,
    /** Let "find the feed" read a web page to look for the feed it offers. */
    val discover: Boolean = false,
    /** Remember which headlines were opened, and show them fainter. */
    val trackRead: Boolean = false,
    /** Mark the headlines that came with the last update. */
    val markNew: Boolean = true,
    /** A line with the day between headlines of different days. */
    val days: Boolean = true,
    /** Closer rows and no pictures. */
    val compact: Boolean = false,
    /** Pictures a little darker on a dark theme. */
    val dimImages: Boolean = false,
    /** A last page with the headlines of every source together. */
    val allPage: Boolean = false,
    /** The size of the text in percent of the usual one. */
    val textPercent: Int = 100,
    /** How long a source is considered up to date, in minutes. */
    val freshMinutes: Int = 15,
) {
    val max get() = if (moreHeadlines) Feeds.MAX_ITEMS_MORE else Feeds.MAX_ITEMS
    val freshMs get() = freshMinutes * 60_000L
    val scale get() = textPercent / 100f

    companion object {
        val TEXT_SIZES = listOf(90, 100, 120, 140)
        val FRESH_MINUTES = listOf(5, 15, 30, 60)
    }
}

/** What a [NewsHub.load] came to. */
sealed interface NewsResult {
    /** [data] is current; [changed] is false when the server said nothing is new. */
    class Ok(val data: NewsData, val changed: Boolean = true) : NewsResult

    class Failed(val why: NewsFailure, val code: Int = 0) : NewsResult
}

/**
 * What the news app keeps: its sources, the headlines of each (in a file apiece, so a page opens at once, offline too), what was read and
 * saved, its options, and the source of the News tile of Start, which the settings used to hold.
 */
class NewsHub(private val context: Context, private val open: (java.net.URL) -> java.net.HttpURLConnection = NewsHttp.default) {
    private val prefs = Prefs.open(context, Prefs.NEWS)
    private val dir = File(context.filesDir, "news")

    fun sources(legacyCode: String, legacyUrl: String, topicName: (String) -> String): List<NewsSourceDef> {
        val saved = NewsSourceList.decode(prefs.getString(KEY_SOURCES, null))
        // ANSA is offered once to a list saved before it was a default; removing it later sticks.
        if (saved.isNotEmpty() && prefs.getBoolean(KEY_ANSA, false)) return saved.also { prune(it) }
        val list = if (saved.isEmpty()) NewsSourceList.initial(legacyCode, legacyUrl, topicName) else NewsSourceList.withAnsa(saved)
        prefs.edit { putBoolean(KEY_ANSA, true) }
        return list.also { saveSources(it); prune(it) }
    }

    fun saveSources(list: List<NewsSourceDef>) = prefs.edit { putString(KEY_SOURCES, NewsSourceList.encode(list)) }

    private fun file(id: String) = File(dir, "d-" + NewsImages.key(id) + ".json")

    /** The kept headlines of [id]: its file, or (for a copy kept before they had files) the old preference, which is moved to a file. */
    fun cached(id: String): NewsData? {
        val f = file(id)
        if (f.exists()) return NewsCodec.decode(runCatching { f.readText() }.getOrNull())
        val old = prefs.getString("data:$id", null) ?: return null
        return NewsCodec.decode(old)?.also { if (write(f, NewsCodec.encode(it))) prefs.edit { remove("data:$id") } }
    }

    /** Writes [text] to [target] whole or not at all. */
    private fun write(target: File, text: String): Boolean = runCatching {
        dir.mkdirs()
        val temporary = File(dir, target.name + ".part")
        temporary.writeText(text)
        temporary.renameTo(target)
    }.getOrDefault(false)

    /** Throws away the kept headlines of the sources that are no longer in [keep], and the preferences of the old format. */
    private fun prune(keep: List<NewsSourceDef>) {
        val names = keep.map { file(it.id).name }.toSet()
        dir.listFiles { f -> f.name.startsWith("d-") }.orEmpty().filter { it.name !in names }.forEach { it.delete() }
        val ids = keep.map { it.id }.toSet()
        val stale = prefs.all.keys.filter { k -> listOf("data:", "http:", "new:").any { k.startsWith(it) } && k.substringAfter(':') !in ids }
        if (stale.isNotEmpty()) prefs.edit { stale.forEach { remove(it) } }
    }

    /**
     * Blocking, call it off the main thread. The headlines of [source] brought up to date: the server is asked only whether they changed
     * when it said how to ask before. [force] is the user pressing refresh: it goes on even when only unmetered connections are allowed.
     */
    fun load(source: NewsSourceDef, now: Long = System.currentTimeMillis(), force: Boolean = false): NewsResult {
        val opts = options
        if (opts.unmeteredOnly && !force && metered()) return NewsResult.Failed(NewsFailure.Metered)
        val edition = NewsEdition.of(Locale.getDefault())
        val url = NewsSources.url(source.code, source.url, edition) ?: return NewsResult.Failed(NewsFailure.Address)
        val key = NewsSources.key(source.code, source.url, edition)
        val kept = cached(source.id)?.takeIf { it.sourceKey == key }
        val (etag, modified) = if (kept == null) "" to "" else prefs.getString("http:${source.id}", "").orEmpty().let { it.substringBefore('\n') to it.substringAfter('\n', "") }
        return when (val reply = NewsHttp.get(url, NewsSources.hosts(url), opts.anySite, etag, modified, open = open)) {
            NewsReply.NotModified ->
                if (kept == null) NewsResult.Failed(NewsFailure.Http, 304)
                else kept.copy(fetchedAt = now).let { write(file(source.id), NewsCodec.encode(it)); NewsResult.Ok(it, changed = false) }
            NewsReply.Offline -> NewsResult.Failed(NewsFailure.Offline)
            NewsReply.Blocked -> NewsResult.Failed(NewsFailure.Blocked)
            NewsReply.TooBig -> NewsResult.Failed(NewsFailure.TooBig)
            is NewsReply.Http -> NewsResult.Failed(NewsFailure.Http, reply.code)
            is NewsReply.Body -> {
                val feed = Feeds.parse(reply.text, opts.max) ?: return NewsResult.Failed(NewsFailure.Parse)
                val data = NewsData(feed.title, feed.items, now, key)
                val before = kept?.items?.map { it.link }?.toSet()
                prefs.edit {
                    putString("http:${source.id}", reply.etag + "\n" + reply.modified)
                    // What the update brought that was not there before; on the very first read nothing is "new".
                    if (before != null) putStringSet("new:${source.id}", data.items.map { it.link }.filter { it !in before }.toSet())
                }
                write(file(source.id), NewsCodec.encode(data))
                NewsResult.Ok(data)
            }
        }
    }

    /** The links of [id] that the last update brought. */
    fun fresh(id: String): Set<String> = prefs.getStringSet("new:$id", null).orEmpty()

    @Suppress("DEPRECATION")
    private fun metered(): Boolean = runCatching { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered }.getOrDefault(false)

    fun forget(id: String) {
        file(id).delete()
        prefs.edit { remove("data:$id"); remove("http:$id"); remove("new:$id") }
    }

    // --- what was read and what is saved for later

    private val readFile get() = File(dir, "read.json")
    private val savedFile get() = File(dir, "saved.json")

    /** The links opened, newest last (at most [MAX_READ]). */
    @Synchronized
    fun readLinks(): Set<String> = runCatching { JSONArray(readFile.readText()).let { a -> LinkedHashSet<String>().also { s -> for (i in 0 until a.length()) s += a.getString(i) } } }.getOrDefault(linkedSetOf())

    @Synchronized
    fun setRead(link: String, read: Boolean): Set<String> {
        val next = LinkedHashSet(readLinks()).also { if (read) { it.remove(link); it.add(link) } else it.remove(link) }
        val kept = next.toList().takeLast(MAX_READ)
        write(readFile, JSONArray(kept).toString())
        return kept.toSet()
    }

    @Synchronized
    fun saved(): List<NewsItem> = NewsCodec.decode(runCatching { savedFile.readText() }.getOrNull())?.items.orEmpty()

    /** Saves [item] for later, or takes it off the saved ones when it is there; the list after (at most [Feeds.KEPT]). */
    @Synchronized
    fun toggleSaved(item: NewsItem): List<NewsItem> {
        val list = saved()
        val next = if (list.any { it.link == item.link }) list.filter { it.link != item.link } else (listOf(item) + list).take(Feeds.KEPT)
        write(savedFile, NewsCodec.encode(NewsData("saved", next, 0L, "saved")))
        return next
    }

    // --- options

    var options: NewsOpts
        get() = NewsOptionsStore.load(prefs)
        set(value) = NewsOptionsStore.save(prefs, value)

    /** Whether the pictures of the headlines are fetched and shown. */
    var showImages: Boolean
        get() = options.images
        set(value) { options = options.copy(images = value) }

    /** The id of the source that the News tile of Start shows ("" until the user chooses one). */
    var tileSource: String
        get() = prefs.getString(KEY_TILE, "").orEmpty()
        set(value) {
            prefs.edit { putString(KEY_TILE, value) }
            NewsTile.set(value)
        }

    companion object {
        private const val KEY_SOURCES = "sources"
        private const val KEY_ANSA = "ansa_offered"
        private const val KEY_TILE = "tile_source"
        private const val MAX_READ = 500

        /** The code and address that the News tile reads, for the source the user chose in the app; null until they choose. */
        fun tileChoice(context: Context): Pair<String, String>? {
            val prefs = Prefs.open(context, Prefs.NEWS)
            val id = prefs.getString(KEY_TILE, "").orEmpty().ifEmpty { return null }
            return NewsSourceList.decode(prefs.getString(KEY_SOURCES, null)).firstOrNull { it.id == id }?.let { it.code to it.url }
        }
    }
}

/** The options as they are kept in the preferences of the app; a value that is not one of the known ones falls back to the usual one. */
object NewsOptionsStore {
    fun load(prefs: android.content.SharedPreferences): NewsOpts {
        val d = NewsOpts()
        return NewsOpts(
            images = prefs.getBoolean("images", d.images),
            anySite = prefs.getBoolean("any_site", d.anySite),
            unmeteredOnly = prefs.getBoolean("unmetered_only", d.unmeteredOnly),
            moreHeadlines = prefs.getBoolean("more_headlines", d.moreHeadlines),
            discover = prefs.getBoolean("discover", d.discover),
            trackRead = prefs.getBoolean("track_read", d.trackRead),
            markNew = prefs.getBoolean("mark_new", d.markNew),
            days = prefs.getBoolean("days", d.days),
            compact = prefs.getBoolean("compact", d.compact),
            dimImages = prefs.getBoolean("dim_images", d.dimImages),
            allPage = prefs.getBoolean("all_page", d.allPage),
            textPercent = prefs.getInt("text_percent", d.textPercent).takeIf { it in NewsOpts.TEXT_SIZES } ?: d.textPercent,
            freshMinutes = prefs.getInt("fresh_minutes", d.freshMinutes).takeIf { it in NewsOpts.FRESH_MINUTES } ?: d.freshMinutes,
        )
    }

    fun save(prefs: android.content.SharedPreferences, o: NewsOpts) = prefs.edit {
        putBoolean("images", o.images).putBoolean("any_site", o.anySite).putBoolean("unmetered_only", o.unmeteredOnly)
        putBoolean("more_headlines", o.moreHeadlines).putBoolean("discover", o.discover).putBoolean("track_read", o.trackRead)
        putBoolean("mark_new", o.markNew).putBoolean("days", o.days).putBoolean("compact", o.compact)
        putBoolean("dim_images", o.dimImages).putBoolean("all_page", o.allPage)
        putInt("text_percent", o.textPercent).putInt("fresh_minutes", o.freshMinutes)
    }
}

/** A change of the source of the News tile, for the launcher to read the headlines again at once. */
object NewsTile {
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun set(@Suppress("UNUSED_PARAMETER") id: String) { _changes.value += 1 }
}
