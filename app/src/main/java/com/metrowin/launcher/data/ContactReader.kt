package com.metrowin.launcher.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Reads the contact that the user picked: its name, an address that opens it, and its photo. */
class ContactReader(private val context: Context) {
    class Picked(val name: String, val lookupUri: String, val photoJpeg: ByteArray?)

    /** Blocking, call it off the main thread, and while the access that the picker gave is still valid. */
    fun read(uri: Uri): Picked? = runCatching {
        val resolver = context.contentResolver
        var name: String? = null
        var lookupKey: String? = null
        var id = 0L
        resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts._ID),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)
                lookupKey = cursor.getString(1)
                id = cursor.getLong(2)
            }
        }
        val label = name?.takeIf { it.isNotBlank() } ?: return null
        val key = lookupKey ?: return null
        // The lookup address survives the contact being merged or its id changing.
        val lookupUri = ContactsContract.Contacts.getLookupUri(id, key)
        val photo = ContactsContract.Contacts.openContactPhotoInputStream(resolver, lookupUri, true)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let(::shrinkToJpeg)
        }
        Picked(label, lookupUri.toString(), photo)
    }.getOrNull()

    /** A number the user picked (a row of the phone table), with the name of its contact and its photo. */
    class PickedNumber(val name: String, val number: String, val photoJpeg: ByteArray?)

    /** Blocking, call it off the main thread, while the access that the picker gave is still valid. */
    fun readNumber(uri: Uri): PickedNumber? = runCatching {
        val resolver = context.contentResolver
        var name: String? = null
        var number: String? = null
        var lookupKey: String? = null
        var id = 0L
        resolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY, ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)
                number = cursor.getString(1)
                lookupKey = cursor.getString(2)
                id = cursor.getLong(3)
            }
        }
        val label = name?.takeIf { it.isNotBlank() } ?: return null
        val clean = number?.let(PhoneCalls::clean)?.takeIf { it.isNotEmpty() } ?: return null
        val photo = lookupKey?.let { key ->
            ContactsContract.Contacts.openContactPhotoInputStream(resolver, ContactsContract.Contacts.getLookupUri(id, key), true)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let(::shrinkToJpeg)
            }
        }
        PickedNumber(label, clean, photo)
    }.getOrNull()

    private fun shrinkToJpeg(bitmap: Bitmap): ByteArray {
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > MAX_SIDE) {
            val factor = MAX_SIDE.toFloat() / longest
            bitmap.scale((bitmap.width * factor).toInt(), (bitmap.height * factor).toInt())
        } else {
            bitmap
        }
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }.toByteArray()
    }

    private companion object {
        const val MAX_SIDE = 512
    }
}
