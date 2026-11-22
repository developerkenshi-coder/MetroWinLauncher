package com.metrowin.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One request of the launcher to the network. Everything the callers used to repeat by hand is a field here. */
class HttpRequest(
    val address: String,
    /** Who may be reached, checked again after every redirect. HTTPS is always required, whatever this says. */
    val allowed: (URL) -> Boolean,
    val method: String = "GET",
    val body: ByteArray? = null,
    val headers: Map<String, String> = emptyMap(),
    /** Validators of a copy the caller already has ([HttpReply.NotModified] comes back when it is current). */
    val etag: String = "",
    val modified: String = "",
    val maxBytes: Int = Http.MAX_BYTES,
    val connectMs: Int = Http.TIMEOUT_MS,
    val readMs: Int = Http.TIMEOUT_MS,
    /** Extra tries after a connection error or a 5xx, for GET only (a POST is never repeated). */
    val retries: Int = 1,
    /** Called with the content type of a good answer: false refuses it ([HttpReply.Blocked]). */
    val accepts: (String?) -> Boolean = { true },
)

/** What came back, said apart so the caller can tell the user what went wrong. */
sealed interface HttpReply {
    /** [stale]: the network failed and this is the copy kept from an earlier answer. */
    class Body(val bytes: ByteArray, val etag: String = "", val modified: String = "", val contentType: String = "", val stale: Boolean = false) : HttpReply {
        val text: String get() = String(bytes, Charsets.UTF_8)
    }

    /** HTTP 304: what the caller has is current. */
    data object NotModified : HttpReply

    /** No connection, no such host, or too slow. */
    data object Offline : HttpReply

    /** The server answered with another code than 200 ([body] is the start of what it said, for an error page or a JSON error). */
    class Status(val code: Int, val body: ByteArray = ByteArray(0)) : HttpReply

    /** Not https, not allowed (also by a redirect), or an unusable answer. */
    data object Blocked : HttpReply

    /** More than the allowed size. */
    data object TooBig : HttpReply
}

/**
 * The only way the launcher talks to the network: https only, an allowed list of hosts, short timeouts, a small answer, redirects followed with
 * the same rules, one more try after a connection error or a server error, and (when [install]ed) a disk copy of the answers that carry an ETag,
 * so a request that did not change costs a 304 and one made with no connection gets the last good copy instead of a failure.
 */
object Http {
    const val TIMEOUT_MS = 8_000
    const val MAX_BYTES = 256 * 1024
    const val MAX_REDIRECTS = 3
    private const val ERROR_BYTES = 16 * 1024
    private const val BACKOFF_MS = 400L
    private val REDIRECTS = setOf(301, 302, 303, 307, 308)

    /** How a connection is opened; a test gives its own. */
    val default: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }

    /** Waits between two tries; a test does not wait. */
    var pause: (Long) -> Unit = { Thread.sleep(it) }

    /** False when the phone has no network at all: nothing is tried, the kept copy (if any) is the answer. */
    var online: () -> Boolean = { true }

    private var cache: HttpCache? = null

    /** For a test: the disk copy in a folder of its own (null: none). */
    internal fun useCache(store: HttpCache?) { cache = store }

    /** Called once by the app: gives the disk copy a folder and the check of the connection. */
    fun install(context: Context) {
        val app = context.applicationContext
        cache = HttpCache(File(app.cacheDir, "http"))
        online = {
            val manager = app.getSystemService(ConnectivityManager::class.java)
            manager == null || manager.activeNetwork != null
        }
    }

    /** Blocking, call it off the main thread. */
    fun fetch(request: HttpRequest, open: (URL) -> HttpURLConnection = default): HttpReply = withRetry(request) { once(request, open) { c -> c.inputStream.use { it.readBytes(request.maxBytes) }?.let { b -> body(c, b) } ?: HttpReply.TooBig } }

    /**
     * Like [fetch] but with the disk copy: a good answer with a validator is kept, a 304 returns the kept body, and when the network fails the
     * kept body comes back marked [HttpReply.Body.stale]. Only for GET and small, public answers (nothing the user typed or a login).
     */
    fun fetchCached(request: HttpRequest, open: (URL) -> HttpURLConnection = default): HttpReply {
        val store = cache
        if (store == null || request.method != "GET") return fetch(request, open)
        val kept = store.get(request.address)
        if (!online() && kept != null) return kept.toBody(stale = true)
        val asking = if (kept != null && request.etag.isEmpty() && request.modified.isEmpty()) HttpRequest(
            request.address, request.allowed, request.method, request.body, request.headers, kept.etag, kept.modified, request.maxBytes, request.connectMs, request.readMs, request.retries, request.accepts,
        ) else request
        return when (val reply = fetch(asking, open)) {
            is HttpReply.Body -> reply.also { if (it.etag.isNotEmpty() || it.modified.isNotEmpty()) store.put(request.address, it) }
            HttpReply.NotModified -> kept?.toBody(stale = false) ?: HttpReply.NotModified
            HttpReply.Offline -> kept?.toBody(stale = true) ?: reply
            is HttpReply.Status -> if (reply.code >= 500) kept?.toBody(stale = true) ?: reply else reply
            else -> reply
        }
    }

    /**
     * Blocking. Streams a good answer into [target] (written as [target].part and moved when complete), counting at most [maxBytes], and says
     * how far it got through [onProgress] (bytes so far, total or -1). The reply is [HttpReply.Body] with no bytes when it worked.
     */
    fun download(request: HttpRequest, target: File, maxBytes: Long, onProgress: (Long, Long) -> Unit = { _, _ -> }, open: (URL) -> HttpURLConnection = default): HttpReply =
        withRetry(request) {
            once(request, open) { connection ->
                val part = File(target.parentFile, target.name + ".part")
                val total = connection.contentLengthLong
                var done = 0L
                val ok = connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            done += read
                            if (done > maxBytes) return@use false
                            output.write(buffer, 0, read)
                            onProgress(done, total)
                        }
                        true
                    }
                }
                if (!ok) {
                    part.delete()
                    HttpReply.TooBig
                } else if (!part.renameTo(target)) {
                    part.delete()
                    HttpReply.Offline
                } else {
                    body(connection, ByteArray(0))
                }
            }
        }

    private fun withRetry(request: HttpRequest, attempt: () -> HttpReply): HttpReply {
        var tries = 0
        while (true) {
            val reply = attempt()
            val worth = reply is HttpReply.Offline || (reply is HttpReply.Status && reply.code in 500..599)
            if (!worth || request.method != "GET" || tries >= request.retries || !online()) return reply
            tries++
            pause(BACKOFF_MS * tries)
        }
    }

    private fun body(connection: HttpURLConnection, bytes: ByteArray) = HttpReply.Body(
        bytes,
        connection.getHeaderField("ETag").orEmpty().take(200),
        connection.getHeaderField("Last-Modified").orEmpty().take(100),
        connection.contentType.orEmpty(),
    )

    private fun once(request: HttpRequest, open: (URL) -> HttpURLConnection, good: (HttpURLConnection) -> HttpReply): HttpReply {
        var url = runCatching { URL(request.address) }.getOrNull() ?: return HttpReply.Blocked
        repeat(MAX_REDIRECTS + 1) {
            if (url.protocol != "https" || !request.allowed(url)) return HttpReply.Blocked
            val connection = try { open(url) } catch (e: IOException) { return HttpReply.Offline }
            try {
                connection.connectTimeout = request.connectMs
                connection.readTimeout = request.readMs
                connection.instanceFollowRedirects = false
                connection.requestMethod = request.method
                connection.setRequestProperty("User-Agent", "MetroWinLauncher")
                request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (request.etag.isNotEmpty()) connection.setRequestProperty("If-None-Match", request.etag)
                if (request.modified.isNotEmpty()) connection.setRequestProperty("If-Modified-Since", request.modified)
                request.body?.let { bytes ->
                    connection.doOutput = true
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_NOT_MODIFIED -> return HttpReply.NotModified
                    code in REDIRECTS && request.method == "GET" ->
                        url = runCatching { URL(url, connection.getHeaderField("Location") ?: return HttpReply.Blocked) }.getOrNull() ?: return HttpReply.Blocked
                    code == HttpURLConnection.HTTP_OK || (request.method != "GET" && code in 200..299) -> {
                        if (!request.accepts(connection.contentType)) return HttpReply.Blocked
                        return good(connection)
                    }
                    else -> return HttpReply.Status(code, connection.errorStream?.use { it.readBytes(ERROR_BYTES) } ?: ByteArray(0))
                }
            } catch (e: IOException) {
                return HttpReply.Offline
            } catch (e: RuntimeException) {
                return HttpReply.Blocked
            } finally {
                connection.disconnect()
            }
        }
        return HttpReply.Blocked
    }

    /** At most [max] bytes of the stream, or null when there is more. */
    private fun InputStream.readBytes(max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > max) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

/** The kept answers: a body and its validators per address (the digest of the address is the name), the oldest go first past [maxBytes]. */
class HttpCache(private val dir: File, private val maxBytes: Long = 8L * 1024 * 1024) {
    class Entry(val bytes: ByteArray, val etag: String, val modified: String, val contentType: String) {
        fun toBody(stale: Boolean) = HttpReply.Body(bytes, etag, modified, contentType, stale)
    }

    private fun name(address: String) = MessageDigest.getInstance("SHA-1").digest(address.toByteArray()).joinToString("") { "%02x".format(it) }

    @Synchronized
    fun get(address: String): Entry? = runCatching {
        val base = File(dir, name(address))
        val meta = File(base.path + ".meta").takeIf { it.exists() }?.readLines() ?: return null
        val body = File(base.path + ".body").takeIf { it.exists() }?.readBytes() ?: return null
        base.setLastModified(System.currentTimeMillis())
        Entry(body, meta.getOrElse(0) { "" }, meta.getOrElse(1) { "" }, meta.getOrElse(2) { "" })
    }.getOrNull()

    @Synchronized
    fun put(address: String, reply: HttpReply.Body) {
        runCatching {
            dir.mkdirs()
            val base = File(dir, name(address))
            File(base.path + ".body").writeBytes(reply.bytes)
            File(base.path + ".meta").writeText(listOf(reply.etag, reply.modified, reply.contentType).joinToString("\n") { it.replace('\n', ' ') })
            trim()
        }
    }

    private fun trim() {
        val bodies = dir.listFiles { f -> f.name.endsWith(".body") }.orEmpty().sortedBy { it.lastModified() }
        var bytes = bodies.sumOf { it.length() }
        for (f in bodies) {
            if (bytes <= maxBytes) break
            bytes -= f.length()
            f.delete()
            File(f.path.removeSuffix(".body") + ".meta").delete()
        }
    }

    @Synchronized
    fun clear() {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }
}
