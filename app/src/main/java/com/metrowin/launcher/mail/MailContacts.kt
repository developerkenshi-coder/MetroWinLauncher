package com.metrowin.launcher.mail

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

/** The addresses of the contacts that start with what the user types in a recipient field. Only with the permission the launcher already has; nothing without it. */
object MailContacts {
    /** The last word of a field of addresses separated by commas: what is being typed now. */
    fun lastPart(field: String): String = field.substringAfterLast(',').trim()

    /** [field] with the part being typed replaced by [address]. */
    fun complete(field: String, address: MailAddress): String =
        (field.substringBeforeLast(',', "").let { if (it.isEmpty()) "" else "$it, " }) + (if (address.name.isBlank()) address.address else "${address.name} <${address.address}>") + ", "

    /** Blocking, off the main thread. */
    fun suggest(context: Context, typed: String, limit: Int = 5): List<MailAddress> {
        if (typed.length < 2) return emptyList()
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching {
            val found = LinkedHashMap<String, MailAddress>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ? OR ${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY} LIKE ?",
                arrayOf("%$typed%", "%$typed%"), "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY} LIMIT ${limit * 3}",
            )?.use { c ->
                while (c.moveToNext() && found.size < limit) {
                    val address = c.getString(1).orEmpty().trim()
                    if (address.isNotEmpty()) found.putIfAbsent(address.lowercase(), MailAddress(c.getString(0).orEmpty(), address))
                }
            }
            found.values.toList()
        }.getOrDefault(emptyList())
    }
}
