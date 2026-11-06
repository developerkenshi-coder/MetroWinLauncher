package com.metrowin.launcher.mail

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import java.util.concurrent.Executors

/** The choices of "Real-time mail" that need no connection, so they are tested with numbers. */
object MailIdle {
    /** Servers close an IDLE at 30 minutes: it is renewed before. */
    const val RENEW_MS = 25 * 60_000L
    private const val BASE_MS = 5_000L
    private const val MAX_MS = 15 * 60_000L
    private const val KEY = "realtime"

    fun enabled(context: Context): Boolean = Prefs.open(context, Prefs.MAIL_SYNC).getBoolean(KEY, false)

    fun setEnabled(context: Context, on: Boolean) {
        Prefs.open(context, Prefs.MAIL_SYNC).edit { putBoolean(KEY, on) }
        sync(context)
    }

    /** The service runs only when the user turned it on and there is an account to watch. */
    fun shouldRun(enabled: Boolean, accounts: Int): Boolean = enabled && accounts > 0

    /** The wait before connection number [attempt] (0 is the first retry): 5 s, doubling, never more than 15 minutes. */
    fun backoffMs(attempt: Int): Long = minOf(MAX_MS, BASE_MS shl attempt.coerceIn(0, 10))

    /** What to stop and what to start when the accounts watched go from [running] to [wanted]. */
    fun plan(running: Set<String>, wanted: Set<String>): Pair<Set<String>, Set<String>> = (running - wanted) to (wanted - running)

    /** Starts the service or stops it, as [shouldRun] says. Asking again changes nothing. */
    fun sync(context: Context, accounts: Int = MailRepository(context).loadAccounts().size) {
        val intent = Intent(context, MailIdleService::class.java)
        // A start the system refuses (the launcher is not in view) only leaves the 30-minute job to do the work.
        runCatching {
            if (shouldRun(enabled(context), accounts)) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }
}

/**
 * Keeps one IMAP IDLE connection on the inbox of each account while "Real-time mail" is on, and looks at the inbox ([MailSync.lookAt]) when
 * the server says something came in. Foreground service of the "special use" kind, with a notification that has a button to stop it.
 * A server without IDLE is left to the 30-minute job of [MailSync]. Nothing runs while there is no network.
 */
class MailIdleService : Service() {
    private val lock = Object()
    private val workers = HashMap<String, Worker>()
    private val unsupported = HashSet<String>()
    private val lookups = Executors.newSingleThreadExecutor()
    @Volatile private var online = true
    private var watching = false

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = setOnline(true)

        override fun onLost(network: Network) {
            val manager = getSystemService(ConnectivityManager::class.java)
            setOnline(manager?.activeNetwork != null)
            // The connections made on the lost network are dead; they are made again at once when another one is there.
            synchronized(lock) { workers.values.toList() }.forEach { it.client?.stopIdle() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A service started as a foreground one must say so at once.
        showNotification()
        if (intent?.action == STOP) {
            MailIdle.setEnabled(this, false)
            return stop()
        }
        val accounts = MailRepository(this).loadAccounts()
        if (!MailIdle.shouldRun(MailIdle.enabled(this), accounts.size)) return stop()
        if (!watching) {
            watching = true
            val manager = getSystemService(ConnectivityManager::class.java)
            online = manager?.activeNetwork != null
            manager?.registerDefaultNetworkCallback(network)
        }
        synchronized(lock) {
            val (gone, added) = MailIdle.plan(workers.keys, accounts.map { it.id }.toSet())
            gone.forEach { workers.remove(it)?.end() }
            accounts.filter { it.id in added }.forEach { workers[it.id] = Worker(it).also(Thread::start) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (watching) runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(network) }
        synchronized(lock) { workers.values.forEach { it.end() }; workers.clear() }
        lookups.shutdownNow()
    }

    private fun stop(): Int {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun setOnline(value: Boolean) = synchronized(lock) { online = value; lock.notifyAll() }

    private fun showNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_mail_idle), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, MailIdleService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MailActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val text = synchronized(lock) { unsupported.toList() }.let {
            if (it.isEmpty()) getString(R.string.mail_idle_active) else getString(R.string.mail_idle_partial, it.joinToString())
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_metro).setContentTitle(getString(R.string.channel_mail_idle)).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(0, getString(R.string.mail_idle_stop), stop).build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    /** A look at the inbox, off the connection that waits, with the phone kept awake for the time it takes. */
    private fun arrived(account: MailAccount) {
        lookups.execute {
            val power = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "metrowin:mailidle")
            power.acquire(60_000L)
            val client = MailRepository(applicationContext).client(account)
            try {
                client?.let { MailSync.lookAt(applicationContext, account, it) }
            } catch (_: Exception) {
                // The connection that waits will fail too and be made again; the job looks later anyway.
            } finally {
                client?.close()
                if (power.isHeld) power.release()
            }
        }
    }

    private inner class Worker(private val account: MailAccount) : Thread("mail-idle") {
        @Volatile private var stopped = false
        @Volatile var client: MailClient? = null

        fun end() {
            stopped = true
            client?.stopIdle()
            synchronized(lock) { lock.notifyAll() }
        }

        private fun waitUntil(ready: () -> Boolean, ms: Long = Long.MAX_VALUE) = synchronized(lock) {
            val end = if (ms == Long.MAX_VALUE) ms else System.currentTimeMillis() + ms
            while (!stopped && !ready() && System.currentTimeMillis() < end) lock.wait(if (end == Long.MAX_VALUE) 0 else end - System.currentTimeMillis())
        }

        override fun run() {
            var attempt = 0
            while (!stopped) {
                waitUntil({ online })
                if (stopped) break
                val idle = MailRepository(applicationContext).client(account) ?: break
                client = idle
                try {
                    val supported = idle.idle(INBOX, MailIdle.RENEW_MS, onConnected = { attempt = 0; arrived(account) }, onArrival = { arrived(account) })
                    if (!supported) {
                        synchronized(lock) { unsupported += account.email }
                        showNotification()
                        break
                    }
                } catch (_: Exception) {
                    // Dropped, refused or timed out: the wait below, then another try.
                } finally {
                    client = null
                    idle.close()
                }
                if (!stopped) waitUntil({ false }, MailIdle.backoffMs(attempt++))
            }
        }
    }

    private companion object {
        const val CHANNEL = "mail_idle"
        const val NOTIFICATION_ID = 0x4D49
        const val INBOX = "INBOX"
        const val STOP = "com.metrowin.launcher.mail.IDLE_STOP"
    }
}
