package com.metrowin.launcher.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Reference pictures of the screens: a capture is shrunk to a quarter and compared with the one saved in `app/src/test/reference`.
 * A few pixels may differ (the edges of the letters are not the same on every machine); a screen that changed shows up as many.
 * After a change of look on purpose, run the tests with the environment variable `RECORD_REFERENCES=1` to write the new ones, and look at them before committing.
 */
object ReferenceImage {
    private const val SHRINK = 4
    private const val CHANNEL_STEP = 32
    private const val DIFFERENT_SHARE = 0.02f

    fun check(name: String, shot: Bitmap) {
        val small = Bitmap.createScaledBitmap(shot, shot.width / SHRINK, shot.height / SHRINK, true)
        val file = File("src/test/reference/$name.png")
        if (System.getenv("RECORD_REFERENCES") == "1") {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        assertTrue("no reference picture for $name: run the tests with RECORD_REFERENCES=1 once", file.exists())
        val reference = BitmapFactory.decodeFile(file.path)
        assertEquals("$name: size of the picture", reference.width to reference.height, small.width to small.height)
        val share = differentShare(reference, small)
        assertTrue("$name: ${(share * 100).toInt()}% of the picture differs from its reference (more than ${(DIFFERENT_SHARE * 100).toInt()}%)", share <= DIFFERENT_SHARE)
    }

    /** The share of pixels where one of the channels is further than [CHANNEL_STEP] steps from the reference. */
    internal fun differentShare(a: Bitmap, b: Bitmap): Float {
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        val different = pa.indices.count { i ->
            val x = pa[i]
            val y = pb[i]
            max(max(abs((x shr 16 and 0xFF) - (y shr 16 and 0xFF)), abs((x shr 8 and 0xFF) - (y shr 8 and 0xFF))), abs((x and 0xFF) - (y and 0xFF))) > CHANNEL_STEP
        }
        return different.toFloat() / pa.size
    }
}
