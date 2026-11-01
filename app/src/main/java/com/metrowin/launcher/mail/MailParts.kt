package com.metrowin.launcher.mail

import javax.mail.Multipart
import javax.mail.Part

/** Reading the parts of a message: the text, the page, the pictures inside the page and the files that came with it. */
object MailParts {
    private const val INLINE_MAX = 1_500_000

    class Collected(val wantedIndex: Int = -1, val light: Boolean = false) {
        val plain = StringBuilder()
        val html = StringBuilder()
        val inline = LinkedHashMap<String, String>()
        val attachments = ArrayList<MailAttachment>()
        var wanted: Part? = null
    }

    /** The parts of a message, down to the leaves: the text, the page, the pictures inside it and the files. */
    fun walk(part: Part, found: Collected) {
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as Multipart
            for (i in 0 until multipart.count) walk(multipart.getBodyPart(i), found)
            return
        }
        val name = runCatching { javax.mail.internet.MimeUtility.decodeText(part.fileName.orEmpty()) }.getOrDefault("")
        val attached = Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)
        val cid = (part as? javax.mail.internet.MimeBodyPart)?.contentID?.trim('<', '>')
        when {
            part.isMimeType("text/plain") && !attached && name.isEmpty() -> found.plain.append(part.content as String)
            part.isMimeType("text/html") && !attached && name.isEmpty() -> found.html.append(part.content as String)
            part.isMimeType("image/*") && cid != null && !attached && part.size in 0..INLINE_MAX -> if (!found.light) {
                val bytes = part.inputStream.use { it.readBytes() }
                val mime = part.contentType.substringBefore(';').trim()
                found.inline[cid] = "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
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
