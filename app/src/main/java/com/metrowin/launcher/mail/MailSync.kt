package com.metrowin.launcher.mail

import com.metrowin.launcher.data.Prefs
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.metrowin.launcher.R
import kotlin.concurrent.thread

/**
 * The look at the inbox of each account that runs by itself, every half hour, while there is a connection and the battery is not low:
 * a notification for what came in since the last look, and the unread count that the Mail tile shows. One job of Android, no service
 * that stays on. It exists only while there is an account (see [schedule]). With "Real-time mail" on, [MailIdleService] tells at once and
 * this job stays as the fallback.
 */
object MailSync {
    const val CHANNEL = "mail_new"
    private const val JOB_ID = 7303
    private const val NOTIFICATION_ID = 0x4D41
    private const val EVERY_MS = 30 * 60_000L
    private const val INBOX = "INBOX"

    private val checkLock = Any()

    private fun prefs(context: Context) = Prefs.open(context, Prefs.MAIL_SYNC)

    /** The messages to tell the user about: none on the first look at an account ([known] is null), else the ones above the UID of the last look. */
    fun toNotify(known: Long?, fresh: List<MailMessage>): List<MailMessage> =
        if (known == null) emptyList() else fresh.filter { it.id.toLong() > known }

    /** The unread messages of all the accounts, as the last look found them. */
    fun unread(context: Context): Int = prefs(context).all.entries.filter { it.key.startsWith("unread_") }.sumOf { (it.value as? Int) ?: 0 }

    /** Keeps the unread count of an account (the Mail screen also does it, so the tile follows what the user reads). */
    fun setUnread(context: Context, accountId: String, count: Int) = prefs(context).edit { putInt("unread_$accountId", count) }

    /** What is kept of an account that was removed. */
    fun forget(context: Context, accountId: String) = prefs(context).edit { remove("unread_$accountId"); remove("uid_$accountId") }

    /** Takes the notification of an account away: its mail is on screen. */
    fun dismiss(context: Context, accountId: String) =
        context.getSystemService(NotificationManager::class.java)?.cancel(accountId, NOTIFICATION_ID)

    /** Plans the job when there is an account and takes it away when there is none. Asking again when it is planned already changes nothing. */
    fun schedule(context: Context) {
        val accounts = MailRepository(context).loadAccounts().size
        MailIdle.sync(context, accounts)
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (accounts == 0) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        // A job that cannot be planned is only mail that is not looked at in the background: the launcher must start all the same.
        runCatching {
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, MailSyncJob::class.java))
                    .setPeriodic(EVERY_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build(),
            )
        }
    }

    /** Blocking, off the main thread. True when every account answered. */
    fun run(context: Context): Boolean {
        val repository = MailRepository(context)
        var allAnswered = true
        repository.loadAccounts().forEach { account ->
            val client = repository.client(account) ?: return@forEach
            try {
                lookAt(context, account, client)
            } catch (e: Exception) {
                allAnswered = false
            } finally {
                client.close()
            }
        }
        return allAnswered
    }

    /**
     * One look at the inbox of [account]: the notification for what came in since the last look and the unread count. One at a time, so the
     * job and the live connection ([MailIdleService]) never tell about the same message twice. Throws when the server cannot be reached.
     */
    fun lookAt(context: Context, account: MailAccount, client: MailClient) = synchronized(checkLock) {
        val store = prefs(context)
        val known = store.getLong("uid_${account.id}", -1L).takeIf { it >= 0 }
        val found = client.arrivals(INBOX, known)
        val news = toNotify(known, found.fresh)
        store.edit { putLong("uid_${account.id}", found.lastUid); putInt("unread_${account.id}", found.unread) }
        if (news.isNotEmpty()) notify(context, account, news)
    }

    private fun notify(context: Context, account: MailAccount, news: List<MailMessage>) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!allowed || manager == null) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_mail), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, account.id.hashCode(), Intent(context, MailActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val first = news.first()
        val text = if (news.size == 1) {
            "${first.senderName.ifBlank { first.senderAddress }}: ${first.subject}"
        } else {
            context.getString(R.string.mail_new_count, news.size)
        }
        manager.notify(
            account.id, NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_metro).setContentTitle(account.email).setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_EMAIL).setAutoCancel(true).setContentIntent(open).build(),
        )
    }
}

/** Runs [MailSync.run] when Android calls it. */
class MailSyncJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "mail-sync") { jobFinished(params, !MailSync.run(applicationContext)) }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
