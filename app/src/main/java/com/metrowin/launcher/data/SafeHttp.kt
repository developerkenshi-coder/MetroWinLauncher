package com.metrowin.launcher.data

import java.net.HttpURLConnection
import java.net.URL

/** The one way the launcher reads from the network: a few named hosts, HTTPS only, short timeouts and a small answer. */
object SafeHttp {
    /**
     * Blocking, call it off the main thread. The body of [address] as text, or null when the address is not HTTPS on one of
     * [hosts], there is no connection, the server does not say 200, or the answer is longer than [maxBytes]. A redirect is
     * followed (at most [MAX_REDIRECTS] times) only to another HTTPS address on one of the same [hosts].
     */
    fun get(address: String, hosts: Set<String>, maxBytes: Int = MAX_BYTES, timeoutMs: Int = TIMEOUT_MS): String? = runCatching {
        var url = URL(address)
        repeat(MAX_REDIRECTS + 1) {
            require(url.protocol == "https" && url.host in hosts)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MetroWinLauncher")
            }
            try {
                val code = connection.responseCode
                if (code in REDIRECTS) {
                    url = URL(url, connection.getHeaderField("Location") ?: return null)
                } else {
                    if (code != HttpURLConnection.HTTP_OK) return null
                    return read(connection, maxBytes)
                }
            } finally {
                connection.disconnect()
            }
        }
        null
    }.getOrNull()

    private fun read(connection: HttpURLConnection, maxBytes: Int): String? = connection.inputStream.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (out.size() + read > maxBytes) return null
            out.write(buffer, 0, read)
        }
        out.toString(Charsets.UTF_8.name())
    }

    const val TIMEOUT_MS = 8_000
    const val MAX_BYTES = 256 * 1024
    const val MAX_REDIRECTS = 3
    private val REDIRECTS = setOf(301, 302, 303, 307, 308)
}
