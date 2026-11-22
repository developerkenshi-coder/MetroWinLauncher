package com.metrowin.launcher.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** The whole stream, or null when it is longer than [limit] bytes; reading stops at the limit, not at the end of a huge file. */
fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) return out.toByteArray()
        if (out.size() + count > limit) return null
        out.write(buffer, 0, count)
    }
}
