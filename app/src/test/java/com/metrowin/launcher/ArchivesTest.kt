package com.metrowin.launcher

import com.metrowin.launcher.files.FileOps
import com.metrowin.launcher.files.Progress
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchivesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun tree(): File {
        val root = tmp.newFolder("src")
        File(root, "a.txt").writeText("alpha")
        File(root, "sub").mkdirs()
        File(root, "sub/b.bin").writeBytes(ByteArray(300_000) { (it % 251).toByte() })
        return root
    }

    @Test
    fun aSevenZipMadeHereComesBackTheSame() {
        val src = tree()
        val archive = File(tmp.root, "out.7z")
        assertTrue(FileOps.sevenZip(listOf(src), archive, Progress()))
        val dest = tmp.newFolder("dest")
        assertEquals(2, FileOps.extract(archive, dest, Progress()))
        assertEquals("alpha", File(dest, "src/a.txt").readText())
        assertTrue(File(src, "sub/b.bin").readBytes().contentEquals(File(dest, "src/sub/b.bin").readBytes()))
    }

    @Test
    fun aSevenZipThatWritesOutsideTheFolderIsRefused() {
        val archive = File(tmp.root, "evil.7z")
        val payload = tmp.newFile("payload.txt").apply { writeText("x") }
        SevenZOutputFile(archive).use { out ->
            out.putArchiveEntry(out.createArchiveEntry(payload, "../evil.txt"))
            out.write("x".toByteArray()); out.closeArchiveEntry()
        }
        val dest = tmp.newFolder("safe")
        val progress = Progress()
        assertEquals(FileOps.DAMAGED, FileOps.extract(archive, dest, progress))
        assertEquals(FileOps.DAMAGED, progress.reason)
        assertFalse(File(dest.parentFile, "evil.txt").exists())
    }

    @Test
    fun aSevenZipThatGrowsPastTheLimitIsStopped() {
        val archive = File(tmp.root, "big.7z")
        assertTrue(FileOps.sevenZip(listOf(tree()), archive, Progress()))
        assertEquals(FileOps.DAMAGED, FileOps.un7z(archive, tmp.newFolder("small"), Progress(), maxBytes = 1000))
    }

    @Test
    fun noiseIsNotAnArchive() {
        val junk = File(tmp.root, "junk.7z").apply { writeBytes(ByteArray(200) { it.toByte() }) }
        assertEquals(FileOps.DAMAGED, FileOps.extract(junk, tmp.newFolder("d1"), Progress()))
        val zipJunk = File(tmp.root, "junk.zip").apply { writeBytes(ByteArray(200) { it.toByte() }) }
        assertEquals(FileOps.DAMAGED, FileOps.extract(zipJunk, tmp.newFolder("d2"), Progress()))
    }

    @Test
    fun aPasswordProtectedZipSaysSo() {
        val zip = File(tmp.root, "secret.zip")
        ZipOutputStream(zip.outputStream()).use { z -> z.putNextEntry(ZipEntry("a.txt")); z.write("hello hello hello".toByteArray()); z.closeEntry() }
        // The flag of encryption is the first bit of the general purpose field, in the local header and in the central one.
        val bytes = zip.readBytes()
        for (i in 0..bytes.size - 10) {
            if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() && bytes[i + 2] == 3.toByte() && bytes[i + 3] == 4.toByte()) bytes[i + 6] = (bytes[i + 6].toInt() or 1).toByte()
            if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() && bytes[i + 2] == 1.toByte() && bytes[i + 3] == 2.toByte()) bytes[i + 8] = (bytes[i + 8].toInt() or 1).toByte()
        }
        zip.writeBytes(bytes)
        val progress = Progress()
        assertEquals(FileOps.ENCRYPTED, FileOps.extract(zip, tmp.newFolder("d"), progress))
        assertEquals(FileOps.ENCRYPTED, progress.reason)
    }

    @Test
    fun aPasswordProtectedSevenZipSaysSo() {
        val archive = File(tmp.root, "locked.7z")
        SevenZOutputFile(archive, "pw".toCharArray()).use { out ->
            val f = tmp.newFile("in.txt").apply { writeText("secret") }
            out.putArchiveEntry(out.createArchiveEntry(f, "in.txt")); out.write("secret".toByteArray()); out.closeArchiveEntry()
        }
        assertEquals(FileOps.ENCRYPTED, FileOps.extract(archive, tmp.newFolder("d"), Progress()))
    }

    @Test
    fun theFileManagerKnowsWhichArchivesItOpens() {
        assertTrue(FileOps.isArchive(File("a.zip"))); assertTrue(FileOps.isArchive(File("a.7z"))); assertTrue(FileOps.isArchive(File("a.CBZ")))
        assertFalse("an apk is installed, not extracted", FileOps.isArchive(File("a.apk")))
        assertFalse("a rar is left to another app", FileOps.isArchive(File("a.rar")))
    }
}
