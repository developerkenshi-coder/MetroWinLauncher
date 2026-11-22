package com.metrowin.launcher

import com.metrowin.launcher.data.Http
import com.metrowin.launcher.data.HttpCache
import com.metrowin.launcher.data.HttpReply
import com.metrowin.launcher.data.HttpRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A connection that answers what it is told and records what was asked: no network. */
private class Fake(
    url: URL,
    private val code: Int,
    private val headers: Map<String, String> = emptyMap(),
    private val body: ByteArray = ByteArray(0),
    private val error: ByteArray? = null,
) : HttpURLConnection(url) {
    val sent = HashMap<String, String>()
    val written = ByteArrayOutputStream()
    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy() = false
    override fun getResponseCode() = code
    override fun getHeaderField(name: String?): String? = headers[name] ?: headers.entries.firstOrNull { it.key.equals(name, true) }?.value
    override fun getInputStream() = body.inputStream()
    override fun getErrorStream() = error?.inputStream()
    override fun getOutputStream() = written
    override fun setRequestProperty(key: String?, value: String?) { sent[key.orEmpty()] = value.orEmpty() }
}

class HttpTest {
    @get:Rule val folder = TemporaryFolder()
    private val paused = ArrayList<Long>()

    @Before fun noWaiting() { Http.pause = { paused += it }; Http.online = { true } }
    @After fun restore() { Http.pause = { Thread.sleep(it) }; Http.online = { true }; Http.useCache(null) }

    private fun request(address: String = "https://api.example.com/x", extra: HttpRequest.() -> Unit = {}) =
        HttpRequest(address, { it.host == "api.example.com" }).also(extra)

    private fun text(bytes: String) = bytes.toByteArray()

    @Test
    fun aGoodAnswerComesBackWithItsValidators() {
        val reply = Http.fetch(request()) { Fake(it, 200, mapOf("ETag" to "\"v1\"", "Last-Modified" to "Mon", "Content-Type" to "text/plain"), text("hello")) }
        reply as HttpReply.Body
        assertEquals("hello", reply.text)
        assertEquals("\"v1\"", reply.etag)
        assertEquals("Mon", reply.modified)
    }

    @Test
    fun theValidatorsOfTheCopyYouHaveAreSentAndA304IsTold() {
        var seen: Fake? = null
        val reply = Http.fetch(HttpRequest("https://api.example.com/x", { true }, etag = "\"v1\"", modified = "Mon")) { Fake(it, 304).also { f -> seen = f } }
        assertEquals(HttpReply.NotModified, reply)
        assertEquals("\"v1\"", seen!!.sent["If-None-Match"])
        assertEquals("Mon", seen!!.sent["If-Modified-Since"])
    }

    @Test
    fun onlyHttpsOnAnAllowedHostAlsoAfterARedirect() {
        assertEquals(HttpReply.Blocked, Http.fetch(request("http://api.example.com/x")) { Fake(it, 200) })
        assertEquals(HttpReply.Blocked, Http.fetch(request("https://other.example.com/x")) { Fake(it, 200) })
        assertEquals(HttpReply.Blocked, Http.fetch(request("not an address")) { Fake(it, 200) })
        val toOther: (URL) -> HttpURLConnection = { Fake(it, 301, mapOf("Location" to "https://evil.example.org/y")) }
        assertEquals(HttpReply.Blocked, Http.fetch(request(), toOther))
        val toHttp: (URL) -> HttpURLConnection = { Fake(it, 302, mapOf("Location" to "http://api.example.com/y")) }
        assertEquals(HttpReply.Blocked, Http.fetch(request(), toHttp))
        val noWhere: (URL) -> HttpURLConnection = { Fake(it, 302) }
        assertEquals(HttpReply.Blocked, Http.fetch(request(), noWhere))
    }

    @Test
    fun aRedirectToTheSameHostIsFollowedAtMostThreeTimes() {
        var calls = 0
        val loop: (URL) -> HttpURLConnection = { calls++; Fake(it, 302, mapOf("Location" to "https://api.example.com/next")) }
        assertEquals(HttpReply.Blocked, Http.fetch(request(), loop))
        assertEquals(Http.MAX_REDIRECTS + 1, calls)
        val once: (URL) -> HttpURLConnection = { if (it.path == "/x") Fake(it, 301, mapOf("Location" to "/moved")) else Fake(it, 200, body = text("ok")) }
        assertEquals("ok", (Http.fetch(request(), once) as HttpReply.Body).text)
    }

    @Test
    fun anAnswerLongerThanTheLimitIsTooBig() {
        val big = ByteArray(1000)
        assertEquals(HttpReply.TooBig, Http.fetch(HttpRequest("https://api.example.com/x", { true }, maxBytes = 999)) { Fake(it, 200, body = big) })
        assertTrue(Http.fetch(HttpRequest("https://api.example.com/x", { true }, maxBytes = 1000)) { Fake(it, 200, body = big) } is HttpReply.Body)
    }

    @Test
    fun aContentTypeTheCallerRefusesIsBlocked() {
        val image = HttpRequest("https://api.example.com/p", { true }, accepts = { it?.startsWith("image/") == true })
        assertEquals(HttpReply.Blocked, Http.fetch(image) { Fake(it, 200, mapOf("Content-Type" to "text/html"), text("<html>")) })
        assertTrue(Http.fetch(image) { Fake(it, 200, mapOf("Content-Type" to "image/png"), text("png")) } is HttpReply.Body)
    }

    @Test
    fun aServerErrorOrALostConnectionIsTriedOnceMoreAndNeverForAPost() {
        var calls = 0
        val flaky: (URL) -> HttpURLConnection = { calls++; if (calls == 1) Fake(it, 503) else Fake(it, 200, body = text("back")) }
        assertEquals("back", (Http.fetch(request(), flaky) as HttpReply.Body).text)
        assertEquals(2, calls)
        assertEquals(1, paused.size)
        calls = 0
        val down: (URL) -> HttpURLConnection = { calls++; throw IOException("no route") }
        assertEquals(HttpReply.Offline, Http.fetch(request(), down))
        assertEquals(2, calls)
        calls = 0
        val post = HttpRequest("https://api.example.com/t", { true }, method = "POST", body = text("a=1"))
        assertEquals(HttpReply.Offline, Http.fetch(post, down))
        assertEquals(1, calls)
        assertEquals(404, (Http.fetch(request()) { Fake(it, 404) } as HttpReply.Status).code)
    }

    @Test
    fun aPostSendsItsBodyAndGivesBackTheErrorPageOfARefusal() {
        var seen: Fake? = null
        val post = HttpRequest("https://api.example.com/t", { true }, method = "POST", body = text("a=1&b=2"), headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"))
        val ok = Http.fetch(post) { Fake(it, 200, body = text("{}")).also { f -> seen = f } }
        assertTrue(ok is HttpReply.Body)
        assertEquals("a=1&b=2", seen!!.written.toString())
        assertEquals("application/x-www-form-urlencoded", seen!!.sent["Content-Type"])
        val refused = Http.fetch(post) { Fake(it, 400, error = text("""{"error":"invalid_grant"}""")) } as HttpReply.Status
        assertEquals(400, refused.code)
        assertEquals("""{"error":"invalid_grant"}""", String(refused.body))
    }

    @Test
    fun aDownloadIsWrittenWholeOrNotAtAll() {
        val target = folder.newFile("a.bin").also { it.delete() }
        val data = ByteArray(5000) { it.toByte() }
        val seen = ArrayList<Long>()
        val ok = Http.download(HttpRequest("https://api.example.com/f", { true }), target, 10_000, { done, _ -> seen += done }) { Fake(it, 200, body = data) }
        assertTrue(ok is HttpReply.Body)
        assertArrayEquals(data, target.readBytes())
        assertEquals(5000L, seen.last())
        assertFalse(java.io.File(target.path + ".part").exists())
        val toomuch = folder.newFile("b.bin").also { it.delete() }
        assertEquals(HttpReply.TooBig, Http.download(HttpRequest("https://api.example.com/f", { true }), toomuch, 4_000) { Fake(it, 200, body = data) })
        assertFalse(toomuch.exists())
        assertFalse(java.io.File(toomuch.path + ".part").exists())
    }

    @Test
    fun theCopyOfTheCacheSurvivesAndTheOldestGoFirst() {
        val cache = HttpCache(folder.newFolder("c"), maxBytes = 25)
        cache.put("https://a/1", HttpReply.Body(ByteArray(10), "e1"))
        Thread.sleep(20)
        cache.put("https://a/2", HttpReply.Body(ByteArray(10), "e2", "m2", "text/x"))
        assertEquals("m2", cache.get("https://a/2")!!.modified)
        Thread.sleep(20)
        cache.put("https://a/3", HttpReply.Body(ByteArray(10), "e3"))
        assertEquals(null, cache.get("https://a/1"))
        assertNotNull(cache.get("https://a/3"))
        cache.clear()
        assertEquals(null, cache.get("https://a/3"))
    }

    @Test
    fun theKeptCopyAnswersA304AndWhenThereIsNoConnection() {
        Http.useCache(HttpCache(folder.newFolder("k")))
        val first = Http.fetchCached(request()) { Fake(it, 200, mapOf("ETag" to "\"v1\""), text("rates")) } as HttpReply.Body
        assertFalse(first.stale)
        // The second ask carries the validator, and the 304 gives the kept body back.
        var seen: Fake? = null
        val again = Http.fetchCached(request()) { Fake(it, 304).also { f -> seen = f } } as HttpReply.Body
        assertEquals("rates", again.text)
        assertEquals("\"v1\"", seen!!.sent["If-None-Match"])
        assertFalse(again.stale)
        // A lost connection, a server error and the phone having no network at all give the kept copy, marked as old.
        val down = Http.fetchCached(request()) { throw IOException("no route") } as HttpReply.Body
        assertTrue(down.stale)
        assertEquals("rates", down.text)
        assertTrue((Http.fetchCached(request()) { Fake(it, 500) } as HttpReply.Body).stale)
        Http.online = { false }
        var asked = false
        assertTrue((Http.fetchCached(request()) { asked = true; Fake(it, 200) } as HttpReply.Body).stale)
        assertFalse(asked)
    }

    @Test
    fun withNothingKeptTheFailureIsTold() {
        Http.useCache(HttpCache(folder.newFolder("n")))
        assertEquals(HttpReply.Offline, Http.fetchCached(request()) { throw IOException("no route") })
        assertEquals(404, (Http.fetchCached(request()) { Fake(it, 404) } as HttpReply.Status).code)
        // An answer with no validator is not kept.
        assertTrue(Http.fetchCached(request()) { Fake(it, 200, body = text("x")) } is HttpReply.Body)
        assertEquals(HttpReply.Offline, Http.fetchCached(request()) { throw IOException("no route") })
    }
}
