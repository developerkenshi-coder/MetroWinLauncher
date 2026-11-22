package com.metrowin.launcher.news

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.metrowin.launcher.data.Http
import com.metrowin.launcher.data.HttpReply
import com.metrowin.launcher.data.HttpRequest
import java.io.File
import java.security.MessageDigest

/** Where the headlines get their pictures from: the real one fetches them, a test gives its own. */
fun interface NewsPictures {
    /** Blocking. The picture at [url] no wider than [maxWidth] pixels, or null. */
    fun load(url: String, maxWidth: Int): Bitmap?
}

/**
 * The pictures of the news: fetched once (over https only, from public hosts only), kept in the cache folder of the app (at most
 * [MAX_FILES] files, about [MAX_BYTES] bytes, the oldest go first) and decoded at the size they are shown at. Only the news app asks for
 * them, and only when its switch for pictures is on, because each picture is a request to the site of the article's picture.
 */
class NewsImages(context: Context) : NewsPictures {
    private val dir = File(context.cacheDir, "news-images").apply { mkdirs() }

    /** Blocking, call it off the main thread. The picture at [url] no wider than [maxWidth] pixels, or null when it cannot be had. */
    override fun load(url: String, maxWidth: Int): Bitmap? {
        val cacheKey = key(url) + ":" + maxWidth
        memory.get(cacheKey)?.let { return it }
        val file = File(dir, key(url))
        // One download per address at a time: the hero and a thumbnail (or two pages) that ask for the same picture wait for the first.
        val ready = synchronized(locks[(file.name.hashCode() and Int.MAX_VALUE) % locks.size]) { file.exists() || download(url, file) }
        if (!ready) return null
        file.setLastModified(System.currentTimeMillis())
        val bitmap = decode(file, maxWidth) ?: run { file.delete(); return null }
        memory.put(cacheKey, bitmap)
        return bitmap
    }

    private fun download(address: String, target: File): Boolean {
        // A picture comes from a public https address only, also after a redirect: a feed must not make the phone reach into the local network.
        val request = HttpRequest(address, { NewsHttp.isPublicHost(it.host) }, accepts = { it?.startsWith("image/") == true })
        if (Http.download(request, target, MAX_PICTURE.toLong()) !is HttpReply.Body) return false
        trim()
        return true
    }

    private fun decode(file: File, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, maxWidth) })
    }

    private fun trim() {
        val files = dir.listFiles { f -> !f.name.endsWith(".part") }.orEmpty().sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        var count = files.size
        for (f in files) {
            if (count <= MAX_FILES && bytes <= MAX_BYTES) break
            bytes -= f.length()
            count--
            f.delete()
        }
    }

    /** What is kept of the pictures is thrown away. */
    fun clear() {
        dir.listFiles().orEmpty().forEach { it.delete() }
        memory.evictAll()
    }

    companion object {
        const val MAX_FILES = 150
        const val MAX_BYTES = 40L * 1024 * 1024
        private const val MAX_PICTURE = 3 * 1024 * 1024

        private val locks = Array(16) { Any() }

        private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        /** The name of the file for [url]: the digest of the address, so any address is a safe name. */
        fun key(url: String): String = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

        /** The power of two to shrink a picture [width] wide by so that it is at least [maxWidth] wide (never smaller than what is shown). */
        fun sampleSize(width: Int, maxWidth: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= maxWidth) sample *= 2
            return sample
        }
    }
}
