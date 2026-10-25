package com.metrowin.launcher

import com.metrowin.launcher.data.readAtMost
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedReadTest {
    @Test
    fun aShortStreamIsReadWhole() {
        val data = ByteArray(20_000) { it.toByte() }
        assertArrayEquals(data, ByteArrayInputStream(data).readAtMost(30_000))
        assertArrayEquals(data, ByteArrayInputStream(data).readAtMost(20_000))
        assertArrayEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAtMost(10))
    }

    @Test
    fun aLongStreamIsRefused() {
        assertNull(ByteArrayInputStream(ByteArray(20_001)).readAtMost(20_000))
    }

    @Test
    fun readingStopsAtTheLimitInsteadOfTakingTheWholeFile() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { served += it }
        }
        assertNull(endless.readAtMost(100_000))
        assert(served < 200_000) { "read $served bytes" }
    }
}
