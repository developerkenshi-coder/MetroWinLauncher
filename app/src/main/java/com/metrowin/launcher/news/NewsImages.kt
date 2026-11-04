package com.metrowin.launcher.news

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The pictures of the news: fetched once (over https only), kept in the cache folder of the app (at most [MAX_FILES] files, about
 * [MAX_BYTES] bytes, the oldest go first) and decoded at the size they are shown at. Only the news app asks for them, and only when
 * its switch for pictures is on, because each picture is a request to the site of the article's picture.
 */
/** Where the headlines get their pictures from: the real one fetches them, a test gives its own. */
fun interface NewsPictures {
    /** Blocking. The picture at [url] no wider than [maxWidth] pixels, or null. */
    fun load(url: String, maxWidth: Int): Bitmap?
}

class NewsImages(context: Context) : NewsPictures {
    private val dir = File(context.cacheDir, "news-images").apply { mkdirs() }

    /** Blocking, call it off the main thread. The picture at [url] no wider than [maxWidth] pixels, or null when it cannot be had. */
    override fun load(url: String, maxWidth: Int): Bitmap? {
        val cacheKey = key(url) + ":" + maxWidth
        memory.get(cacheKey)?.let { return it }
        val file = File(dir, key(url))
        if (!file.exists() && !download(url, file)) return null
        file.setLastModified(System.currentTimeMillis())
        val bitmap = decode(file, maxWidth) ?: run { file.delete(); return null }
        memory.put(cacheKey, bitmap)
        return bitmap
    }

    private fun download(address: String, target: File): Boolean = runCatching {
        var url = URL(address)
        repeat(REDIRECTS + 1) {
            require(url.protocol == "https")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MetroWinLauncher")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    url = URL(url, connection.getHeaderField("Location") ?: return false)
                } else {
                    if (code != HttpURLConnection.HTTP_OK || connection.contentType?.startsWith("image/") != true) return false
                    val temporary = File(target.parentFile, target.name + ".part")
                    var total = 0
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_PICTURE) { temporary.delete(); return false }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    val moved = temporary.renameTo(target)
                    if (moved) trim()
                    return moved
                }
            } finally {
                connection.disconnect()
            }
        }
        false
    }.getOrDefault(false)

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
        private const val REDIRECTS = 3
        private const val TIMEOUT_MS = 8_000

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
