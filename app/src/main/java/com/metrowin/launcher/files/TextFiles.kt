package com.metrowin.launcher.files

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** A text file read for the editor: its text with "\n" line ends, and what is needed to write it back as it was. */
class TextDoc(val text: String, val charset: Charset, val crlf: Boolean, val bom: Boolean)

/** Reading and writing text files without losing what the editor does not show: the code, the line ends, the mark at the start. */
object TextFiles {
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** The charsets the editor offers. */
    val CHARSETS: List<Charset> = listOf(Charsets.UTF_8, Charsets.ISO_8859_1, Charsets.UTF_16LE, Charsets.UTF_16BE)

    /** [bytes] as a text: a mark at the start tells UTF-8 or UTF-16; else UTF-8 when the bytes are valid, else Latin-1. [charset] forces one. */
    fun decode(bytes: ByteArray, charset: Charset? = null): TextDoc {
        var offset = 0
        var cs = charset
        var bom = false
        when {
            bytes.size >= 3 && bytes.copyOfRange(0, 3).contentEquals(UTF8_BOM) -> { offset = 3; bom = true; if (cs == null) cs = Charsets.UTF_8 }
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> { offset = 2; bom = true; if (cs == null) cs = Charsets.UTF_16LE }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> { offset = 2; bom = true; if (cs == null) cs = Charsets.UTF_16BE }
        }
        val body = ByteBuffer.wrap(bytes, offset, bytes.size - offset)
        val chosen = cs ?: if (valid(body.duplicate())) Charsets.UTF_8 else Charsets.ISO_8859_1
        if (bom && charset != null && charset != cs) bom = false
        val raw = chosen.decode(body).toString()
        val crlf = raw.contains("\r\n")
        return TextDoc(if (crlf) raw.replace("\r\n", "\n") else raw, chosen, crlf, bom)
    }

    private fun valid(buffer: ByteBuffer): Boolean =
        runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer); true }.getOrDefault(false)

    fun read(file: File, charset: Charset? = null): TextDoc = decode(file.readBytes(), charset)

    /** [doc] as bytes: line ends as they were, the mark at the start when there was one. */
    fun encode(text: String, charset: Charset, crlf: Boolean, bom: Boolean): ByteArray {
        val body = (if (crlf) text.replace("\n", "\r\n") else text).toByteArray(charset)
        val mark = when {
            !bom -> ByteArray(0)
            charset == Charsets.UTF_8 -> UTF8_BOM
            charset == Charsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            charset == Charsets.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            else -> ByteArray(0)
        }
        return mark + body
    }

    /** Writes [bytes] beside [file] and moves them over it, so a failure leaves the old file whole. False when it could not be written. */
    fun writeAtomic(file: File, bytes: ByteArray): Boolean {
        val part = File(file.parentFile, ".part-${System.nanoTime()}")
        return try {
            part.writeBytes(bytes)
            java.nio.file.Files.move(part.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            false
        } finally {
            part.delete()
        }
    }
}
