package com.metrowin.launcher.data

import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.RawContacts

object ContactImporter {

    /** Creates an intent to insert a contact in the system contacts app with prefilled name and phone number. */
    fun createInsertIntent(name: String = "", number: String = ""): Intent {
        return Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            if (name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
            if (number.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, number)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Programmatically inserts a contact directly into Android contacts for the chosen [source]. Returns true on success. */
    fun insertContact(context: Context, name: String, number: String, source: ContactSource): Boolean {
        if (name.isBlank() || number.isBlank()) return false
        if (!LivePermissions.isGranted(context, LivePermissions.contacts)) return false

        return runCatching {
            val ops = ArrayList<ContentProviderOperation>()

            val (accountType, accountName) = when (source) {
                ContactSource.Google -> "com.google" to "Google Account"
                ContactSource.Sim -> "com.android.sim" to "SIM"
                else -> null to null
            }

            val rawContactBuilder = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            if (accountType != null) {
                rawContactBuilder.withValue(RawContacts.ACCOUNT_TYPE, accountType)
                rawContactBuilder.withValue(RawContacts.ACCOUNT_NAME, accountName)
            } else {
                rawContactBuilder.withValue(RawContacts.ACCOUNT_TYPE, null)
                rawContactBuilder.withValue(RawContacts.ACCOUNT_NAME, null)
            }
            ops.add(rawContactBuilder.build())

            // Add Display Name
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // Add Phone Number
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, number)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .build()
            )

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        }.getOrDefault(false)
    }

    /** Intent to open a VCF / vCard file to import contacts into the phone address book. */
    fun createVcfImportIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/x-vcard")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
