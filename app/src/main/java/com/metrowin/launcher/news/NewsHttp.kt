package com.metrowin.launcher.news

import com.metrowin.launcher.data.Http
import com.metrowin.launcher.data.HttpReply
import com.metrowin.launcher.data.HttpRequest
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** What came back from the network for a feed, said apart so the app can tell the user what went wrong. */
sealed interface NewsReply {
    /** The text of the answer, with the validators the server gave ([etag], [modified]) to ask again cheaply next time. */
    class Body(val text: String, val etag: String = "", val modified: String = "") : NewsReply

    /** The server says what the app already has is still current (HTTP 304). */
    data object NotModified : NewsReply

    /** No connection, no such host, or too slow. */
    data object Offline : NewsReply

    /** The server answered, but with an error [code]. */
    class Http(val code: Int) : NewsReply

    /** A redirect to a place that is not allowed (not https, a private address, another site when that is not permitted), or an unusable answer. */
    data object Blocked : NewsReply

    /** More than the allowed size. */
    data object TooBig : NewsReply
}

/** Why a source could not be brought up to date. */
enum class NewsFailure { Offline, Http, Parse, Blocked, TooBig, Metered, Address }

/**
 * The network of the news app: https only, short timeouts, a small answer, redirects followed with rules, and a conditional request
 * (ETag, If-Modified-Since) so a feed that did not change costs almost nothing.
 */
object NewsHttp {
    const val TIMEOUT_MS = Http.TIMEOUT_MS
    const val MAX_REDIRECTS = Http.MAX_REDIRECTS

    /** How a connection is opened; a test gives its own. */
    val default: (URL) -> HttpURLConnection = Http.default

    /**
     * Blocking, call it off the main thread. Reads [address] (https on one of [hosts]). A redirect goes on to another https address on one
     * of the same [hosts], or, when [anySite] is on, to any public host (see [isPublicHost]); at most [MAX_REDIRECTS] times.
     */
    fun get(
        address: String,
        hosts: Set<String>,
        anySite: Boolean = false,
        etag: String = "",
        modified: String = "",
        maxBytes: Int = 512 * 1024,
        open: (URL) -> HttpURLConnection = default,
    ): NewsReply {
        val request = HttpRequest(
            address, { it.host.lowercase(Locale.ROOT) in hosts || (anySite && isPublicHost(it.host)) },
            etag = etag, modified = modified, maxBytes = maxBytes,
        )
        return when (val reply = Http.fetch(request, open)) {
            is HttpReply.Body -> NewsReply.Body(reply.text, reply.etag, reply.modified)
            HttpReply.NotModified -> NewsReply.NotModified
            HttpReply.Offline -> NewsReply.Offline
            is HttpReply.Status -> NewsReply.Http(reply.code)
            HttpReply.Blocked -> NewsReply.Blocked
            HttpReply.TooBig -> NewsReply.TooBig
        }
    }

    /**
     * Whether [host] is somewhere on the public Internet by its name or number alone: not `localhost`, not a name without a dot, not a
     * local suffix, not a private, loopback or link-local number, and no IPv6 number at all.
     * shortcut: a public name that resolves to a private number (DNS rebinding) is not caught; upgrade when the app resolves names itself.
     */
    fun isPublicHost(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT).trimEnd('.')
        if (h.isEmpty() || h == "localhost" || '.' !in h || ':' in h || h.startsWith("[")) return false
        if (listOf(".local", ".localhost", ".internal", ".lan", ".home", ".home.arpa", ".corp", ".intranet").any { h.endsWith(it) }) return false
        val parts = h.split('.')
        if (parts.all { p -> p.isNotEmpty() && p.all(Char::isDigit) }) {
            if (parts.size != 4) return false
            val n = parts.map { it.toIntOrNull() ?: return false }
            if (n.any { it > 255 }) return false
            val (a, b) = n
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || (a == 169 && b == 254) || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127))
        }
        return true
    }
}
