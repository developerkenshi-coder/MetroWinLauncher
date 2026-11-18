package com.metrowin.launcher.news

import com.metrowin.launcher.data.Feeds
import java.util.Locale

/** One piece of an article in reading mode: a heading, a paragraph (a list item starts with a bullet), or the address of a picture. */
sealed interface ReadBlock {
    class Head(val text: String) : ReadBlock

    class Para(val text: String) : ReadBlock

    class Pic(val url: String) : ReadBlock
}

/**
 * The readable part of a web page or of the HTML in a feed: headings, paragraphs and the main pictures, nothing else. Nothing is run and
 * nothing is fetched here: the HTML is only read as text, and what is not the story (scripts, frames, forms, menus, ads, trackers) is dropped.
 * shortcut: a tag reader, not a full HTML parser: no unclosed-tag repair beyond the usual, no text of other charsets than UTF-8; upgrade to a real parser if pages go wrong often.
 */
object ReadableText {
    /** Less readable text than this (in letters) is not an article: the page is a login, a gate, a video, or needs scripts to show its text. */
    const val MIN_TEXT = 300
    private const val MAX_HTML = 2_500_000
    private const val MAX_BLOCKS = 400
    private const val MAX_PARA = 4_000
    private const val MAX_PICS = 8
    private const val MAX_STORED = 12_000

    private val TAG = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>""")
    private val DROP = setOf(
        "script", "style", "noscript", "iframe", "frame", "frameset", "form", "nav", "aside", "footer", "header", "button", "select", "textarea",
        "svg", "canvas", "template", "object", "embed", "applet", "video", "audio", "dialog", "menu", "title", "h1", // the title is shown from the feed
    )
    private val RAW = setOf("script", "style", "noscript", "template")
    private val VOID = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    private val BREAKS = setOf("p", "div", "section", "ul", "ol", "li", "blockquote", "pre", "figure", "figcaption", "tr", "table", "dd", "dt", "br", "hr", "h1", "h2", "h3", "h4", "h5", "h6")
    private val HEADS = setOf("h2", "h3", "h4", "h5", "h6")
    private val JUNK_NAME = Regex(
        """(^|[\s_-])(ads?|adv|advert\w*|advertis\w*|sponsor\w*|promo\w*|banner|cookie\w*|consent|newsletter|subscribe\w*|social|share\w*|sharing|related|recommend\w*|comments?|popup|modal|outbrain|taboola|paywall|breadcrumbs?|toolbar|sidebar|widget)($|[\s_-])""",
    )
    private val JUNK_SRC = Regex("""pixel|beacon|track|1x1|spacer|doubleclick|adservice|/ads?/|/ad[_-]|analytics|emoji""")
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "hellip" to "…", "ndash" to "–", "mdash" to "—",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»", "copy" to "©", "euro" to "€",
        "eacute" to "é", "egrave" to "è", "agrave" to "à", "ugrave" to "ù", "ograve" to "ò", "igrave" to "ì", "ccedil" to "ç", "uuml" to "ü",
        "ouml" to "ö", "auml" to "ä", "szlig" to "ß", "ntilde" to "ñ", "aacute" to "á", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
    )
    private val ENTITY = Regex("""&(#x[0-9a-fA-F]{1,6}|#\d{1,7}|[a-zA-Z]{2,8});""")

    /** [html] read for its story, or null when there is not enough text in it (see [MIN_TEXT]). [base] is the address of the page, for pictures with a relative address. */
    fun extract(html: String, base: String = ""): List<ReadBlock>? {
        val text = if (html.length > MAX_HTML) html.take(MAX_HTML) else html
        // A page whose class names mislead (a wrapper called "with-sidebar") is read again by its tags alone.
        return run(text, base, judgeNames = true) ?: run(text, base, judgeNames = false)
    }

    private class Raw(val region: Int, val block: ReadBlock)

    private fun run(html: String, base: String, judgeNames: Boolean): List<ReadBlock>? {
        val out = ArrayList<Raw>()
        val buffer = StringBuilder()
        var lead = ""
        var region = 0
        var regions = 0
        var articleDepth = 0
        var skip: String? = null
        var skipDepth = 0
        var head = false
        var item = false
        var pics = 0

        fun flush() {
            val text = decode(buffer.toString())
            buffer.setLength(0)
            if (text.isEmpty() || out.size >= MAX_BLOCKS) { item = false; return }
            val line = if (item) "• $text" else text
            out += Raw(region, if (head) ReadBlock.Head(line.take(MAX_PARA)) else ReadBlock.Para(line.take(MAX_PARA)))
            item = false
        }

        var i = 0
        val n = html.length
        while (i < n) {
            val lt = html.indexOf('<', i)
            if (lt < 0) { if (skip == null) buffer.append(html, i, n); break }
            if (lt > i && skip == null) buffer.append(html, i, lt)
            if (html.startsWith("<!--", lt)) {
                val end = html.indexOf("-->", lt + 4)
                i = if (end < 0) n else end + 3
                continue
            }
            val m = TAG.matchAt(html, lt)
            if (m == null) { if (skip == null) buffer.append('<'); i = lt + 1; continue }
            i = m.range.last + 1
            val closing = m.groupValues[1].isNotEmpty()
            val name = m.groupValues[2].lowercase(Locale.ROOT)
            val attrs = m.groupValues[3]
            val selfClosed = attrs.trimEnd().endsWith("/")
            if (!closing && name in RAW && !selfClosed) {
                // The inside of a script or a style is never markup: jump to its end.
                val end = html.indexOf("</$name", i, ignoreCase = true)
                val close = if (end < 0) -1 else html.indexOf('>', end)
                if (skip == null) { flush(); }
                i = if (close < 0) n else close + 1
                continue
            }
            if (skip != null) {
                if (name == skip && name !in VOID) {
                    if (closing) { if (--skipDepth == 0) skip = null } else if (!selfClosed) skipDepth++
                }
                continue
            }
            if (!closing) {
                val dropped = name in DROP || (name == "article" && articleDepth > 0) ||
                    (name != "html" && name != "body" && name != "main" && name != "article" && junk(attrs, judgeNames))
                if (dropped && name !in VOID && !selfClosed) { flush(); skip = name; skipDepth = 1; continue }
                if (dropped) continue
            }
            when {
                name == "meta" && !closing -> if (lead.isEmpty()) {
                    val key = attr(attrs, "property").ifEmpty { attr(attrs, "name") }.lowercase(Locale.ROOT)
                    if (key == "og:image" || key == "twitter:image") lead = picture(attr(attrs, "content"), base).orEmpty()
                }
                name == "img" -> if (pics < MAX_PICS) {
                    val url = picture(attr(attrs, "src").ifEmpty { attr(attrs, "data-src") }, base)
                    val tiny = listOf("width", "height").any { a -> attr(attrs, a).toIntOrNull()?.let { it <= 3 } == true }
                    if (url != null && !tiny && !JUNK_SRC.containsMatchIn(url.lowercase(Locale.ROOT))) { flush(); out += Raw(region, ReadBlock.Pic(url)); pics++ }
                }
                name == "article" -> if (closing) { if (articleDepth > 0 && --articleDepth == 0) { flush(); region = 0 } } else { flush(); if (articleDepth++ == 0) region = ++regions }
                name == "li" -> { flush(); item = !closing }
                name in HEADS -> { flush(); head = !closing }
                name in BREAKS -> flush()
            }
        }
        flush()

        val inArticles = out.filter { it.region > 0 }
        val best = inArticles.groupBy { it.region }.values.maxByOrNull { g -> g.sumOf { textLength(it.block) } }
        // Inside an article every paragraph counts; on a page without one, short lines (menus, captions, buttons) do not.
        val chosen = if (best != null && best.sumOf { textLength(it.block) } >= MIN_TEXT) best.map { it.block }
        else out.map { it.block }.filter { it !is ReadBlock.Para || it.text.length >= 60 }
        if (chosen.sumOf { textLength(it) } < MIN_TEXT) return null
        val withLead = if (lead.isNotEmpty() && chosen.none { it is ReadBlock.Pic && it.url == lead }) listOf<ReadBlock>(ReadBlock.Pic(lead)) + chosen else chosen
        return withLead
    }

    private fun textLength(block: ReadBlock) = if (block is ReadBlock.Para) block.text.length else 0

    private fun junk(attrs: String, judgeNames: Boolean): Boolean {
        val a = attrs.lowercase(Locale.ROOT)
        if (Regex("""\bhidden\b|aria-hidden\s*=\s*["']true|display\s*:\s*none|\bdata-ad[-=]|adsbygoogle""").containsMatchIn(a)) return true
        if (!judgeNames) return false
        return JUNK_NAME.containsMatchIn(attr(a, "class") + " " + attr(a, "id") + " " + attr(a, "role").replace("complementary", "sidebar"))
    }

    private fun attr(attrs: String, name: String): String =
        Regex("""(?:^|\s)${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }.orEmpty()

    /** The address of a picture that is safe to show: https only, made absolute against [base]; data: and anything else is dropped. */
    private fun picture(raw: String, base: String): String? {
        val src = decode(raw)
        if (src.isEmpty()) return null
        val absolute = if (src.startsWith("//")) "https:$src" else if (src.startsWith("/") || !src.contains(':')) runCatching { java.net.URI(base).resolve(src).toString() }.getOrNull() else src
        return Feeds.safeImage(absolute)
    }

    /** [text] with its HTML entities spelled out and its spaces closed up. */
    fun decode(text: String): String =
        ENTITY.replace(text) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let(::codePoint)
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED[e] ?: NAMED[e.lowercase(Locale.ROOT)]
            } ?: m.value
        }.replace(Regex("\\s+"), " ").trim()

    private fun codePoint(c: Int): String? = if (c in 1..0x10FFFF && c !in 0xD800..0xDFFF) String(Character.toChars(c)) else null

    // --- what is kept in the file of a feed: one block a line, "H", "P" or "I", a tab, then the text or the address

    /** The blocks in a line each, or "" when they would be more than the file of a feed should hold (the page is then read when the user opens it). */
    fun encode(blocks: List<ReadBlock>): String {
        val text = blocks.joinToString("\n") {
            when (it) {
                is ReadBlock.Head -> "H\t${it.text}"
                is ReadBlock.Para -> "P\t${it.text}"
                is ReadBlock.Pic -> "I\t${it.url}"
            }
        }
        return if (text.length > MAX_STORED) "" else text
    }

    fun decodeStored(text: String): List<ReadBlock> = text.lineSequence().mapNotNull { line ->
        val body = line.drop(2)
        when (line.take(2)) {
            "H\t" -> ReadBlock.Head(body)
            "P\t" -> ReadBlock.Para(body)
            "I\t" -> Feeds.safeImage(body)?.let { ReadBlock.Pic(it) }
            else -> null
        }
    }.toList()

    /** What a feed's own HTML ([html], its `content:encoded`) keeps for reading offline: encoded blocks, or "" when it holds no whole article. */
    fun forFeed(html: String): String = extract(html)?.let(::encode).orEmpty()
}
