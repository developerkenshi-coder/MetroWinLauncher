package com.metrowin.launcher.messages

import com.metrowin.launcher.data.Prefs
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.SecureStore
import com.metrowin.launcher.people.PeopleBook
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class Conversation(
    val threadId: Long, val address: String, val name: String?, val snippet: String, val date: Long, val unread: Int,
    /** The thumbnail of the contact, when the phone has one. */
    val photo: String? = null,
) {
    val title get() = name ?: address
}

@Immutable
data class Sms(
    val id: Long, val body: String, val date: Long, val outgoing: Boolean, val failed: Boolean = false, val image: Uri? = null, val mms: Boolean = false,
    /** The SMS centre said the message arrived (only when delivery reports are asked for). */
    val delivered: Boolean = false,
    /** A multimedia message the network announced and the phone has not fetched yet: the bubble can try again. */
    val pending: Boolean = false,
    /** The type of the first attachment that is not a picture (video, audio, card), for a bubble that only says so. */
    val attachment: String? = null,
    /** Who wrote a received text (its number), to name the writer in a group. */
    val sender: String? = null,
)

private data class ThreadRow(val thread: Long, val people: List<String>, val snippet: String, val date: Long)

/** The text messages of the phone, read from and written to the messages of Android, and the drafts, kept encrypted. */
class SmsRepository(context: Context) {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver
    private val drafts = Prefs.open(context, Prefs.SMS_DRAFTS)
    private val options = MessagesOptions(context)

    /**
     * The conversations, newest first. Android keeps a line per thread (its newest text and date): that table is read, not every message;
     * the unread ones are counted apart. A conversation read here while Android refuses to mark it read ([MessagesOptions.markSeen]) counts none.
     */
    fun conversations(): List<Conversation> {
        val canonical = canonical()
        val unread = unreadCounts()
        val out = ArrayList<Conversation>()
        val rows = ArrayList<ThreadRow>()
        runCatching {
            resolver.query(threadsUri(), arrayOf("_id", "date", "snippet", "message_count", "recipient_ids"), null, null, "date DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getInt(3) == 0) continue
                    rows += ThreadRow(cursor.getLong(0), recipientNumbers(cursor.getString(4).orEmpty(), canonical), cursor.getString(2).orEmpty(), cursor.getLong(1))
                }
            }
        }
        // A number not known yet costs one query to the contacts of the phone, slow on some phones: the unknown ones are asked side by side.
        rows.flatMap { it.people }.distinct().filter { !cache.containsKey(it) }.parallelStream().forEach { contactOf(it) }
        for ((thread, people, snippet, date) in rows) {
            val address = people.joinToString(",")
            val contact = if (people.size == 1) contactOf(address) else null
            val name = if (people.size > 1) people.joinToString(", ") { nameOf(it) ?: it } else contact?.first
            out += Conversation(thread, address, name, snippet, date, unread[thread] ?: 0, contact?.second)
        }
        return out.filter { it.address.isNotEmpty() }
    }

    private fun threadsUri(): Uri = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()

    /** Per thread, how many received messages are unread (the texts and the multimedia ones). */
    private fun unreadCounts(): Map<Long, Int> {
        val counts = HashMap<Long, Int>()
        runCatching {
            resolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.DATE), "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()), null)?.use {
                while (it.moveToNext()) if (it.getLong(1) > options.seenUpTo(it.getLong(0))) counts.merge(it.getLong(0), 1, Int::plus)
            }
        }
        runCatching {
            resolver.query(MMS, arrayOf("thread_id", "date"), "msg_box = ? AND read = 0", arrayOf(MMS_INBOX.toString()), null)?.use {
                while (it.moveToNext()) if (it.getLong(1) * 1000 > options.seenUpTo(it.getLong(0))) counts.merge(it.getLong(0), 1, Int::plus)
            }
        }
        return counts
    }

    /** The text of each multimedia message of [ids] (a picture alone reads as nothing), the first picture and the first other attachment, in one query. */
    private fun mmsParts(ids: List<Long>): Map<Long, Triple<String, Uri?, String?>> {
        if (ids.isEmpty()) return emptyMap()
        val texts = HashMap<Long, String>()
        val images = HashMap<Long, Uri>()
        val others = HashMap<Long, String>()
        runCatching {
            resolver.query(MMS_PART, arrayOf("_id", "mid", "ct", "text"), "mid IN (${ids.joinToString(",")})", null, "_id ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mid = cursor.getLong(1)
                    val type = cursor.getString(2).orEmpty()
                    when {
                        type == "text/plain" -> texts[mid] = texts[mid].orEmpty() + cursor.getString(3).orEmpty()
                        type.startsWith("image/") -> images.putIfAbsent(mid, Uri.withAppendedPath(MMS_PART, cursor.getLong(0).toString()))
                        type == "application/smil" || type.startsWith("text/") && type != "text/x-vcard" -> Unit
                        else -> others.putIfAbsent(mid, type)
                    }
                }
            }
        }
        return ids.associateWith { Triple(texts[it].orEmpty(), images[it], others[it]) }
    }

    /** The picture at [uri], decoded no larger than about [max] pixels wide (the bubble is small: a photo is not read in full). */
    fun picture(uri: Uri, max: Int): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max) sample *= 2
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
    }.getOrNull()

    /** The size in pixels of the picture at [uri], read without decoding it, to give its placeholder the right proportions. */
    fun pictureSize(uri: Uri): Pair<Int, Int>? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
    }.getOrNull()

    /** The "canonical addresses" of the phone: the numbers its threads refer to by id. */
    private fun canonical(): Map<Long, String> = runCatching {
        val numbers = HashMap<Long, String>()
        resolver.query("content://mms-sms/canonical-addresses".toUri(), arrayOf("_id", "address"), null, null, null)?.use { while (it.moveToNext()) numbers[it.getLong(0)] = it.getString(1).orEmpty() }
        numbers
    }.getOrDefault(emptyMap())

    /**
     * The newest [limit] messages of a thread, oldest first (0 for all of them): the texts and the multimedia ones, with what a bubble
     * shows of them, plus what was sent from here while Android kept none of it ([MessagesOptions.addSentLocal]).
     */
    fun messages(threadId: Long, limit: Int = 0, address: String? = null): List<Sms> {
        val all = smsOf(threadId, limit) + mmsOf(threadId, limit) + address?.let(::localSent).orEmpty()
        val sorted = all.sortedBy { it.date }
        return if (limit > 0) sorted.takeLast(limit) else sorted
    }

    private fun localSent(address: String): List<Sms> = options.sentLocal(address).map { (at, body) -> Sms(-at, body, at, outgoing = true) }

    private fun mmsOf(threadId: Long, limit: Int): List<Sms> = runCatching {
        val rows = ArrayList<List<Any?>>()
        resolver.query(
            MMS, arrayOf("_id", "date", "msg_box", "m_type", "sub"), "thread_id = ?", arrayOf(threadId.toString()),
            "date DESC" + if (limit > 0) " LIMIT $limit" else "",
        )?.use { c -> while (c.moveToNext()) rows += listOf(c.getLong(0), c.getLong(1), c.getInt(2), c.getInt(3), c.getString(4).orEmpty()) }
        val parts = mmsParts(rows.map { it[0] as Long })
        rows.map { r ->
            val (text, image, other) = parts.getValue(r[0] as Long)
            Sms(
                r[0] as Long, text.ifEmpty { r[4] as String }, (r[1] as Long) * 1000, r[2] != MMS_INBOX, image = image, mms = true,
                pending = r[3] == NOTIFICATION_IND, attachment = other,
            )
        }
    }.getOrDefault(emptyList())

    private fun smsOf(threadId: Long, limit: Int): List<Sms> = runCatching {
        buildList {
            resolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS, Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} DESC" + if (limit > 0) " LIMIT $limit" else "",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(3)
                    add(Sms(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getLong(2), type != Telephony.Sms.MESSAGE_TYPE_INBOX, type == Telephony.Sms.MESSAGE_TYPE_FAILED, delivered = cursor.getInt(4) == Telephony.Sms.STATUS_COMPLETE, sender = cursor.getString(5)))
                }
            }
        }
    }.getOrDefault(emptyList())

    /**
     * Only the messaging app of the phone may write the messages; for any other app Android refuses. Then the time of reading is kept here, so
     * the conversation does not count as unread again for this app.
     */
    fun markRead(threadId: Long) {
        var written = false
        runCatching {
            resolver.update(Telephony.Sms.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
            written = true
        }
        runCatching { resolver.update(MMS, ContentValues().apply { put("read", 1); put("seen", 1) }, "thread_id = ? AND read = 0", arrayOf(threadId.toString())) }
        if (!written) options.markSeen(threadId)
    }

    fun delete(threadId: Long) {
        runCatching { resolver.delete(Uri.withAppendedPath(Telephony.Threads.CONTENT_URI, threadId.toString()), null, null) }
    }

    /** Deletes one message (text or multimedia). */
    fun delete(message: Sms) {
        val base = if (message.mms) MMS else Telephony.Sms.CONTENT_URI
        if (message.id > 0) runCatching { resolver.delete(ContentUris.withAppendedId(base, message.id), null, null) }
    }

    /** The SIMs of the phone, or none when the permission to read them is not given. */
    fun sims(): List<Pair<Int, String>> = runCatching {
        if (!LivePermissions.isGranted(context, android.Manifest.permission.READ_PHONE_STATE)) return@runCatching emptyList()
        context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().map { it.subscriptionId to "${it.simSlotIndex + 1} ${it.displayName}" }
    }.getOrDefault(emptyList())

    /**
     * Sends [body] to [address] (a long text goes in parts) and, when the launcher is the messaging app, keeps it among the sent messages;
     * if the radio then refuses it, [SmsSentReceiver] turns that row into a failed one. [sub] is the SIM (-1 for the default one); with
     * [report] the network is asked to say when it arrived ([SmsDeliveredReceiver]).
     */
    fun send(address: String, body: String, sub: Int = -1, report: Boolean = false): Boolean {
        var row: Uri? = null
        return runCatching {
            val manager = if (sub >= 0) SmsManager.getSmsManagerForSubscriptionId(sub)
            else if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            val isDefault = SmsRole.isDefault(context)
            if (isDefault) {
                row = resolver.insert(
                    Telephony.Sms.Sent.CONTENT_URI,
                    ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.READ, 1)
                        if (report) put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                    },
                )
            }
            val parts = manager.divideMessage(body)
            val sent = row?.let { PendingIntent.getBroadcast(context, it.hashCode(), Intent(context, SmsSentReceiver::class.java).setData(it), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
            val delivered = row?.takeIf { report }?.let { PendingIntent.getBroadcast(context, it.hashCode(), Intent(context, SmsDeliveredReceiver::class.java).setData(it), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
            manager.sendMultipartTextMessage(address, null, parts, sent?.let { ArrayList(List(parts.size) { _ -> it }) }, delivered?.let { ArrayList(List(parts.size) { _ -> it }) })
            if (!isDefault) options.addSentLocal(address, System.currentTimeMillis(), body)
            saveDraft(address, "")
            true
        }.getOrElse { row?.let(::markFailed); false }
    }

    /** Sends the text of a failed message again and, once it left, drops the failed copy. */
    fun retry(address: String, failed: Sms, sub: Int = -1, report: Boolean = false): Boolean =
        send(address, failed.body, sub, report).also { if (it) runCatching { resolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, failed.id.toString()), null, null) } }

    fun markFailed(row: Uri) {
        runCatching { resolver.update(row, ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }, null, null) }
    }

    /** The delivery report of a message sent: delivered, or failed at the SMS centre. */
    fun markDelivered(row: Uri, status: Int) {
        runCatching { resolver.update(row, ContentValues().apply { put(Telephony.Sms.STATUS, if (status >= 64) Telephony.Sms.STATUS_FAILED else if (status == 0) Telephony.Sms.STATUS_COMPLETE else Telephony.Sms.STATUS_PENDING) }, null, null) }
    }

    /** The conversations holding [query] in a message, each with its newest matching message as the line (the multimedia messages count by their text too). */
    fun search(query: String): List<Conversation> = runCatching {
        val found = ArrayList<Triple<Long, String, Long>>() // thread, line, date
        val escaped = "%${likeEscape(query)}%"
        resolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'", arrayOf(escaped), "${Telephony.Sms.DATE} DESC LIMIT $MAX_HITS",
        )?.use { c -> while (c.moveToNext()) found += Triple(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2)) }
        runCatching {
            val texts = HashMap<Long, String>()
            resolver.query(MMS_PART, arrayOf("mid", "text"), "ct = 'text/plain' AND text LIKE ? ESCAPE '\\'", arrayOf(escaped), "_id DESC LIMIT $MAX_HITS")?.use { c -> while (c.moveToNext()) texts.putIfAbsent(c.getLong(0), c.getString(1).orEmpty()) }
            if (texts.isNotEmpty()) resolver.query(MMS, arrayOf("_id", "thread_id", "date"), "_id IN (${texts.keys.joinToString(",")})", null, null)?.use { c ->
                while (c.moveToNext()) found += Triple(c.getLong(1), texts.getValue(c.getLong(0)), c.getLong(2) * 1000)
            }
        }
        val canonical = canonical()
        val people = HashMap<Long, String>()
        resolver.query(threadsUri(), arrayOf("_id", "recipient_ids"), null, null, null)?.use { c -> while (c.moveToNext()) people[c.getLong(0)] = recipientNumbers(c.getString(1).orEmpty(), canonical).joinToString(",") }
        found.sortedByDescending { it.third }.distinctBy { it.first }.take(MAX_HITS).mapNotNull { (thread, line, date) ->
            val address = people[thread].orEmpty().ifEmpty { return@mapNotNull null }
            Conversation(thread, address, if (',' in address) address.split(',').joinToString(", ") { nameOf(it) ?: it } else nameOf(address), line, date, 0)
        }
    }.getOrDefault(emptyList())

    /** A message that arrived: written among the received ones, which only the messaging app of the phone may do. */
    fun receive(address: String, body: String, date: Long) {
        runCatching {
            resolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, date)
                    // A blocked sender is kept, as read: the messages are never dropped, only not told.
                    val quiet = options.isBlocked(address)
                    put(Telephony.Sms.READ, if (quiet) 1 else 0); put(Telephony.Sms.SEEN, if (quiet) 1 else 0)
                },
            )
        }
    }

    // --- multimedia messages that arrive (see MmsReceiver)

    /** Keeps the announcement of a multimedia message among the messages as one to download; the id of its row (the one already there for a message announced twice). */
    internal fun saveMmsNotification(note: MmsPdu.Notification, sub: Int): Long? = runCatching {
        if (!SmsRole.isDefault(context)) return@runCatching null
        resolver.query(MMS, arrayOf("_id", "m_type"), "tr_id = ?", arrayOf(note.transactionId), null)?.use { c ->
            if (c.moveToFirst()) return@runCatching if (c.getInt(1) == NOTIFICATION_IND) c.getLong(0) else null
        }
        val from = note.from?.takeIf { it.isNotBlank() } ?: UNKNOWN_SENDER
        val thread = Telephony.Threads.getOrCreateThreadId(context, from)
        val now = System.currentTimeMillis() / 1000
        val uri = resolver.insert(
            Telephony.Mms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put("thread_id", thread); put("date", now); put("m_type", NOTIFICATION_IND); put("v", 18); put("ct_l", note.location); put("tr_id", note.transactionId)
                put("m_size", note.size); put("read", 0); put("seen", 0); put("pri", 129); put("ct_t", "application/vnd.wap.mms-message")
                note.subject?.let { put("sub", it); put("sub_cs", 106) }
                if (sub >= 0) put("sub_id", sub)
            },
        ) ?: return@runCatching null
        val id = ContentUris.parseId(uri)
        resolver.insert("content://mms/$id/addr".toUri(), ContentValues().apply { put("address", from); put("type", ADDR_FROM); put("charset", 106); put("msg_id", id) })
        id
    }.getOrNull()

    /** The address of a message to fetch again (a notification the phone failed to download), asking the phone once more; false when there is nothing to fetch. */
    fun redownload(rowId: Long): Boolean = runCatching {
        resolver.query(MMS, arrayOf("ct_l", "tr_id", "m_type"), "_id = ?", arrayOf(rowId.toString()), null)?.use { c ->
            c.moveToFirst() && c.getInt(2) == NOTIFICATION_IND && MmsReceiver.download(context, rowId, c.getString(0).orEmpty(), c.getString(1).orEmpty())
        } == true
    }.getOrDefault(false)

    /**
     * Writes a fetched multimedia message among the received ones and removes the announcement [notificationRow] it replaces. All of it or
     * none: when a step fails what was written is removed and the announcement stays, so the message can be fetched again.
     */
    internal fun saveMms(message: MmsPdu.Retrieved, notificationRow: Long): Long? {
        var id = -1L
        return runCatching {
            val from = message.from?.takeIf { it.isNotBlank() } ?: UNKNOWN_SENDER
            val thread = Telephony.Threads.getOrCreateThreadId(context, from)
            val textOnly = message.parts.all { it.type.startsWith("text/") }
            val uri = resolver.insert(
                Telephony.Mms.Inbox.CONTENT_URI,
                ContentValues().apply {
                    put("thread_id", thread); put("date", message.date.takeIf { it > 0 } ?: (System.currentTimeMillis() / 1000)); put("m_type", RETRIEVE_CONF); put("v", 18)
                    put("ct_t", "application/vnd.wap.multipart.related"); put("read", 0); put("seen", 0); put("pri", 129); put("text_only", if (textOnly) 1 else 0)
                    message.messageId?.let { put("m_id", it) }
                    message.subject?.let { put("sub", it); put("sub_cs", 106) }
                    resolver.query(MMS, arrayOf("tr_id"), "_id = ?", arrayOf(notificationRow.toString()), null)?.use { if (it.moveToFirst()) put("tr_id", it.getString(0)) }
                },
            ) ?: error("no row")
            id = ContentUris.parseId(uri)
            val addr = "content://mms/$id/addr".toUri()
            resolver.insert(addr, ContentValues().apply { put("address", from); put("type", ADDR_FROM); put("charset", 106); put("msg_id", id) })
            message.to.forEach { to -> resolver.insert(addr, ContentValues().apply { put("address", to); put("type", ADDR_TO); put("charset", 106); put("msg_id", id) }) }
            message.parts.forEachIndexed { index, part ->
                val values = ContentValues().apply {
                    put("mid", id); put("seq", index); put("ct", part.type); put("name", part.name ?: part.type.substringAfter('/')); put("cid", part.contentId ?: "<$index>"); put("cl", part.name ?: "part$index")
                    if (part.type == "text/plain") { put("chset", 106); put("text", String(part.data, if (part.charset == 1000) Charsets.UTF_16BE else Charsets.UTF_8)) }
                }
                val partUri = resolver.insert("content://mms/$id/part".toUri(), values) ?: error("no part")
                if (part.type != "text/plain") (resolver.openOutputStream(partUri) ?: error("no stream")).use { it.write(part.data) }
            }
            resolver.delete(ContentUris.withAppendedId(MMS, notificationRow), null, null)
            id
        }.getOrElse {
            if (id > 0) runCatching { resolver.delete(ContentUris.withAppendedId(MMS, id), null, null) }
            null
        }
    }

    // --- drafts

    /** The draft of a conversation (kept under a hash of the number; one saved under the number itself by an older version is read once and moved). */
    fun draft(address: String): String {
        val key = DraftKeys.of(address)
        drafts.getString(key, null)?.let { return SecureStore.decrypt(it).orEmpty() }
        val old = drafts.getString(address, null)?.let(SecureStore::decrypt).orEmpty()
        if (old.isNotEmpty()) { saveDraft(address, old); drafts.edit { remove(address) } }
        return old
    }

    fun saveDraft(address: String, text: String) {
        val key = DraftKeys.of(address)
        if (text.isBlank()) drafts.edit { remove(key); remove(address) } else SecureStore.putSecure(drafts, key, text)
    }

    // --- names

    // The names found are kept for the process: the reader of contacts is asked once per number (a miss too, as an empty text) until [forgetNames].
    private companion object {
        const val MAX_HITS = 200
        const val MMS_INBOX = 1
        const val NOTIFICATION_IND = 130
        const val RETRIEVE_CONF = 132
        const val ADDR_FROM = 137
        const val ADDR_TO = 151
        const val UNKNOWN_SENDER = "insert-address-token"
        val MMS: Uri = "content://mms".toUri()
        val MMS_PART: Uri = "content://mms/part".toUri()
        val cache = ConcurrentHashMap<String, Pair<String, String?>>()
        const val NAMES_TTL_MS = 10 * 60 * 1000L
        @Volatile var forgotAt = System.currentTimeMillis()
    }

    /** The cached names are dropped: a contact was added or renamed (the list asks again). */
    fun forgetNames() { cache.clear(); forgotAt = System.currentTimeMillis() }

    /** Coming back to the app: the names are asked again only when the last time was long ago (the contacts observer covers the changes seen meanwhile). */
    fun forgetOldNames(now: Long = System.currentTimeMillis()) { if (now - forgotAt > NAMES_TTL_MS) forgetNames() }

    fun nameOf(address: String): String? = contactOf(address)?.first

    /** The name of the contact with this number and the thumbnail of its photo (null when there is none), looked up once and kept. */
    fun contactOf(address: String): Pair<String, String?>? {
        if (address.isEmpty() || !LivePermissions.isGranted(context, LivePermissions.contacts)) return null
        val hit = cache.getOrPut(address) {
            runCatching {
                resolver.query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null,
                )?.use { if (it.moveToFirst()) it.getString(0).orEmpty() to it.getString(1) else null }
            }.getOrNull() ?: ("" to null)
        }
        return hit.takeIf { it.first.isNotEmpty() }
    }

    /** The thread of [address] among [conversations]: the same text, or the same line however the number is written (+39 333… and 333…). */
    fun threadOf(address: String, conversations: List<Conversation>): Conversation? =
        conversations.firstOrNull { it.address == address } ?: conversations.firstOrNull { ',' !in it.address && ',' !in address && PeopleBook.sameNumber(it.address, address) }
}

/** [text] for a `LIKE ... ESCAPE '\'` pattern: `%` and `_` stand for themselves. */
internal fun likeEscape(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** The numbers of a thread out of its "12 7" ids and the table of canonical addresses; an id nobody knows is left out. */
internal fun recipientNumbers(ids: String, numbers: Map<Long, String>): List<String> =
    ids.split(' ').mapNotNull { it.toLongOrNull()?.let(numbers::get)?.takeIf(String::isNotEmpty) }
