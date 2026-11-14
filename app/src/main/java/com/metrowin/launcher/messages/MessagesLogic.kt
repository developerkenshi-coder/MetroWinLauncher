package com.metrowin.launcher.messages

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The small rules of the messages app that need no screen: codes, links, days, grouping, the size of a text in parts. */
internal object MessagesLogic {
    private val CODE = Regex("(?<![\\d+])\\d{4,8}(?![\\d])")
    private val WORDS = Regex("code|codice|otp|pin|passcode|password|verif|c[oó]digo|kod|chiave|token|sicurezza|security", RegexOption.IGNORE_CASE)

    /** The one-time code in [text] (4 to 8 digits next to a word that says what it is), or null. A long message is not a code message. */
    fun otpCode(text: String): String? =
        if (text.length > 240 || !WORDS.containsMatchIn(text)) null else CODE.findAll(text).map { it.value }.firstOrNull()

    class Link(val start: Int, val end: Int, val url: String)

    private val LINK = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>\"]+|[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+|(?<![\\w])\\+?\\d[\\d ().-]{5,}\\d")

    /** The web addresses, e-mail addresses and phone numbers of [text], with the address each opens (a dot or comma that ends a sentence is not part of it). */
    fun links(text: String): List<Link> = LINK.findAll(text).mapNotNull { m ->
        val value = m.value.trimEnd('.', ',', ')', ';', ':', '!', '?')
        if (value.length < 4) return@mapNotNull null
        val url = when {
            value.contains('@') && !value.contains("://") -> "mailto:$value"
            value.startsWith("http", ignoreCase = true) -> value
            value.startsWith("www.", ignoreCase = true) -> "https://$value"
            else -> "tel:" + value.filter { it.isDigit() || it == '+' }.also { if (it.count(Char::isDigit) < 6) return@mapNotNull null }
        }
        Link(m.range.first, m.range.first + value.length, url)
    }.toList()

    fun day(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** True when [message] continues the run of bubbles before it: the same side, within [GROUP_MS] (the sender is not repeated and the gap is small). */
    fun continues(previous: Sms?, message: Sms, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        previous != null && previous.outgoing == message.outgoing && message.date - previous.date in 0..GROUP_MS && day(previous.date, zone) == day(message.date, zone)

    /** How many SMS a text takes, and how many characters are left in the one it ends (7-bit text: 160 or 153 each; with any other character, 70 or 67). */
    fun parts(text: String): Pair<Int, Int> {
        val gsm = text.all { it.code < 0x80 || it in "£¥èéùìòÇØøÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ¤¡ÄÖÑÜ§¿äöñüà€" }
        val (single, multi) = if (gsm) 160 to 153 else 70 to 67
        if (text.length <= single) return 1 to single - text.length
        val count = (text.length + multi - 1) / multi
        return count to count * multi - text.length
    }

    const val GROUP_MS = 5 * 60 * 1000L
}
