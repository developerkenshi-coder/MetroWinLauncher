package com.metrowin.launcher.messages

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.SecureStore

@Immutable
data class Conversation(val threadId: Long, val address: String, val name: String?, val snippet: String, val date: Long, val unread: Int) {
    val title get() = name ?: address
}

@Immutable
data class Sms(val id: Long, val body: String, val date: Long, val outgoing: Boolean, val failed: Boolean = false, val image: Uri? = null, val mms: Boolean = false)

/** The text messages of the phone, read from and written to the messages of Android, and the drafts, kept encrypted. */
class SmsRepository(context: Context) {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver
    private val drafts = context.getSharedPreferences("metrowin_sms_drafts", Context.MODE_PRIVATE)

    fun conversations(): List<Conversation> {
        val rows = LinkedHashMap<Long, MutableList<Triple<String, Long, Boolean>>>() // body, date, unread
        val addresses = HashMap<Long, String>()
        runCatching {
            resolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ),
                null, null, "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count++ < MAX_ROWS) {
                    val thread = cursor.getLong(0)
                    addresses.putIfAbsent(thread, cursor.getString(1).orEmpty())
                    val unread = cursor.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX && cursor.getInt(5) == 0
                    rows.getOrPut(thread) { ArrayList() } += Triple(cursor.getString(2).orEmpty(), cursor.getLong(3), unread)
                }
            }
        }
        // Pictures and group texts are multimedia messages: they sit in their own table, and the line of a thread is the newest of the two.
        runCatching {
            val seen = HashSet<Long>()
            resolver.query(MMS, arrayOf("_id", "thread_id", "date", "msg_box", "read"), null, null, "date DESC")?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count++ < MAX_ROWS) {
                    val thread = cursor.getLong(1)
                    val unread = cursor.getInt(3) == MMS_INBOX && cursor.getInt(4) == 0
                    // Only the newest of a thread can be its line, so only that one has its text looked up.
                    val body = if (seen.add(thread)) mmsParts(cursor.getLong(0)).first else ""
                    rows.getOrPut(thread) { ArrayList() } += Triple(body, cursor.getLong(2) * 1000, unread)
                }
            }
        }
        val people = recipients()
        return rows.map { (thread, unsorted) ->
            val list = unsorted.sortedByDescending { it.second }
            val group = people[thread].orEmpty().takeIf { it.size > 1 }
            val address = group?.joinToString(",") ?: addresses[thread] ?: people[thread]?.firstOrNull().orEmpty()
            Conversation(thread, address, if (group != null) group.joinToString(", ") { nameOf(it) ?: it } else nameOf(address), list.first().first, list.first().second, list.count { it.third })
        }.sortedByDescending { it.date }
    }

    /** The text of a multimedia message (a picture alone reads as nothing) and the first picture it carries. */
    private fun mmsParts(id: Long): Pair<String, Uri?> {
        var text = ""
        var image: Uri? = null
        runCatching {
            resolver.query(MMS_PART, arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(id.toString()), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getString(1).orEmpty()
                    if (type == "text/plain") text += cursor.getString(2).orEmpty()
                    else if (type.startsWith("image/") && image == null) image = Uri.withAppendedPath(MMS_PART, cursor.getLong(0).toString())
                }
            }
        }
        return text to image
    }

    /** The picture at [uri], decoded no larger than about [max] pixels wide (the bubble is small: a photo is not read in full). */
    fun picture(uri: Uri, max: Int): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max) sample *= 2
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
    }.getOrNull()

    /** The people of each thread, as the phone keeps them: ids of "canonical addresses". */
    private fun recipients(): Map<Long, List<String>> = runCatching {
        val numbers = HashMap<Long, String>()
        resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)?.use { while (it.moveToNext()) numbers[it.getLong(0)] = it.getString(1).orEmpty() }
        val threads = HashMap<Long, List<String>>()
        resolver.query(Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build(), arrayOf("_id", "recipient_ids"), null, null, null)?.use {
            while (it.moveToNext()) threads[it.getLong(0)] = recipientNumbers(it.getString(1).orEmpty(), numbers)
        }
        threads
    }.getOrDefault(emptyMap())

    fun messages(threadId: Long): List<Sms> = (smsOf(threadId) + mmsOf(threadId)).sortedBy { it.date }

    private fun mmsOf(threadId: Long): List<Sms> = runCatching {
        buildList {
            resolver.query(MMS, arrayOf("_id", "date", "msg_box"), "thread_id = ?", arrayOf(threadId.toString()), "date ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val (text, image) = mmsParts(cursor.getLong(0))
                    add(Sms(cursor.getLong(0), text, cursor.getLong(1) * 1000, cursor.getInt(2) != MMS_INBOX, image = image, mms = true))
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun smsOf(threadId: Long): List<Sms> = runCatching {
        buildList {
            resolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(3)
                    add(Sms(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getLong(2), type != Telephony.Sms.MESSAGE_TYPE_INBOX, type == Telephony.Sms.MESSAGE_TYPE_FAILED))
                }
            }
        }
    }.getOrDefault(emptyList())

    /** Only the messaging app of the phone may write the messages; for any other app Android refuses, and the messages stay unread. */
    fun markRead(threadId: Long) {
        runCatching {
            resolver.update(Telephony.Sms.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
        }
        runCatching { resolver.update(MMS, ContentValues().apply { put("read", 1); put("seen", 1) }, "thread_id = ? AND read = 0", arrayOf(threadId.toString())) }
    }

    fun delete(threadId: Long) {
        runCatching { resolver.delete(Uri.withAppendedPath(Telephony.Threads.CONTENT_URI, threadId.toString()), null, null) }
    }

    /**
     * Sends [body] to [address] (a long text goes in parts) and, when the launcher is the messaging app, keeps it among the sent messages;
     * if the radio then refuses it, [SmsSentReceiver] turns that row into a failed one.
     */
    fun send(address: String, body: String): Boolean {
        var row: Uri? = null
        return runCatching {
            val manager = if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            if (SmsRole.isDefault(context)) {
                row = resolver.insert(
                    Telephony.Sms.Sent.CONTENT_URI,
                    ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.READ, 1)
                    },
                )
            }
            val parts = manager.divideMessage(body)
            val sent = row?.let { PendingIntent.getBroadcast(context, it.hashCode(), Intent(context, SmsSentReceiver::class.java).setData(it), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
            manager.sendMultipartTextMessage(address, null, parts, sent?.let { ArrayList(List(parts.size) { _ -> it }) }, null)
            saveDraft(address, "")
            true
        }.getOrElse { row?.let(::markFailed); false }
    }

    /** Sends the text of a failed message again and, once it left, drops the failed copy. */
    fun retry(address: String, failed: Sms): Boolean =
        send(address, failed.body).also { if (it) runCatching { resolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, failed.id.toString()), null, null) } }

    fun markFailed(row: Uri) {
        runCatching { resolver.update(row, ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }, null, null) }
    }

    /** The conversations holding [query] in a message, each with its newest matching message as the line. */
    fun search(query: String): List<Conversation> = runCatching {
        val hits = LinkedHashMap<Long, Conversation>()
        resolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'", arrayOf("%${likeEscape(query)}%"), "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && hits.size < MAX_HITS) {
                val thread = cursor.getLong(0)
                if (thread !in hits) {
                    val address = cursor.getString(1).orEmpty()
                    hits[thread] = Conversation(thread, address, nameOf(address), cursor.getString(2).orEmpty(), cursor.getLong(3), 0)
                }
            }
        }
        hits.values.toList()
    }.getOrDefault(emptyList())

    /** A message that arrived: written among the received ones, which only the messaging app of the phone may do. */
    fun receive(address: String, body: String, date: Long) {
        runCatching {
            resolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, date)
                    put(Telephony.Sms.READ, 0); put(Telephony.Sms.SEEN, 0)
                },
            )
        }
    }

    fun draft(address: String): String = drafts.getString(address, null)?.let(SecureStore::decrypt).orEmpty()

    fun saveDraft(address: String, text: String) {
        if (text.isBlank()) drafts.edit { remove(address) } else SecureStore.putSecure(drafts, address, text)
    }

    private val names = HashMap<String, String?>()

    fun nameOf(address: String): String? {
        if (address.isEmpty() || !LivePermissions.isGranted(context, LivePermissions.contacts)) return null
        return names.getOrPut(address) {
            runCatching {
                resolver.query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
                )?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull()
        }
    }

    private companion object {
        const val MAX_ROWS = 3000
        const val MAX_HITS = 200
        const val MMS_INBOX = 1
        val MMS: Uri = Uri.parse("content://mms")
        val MMS_PART: Uri = Uri.parse("content://mms/part")
    }
}

/** [text] for a `LIKE ... ESCAPE '\'` pattern: `%` and `_` stand for themselves. */
internal fun likeEscape(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** The numbers of a thread out of its "12 7" ids and the table of canonical addresses; an id nobody knows is left out. */
internal fun recipientNumbers(ids: String, numbers: Map<Long, String>): List<String> =
    ids.split(' ').mapNotNull { it.toLongOrNull()?.let(numbers::get)?.takeIf(String::isNotEmpty) }
