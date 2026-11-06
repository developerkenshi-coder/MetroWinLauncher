package com.metrowin.launcher.news

import com.metrowin.launcher.data.Prefs
import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.data.NewsCodec
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsEdition
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.Feeds
import com.metrowin.launcher.data.SafeHttp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
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
    )
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

/**
 * What the news app keeps: its sources, the headlines of each (so a page opens at once, offline too), and the source of the News tile
 * of Start, which the settings used to hold.
 */
class NewsHub(private val context: Context) {
    private val prefs = Prefs.open(context, Prefs.NEWS)

    fun sources(legacyCode: String, legacyUrl: String, topicName: (String) -> String): List<NewsSourceDef> {
        val saved = NewsSourceList.decode(prefs.getString(KEY_SOURCES, null))
        // ANSA is offered once to a list saved before it was a default; removing it later sticks.
        if (saved.isNotEmpty() && prefs.getBoolean(KEY_ANSA, false)) return saved
        val list = if (saved.isEmpty()) NewsSourceList.initial(legacyCode, legacyUrl, topicName) else NewsSourceList.withAnsa(saved)
        prefs.edit { putBoolean(KEY_ANSA, true) }
        return list.also { saveSources(it) }
    }

    fun saveSources(list: List<NewsSourceDef>) = prefs.edit { putString(KEY_SOURCES, NewsSourceList.encode(list)) }

    fun cached(id: String): NewsData? = NewsCodec.decode(prefs.getString("data:$id", null))

    /** Blocking, call it off the main thread. The headlines of [source] now (and they are kept), or null when they could not be read. */
    fun fetch(source: NewsSourceDef, now: Long = System.currentTimeMillis()): NewsData? {
        val edition = NewsEdition.of(Locale.getDefault())
        val url = NewsSources.url(source.code, source.url, edition) ?: return null
        val xml = SafeHttp.get(url, NewsSources.hosts(url), maxBytes = 512 * 1024) ?: return null
        val feed = Feeds.parse(xml) ?: return null
        val data = NewsData(feed.title, feed.items, now, NewsSources.key(source.code, source.url, edition))
        prefs.edit { putString("data:${source.id}", NewsCodec.encode(data)) }
        return data
    }

    fun forget(id: String) = prefs.edit { remove("data:$id") }

    /** Whether the pictures of the headlines are fetched and shown (each one is a request to the site that has it). */
    var showImages: Boolean
        get() = prefs.getBoolean(KEY_IMAGES, true)
        set(value) = prefs.edit { putBoolean(KEY_IMAGES, value) }

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
        private const val KEY_IMAGES = "images"

        /** The code and address that the News tile reads, for the source the user chose in the app; null until they choose. */
        fun tileChoice(context: Context): Pair<String, String>? {
            val prefs = Prefs.open(context, Prefs.NEWS)
            val id = prefs.getString(KEY_TILE, "").orEmpty().ifEmpty { return null }
            return NewsSourceList.decode(prefs.getString(KEY_SOURCES, null)).firstOrNull { it.id == id }?.let { it.code to it.url }
        }
    }
}

/** A change of the source of the News tile, for the launcher to read the headlines again at once. */
object NewsTile {
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun set(@Suppress("UNUSED_PARAMETER") id: String) { _changes.value += 1 }
}
