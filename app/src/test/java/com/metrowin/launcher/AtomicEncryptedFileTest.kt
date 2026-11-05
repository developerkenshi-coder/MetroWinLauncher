package com.metrowin.launcher

import com.metrowin.launcher.data.AtomicEncryptedFile
import com.metrowin.launcher.notes.NoteCrypt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicEncryptedFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private object Plain : NoteCrypt {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(text: String) = text.removePrefix("enc:").takeIf { text.startsWith("enc:") }
    }

    private object Broken : NoteCrypt {
        override fun encrypt(plain: String) = ""
        override fun decrypt(text: String): String? = null
    }

    private fun target() = File(folder.root, "data.dat")

    @Test
    fun writesAndReadsBack() {
        val file = AtomicEncryptedFile(target(), Plain)
        assertTrue(file.write("one"))
        assertTrue(file.write("two"))
        assertEquals("two", file.read())
        assertEquals("enc:two", target().readText())
        assertFalse(File(folder.root, "data.dat.tmp").exists())
    }

    @Test
    fun existingFileOfTheOldFormatStaysReadable() {
        target().writeText("enc:kept")
        assertEquals("kept", AtomicEncryptedFile(target(), Plain).read())
    }

    @Test
    fun failureInTheMiddleLeavesTheOldFileWhole() {
        val file = AtomicEncryptedFile(target(), Plain)
        assertTrue(file.write("old"))
        // A folder where the temporary file goes: the write fails after the old file was read, before anything replaces it.
        assertTrue(File(folder.root, "data.dat.tmp").mkdir())
        assertFalse(file.write("new"))
        assertEquals("old", file.read())
    }

    @Test
    fun keystoreThatCannotEncryptWritesNothing() {
        target().writeText("enc:old")
        assertFalse(AtomicEncryptedFile(target(), Broken).write("new"))
        assertEquals("enc:old", target().readText())
    }

    @Test
    fun unreadableFileIsPutAsideNotLost() {
        target().writeText("garbage")
        val file = AtomicEncryptedFile(target(), Plain)
        assertNull(file.read())
        file.putAside("data.unreadable")
        assertFalse(target().exists())
        assertEquals("garbage", File(folder.root, "data.unreadable").readText())
    }
}
