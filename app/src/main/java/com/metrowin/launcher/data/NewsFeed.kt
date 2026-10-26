package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import java.io.StringReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** One headline: what it says, who published it, where to read it, and when (epoch milliseconds, 0 when the feed does not say). */
@Immutable
data class NewsItem(val title: String, val source: String, val link: String, val publishedAt: Long)

/** The headlines of a source, as they were when [fetchedAt]. [title] is what the feed calls itself ("Top stories - Google News"). */
@Immutable
data class NewsData(val title: String, val items: List<NewsItem>, val fetchedAt: Long, val sourceKey: String)

/** The edition of Google News: its language and country, which the phone's language and region give. */
data class NewsEdition(val hl: String, val gl: String, val ceid: String) {
    companion object {
        private val COUNTRY_OF_LANGUAGE = mapOf(
            "en" to "US", "it" to "IT", "fr" to "FR", "de" to "DE", "es" to "ES", "pt" to "PT", "nl" to "NL", "pl" to "PL",
            "ru" to "RU", "ja" to "JP", "ko" to "KR", "sv" to "SE", "tr" to "TR", "cs" to "CZ", "el" to "GR", "ro" to "RO",
        )

        /** The edition for the language and the region of [locale]; the English of the United States when they are not known. */
        fun of(locale: Locale): NewsEdition {
            val language = locale.language.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "en"
            val country = locale.country.uppercase(Locale.ROOT).takeIf { it.length == 2 } ?: COUNTRY_OF_LANGUAGE[language] ?: "US"
            return NewsEdition(
                hl = if (language == "en") "en-$country" else language,
                gl = country,
                ceid = "$country:$language",
            )
        }
    }
}

/**
 * Where the News tile reads from. By default the top stories of Google News (the day's most important ones, in the language and
 * the country of the phone); or one of its topics; or the feed (RSS or Atom) at an address the user gives.
 */
object NewsSources {
    const val GOOGLE_TOP = "google"
    const val CUSTOM = "custom"
    private const val TOPIC_PREFIX = "google:"
    const val MAX_URL = 300

    /** The sections of Google News, as it names them. */
    val TOPICS = listOf("WORLD", "NATION", "BUSINESS", "TECHNOLOGY", "ENTERTAINMENT", "SPORTS", "SCIENCE", "HEALTH")

    fun topic(topic: String) = TOPIC_PREFIX + topic

    /** Every source the settings offer, in order: the top stories, the topics, a feed of your own. */
    val choices: List<String> = listOf(GOOGLE_TOP) + TOPICS.map(::topic) + CUSTOM

    /** [code] when it is one of the [choices], the top stories of Google News otherwise. */
    fun normalize(code: String?): String = code?.takeIf { it in choices } ?: GOOGLE_TOP

    fun isGoogle(code: String) = code == GOOGLE_TOP || code.startsWith(TOPIC_PREFIX)

    /** The topic of a Google source, or null for the top stories and for a feed of the user's. */
    fun topicOf(code: String): String? = code.takeIf { it.startsWith(TOPIC_PREFIX) }?.removePrefix(TOPIC_PREFIX)?.takeIf { it in TOPICS }

    /**
     * The address of a feed the user typed: with `https://` put in front when there is no scheme, and null for anything that is
     * not a web address on the Internet (another scheme, an address with a name and password in it, no host, a dot-less name).
     */
    fun customUrl(text: String): String? {
        val typed = text.trim()
        if (typed.isEmpty() || typed.length > MAX_URL || typed.any { it.isWhitespace() }) return null
        val withScheme = when {
            typed.startsWith("https://", ignoreCase = true) -> typed
            typed.contains("://") || typed.startsWith("http:", ignoreCase = true) -> return null
            else -> "https://$typed"
        }
        val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (uri.userInfo != null || '.' !in host || host.startsWith(".") || host.endsWith(".")) return null
        return withScheme
    }

    /** The address to read for [code]; null for a feed of the user's that is not a good address. */
    fun url(code: String, custom: String, edition: NewsEdition): String? {
        if (code == CUSTOM) return customUrl(custom)
        val tail = "hl=${edition.hl}&gl=${edition.gl}&ceid=${edition.ceid}"
        val topic = topicOf(code)
        return if (topic == null) "https://news.google.com/rss?$tail" else "https://news.google.com/rss/headlines/section/topic/$topic?$tail"
    }

    /** The hosts the answer may come from (and the redirects may go to): Google News, or the host of the feed with and without `www.`. */
    fun hosts(url: String): Set<String> {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase(Locale.ROOT) ?: return emptySet()
        return setOf(host, if (host.startsWith("www.")) host.removePrefix("www.") else "www.$host")
    }

    /** What identifies the source of the headlines kept: a change of the source or of its address throws them away. */
    fun key(code: String, custom: String, edition: NewsEdition): String =
        if (code == CUSTOM) "custom:${customUrl(custom).orEmpty()}" else "$code:${edition.hl}:${edition.gl}"
}

/** The headlines of an RSS or Atom feed, read without anything that could reach out: no outside entities, no pages, no scripts. */
object Feeds {
    const val MAX_ITEMS = 20
    const val MAX_TITLE = 160
    private const val MAX_CHARS = 600_000

    class Parsed(val title: String, val items: List<NewsItem>)

    /** The feed in [xml], or null when it is not an RSS or Atom feed (or has a document type, which a feed has no use for). */
    fun parse(xml: String): Parsed? {
        if (xml.length > MAX_CHARS || xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) return null
        val document = runCatching { factory.newDocumentBuilder().parse(InputSource(StringReader(xml))) }.getOrNull() ?: return null
        val root = document.documentElement ?: return null
        return when (root.tagName.lowercase(Locale.ROOT)) {
            "rss", "rdf:rdf" -> rss(root)
            "feed" -> atom(root)
            else -> null
        }
    }

    private val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        isValidating = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }

    private fun rss(root: Element): Parsed? {
        val channel = root.children("channel").firstOrNull() ?: root
        val feedTitle = clean(channel.children("title").firstOrNull()?.textContent.orEmpty(), 80)
        val elements = channel.children("item").ifEmpty { root.children("item") }
        val items = elements.mapNotNull { item ->
            val source = clean(item.children("source").firstOrNull()?.textContent.orEmpty(), 60)
            val raw = clean(item.children("title").firstOrNull()?.textContent.orEmpty(), MAX_TITLE + 80)
            val title = withoutSource(raw, source).take(MAX_TITLE)
            val link = safeLink(item.children("link").firstOrNull()?.textContent) ?: return@mapNotNull null
            if (title.isEmpty()) return@mapNotNull null
            NewsItem(title, source, link, date(item.children("pubDate").firstOrNull()?.textContent ?: item.children("dc:date").firstOrNull()?.textContent))
        }
        return finish(feedTitle, items)
    }

    private fun atom(root: Element): Parsed? {
        val feedTitle = clean(root.children("title").firstOrNull()?.textContent.orEmpty(), 80)
        val items = root.children("entry").mapNotNull { entry ->
            val title = clean(entry.children("title").firstOrNull()?.textContent.orEmpty(), MAX_TITLE)
            val links = entry.children("link")
            val href = (links.firstOrNull { it.getAttribute("rel").let { rel -> rel.isEmpty() || rel == "alternate" } } ?: links.firstOrNull())?.getAttribute("href")
            val link = safeLink(href) ?: return@mapNotNull null
            if (title.isEmpty()) return@mapNotNull null
            val source = clean(entry.children("source").firstOrNull()?.children("title")?.firstOrNull()?.textContent.orEmpty(), 60)
            NewsItem(title, source, link, date(entry.children("updated").firstOrNull()?.textContent ?: entry.children("published").firstOrNull()?.textContent))
        }
        return finish(feedTitle, items)
    }

    private fun finish(title: String, items: List<NewsItem>): Parsed? {
        val distinct = items.distinctBy { it.title }.take(MAX_ITEMS)
        return if (distinct.isEmpty()) null else Parsed(title, distinct)
    }

    /** "Headline - Publisher": the publisher is said apart, so it comes off the headline. */
    fun withoutSource(title: String, source: String): String =
        if (source.isNotEmpty() && title.endsWith(" - $source")) title.removeSuffix(" - $source").trim() else title

    /** A link that opens a web page, or null: nothing but http and https is ever opened. */
    fun safeLink(text: String?): String? {
        val link = text?.trim().orEmpty()
        if (link.length > 2_000 || !(link.startsWith("https://", true) || link.startsWith("http://", true))) return null
        return link.takeIf { runCatching { java.net.URI(it).host }.getOrNull() != null }
    }

    /** Text from a feed made safe to show: no tags, the entities of HTML spelled out, spaces closed up, at most [max] letters. */
    fun clean(text: String, max: Int): String {
        val noTags = text.replace(Regex("<[^>]*>"), " ")
        val spelled = noTags
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace(Regex("&#(\\d{1,5});")) { m -> m.groupValues[1].toInt().toChar().toString() }
        return spelled.replace(Regex("\\s+"), " ").trim().take(max)
    }

    /** The moment in a feed's date (RFC 1123 in RSS, ISO 8601 in Atom), or 0. */
    fun date(text: String?): Long {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return 0L
        return runCatching { OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: 0L
    }

    private fun Element.children(name: String): List<Element> {
        val found = ArrayList<Element>()
        var node: Node? = firstChild
        while (node != null) {
            if (node is Element && node.tagName == name) found += node
            node = node.nextSibling
        }
        return found
    }
}

/** How long ago, in the words of a tile: "now", "12 min", "3 h", "2 d"; kept pure so it is tested in any language by its parts. */
object NewsAge {
    enum class Unit { Now, Minutes, Hours, Days }

    class Age(val unit: Unit, val value: Int)

    fun of(publishedAt: Long, now: Long): Age? {
        if (publishedAt <= 0L) return null
        val minutes = ((now - publishedAt) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes < 1 -> Age(Unit.Now, 0)
            minutes < 60 -> Age(Unit.Minutes, minutes.toInt())
            minutes < 24 * 60 -> Age(Unit.Hours, (minutes / 60).toInt())
            else -> Age(Unit.Days, (minutes / (24 * 60)).coerceAtMost(99).toInt())
        }
    }
}

/** The headlines as JSON, for the copy kept on the phone, apart from Android so it can be tested. */
object NewsCodec {
    fun encode(data: NewsData): String = JSONObject().apply {
        put("title", data.title)
        put("fetchedAt", data.fetchedAt)
        put("key", data.sourceKey)
        put(
            "items",
            JSONArray().also { array ->
                data.items.forEach { item ->
                    array.put(JSONObject().put("t", item.title).put("s", item.source).put("l", item.link).put("p", item.publishedAt))
                }
            },
        )
    }.toString()

    fun decode(json: String?): NewsData? = runCatching {
        val o = JSONObject(json ?: return null)
        val array = o.getJSONArray("items")
        val items = List(array.length()) { i ->
            val item = array.getJSONObject(i)
            NewsItem(item.getString("t"), item.optString("s"), Feeds.safeLink(item.getString("l")) ?: error("link"), item.optLong("p"))
        }
        NewsData(o.optString("title"), items.take(Feeds.MAX_ITEMS), o.getLong("fetchedAt"), o.optString("key"))
    }.getOrNull()
}
