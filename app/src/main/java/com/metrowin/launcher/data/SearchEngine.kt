package com.metrowin.launcher.data

import java.net.URLEncoder

/** The web search the search box offers. It opens in the browser; nothing is sent until the user touches the row. */
enum class SearchEngine(val code: String, val label: String, private val template: String) {
    DuckDuckGo("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    Google("google", "Google", "https://www.google.com/search?q=%s"),
    Bing("bing", "Bing", "https://www.bing.com/search?q=%s"),
    Brave("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
    Startpage("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s"),
    Ecosia("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s"),
    Qwant("qwant", "Qwant", "https://www.qwant.com/?q=%s");

    /** The address of a search for [query] with this engine. */
    fun url(query: String): String = template.format(URLEncoder.encode(query.trim(), "UTF-8"))

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: DuckDuckGo
    }
}
