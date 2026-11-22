package com.metrowin.launcher.data

/**
 * The small reads of the launcher from named hosts (weather, rates, the update list): HTTPS only, short timeouts, a small answer. It is [Http] with
 * a list of hosts; [cached] adds the disk copy of [Http.fetchCached] for answers that change slowly.
 */
object SafeHttp {
    /**
     * Blocking, call it off the main thread. The body of [address] as text, or null when the address is not HTTPS on one of
     * [hosts], there is no connection, the server does not say 200, or the answer is longer than [maxBytes]. A redirect is
     * followed (at most [MAX_REDIRECTS] times) only to another HTTPS address on one of the same [hosts].
     */
    fun get(address: String, hosts: Set<String>, maxBytes: Int = MAX_BYTES, timeoutMs: Int = TIMEOUT_MS, cached: Boolean = false): String? {
        val request = HttpRequest(address, { it.host in hosts }, maxBytes = maxBytes, connectMs = timeoutMs, readMs = timeoutMs)
        return ((if (cached) Http.fetchCached(request) else Http.fetch(request)) as? HttpReply.Body)?.text
    }

    const val TIMEOUT_MS = Http.TIMEOUT_MS
    const val MAX_BYTES = Http.MAX_BYTES
    const val MAX_REDIRECTS = Http.MAX_REDIRECTS
}
