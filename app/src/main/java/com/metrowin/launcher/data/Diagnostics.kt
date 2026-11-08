package com.metrowin.launcher.data

import android.util.Log

/**
 * Where the failures that `runCatching` absorbs are written: the log of the app, which the bug report ([BugReport]) already attaches.
 * Only the place, the kind of error and the start of its message are kept, never the data the code was working on. Used where a silent
 * failure costs the user something (Shizuku, notifications, calls, backup, sync); a failure that is the normal answer of the phone
 * (a permission not given yet) stays unlogged.
 */
object Diagnostics {
    private const val TAG = "MetroWin"
    private const val MESSAGE_MAX = 200

    fun describe(where: String, error: Throwable, withMessage: Boolean = true): String =
        "$where: ${error.javaClass.simpleName}${error.message?.takeIf { withMessage && it.isNotBlank() }?.let { ": " + it.take(MESSAGE_MAX) }.orEmpty()}"

    fun warn(where: String, error: Throwable, withMessage: Boolean = true) {
        Log.w(TAG, describe(where, error, withMessage))
    }
}

/**
 * `runCatching { ... }.logged("where")`: the same result, with a failure written to the log first. [withMessage] false keeps the message out,
 * for code whose errors carry the number or the text the user was working on (an intent prints its data).
 */
fun <T> Result<T>.logged(where: String, withMessage: Boolean = true): Result<T> = onFailure { Diagnostics.warn(where, it, withMessage) }
