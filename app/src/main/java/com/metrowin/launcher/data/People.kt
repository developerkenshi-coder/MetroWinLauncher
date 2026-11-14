package com.metrowin.launcher.data

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

/** A favourite person: a starred contact with a number, and a photo when there is one. */
@Immutable
data class Favourite(val name: String, val lookupUri: String, val number: String, val photo: ImageBitmap?)

/** A call of the log: with whom, which way, when. [type] is one of the types of [CallLog.Calls]. */
@Immutable
data class RecentCall(
    val name: String, val number: String, val type: Int, val date: Long,
    /** The row of the log (0 when not known): what a deletion is matched on, so no other row is touched. */
    val id: Long = 0,
    /** Seconds of talk; 0 for a call that was not answered. */
    val duration: Int = 0,
    /** The SIM (phone account) the call went through, as Android names it; null when the log does not say. */
    val accountId: String? = null,
) {
    val missed: Boolean get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
    val outgoing: Boolean get() = type == CallLog.Calls.OUTGOING_TYPE
    val rejected: Boolean get() = type == CallLog.Calls.REJECTED_TYPE
    val blocked: Boolean get() = type == CallLog.Calls.BLOCKED_TYPE
}

/** Reads the favourites and the recent calls for the speed dial page and the people hub. Blocking: off the main thread, and only when the page opens. */
class PeopleReader(private val context: Context) {
    /** The starred contacts that have a number, in the order of the contacts; empty without the permission to read contacts. */
    fun favourites(limit: Int = MAX_FAVOURITES): List<Favourite> {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return emptyList()
        return runCatching {
            val resolver = context.contentResolver
            val found = LinkedHashMap<String, Triple<String, String, Uri?>>()
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED}=1", null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC, ${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
            )?.use { cursor ->
                while (found.size < limit && cursor.moveToNext()) {
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val key = cursor.getString(1) ?: continue
                    val number = cursor.getString(3)?.let(PhoneCalls::clean)?.takeIf { it.isNotEmpty() } ?: continue
                    val lookup = ContactsContract.Contacts.getLookupUri(cursor.getLong(0), key).toString()
                    // A contact with several numbers is one favourite: its main number comes first.
                    if (lookup !in found) found[lookup] = Triple(name, number, cursor.getString(4)?.let(Uri::parse))
                }
            }
            found.map { (lookup, value) ->
                val photo = value.third?.let { uri -> runCatching { resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull() }
                Favourite(value.first, lookup, value.second, photo?.let { shrink(it).asImageBitmap() })
            }
        }.getOrDefault(emptyList())
    }

    /**
     * The latest calls, newest first; empty without the permission to read the call log. The name is the one the contacts have now (the
     * log keeps the name of the day of the call, which goes stale), and the one of the log when the contacts cannot be read or do not know it.
     */
    fun recentCalls(limit: Int = MAX_CALLS): List<RecentCall> = readCalls(null, null, limit)

    /** The calls of one [number], newest first, read from the log with a filter on the number (not by scanning the whole log). */
    fun callsOf(number: String, limit: Int): List<RecentCall> {
        val clean = PhoneCalls.clean(number)
        if (clean.isEmpty()) return emptyList()
        // The log writes the number as dialed (with or without the prefix): the tail of it finds them all, the compare below keeps the right ones.
        val tail = clean.takeLast(TAIL)
        return readCalls("${CallLog.Calls.NUMBER} LIKE ?", arrayOf("%$tail"), limit * 4).filter { sameNumber(it.number, clean) }.take(limit)
    }

    private fun sameNumber(a: String, b: String): Boolean {
        val x = PhoneCalls.clean(a)
        val y = PhoneCalls.clean(b)
        return x == y || (x.length >= TAIL && y.length >= TAIL && x.takeLast(TAIL) == y.takeLast(TAIL))
    }

    private fun readCalls(selection: String?, args: Array<String>?, limit: Int): List<RecentCall> {
        if (!LivePermissions.isGranted(context, LivePermissions.calls)) return emptyList()
        return runCatching {
            val found = ArrayList<RecentCall>(limit)
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls._ID, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID),
                selection, args, "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                while (found.size < limit && cursor.moveToNext()) {
                    val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    found += RecentCall(
                        cursor.getString(0)?.takeIf { it.isNotBlank() } ?: number, number, cursor.getInt(2), cursor.getLong(3),
                        cursor.getLong(4), cursor.getInt(5), cursor.getString(6),
                    )
                }
            }
            freshNames(found)
        }.getOrDefault(emptyList())
    }

    /** [calls] with the name the contacts give to each number now; a number the contacts do not know keeps what the log had. */
    private fun freshNames(calls: List<RecentCall>): List<RecentCall> {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return calls
        val names = HashMap<String, String?>()
        return calls.map { call ->
            val name = names.getOrPut(call.number) { nameOf(call.number) } ?: return@map call
            if (name == call.name) call else call.copy(name = name)
        }
    }

    private fun nameOf(number: String): String? = runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0)?.takeIf { name -> name.isNotBlank() } else null }
    }.getOrNull()

    /** Takes [call] out of the call log. False without the permission to write it, or when nothing was removed. */
    fun deleteCall(call: RecentCall): Boolean {
        if (!LivePermissions.isGranted(context, Manifest.permission.WRITE_CALL_LOG)) return false
        return runCatching {
            // The row is the one with this id; two calls of the same second and number (a missed call and its redial) are not mixed up.
            if (call.id > 0) context.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(call.id.toString())) > 0
            else context.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.DATE}=? AND ${CallLog.Calls.NUMBER}=?", arrayOf(call.date.toString(), call.number)) > 0
        }.getOrDefault(false)
    }

    private fun shrink(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= PHOTO_PX) return bitmap
        val scale = PHOTO_PX.toFloat() / longest
        return bitmap.scale((bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
    }

    private companion object {
        const val MAX_FAVOURITES = 24
        const val MAX_CALLS = 20
        const val PHOTO_PX = 256
        const val TAIL = 7
    }
}
