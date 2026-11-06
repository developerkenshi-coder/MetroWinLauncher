package com.metrowin.launcher.files

import java.io.File
import java.util.Locale

/** The groups the file manager sorts files into. */
enum class Category { Documents, Photos, Videos, Audio, Apk, Other }

/** The kinds of document; [Other] is a document that is none of the others. */
enum class DocKind { Pdf, Word, Excel, PowerPoint, Text, Ebook, Other }

/** What a file is, and which group and kind of document it belongs to. */
data class Kind(val category: Category, val doc: DocKind? = null)

/**
 * Tells what a file is from its extension and, when the extension says nothing (none, or one nobody knows), from the first bytes of
 * the file. Nothing here touches the disk except [sniff] and [scan], so the rules are tested on bytes.
 */
object FileKinds {
    /** How many bytes of the start of a file are enough to tell its kind: the names of the parts of an Office file are in there. */
    const val HEADER = 64 * 1024

    private val DOCS = mapOf(
        "pdf" to DocKind.Pdf,
        "doc" to DocKind.Word, "docx" to DocKind.Word, "dot" to DocKind.Word, "dotx" to DocKind.Word, "docm" to DocKind.Word, "rtf" to DocKind.Word,
        "xls" to DocKind.Excel, "xlsx" to DocKind.Excel, "xlsm" to DocKind.Excel, "xlt" to DocKind.Excel, "xltx" to DocKind.Excel,
        "ppt" to DocKind.PowerPoint, "pptx" to DocKind.PowerPoint, "pptm" to DocKind.PowerPoint, "pps" to DocKind.PowerPoint, "ppsx" to DocKind.PowerPoint,
        "txt" to DocKind.Text, "md" to DocKind.Text, "csv" to DocKind.Text, "log" to DocKind.Text, "json" to DocKind.Text, "xml" to DocKind.Text,
        "epub" to DocKind.Ebook, "mobi" to DocKind.Ebook, "azw" to DocKind.Ebook, "azw3" to DocKind.Ebook, "fb2" to DocKind.Ebook,
        "odt" to DocKind.Other, "ods" to DocKind.Other, "odp" to DocKind.Other, "djvu" to DocKind.Other, "pages" to DocKind.Other, "numbers" to DocKind.Other, "key" to DocKind.Other,
    )
    private val PHOTOS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff", "dng", "raw", "svg", "ico")
    private val VIDEOS = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "3g2", "wmv", "flv", "mpg", "mpeg", "ts")
    private val AUDIO = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "wma", "mid", "midi", "mka", "awb")

    /** The kind of a file called [name], from its extension alone; null when the extension is none or unknown. */
    fun byName(name: String): Kind? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isEmpty() || name.endsWith(".")) return null
        return when {
            ext == "apk" || ext == "apks" || ext == "xapk" -> Kind(Category.Apk)
            ext in DOCS -> Kind(Category.Documents, DOCS.getValue(ext))
            ext in PHOTOS -> Kind(Category.Photos)
            ext in VIDEOS -> Kind(Category.Videos)
            ext in AUDIO -> Kind(Category.Audio)
            else -> null
        }
    }

    /** The kind of a file from its first bytes ([header]); null when they say nothing. */
    fun byHeader(header: ByteArray): Kind? {
        val n = header.size
        fun at(i: Int, text: String) = n >= i + text.length && text.indices.all { header[i + it] == text[it].code.toByte() }
        fun atBytes(i: Int, vararg b: Int) = n >= i + b.size && b.indices.all { header[i + it] == b[it].toByte() }
        return when {
            at(0, "%PDF") -> Kind(Category.Documents, DocKind.Pdf)
            at(0, "{\\rtf") -> Kind(Category.Documents, DocKind.Word)
            // The old Office files are all one container; which of the three it is shows in the names of its streams.
            atBytes(0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) -> Kind(Category.Documents, oleKind(header))
            at(0, "PK\u0003\u0004") -> zipKind(header)
            atBytes(0, 0xFF, 0xD8, 0xFF) || atBytes(0, 0x89, 0x50, 0x4E, 0x47) || at(0, "GIF8") || (at(0, "RIFF") && at(8, "WEBP")) -> Kind(Category.Photos)
            at(0, "ID3") || at(0, "OggS") || at(0, "fLaC") || at(0, "#!AMR") || (at(0, "RIFF") && at(8, "WAVE")) -> Kind(Category.Audio)
            atBytes(0, 0x1A, 0x45, 0xDF, 0xA3) || (at(0, "RIFF") && at(8, "AVI ")) -> Kind(Category.Videos)
            at(4, "ftyp") -> {
                val brand = if (n >= 12) String(header, 8, 4, Charsets.ISO_8859_1) else ""
                when {
                    brand.startsWith("M4A") || brand.startsWith("M4B") -> Kind(Category.Audio)
                    brand in setOf("heic", "heix", "hevc", "mif1", "msf1", "avif") -> Kind(Category.Photos)
                    else -> Kind(Category.Videos)
                }
            }
            else -> null
        }
    }

    /** The kind of [name] when [header] is read lazily: the extension first, the bytes only when it does not tell. Everything else is [Category.Other]. */
    fun kindOf(name: String, header: () -> ByteArray? = { null }): Kind =
        byName(name) ?: header()?.let(::byHeader) ?: Kind(Category.Other)

    /** True when the extension tells nothing, so [kindOf] would read the file. */
    fun needsHeader(name: String): Boolean = byName(name) == null

    private fun contains(header: ByteArray, text: String, utf16: Boolean = false): Boolean {
        val needle = if (utf16) text.toByteArray(Charsets.UTF_16LE) else text.toByteArray(Charsets.ISO_8859_1)
        outer@ for (i in 0..header.size - needle.size) {
            for (j in needle.indices) if (header[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    private fun oleKind(header: ByteArray): DocKind = when {
        contains(header, "WordDocument", utf16 = true) -> DocKind.Word
        contains(header, "PowerPoint Document", utf16 = true) -> DocKind.PowerPoint
        contains(header, "Workbook", utf16 = true) || contains(header, "Book", utf16 = true) -> DocKind.Excel
        // shortcut: the names of the streams of a big old file may lie past the first HEADER bytes; it is then a document of no known kind.
        else -> DocKind.Other
    }

    private fun zipKind(header: ByteArray): Kind? = when {
        contains(header, "mimetypeapplication/epub+zip") -> Kind(Category.Documents, DocKind.Ebook)
        contains(header, "word/") -> Kind(Category.Documents, DocKind.Word)
        contains(header, "xl/") -> Kind(Category.Documents, DocKind.Excel)
        contains(header, "ppt/") -> Kind(Category.Documents, DocKind.PowerPoint)
        contains(header, "AndroidManifest.xml") -> Kind(Category.Apk)
        // A plain zip is no document, photo or the like.
        else -> null
    }

    /** The first bytes of [file], or null when it is empty or cannot be read. */
    fun sniff(file: File): ByteArray? = runCatching {
        file.inputStream().use { input ->
            val buffer = ByteArray(HEADER)
            val read = input.read(buffer)
            if (read <= 0) null else buffer.copyOf(read)
        }
    }.getOrNull()

    /**
     * The files under [root] of [category] (and of [doc] when it is a document kind), at most [limit], newest first on the way out of the
     * caller. [active] is asked now and then: when it says false the walk stops with what it has. At most [MAX_SNIFFED] files are opened
     * to read their first bytes, so a folder of files with no extension cannot keep the scan for long.
     */
    fun scan(root: File, category: Category, doc: DocKind?, showHidden: Boolean, limit: Int = 500, active: () -> Boolean = { true }): List<Entry> {
        val found = ArrayList<Entry>()
        val queue = ArrayDeque<File>().apply { add(root) }
        var sniffed = 0
        while (queue.isNotEmpty() && found.size < limit && active()) {
            val dir = queue.removeFirst()
            for (child in dir.listFiles().orEmpty()) {
                if (found.size >= limit || !active()) break
                if (!showHidden && child.name.startsWith(".")) continue
                if (child.isDirectory) { if (!FileOps.isLink(child)) queue.add(child); continue }
                val byName = byName(child.name)
                val kind = byName ?: if (sniffed < MAX_SNIFFED && child.length() > 0) { sniffed++; sniff(child)?.let(::byHeader) } else null
                val k = kind ?: Kind(Category.Other)
                if (k.category == category && (doc == null || k.doc == doc)) found += Entry(child, false, child.length(), child.lastModified(), child.name.startsWith("."))
            }
        }
        return found
    }

    const val MAX_SNIFFED = 2000
}
