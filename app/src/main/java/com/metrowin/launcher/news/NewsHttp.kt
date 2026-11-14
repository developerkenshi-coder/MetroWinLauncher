package com.metrowin.launcher.news

import java.io.ByteArrayOutputStream
import java.io.IOException
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
    const val TIMEOUT_MS = 8_000
    const val MAX_REDIRECTS = 3
    private val REDIRECTS = setOf(301, 302, 303, 307, 308)

    /** How a connection is opened; a test gives its own. */
    val default: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }

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
        var url = runCatching { URL(address) }.getOrNull() ?: return NewsReply.Blocked
        repeat(MAX_REDIRECTS + 1) {
            if (url.protocol != "https" || !(url.host.lowercase(Locale.ROOT) in hosts || (anySite && isPublicHost(url.host)))) return NewsReply.Blocked
            val connection = try { open(url) } catch (e: IOException) { return NewsReply.Offline }
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "MetroWinLauncher")
                if (etag.isNotEmpty()) connection.setRequestProperty("If-None-Match", etag)
                if (modified.isNotEmpty()) connection.setRequestProperty("If-Modified-Since", modified)
                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_NOT_MODIFIED -> return NewsReply.NotModified
                    code in REDIRECTS -> url = runCatching { URL(url, connection.getHeaderField("Location") ?: return NewsReply.Blocked) }.getOrNull() ?: return NewsReply.Blocked
                    code != HttpURLConnection.HTTP_OK -> return NewsReply.Http(code)
                    else -> {
                        val text = read(connection, maxBytes) ?: return NewsReply.TooBig
                        return NewsReply.Body(text, connection.getHeaderField("ETag").orEmpty().take(200), connection.getHeaderField("Last-Modified").orEmpty().take(100))
                    }
                }
            } catch (e: IOException) {
                return NewsReply.Offline
            } catch (e: RuntimeException) {
                return NewsReply.Blocked
            } finally {
                connection.disconnect()
            }
        }
        return NewsReply.Blocked
    }

    private fun read(connection: HttpURLConnection, maxBytes: Int): String? = connection.inputStream.use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (out.size() + read > maxBytes) return null
            out.write(buffer, 0, read)
        }
        out.toString(Charsets.UTF_8.name())
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
