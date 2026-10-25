package com.metrowin.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/** Looks for contacts by name. It needs the contacts permission, and finds nothing without it. */
class ContactSearch(private val context: Context) {
    /** Blocking, call it off the main thread. */
    fun search(query: String, limit: Int = LIMIT): List<ContactMatch> {
        val text = query.trim()
        if (text.isEmpty() || !LivePermissions.isGranted(context, LivePermissions.contacts)) return emptyList()
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(text))
            val matches = ArrayList<ContactMatch>(limit)
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null, null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                while (matches.size < limit && cursor.moveToNext()) {
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val key = cursor.getString(1) ?: continue
                    matches += ContactMatch(name, ContactsContract.Contacts.getLookupUri(cursor.getLong(0), key).toString())
                }
            }
            matches
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val LIMIT = 5
    }
}
