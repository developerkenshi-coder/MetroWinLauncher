package com.metrowin.launcher.data

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R

/**
 * Where a contact is kept: in a Google account, in the phone itself, on the SIM, or in another account (Exchange, WhatsApp and so on).
 * Android merges them all in one list; the dialer lets the user show only some, as a mask of the [bit]s.
 */
enum class ContactSource(val bit: Int, @param:StringRes val label: Int) {
    Google(1, R.string.dialer_source_google),
    Phone(2, R.string.dialer_source_phone),
    Sim(4, R.string.dialer_source_sim),
    Other(8, R.string.dialer_source_other);

    companion object {
        const val ALL = 15

        /** The source of a contact kept in an account of [accountType]; no account at all (or the phone's own) is the phone. */
        fun of(accountType: String?): ContactSource {
            val type = accountType?.lowercase().orEmpty()
            return when {
                type == "com.google" -> Google
                "sim" in type || "usim" in type -> Sim
                type.isEmpty() || "local" in type || type.endsWith(".phone") || type == "com.android.contacts" -> Phone
                else -> Other
            }
        }

        fun has(mask: Int, source: ContactSource) = mask and source.bit != 0

        fun toggle(mask: Int, source: ContactSource) = mask xor source.bit
    }
}

/** A contact with a number for the address book of the dialer. [sources] is the mask of every place the contact is kept. */
@Immutable
data class DialerContact(
    val name: String, val number: String, val lookupUri: String, val sources: Int,
    /** Every number of the contact (type label, number), the main one first; [number] is the first. Empty when only one is known. */
    val numbers: List<Pair<Int, String>> = emptyList(),
    /** The small picture of the contact, if it has one. */
    val photoUri: String? = null,
) {
    /** Whether the contact is in one of the places of [mask]. */
    fun shownIn(mask: Int) = sources and mask != 0
}

/** Reads the address book with the place of each contact. Blocking: off the main thread; empty without the permission to read contacts. */
class DialerContactReader(private val context: Context) {
    fun read(): List<DialerContact> {
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return emptyList()
        return runCatching {
            val resolver = context.contentResolver
            val sources = HashMap<Long, Int>()
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.DELETED}=0", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    sources[id] = (sources[id] ?: 0) or ContactSource.of(c.getString(1)).bit
                }
            }
            val found = LinkedHashMap<Long, DialerContact>()
            resolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.PHOTO_THUMBNAIL_URI),
                null, null, "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC, ${Phone.IS_SUPER_PRIMARY} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val number = c.getString(3)?.let(PhoneCalls::clean)?.takeIf { it.isNotEmpty() } ?: continue
                    val kept = found[id]
                    if (kept != null) {
                        // A contact with several numbers is listed once, with the one that comes first (the default one); the others wait in [numbers].
                        if (kept.numbers.none { it.second == number }) found[id] = kept.copy(numbers = kept.numbers + (c.getInt(4) to number))
                        continue
                    }
                    val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val key = c.getString(1) ?: continue
                    found[id] = DialerContact(
                        name, number, ContactsContract.Contacts.getLookupUri(id, key).toString(), sources[id] ?: ContactSource.Phone.bit,
                        listOf(c.getInt(4) to number), c.getString(5),
                    )
                }
            }
            found.values.toList()
        }.getOrDefault(emptyList())
    }
}
