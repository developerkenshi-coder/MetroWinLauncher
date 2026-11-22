package com.metrowin.launcher.data

import java.net.URI

/** What the user types when pinning a web page to Start. */
object SiteInput {
    /**
     * A usable address, or null. "example.com" becomes "https://example.com". Only http and https addresses are
     * accepted (not javascript:, file:, intent: and the like), and the address needs a host name.
     */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = if ("://" in text) text else "https://$text"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        if (scheme != "http" && scheme != "https") return null
        // A bare word is a mistake ("hello"); a host needs a dot (or to be localhost).
        if ('.' !in host && host != "localhost") return null
        return withScheme
    }

    /** "https://www.example.com/page" is called "example.com" until the user names it. */
    fun suggestedTitle(address: String): String =
        runCatching { URI(address).host }.getOrNull()?.removePrefix("www.") ?: address
}
