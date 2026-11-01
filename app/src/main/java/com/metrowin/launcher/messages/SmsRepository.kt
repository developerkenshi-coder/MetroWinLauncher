package com.metrowin.launcher.messages

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.SecureStore

@Immutable
data class Conversation(val threadId: Long, val address: String, val name: String?, val snippet: String, val date: Long, val unread: Int) {
    val title get() = name ?: address
}

@Immutable
data class Sms(val id: Long, val body: String, val date: Long, val outgoing: Boolean, val failed: Boolean = false)

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
        return rows.map { (thread, list) ->
            val address = addresses[thread].orEmpty()
            Conversation(thread, address, nameOf(address), list.first().first, list.first().second, list.count { it.third })
        }
    }

    fun messages(threadId: Long): List<Sms> = runCatching {
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
    }

    fun delete(threadId: Long) {
        runCatching { resolver.delete(Uri.parse("content://sms/conversations/$threadId"), null, null) }
    }

    /** Sends [body] to [address] (a long text goes in parts) and, when the launcher is the messaging app, keeps it among the sent messages. */
    fun send(address: String, body: String): Boolean = runCatching {
        val manager = if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        manager.sendMultipartTextMessage(address, null, manager.divideMessage(body), null, null)
        if (SmsRole.isDefault(context)) {
            resolver.insert(
                Telephony.Sms.Sent.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.READ, 1)
                },
            )
        }
        saveDraft(address, "")
        true
    }.getOrDefault(false)

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
        drafts.edit { if (text.isBlank()) remove(address) else putString(address, SecureStore.encrypt(text)) }
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
    }
}
