package com.metrowin.launcher.mail

import javax.mail.Multipart
import javax.mail.Part

/** Reading the parts of a message: the text, the page, the pictures inside the page and the files that came with it. */
object MailParts {
    private const val INLINE_MAX = 1_500_000

    /** All the pictures inside one page together: more stay out (the page shows its own broken-picture mark), so a message cannot fill the memory. */
    private const val INLINE_TOTAL_MAX = 6_000_000

    class Collected(val wantedIndex: Int = -1, val light: Boolean = false) {
        val plain = StringBuilder()
        val html = StringBuilder()
        val inline = LinkedHashMap<String, String>()
        val attachments = ArrayList<MailAttachment>()
        var wanted: Part? = null
        var inlineBytes = 0
    }

    /** The most of a part read for a line of the list ([Collected.light]): one block of the server, not a newsletter of 300 KB. */
    private const val LIGHT_BYTES = 16 * 1024

    /** The start of a text part, as text: at most [LIGHT_BYTES] are fetched from the server, in the charset the part says. */
    private fun startOf(part: Part): String {
        val charset = runCatching { javax.mail.internet.MimeUtility.javaCharset(javax.mail.internet.ContentType(part.contentType).getParameter("charset")) }.getOrNull()
        val buffer = ByteArray(LIGHT_BYTES)
        var read = 0
        part.inputStream.use { stream ->
            while (read < buffer.size) {
                val n = stream.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
        }
        return String(buffer, 0, read, runCatching { charset(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8))
    }

    private fun nameOf(part: Part) = runCatching { javax.mail.internet.MimeUtility.decodeText(part.fileName.orEmpty()) }.getOrDefault("")

    private fun cidOf(part: Part) = (part as? javax.mail.internet.MimeBodyPart)?.contentID?.trim('<', '>')

    private fun isText(part: Part, name: String, attached: Boolean) =
        (part.isMimeType("text/plain") || part.isMimeType("text/html")) && !attached && name.isEmpty()

    private fun isInlinePicture(part: Part, attached: Boolean) = part.isMimeType("image/*") && cidOf(part) != null && !attached && part.size in 0..INLINE_MAX

    /** Whether a leaf of a message is a file that came with it: neither the text, nor a picture the page shows. Reads no content (the structure of the message is enough). */
    private fun isFile(part: Part): Boolean {
        val attached = Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)
        return !isText(part, nameOf(part), attached) && !isInlinePicture(part, attached)
    }

    /** Whether the message has files with it, from its structure alone: "multipart/mixed" is no proof (a message with pictures in its page is "related", a signed one "signed"). */
    fun hasFiles(part: Part): Boolean {
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as Multipart
            return (0 until multipart.count).any { hasFiles(multipart.getBodyPart(it)) }
        }
        return isFile(part)
    }

    /** The parts of a message, down to the leaves: the text, the page, the pictures inside it and the files. */
    fun walk(part: Part, found: Collected) {
        // A line of the list needs the first text there is: once it is in hand, the rest of the message (the page that repeats it, the files) is not fetched.
        if (found.light && (found.plain.isNotEmpty() || found.html.isNotEmpty())) return
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as Multipart
            for (i in 0 until multipart.count) walk(multipart.getBodyPart(i), found)
            return
        }
        val name = nameOf(part)
        val attached = Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)
        val cid = cidOf(part)
        when {
            part.isMimeType("text/plain") && !attached && name.isEmpty() -> found.plain.append(if (found.light) startOf(part) else part.content as String)
            part.isMimeType("text/html") && !attached && name.isEmpty() -> found.html.append(if (found.light) startOf(part) else part.content as String)
            isInlinePicture(part, attached) && (found.light || found.inlineBytes + part.size <= INLINE_TOTAL_MAX) -> if (!found.light) {
                val bytes = part.inputStream.use { it.readBytes() }
                found.inlineBytes += bytes.size
                val mime = part.contentType.substringBefore(';').trim()
                found.inline[cid!!] = "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
            else -> {
                val index = found.attachments.size
                val mime = part.contentType.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
                found.attachments += MailAttachment(name.ifEmpty { "attachment-${index + 1}" }, part.size.toLong(), mime, index)
                if (index == found.wantedIndex) found.wanted = part
            }
        }
    }

    fun stripTags(html: String) = html.replace(Regex("(?is)<(script|style).*?</\\1>"), "").replace(Regex("(?i)<br\\s*/?>|</p>"), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")

}
