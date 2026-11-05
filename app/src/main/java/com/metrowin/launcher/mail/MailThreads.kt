package com.metrowin.launcher.mail

/**
 * The messages of a list gathered into conversations: the ones with the same subject once "Re:", "Fwd:" and the like are taken off.
 * The order of the list is kept (a conversation sits where its first message is), so with the newest first each conversation starts with its newest.
 * shortcut: the subject alone decides (no References / In-Reply-To headers, which cost a fetch of each message), so two unrelated
 * mails titled "Hello" meet; upgrade by fetching those headers in MailClient.summaries when that shows.
 */
object MailThreads {
    private val prefix = Regex("""^\s*(re|fwd?|aw|wg|sv|vs|tr|rv|res|enc|r|i)(\[\d+])?\s*:\s*""", RegexOption.IGNORE_CASE)

    fun normalize(subject: String): String {
        var s = subject
        while (true) s = prefix.replaceFirst(s, "").takeIf { it != s } ?: break
        return s.trim().replace(Regex("""\s+"""), " ").lowercase()
    }

    fun group(messages: List<MailMessage>): List<List<MailMessage>> {
        val groups = LinkedHashMap<String, MutableList<MailMessage>>()
        // A message without a subject is on its own: "no subject" is not a conversation.
        messages.forEach { groups.getOrPut(normalize(it.subject).ifEmpty { "\u0000${it.id}" }) { ArrayList() } += it }
        return groups.values.toList()
    }
}
