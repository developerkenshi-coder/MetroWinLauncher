package com.metrowin.launcher.data

import java.net.URLEncoder

/**
 * Places on Start: a tile that starts the navigation to an address. Google Maps takes it (`google.navigation:`), and where it
 * is not installed any app that opens a `geo:` address does, and in the end the page of Google Maps in the browser.
 */
object Places {
    const val MAX_ADDRESS = 200
    const val MAX_NAME = 40

    /** [address] on one line, or null when there is nothing in it. */
    fun clean(address: String): String? = address.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.take(MAX_ADDRESS)

    /** The name of a place: what was typed, or the beginning of the address when nothing was. */
    fun nameFor(name: String, address: String): String =
        name.trim().ifEmpty { address.substringBefore(',').trim() }.take(MAX_NAME)

    /** Navigation to [address] in Google Maps. */
    fun navigationUri(address: String): String = NAVIGATION + encode(address)

    /** [address] on the map of any app that shows addresses. */
    fun geoUri(address: String): String = GEO + encode(address)

    /** Directions to [address] on the Google Maps page: opens in the Maps app where it is installed, in the browser otherwise. */
    fun webUri(address: String): String = WEB + encode(address)

    /** What to try, in order, to open [uri]: a navigation address has two more ways to open when Google Maps is not there. */
    fun fallbacks(uri: String): List<String> {
        if (!uri.startsWith(NAVIGATION)) return listOf(uri)
        val encoded = uri.removePrefix(NAVIGATION)
        return listOf(uri, GEO + encoded, WEB + encoded)
    }

    /** The address a navigation [uri] is going to, as it was typed; null for any other address. */
    fun addressOf(uri: String): String? =
        uri.takeIf { it.startsWith(NAVIGATION) }?.removePrefix(NAVIGATION)?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private const val NAVIGATION = "google.navigation:q="
    private const val GEO = "geo:0,0?q="
    private const val WEB = "https://www.google.com/maps/dir/?api=1&destination="
}
