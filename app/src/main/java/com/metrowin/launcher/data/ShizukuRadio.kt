package com.metrowin.launcher.data

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Switches Wi-Fi and Bluetooth through Shizuku: since Android 10 an app cannot do it itself, but the shell can (`svc wifi enable`), and
 * Shizuku runs commands with the rights of the shell for the apps the user allowed. Everything here is a no-op that says "no" when Shizuku
 * is not installed, not running or not allowed, so the caller falls back to the panels of Android.
 */
object ShizukuRadio {
    private const val REQUEST_CODE = 4242

    /** The one place that decides: only when the user asked for it, Shizuku runs and has allowed this app. */
    fun route(useShizuku: Boolean, running: Boolean, granted: Boolean): Boolean = useShizuku && running && granted

    /** One line for the user when something does not work: whether the launcher has Shizuku's connection, whether it is allowed, and who the commands run as. */
    fun diagnosis(): String = runCatching {
        val binder = Shizuku.pingBinder()
        val permission = if (binder) (if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "yes" else "no") else "-"
        "binder: ${if (binder) "yes" else "no"} · permission: $permission" + if (binder) " · Shizuku ${Shizuku.getVersion()} · uid ${Shizuku.getUid()}" else ""
    }.getOrElse { "error: ${it.message}" }

    fun running(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun granted(): Boolean = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    /** Asks Shizuku to show its dialog that allows this app (the answer is read again when the launcher is back on screen). */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** The command that switches [action], or null for a button that is not a radio of this kind. */
    fun command(action: QuickAction, on: Boolean): Array<String>? {
        val word = if (on) "enable" else "disable"
        return when (action) {
            QuickAction.Wifi -> arrayOf("svc", "wifi", word)
            QuickAction.Bluetooth -> arrayOf("svc", "bluetooth", word)
            else -> null
        }
    }

    /** Runs the command off the main thread; [done] says whether it ended well. */
    fun switch(action: QuickAction, on: Boolean, done: (Boolean) -> Unit) {
        val args = command(action, on)
        if (args == null) done(false) else ShizukuShell.async(listOf(args), done)
    }
}
