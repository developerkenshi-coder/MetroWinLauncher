package com.metrowin.launcher.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.net.toUri
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

/**
 * The one place that watches contacts, calendar, call log and text messages. Each source has a single ContentObserver, registered with
 * the first listener and removed with the last; every listener gets the change after a short pause, so a burst of writes is one.
 * Without the permission (or when [enabled] is false) nothing is registered and the flow stays silent, as before.
 */
object DataChanges {
    enum class Source(val uri: Uri, val permission: String?) {
        Contacts(ContactsContract.Contacts.CONTENT_URI, LivePermissions.contacts),
        Calendar(CalendarContract.CONTENT_URI, LivePermissions.calendar),
        Calls(CallLog.Calls.CONTENT_URI, LivePermissions.calls),
        /** Text messages: the caller says when it may read them (READ_SMS and SEND_SMS, or the SMS role). */
        Messages("content://mms-sms/".toUri(), null),
    }

    const val PAUSE_MS = 400L

    private class Watch(val observer: ContentObserver, val listeners: MutableList<() -> Unit> = mutableListOf())

    private val watches = mutableMapOf<Source, Watch>()

    /** Number of listeners now on [source]; for tests. */
    fun listeners(source: Source): Int = synchronized(watches) { watches[source]?.listeners?.size ?: 0 }

    @OptIn(FlowPreview::class)
    fun flow(context: Context, source: Source, enabled: Boolean = true): Flow<Unit> = callbackFlow {
        val app = context.applicationContext
        val granted = enabled && (source.permission == null || LivePermissions.isGranted(app, source.permission))
        val listener: () -> Unit = { trySend(Unit) }
        val added = granted && add(app, source, listener)
        awaitClose { if (added) remove(app, source, listener) }
    }.debounce(PAUSE_MS)

    private fun add(app: Context, source: Source, listener: () -> Unit): Boolean = synchronized(watches) {
        val watch = watches[source] ?: run {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    val now = synchronized(watches) { watches[source]?.listeners?.toList() }.orEmpty()
                    now.forEach { it() }
                }
            }
            if (runCatching { app.contentResolver.registerContentObserver(source.uri, true, observer) }.isFailure) return false
            Watch(observer).also { watches[source] = it }
        }
        watch.listeners += listener
        true
    }

    private fun remove(app: Context, source: Source, listener: () -> Unit) = synchronized(watches) {
        val watch = watches[source] ?: return@synchronized
        watch.listeners -= listener
        if (watch.listeners.isEmpty()) {
            watches.remove(source)
            runCatching { app.contentResolver.unregisterContentObserver(watch.observer) }
        }
    }
}
