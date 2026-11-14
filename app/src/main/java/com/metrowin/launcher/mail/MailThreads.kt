package com.metrowin.launcher.mail

/**
 * The messages of a list gathered into conversations. Messages that carry Message-ID, In-Reply-To and References headers meet by them (a reply
 * and what it answers are one conversation, two unrelated mails titled "Hello" are not); a message without any of those meets the others
 * without by its subject once "Re:", "Fwd:" and the like are taken off. The order of the list is kept (a conversation sits where its first
 * message is), so with the newest first each conversation starts with its newest.
 */
object MailThreads {
    private val prefix = Regex("""^\s*(re|fwd?|aw|wg|sv|vs|tr|rv|res|enc|r|i)(\[\d+])?\s*:\s*""", RegexOption.IGNORE_CASE)

    fun normalize(subject: String): String {
        var s = subject
        while (true) s = prefix.replaceFirst(s, "").takeIf { it != s } ?: break
        return s.trim().replace(Regex("""\s+"""), " ").lowercase()
    }

    /** The subject of an answer: "Re: x", and no second "Re:" when the subject is one already (in any of the languages of [prefix]). */
    fun reply(subject: String): String = if (isReply(subject)) subject.trim() else "Re: ${subject.trim()}".trim()

    /** The subject of a forwarded message: "Fwd: x", not "Fwd: Fwd: x". */
    fun forward(subject: String): String = if (Regex("""^\s*(fwd?|tr|rv|enc|wg)\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(subject)) subject.trim() else "Fwd: ${subject.trim()}".trim()

    private fun isReply(subject: String) = Regex("""^\s*(re|aw|sv|vs|res|r)(\[\d+])?\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(subject)

    private fun ids(refs: String) = refs.split(Regex("""\s+""")).filter { it.isNotBlank() }

    fun group(messages: List<MailMessage>): List<List<MailMessage>> {
        val parent = IntArray(messages.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        val byId = HashMap<String, Int>()
        messages.forEachIndexed { i, m -> if (m.messageId.isNotBlank()) byId.putIfAbsent(m.messageId, i) }
        // Two replies to a message that is not in the list (an older page) still share what they answer.
        val answered = HashMap<String, Int>()
        messages.forEachIndexed { i, m ->
            ids(m.refs).forEach { ref ->
                byId[ref]?.let { union(i, it) }
                answered[ref]?.let { union(i, it) } ?: run { answered[ref] = i }
            }
        }
        val bySubject = HashMap<String, Int>()
        messages.forEachIndexed { i, m ->
            if (m.messageId.isBlank() && m.refs.isBlank()) {
                // A message without a subject is on its own: "no subject" is not a conversation.
                val key = normalize(m.subject)
                if (key.isNotEmpty()) bySubject[key]?.let { union(i, it) } ?: run { bySubject[key] = i }
            }
        }
        val groups = LinkedHashMap<Int, MutableList<MailMessage>>()
        messages.forEachIndexed { i, m -> groups.getOrPut(find(i)) { ArrayList() } += m }
        return groups.values.toList()
    }
}
