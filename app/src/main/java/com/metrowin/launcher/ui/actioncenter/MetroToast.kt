package com.metrowin.launcher.ui.actioncenter

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.notifications.NotificationEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * The toast of Windows Phone: the banner of the launcher, with the icon of the app, that comes down from the top and goes by itself.
 * Where no [MetroToastHost] is on screen (a service, the call screen) it falls back to the plain Android toast, so the message is never lost.
 */
object MetroToast {
    internal data class Message(val id: Long, val text: String, val packageName: String, val until: Long)

    private val now = MutableStateFlow<Message?>(null)
    internal val current: StateFlow<Message?> = now
    private val hosts = AtomicInteger()
    private var last = 0L

    fun show(context: Context, text: CharSequence) {
        if (hosts.get() == 0) {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
            return
        }
        synchronized(this) {
            last = maxOf(last + 1, System.currentTimeMillis())
            now.value = Message(last, text.toString(), context.packageName, System.currentTimeMillis() + BANNER_MS + FADE_MS)
        }
    }

    fun show(context: Context, @StringRes id: Int) = show(context, context.getString(id))

    internal fun attach() { hosts.incrementAndGet() }
    internal fun detach() { hosts.decrementAndGet() }
    internal fun clear(id: Long) { now.compareAndSet(now.value?.takeIf { it.id == id }, null) }

    private const val FADE_MS = 600L
}

/** Where the toasts of [MetroToast] show: once per screen (the launcher, a mini app). Give it the top centre of a Box that fills the screen. */
@Composable
fun MetroToastHost(modifier: Modifier = Modifier) {
    // Counted only while its screen is started: a launcher stopped behind another app must not swallow a toast.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var counted = false
        fun count(on: Boolean) {
            if (on == counted) return
            counted = on
            if (on) MetroToast.attach() else MetroToast.detach()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) count(true) else if (event == Lifecycle.Event.ON_STOP) count(false)
        }
        count(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); count(false) }
    }
    val message by MetroToast.current.collectAsState()
    val m = message?.takeIf { it.until > System.currentTimeMillis() } ?: return
    val context = LocalContext.current
    val entry = remember(m.id) {
        NotificationEntry(key = "toast:${m.id}", packageName = m.packageName, title = "", text = m.text, postTime = m.id, contentIntent = null, autoCancel = true)
    }
    val app = remember(m.packageName) { AppItem(id = "toast:${m.packageName}", label = "", launch = Launch.Link(""), packageName = context.packageName, pinOnly = true) }
    ToastBanner(entry = entry, app = app, hideText = false, onOpen = { MetroToast.clear(m.id) }, onDismiss = { MetroToast.clear(m.id) }, modifier = modifier)
}
