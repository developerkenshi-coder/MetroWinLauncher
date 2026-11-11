package com.metrowin.launcher.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.core.content.edit

/**
 * The missed calls the user has not looked at: the number on the Phone tile. Android keeps a "new" flag on each missed call that only
 * the phone app of the system clears, so with the flag alone the number never went away; the launcher also writes down when the history
 * was last shown, and counts only the calls after that.
 */
object MissedCalls {
    private const val PREFS = "missed_calls"
    private const val SEEN_AT = "seen_at"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The moment (epoch milliseconds) before which a missed call counts as seen. */
    fun seenAt(context: Context): Long = prefs(context).getLong(SEEN_AT, 0L)

    /** The history was shown: what is in the log now is seen. Also clears the flag in the log when Android lets the launcher write it. Blocking: off the main thread. */
    fun markSeen(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit { putLong(SEEN_AT, now) }
        if (LivePermissions.isGranted(context, Manifest.permission.WRITE_CALL_LOG)) {
            runCatching {
                context.contentResolver.update(
                    CallLog.Calls.CONTENT_URI,
                    ContentValues().apply { put(CallLog.Calls.NEW, 0); put(CallLog.Calls.IS_READ, 1) },
                    "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.NEW}=1",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                )
            }
        }
        // Only the phone app of the system may do this; the notice of Android about the missed call goes with the look at the history.
        runCatching { context.getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification() }
    }
}
