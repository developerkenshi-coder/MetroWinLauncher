package com.metrowin.launcher.messages

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.edit
import com.metrowin.launcher.data.SecureStore
import com.metrowin.launcher.people.PeopleBook

/**
 * The options of the messages app (the "⋯" menu of its application bar), kept in the app's own preferences. What touches privacy, the
 * network, the battery or a permission starts off; what is only looks starts on. Fixes (the privacy of the notification, drafts, the
 * conversation match by number...) are not options.
 */
class MessagesOptions(context: Context) {
    enum class Key(val id: String, val default: Boolean) {
        /** Day separators, bubbles grouped by sender, adaptive width, links, delivery icon, failed in red. */
        Bubbles("rich_bubbles", true),
        /** An initial or the photo of the contact in each conversation, and the photo in the title of a conversation. */
        Avatars("avatars", true),
        /** The list follows the messages of the phone as they change (an observer), instead of waiting for refresh. */
        Live("live_list", true),
        /** Two panes (list and conversation) when the screen is wide. */
        Panes("two_panes", true),
        /** Chips of quick replies over the box of a conversation. */
        Quick("quick_replies", false),
        /** Ask the network for the delivery report of each message sent (the SMS centre tells when it arrived). */
        Delivery("delivery_reports", false),
        /** Two SIMs: the SIM of the contact, the default for texts, or a question (asks once for the permission to read the SIM list). */
        Sims("sim_choice", true),
        /** A "copy code" button on the notification of a message with a one-time code. */
        Otp("otp_copy", false),
        /** Messages of blocked numbers are kept but not told; blocking from the long press menu. */
        Block("spam_block", false),
        /** The notification shows the text of the message on the lock screen too (off: "new message" only). */
        Lock("lock_text", false),
    }

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = mutableStateMapOf<String, Boolean>().apply { Key.entries.forEach { put(it.id, prefs.getBoolean(it.id, it.default)) } }

    fun on(key: Key): Boolean = state[key.id] ?: key.default

    fun set(key: Key, value: Boolean) {
        state[key.id] = value
        prefs.edit { putBoolean(key.id, value) }
    }

    // --- what the phone cannot keep when the launcher is not the messaging app

    /** The time up to which the conversation was read here, for when Android refuses to mark it read (the launcher is not the messaging app). */
    fun seenUpTo(thread: Long): Long = prefs.getLong("seen:$thread", 0)

    fun markSeen(thread: Long, now: Long = System.currentTimeMillis()) = prefs.edit { putLong("seen:$thread", now) }

    // --- blocked numbers (kept encrypted: it says whom the user avoids)

    fun blocked(): List<String> = prefs.getString(BLOCKED, null)?.let(SecureStore::decrypt)?.split('\n')?.filter(String::isNotEmpty).orEmpty()

    fun isBlocked(address: String): Boolean = on(Key.Block) && blocked().any { PeopleBook.sameNumber(it, address) }

    fun block(address: String, on: Boolean) {
        val rest = blocked().filterNot { PeopleBook.sameNumber(it, address) }
        SecureStore.putSecure(prefs, BLOCKED, (if (on) rest + address else rest).joinToString("\n"))
    }

    /** The messages sent while the launcher was not the messaging app (Android keeps none of them), newest last, as "millis TAB text". */
    fun sentLocal(address: String): List<Pair<Long, String>> =
        prefs.getString(sentKey(address), null)?.let(SecureStore::decrypt)?.lines()?.mapNotNull { line ->
            line.substringBefore('\t').toLongOrNull()?.let { it to line.substringAfter('\t').replace(" ", "\n") }
        }.orEmpty()

    fun addSentLocal(address: String, at: Long, body: String) {
        val all = (sentLocal(address) + (at to body)).takeLast(MAX_LOCAL)
        SecureStore.putSecure(prefs, sentKey(address), all.joinToString("\n") { "${it.first}\t${it.second.replace("\n", " ")}" })
    }

    private fun sentKey(address: String) = "sent:" + DraftKeys.of(address)

    private companion object {
        const val FILE = "metrowin_messages"
        const val BLOCKED = "blocked"
        const val MAX_LOCAL = 100
    }
}

/** The key of a draft or of a saved list for a conversation: a hash of the number (its last nine digits), so the file holds no number in the clear. */
internal object DraftKeys {
    fun of(address: String): String {
        val digits = address.filter { it.isDigit() }
        val basis = if (',' in address || digits.isEmpty()) address else digits.takeLast(9)
        return java.security.MessageDigest.getInstance("SHA-256").digest(basis.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}
