package com.metrowin.launcher.data

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
data class RecentCall(val name: String, val number: String, val type: Int, val date: Long) {
    val missed: Boolean get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
    val outgoing: Boolean get() = type == CallLog.Calls.OUTGOING_TYPE
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

    /** The latest calls, newest first; empty without the permission to read the call log. */
    fun recentCalls(limit: Int = MAX_CALLS): List<RecentCall> {
        if (!LivePermissions.isGranted(context, LivePermissions.calls)) return emptyList()
        return runCatching {
            val found = ArrayList<RecentCall>(limit)
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                while (found.size < limit && cursor.moveToNext()) {
                    val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    found += RecentCall(cursor.getString(0)?.takeIf { it.isNotBlank() } ?: number, number, cursor.getInt(2), cursor.getLong(3))
                }
            }
            found
        }.getOrDefault(emptyList())
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
    }
}
