package com.metrowin.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.ContactsContract

/**
 * Placing a call from the launcher. A call needs the phone permission; without it the dialer opens with the number
 * written in, which is one tap away and needs nothing. Pure where it can be, so it is tested without a phone.
 */
object PhoneCalls {
    const val permission = Manifest.permission.CALL_PHONE

    /** The number as the dialer wants it: digits and the signs of a number (+, *, #), nothing else. */
    fun clean(number: String): String = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    fun uri(number: String): Uri = Uri.fromParts("tel", clean(number), null)

    /** The address a speed dial tile keeps: `tel:` and the number, with the plus as it is written. */
    fun address(number: String): String = "tel:" + clean(number)

    /** The intent that calls [number] at once ([direct], needs the permission) or opens the dialer with it. */
    fun intent(number: String, direct: Boolean): Intent =
        Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri(number)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The number inside a `tel:` address, or null when [address] is not one. */
    fun numberOf(address: String): String? =
        address.takeIf { it.startsWith("tel:") }?.removePrefix("tel:")?.let(::clean)?.takeIf { it.isNotEmpty() }

    /**
     * The phone number of a contact: the one marked as the default, else the first. Blocking, call it off the main
     * thread; needs the contacts permission and finds nothing without it.
     */
    fun numberOfContact(context: Context, lookupUri: String): String? {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return null
        return runCatching {
            val resolver = context.contentResolver
            val contact = ContactsContract.Contacts.lookupContact(resolver, lookupUri.toUri()) ?: return null
            val id = ContentUris.parseId(contact)
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id.toString()),
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.let(::clean)?.takeIf { it.isNotEmpty() } else null
            }
        }.getOrNull()
    }
}
