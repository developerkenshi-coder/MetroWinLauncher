package com.metrowin.launcher.news

import com.metrowin.launcher.data.Feeds
import com.metrowin.launcher.data.NewsSources
import org.w3c.dom.Element

/** The list of feeds as OPML, the file other readers export and import. Only the feeds with an address (not the Google News pages) go out. */
object NewsOpml {
    /** The OPML of the feeds in [list]. */
    fun encode(list: List<NewsSourceDef>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\"><head><title>MetroWin News</title></head><body>\n")
        list.filter { it.url.isNotEmpty() }.forEach { append("<outline type=\"rss\" text=\"${esc(it.name)}\" xmlUrl=\"${esc(it.url)}\"/>\n") }
        append("</body></opml>\n")
    }

    /** The feeds in [xml]: name and address of every outline that has a good https address (see [NewsSources.customUrl]); at most [limit]. */
    fun parse(xml: String, limit: Int = NewsSourceList.MAX): List<Pair<String, String>> {
        val root = Feeds.document(xml)?.documentElement?.takeIf { it.tagName.equals("opml", ignoreCase = true) } ?: return emptyList()
        val found = ArrayList<Pair<String, String>>()
        fun walk(element: Element) {
            var node = element.firstChild
            while (node != null && found.size < limit) {
                if (node is Element) {
                    val url = NewsSources.customUrl(node.getAttribute("xmlUrl"))
                    if (node.tagName == "outline" && url != null) found += node.getAttribute("text").ifEmpty { node.getAttribute("title") }.take(40) to url
                    walk(node)
                }
                node = node.nextSibling
            }
        }
        walk(root)
        return found
    }

    private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
