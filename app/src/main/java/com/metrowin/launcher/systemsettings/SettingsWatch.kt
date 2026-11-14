package com.metrowin.launcher.systemsettings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Counts the commands that ended: every page reads again what Android now says, so a switch never shows more than the phone does. */
internal object Nudge {
    val tick = MutableStateFlow(0)
    private val main = Handler(Looper.getMainLooper())

    /** A moment after the command, when Android has had time to apply it. */
    fun soon() { main.postDelayed({ tick.value++ }, 600) }
}

/** Runs the command through Shizuku, off the main thread; a refusal is said at once and the page goes back to what is true. */
internal fun exec(context: Context, vararg args: String) {
    val app = context.applicationContext
    SettingsPrefs.log(app, arrayOf(*args))
    Thread {
        val result = ShizukuShell.run(arrayOf(*args))
        if (!result.ok) Handler(Looper.getMainLooper()).post { MetroToast.show(app, R.string.ssx_failed) }
        Nudge.soon()
    }.start()
}

/** A signal each time one of the [actions] is broadcast or one of the [uris] changes. */
private fun changes(context: Context, actions: List<String>, uris: List<Uri>): Flow<Unit> = callbackFlow {
    val app = context.applicationContext
    val main = Handler(Looper.getMainLooper())
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { trySend(Unit) }
    }
    val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { trySend(Unit) }
    }
    if (actions.isNotEmpty()) ContextCompat.registerReceiver(app, receiver, IntentFilter().apply { actions.forEach(::addAction) }, ContextCompat.RECEIVER_NOT_EXPORTED)
    uris.forEach { app.contentResolver.registerContentObserver(it, true, observer) }
    awaitClose {
        if (actions.isNotEmpty()) app.unregisterReceiver(receiver)
        app.contentResolver.unregisterContentObserver(observer)
    }
}

/**
 * [read] now, and again when Android says it changed ([actions], [uris]) or a command of this app ended, only while the page is on screen.
 * shortcut: a slow read every [fallbackMs] stays as the net for makers that keep a switch where no broadcast or key tells; drop it when none is left.
 */
@Composable
internal fun <T> watched(actions: List<String> = emptyList(), uris: List<Uri> = emptyList(), fallbackMs: Long = 10_000, read: () -> T): T {
    val context = LocalContext.current
    var value by remember { mutableStateOf(read()) }
    val current by rememberUpdatedState(read)
    LaunchedWhileStarted(Unit) {
        value = current()
        merge(changes(context, actions, uris), Nudge.tick.drop(1).map { }, flow { while (true) { delay(fallbackMs); emit(Unit) } }).collect { value = current() }
    }
    return value
}
