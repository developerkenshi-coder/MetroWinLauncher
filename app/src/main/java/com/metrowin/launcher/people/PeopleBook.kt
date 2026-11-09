package com.metrowin.launcher.people

import android.content.Context
import android.provider.ContactsContract
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.data.LivePermissions
import java.text.Normalizer

/** A contact of the People hub: [key] is its lookup address, which survives merges. [photoUri] is the small photo, when there is one. */
@Immutable
data class Person(val key: String, val name: String, val starred: Boolean, val photoUri: String?)

/** Reads every contact for the "all" page of the People hub and finds what the card of one needs. Blocking: off the main thread. */
object PeopleBook {
    /** The contacts that have a name, in the order of the language of the phone; empty without the permission to read them. */
    fun read(context: Context): List<Person> {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return emptyList()
        return runCatching {
            val out = ArrayList<Person>()
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER}=1", null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val key = c.getString(1) ?: continue
                    out += Person(ContactsContract.Contacts.getLookupUri(c.getLong(0), key).toString(), name, c.getInt(3) == 1, c.getString(4))
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    /** The numbers of the contact at [lookupUri], the main one first. */
    fun numbersOf(context: Context, lookupUri: String): List<String> = runCatching {
        val found = LinkedHashSet<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY}=?", arrayOf(android.net.Uri.parse(lookupUri).pathSegments.getOrNull(2).orEmpty()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { while (it.moveToNext()) it.getString(0)?.filter { ch -> ch.isDigit() || ch == '+' }?.takeIf { n -> n.isNotEmpty() }?.let(found::add) }
        found.toList()
    }.getOrDefault(emptyList())

    /** The letter a name is listed under: its first letter without accents, in capitals; "#" for a name that does not start with one. */
    fun initial(name: String): String {
        val first = Normalizer.normalize(name.trim().take(1), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return "#"
        return if (first in 'A'..'Z') first.toString() else "#"
    }

    /** The contacts under their letters, "#" last. */
    fun grouped(people: List<Person>): List<Pair<String, List<Person>>> =
        people.groupBy { initial(it.name) }.toList().sortedWith(compareBy({ it.first == "#" }, { it.first }))

    /** True when two numbers are the same line however they are written (+39 333 1234567 and 3331234567): the last nine digits agree. */
    fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }
        val y = b.filter { it.isDigit() }
        if (x.isEmpty() || y.isEmpty()) return false
        return x.takeLast(NUMBER_TAIL) == y.takeLast(NUMBER_TAIL)
    }

    private const val NUMBER_TAIL = 9
}
