package com.metrowin.launcher.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.metrowin.launcher.MainActivity
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.ReminderStore
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.ShortcutStore
import com.metrowin.launcher.model.TimerState

/**
 * The two things the launcher does when it is not on screen, both at a moment the user chose: a timer that ends and a reminder
 * that is due. Each is one alarm of Android and nothing runs in between; the receiver wakes at that moment, shows a notification
 * and goes back to sleep. After a reboot or an update of the launcher the alarms are set again from what is kept.
 */
object Alerts {
    const val CHANNEL_TIMER = "timer"
    const val CHANNEL_REMINDER = "reminder"

    const val ACTION_TIMER = "com.metrowin.launcher.action.TIMER_DONE"
    const val ACTION_REMINDER = "com.metrowin.launcher.action.REMINDER_DUE"
    const val ACTION_REMINDER_DONE = "com.metrowin.launcher.action.REMINDER_DONE"
    const val ACTION_REMINDER_SNOOZE = "com.metrowin.launcher.action.REMINDER_SNOOZE"
    const val ACTION_TIMER_STOP = "com.metrowin.launcher.action.TIMER_STOP"
    const val ACTION_QUIET_END = "com.metrowin.launcher.action.QUIET_END"
    const val ACTION_TORCH_OFF = "com.metrowin.launcher.action.TORCH_OFF"

    const val EXTRA_ID = "id"
    const val EXTRA_TEXT = "text"

    /** What an activity of the launcher is asked to open: the page of the timer, or the reminders. */
    const val EXTRA_OPEN = "open"
    const val OPEN_REMINDERS = "reminders"
    const val OPEN_TIMER_PREFIX = "timer:"

    const val SNOOZE_MS = 10 * 60_000L

    private const val TIMER_NOTIFICATION = 0x7100_0000
    private const val REMINDER_NOTIFICATION = 0x7200_0000

    private fun quietEndIntent(context: Context) = android.app.PendingIntent.getBroadcast(
        context, 0, Intent(ACTION_QUIET_END).setClass(context, AlertReceiver::class.java),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    /** Asks Android to end the quiet hours the launcher switched on, in [minutes] (an inexact alarm: it may come a little late). */
    fun scheduleQuietEnd(context: Context, minutes: Int) {
        val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        runCatching { alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L, quietEndIntent(context)) }
    }

    fun cancelQuietEnd(context: Context) {
        runCatching { context.getSystemService(android.app.AlarmManager::class.java)?.cancel(quietEndIntent(context)) }
    }

    private fun torchOffIntent(context: Context) = android.app.PendingIntent.getBroadcast(
        context, 1, Intent(ACTION_TORCH_OFF).setClass(context, AlertReceiver::class.java),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    /** Asks Android to put the flashlight out in [minutes] (an inexact alarm: it may come a little late). */
    fun scheduleTorchOff(context: Context, minutes: Int) {
        val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        runCatching { alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L, torchOffIntent(context)) }
    }

    fun cancelTorchOff(context: Context) {
        runCatching { context.getSystemService(android.app.AlarmManager::class.java)?.cancel(torchOffIntent(context)) }
    }

    /** The time is up: everything is let through again, if the launcher is still allowed to say so. */
    fun onQuietEnd(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.isNotificationPolicyAccessGranted) runCatching { manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alarm = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_timer_desc)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarm)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reminder_desc)
                enableVibration(true)
            },
        )
    }

    // --- the alarms

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun broadcast(context: Context, action: String, requestCode: Int, id: String, text: String = ""): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlertReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id).putExtra(EXTRA_TEXT, text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun open(context: Context, what: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_OPEN, what),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun timerCode(tileId: String) = tileId.hashCode()

    /** A timer that ends at [endAt] (epoch milliseconds): an alarm clock of Android, which no battery saving holds back. */
    fun scheduleTimer(context: Context, tileId: String, label: String, endAt: Long) {
        val manager = alarms(context) ?: return
        val operation = broadcast(context, ACTION_TIMER, timerCode(tileId), tileId, label)
        runCatching { manager.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, open(context, OPEN_TIMER_PREFIX + tileId, timerCode(tileId))), operation) }
    }

    fun cancelTimer(context: Context, tileId: String) {
        alarms(context)?.cancel(broadcast(context, ACTION_TIMER, timerCode(tileId), tileId))
        NotificationManagerCompat.from(context).cancel(TIMER_NOTIFICATION + (timerCode(tileId) and 0xFFFF))
    }

    /** A reminder due at its time. Exact where Android lets the launcher ask for it, and within a few minutes otherwise. */
    fun scheduleReminder(context: Context, reminder: Reminder) {
        val manager = alarms(context) ?: return
        val operation = broadcast(context, ACTION_REMINDER, reminder.id.toInt(), reminder.id.toString())
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        val set = runCatching {
            if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, operation) else throw SecurityException()
        }.isSuccess
        if (!set) runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, operation) }
    }

    fun cancelReminder(context: Context, id: Long) {
        alarms(context)?.cancel(broadcast(context, ACTION_REMINDER, id.toInt(), id.toString()))
        NotificationManagerCompat.from(context).cancel(REMINDER_NOTIFICATION + (id.toInt() and 0xFFFF))
    }

    /** Whether Android lets the launcher set a reminder to the minute (Android 12 and later ask the user for it). */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms(context)?.canScheduleExactAlarms() == true

    // --- the moment comes

    fun onTimerDone(context: Context, tileId: String, label: String) {
        ensureChannels(context)
        val title = label.ifBlank { context.getString(R.string.timer_default_label) }
        if (!notificationsAllowed(context)) {
            ring(context)
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_metro)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.timer_done))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open(context, OPEN_TIMER_PREFIX + tileId, timerCode(tileId)))
            .addAction(0, context.getString(R.string.timer_stop), broadcast(context, ACTION_TIMER_STOP, timerCode(tileId), tileId))
            .build()
        post(context, TIMER_NOTIFICATION + (timerCode(tileId) and 0xFFFF), notification)
    }

    fun onReminderDue(context: Context, id: Long) {
        val store = ReminderStore(context)
        val reminder = store.load().firstOrNull { it.id == id && !it.done } ?: return
        store.save(Reminders.markRang(store.load(), id))
        notifyReminder(context, reminder)
    }

    private fun notifyReminder(context: Context, reminder: Reminder) {
        ensureChannels(context)
        if (!notificationsAllowed(context)) {
            ring(context)
            return
        }
        val code = reminder.id.toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_metro)
            .setContentTitle(reminder.text)
            .setContentText(context.getString(R.string.reminder_due))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open(context, OPEN_REMINDERS, code))
            .addAction(0, context.getString(R.string.reminder_done), broadcast(context, ACTION_REMINDER_DONE, code, reminder.id.toString()))
            .addAction(0, context.getString(R.string.reminder_snooze), broadcast(context, ACTION_REMINDER_SNOOZE, code, reminder.id.toString()))
            .build()
        post(context, REMINDER_NOTIFICATION + (code and 0xFFFF), notification)
    }

    fun onReminderDone(context: Context, id: Long) {
        val store = ReminderStore(context)
        store.save(Reminders.complete(store.load(), id))
        cancelReminder(context, id)
    }

    fun onReminderSnooze(context: Context, id: Long, now: Long = System.currentTimeMillis()) {
        val store = ReminderStore(context)
        val list = Reminders.reschedule(store.load(), id, now + SNOOZE_MS)
        store.save(list)
        NotificationManagerCompat.from(context).cancel(REMINDER_NOTIFICATION + (id.toInt() and 0xFFFF))
        list.firstOrNull { it.id == id }?.let { scheduleReminder(context, it) }
    }

    /** After a reboot or an update the alarms are gone: set again the ones still to come, and tell of the ones that came due meanwhile. */
    fun rescheduleAll(context: Context, now: Long = System.currentTimeMillis()) {
        ShortcutStore(context).load().filter { it.kind == ShortcutKind.Timer }.forEach { def ->
            val state = TimerState.decode(def.text)
            if (!state.running) return@forEach
            if (state.endAt > now) scheduleTimer(context, def.id, def.label, state.endAt) else onTimerDone(context, def.id, def.label)
        }
        val store = ReminderStore(context)
        store.load().filter { !it.done }.forEach { reminder ->
            when {
                reminder.at > now -> scheduleReminder(context, reminder)
                !reminder.rang -> {
                    store.save(Reminders.markRang(store.load(), reminder.id))
                    notifyReminder(context, reminder)
                }
            }
        }
    }

    // --- without notifications

    private fun notificationsAllowed(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Posts [notification]; false when Android 13 or later has not been given the permission, or anything else stops it. */
    private fun post(context: Context, id: Int, notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching { NotificationManagerCompat.from(context).notify(id, notification) }.isSuccess
    }

    /**
     * Notifications are off for the launcher (Android 13 asks for them): a timer that ends in silence would be of no use, so the
     * alarm sound plays by itself for a few seconds.
     */
    private fun ring(context: Context) {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        runCatching { ringtone.play() }
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { ringtone.stop() } }, RING_MS)
    }

    private const val RING_MS = 8_000L
}

/** Wakes at the moment of a timer or a reminder, and after a reboot or an update to set the alarms again. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Alerts.EXTRA_ID).orEmpty()
        when (intent.action) {
            Alerts.ACTION_TIMER -> Alerts.onTimerDone(context, id, intent.getStringExtra(Alerts.EXTRA_TEXT).orEmpty())
            Alerts.ACTION_QUIET_END -> Alerts.onQuietEnd(context)
            Alerts.ACTION_TORCH_OFF -> com.metrowin.launcher.data.switchTorchOff(context)
            Alerts.ACTION_TIMER_STOP -> androidx.core.app.NotificationManagerCompat.from(context).cancel(0x7100_0000 + (id.hashCode() and 0xFFFF))
            Alerts.ACTION_REMINDER -> id.toLongOrNull()?.let { Alerts.onReminderDue(context, it) }
            Alerts.ACTION_REMINDER_DONE -> id.toLongOrNull()?.let { Alerts.onReminderDone(context, it) }
            Alerts.ACTION_REMINDER_SNOOZE -> id.toLongOrNull()?.let { Alerts.onReminderSnooze(context, it) }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Alerts.rescheduleAll(context)
        }
    }
}
