package com.metrowin.launcher.messages

/**
 * The two multimedia-message PDUs the launcher has to read as the messaging app: the notification the network pushes (what to fetch and
 * from where) and the message itself once fetched. Only what is needed to store and show a message is decoded (WAP-209 / OMA MMS 1.2).
 * Anything the reader does not understand ends the parse with null: a message is never stored half read.
 */
internal object MmsPdu {
    class Notification(val transactionId: String, val location: String, val from: String?, val size: Long, val subject: String?)

    class Part(val type: String, val name: String?, val contentId: String?, val data: ByteArray, val charset: Int)

    class Retrieved(val from: String?, val to: List<String>, val subject: String?, val messageId: String?, val date: Long, val parts: List<Part>)

    private const val MSG_TYPE = 0x8C
    private const val NOTIFICATION_IND = 130
    private const val RETRIEVE_CONF = 132

    private class Reader(val b: ByteArray, var p: Int = 0, val end: Int = b.size) {
        fun more() = p < end
        fun u8(): Int = if (p < end) b[p++].toInt() and 0xFF else throw IllegalArgumentException("end")
        fun peek(): Int = if (p < end) b[p].toInt() and 0xFF else throw IllegalArgumentException("end")
        fun uintvar(): Int {
            var v = 0
            repeat(5) {
                val c = u8()
                v = (v shl 7) or (c and 0x7F)
                if (c and 0x80 == 0) return v
            }
            throw IllegalArgumentException("uintvar")
        }

        fun bytes(n: Int): ByteArray {
            require(n >= 0 && n <= end - p) { "length" }
            return b.copyOfRange(p, p + n).also { p += n }
        }

        fun text(): String {
            if (peek() == 0x7F || peek() == 0x22) p++
            val start = p
            while (u8() != 0) Unit
            return String(b, start, p - 1 - start, Charsets.UTF_8)
        }
    }

    /** A WSP value: a short integer, a text, or the bytes of a length-framed value. */
    private sealed interface Value
    private class Short(val n: Int) : Value
    private class Str(val s: String) : Value
    private class Framed(val b: ByteArray) : Value

    private fun Reader.value(): Value {
        val c = peek()
        return when {
            c >= 0x80 -> { p++; Short(c and 0x7F) }
            c <= 30 -> { p++; Framed(bytes(c)) }
            c == 31 -> { p++; Framed(bytes(uintvar())) }
            else -> Str(text())
        }
    }

    private fun Value.str(): String? = when (this) {
        is Str -> s
        is Framed -> runCatching { Reader(b).let { if (it.peek() >= 0x80) it.p++; it.text() } }.getOrNull()
        else -> null
    }

    /** An address ("From" and "To"): "address-present" then the encoded string, possibly after its charset; the token "insert-address" has none. */
    private fun Value.address(): String? = when (this) {
        is Framed -> runCatching { Reader(b).let { if (it.u8() == 0x80) it.value().str()?.removeSuffix("/TYPE=PLMN") else null } }.getOrNull()
        is Str -> s.removeSuffix("/TYPE=PLMN")
        else -> null
    }

    private fun Value.number(): Long = when (this) {
        is Short -> n.toLong()
        is Framed -> b.fold(0L) { acc, x -> (acc shl 8) or (x.toLong() and 0xFF) }
        else -> 0
    }

    fun notification(pdu: ByteArray): Notification? = runCatching {
        val r = Reader(pdu)
        var type = -1
        var id: String? = null
        var location: String? = null
        var from: String? = null
        var size = 0L
        var subject: String? = null
        while (r.more()) {
            val field = r.u8()
            if (field < 0x80) { r.p--; r.text(); r.value(); continue }
            val v = r.value()
            when (field) {
                MSG_TYPE -> type = (v as Short).n or 0x80
                0x98 -> id = v.str()
                0x83 -> location = v.str()
                0x89 -> from = v.address()
                0x8E -> size = v.number()
                0x96 -> subject = v.str()
            }
        }
        if (type != NOTIFICATION_IND || id == null || location.isNullOrBlank()) null else Notification(id, location, from, size, subject)
    }.getOrNull()

    fun retrieved(pdu: ByteArray): Retrieved? = runCatching {
        val r = Reader(pdu)
        var type = -1
        var from: String? = null
        val to = ArrayList<String>()
        var subject: String? = null
        var id: String? = null
        var date = 0L
        while (true) {
            val field = r.u8()
            if (field < 0x80) { r.p--; r.text(); r.value(); continue }
            if (field == 0x84) { r.value(); break } // content type: the body follows
            val v = r.value()
            when (field) {
                MSG_TYPE -> type = (v as Short).n or 0x80
                0x89 -> from = v.address()
                0x97 -> v.address()?.let(to::add)
                0x96 -> subject = v.str()
                0x8B -> id = v.str()
                0x85 -> date = v.number()
            }
        }
        if (type != RETRIEVE_CONF) return@runCatching null
        val count = r.uintvar()
        val parts = buildList {
            repeat(count) {
                val headers = r.uintvar()
                val size = r.uintvar()
                val h = Reader(r.bytes(headers))
                val data = r.bytes(size)
                var charset = 0
                val v = h.value()
                val type = contentType(v) { charset = it }
                var name: String? = null
                var cid: String? = null
                while (h.more()) {
                    val f = h.u8()
                    if (f < 0x80) { h.p--; h.text(); h.value(); continue }
                    val x = h.value()
                    when (f) { 0xC0 -> cid = x.str(); 0x8E -> name = x.str() }
                }
                add(Part(type, name, cid, data, charset))
            }
        }
        Retrieved(from, to, subject, id, date, parts)
    }.getOrNull()

    /** The media type of a part, and through [charset] its character set (an IANA number) when it says one. */
    private fun contentType(v: Value, charset: (Int) -> Unit): String = when (v) {
        is Short -> TYPES[v.n] ?: "application/octet-stream"
        is Str -> v.s.lowercase()
        is Framed -> {
            val r = Reader(v.b)
            val head = r.value()
            while (r.more()) {
                val param = r.u8()
                if (param < 0x80) { r.p--; r.text(); r.value(); continue }
                val x = r.value()
                if (param == 0x81) charset(x.number().toInt())
            }
            contentType(head, charset)
        }
    }

    // The well-known media types of WSP (WAP-230 table 40) that a message carries; the rest is sent as text or read as plain data.
    private val TYPES = mapOf(
        0x01 to "text/*", 0x02 to "text/html", 0x03 to "text/plain", 0x06 to "text/x-vCalendar", 0x07 to "text/x-vCard",
        0x1C to "image/*", 0x1D to "image/gif", 0x1E to "image/jpeg", 0x1F to "image/tiff", 0x20 to "image/png", 0x21 to "image/vnd.wap.wbmp",
        0x23 to "application/vnd.wap.multipart.mixed", 0x33 to "application/vnd.wap.multipart.related", 0x4F to "audio/*", 0x50 to "video/*",
    )
}
