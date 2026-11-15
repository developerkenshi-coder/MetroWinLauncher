package com.metrowin.launcher.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.metrowin.launcher.MainActivity
import com.metrowin.launcher.R
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.notifications.Alerts
import com.metrowin.launcher.todo.TodoStore
import com.metrowin.launcher.todo.Todos
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The morning summary of an info tile: at the hour the tile says, one notification with what its faces say (the day's events, the alarm, the weather, what is
 * to do). One alarm of Android for each tile that asks for it and nothing runs in between; the receiver (see [com.metrowin.launcher.notifications.AlertReceiver])
 * wakes at that moment, reads what it needs, shows the notification and sets the next day's alarm. After a reboot the alarms are set again from what is kept.
 */
object InfoBrief {
    const val ACTION = "com.metrowin.launcher.action.INFO_BRIEF"
    private const val CHANNEL = "info_brief"
    private const val NOTIFICATION = 0x7300_0000

    /** The moment of the next [hour] o'clock after [now]. Pure. */
    fun nextAt(now: LocalDateTime, hour: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(hour, 0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun intent(context: Context, tileId: String) =
        Intent(context, com.metrowin.launcher.notifications.AlertReceiver::class.java).setAction(ACTION).putExtra(Alerts.EXTRA_ID, tileId)

    private fun pending(context: Context, tileId: String, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) =
        PendingIntent.getBroadcast(context, NOTIFICATION + (tileId.hashCode() and 0xFFFF), intent(context, tileId), flags or PendingIntent.FLAG_IMMUTABLE)

    /** Sets (or, with [hour] below 0, cancels) the alarm of the summary of the tile [tileId]. */
    fun schedule(context: Context, tileId: String, hour: Int) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pending(context, tileId)
        manager.cancel(pending)
        if (hour !in 0..23) return
        val at = nextAt(LocalDateTime.now(), hour).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
    }

    /** After a reboot or an update: every tile that asks for a summary has its alarm again. */
    fun rescheduleAll(context: Context) {
        ShortcutStore(context).load().filter { it.kind == ShortcutKind.Info }.forEach { def ->
            schedule(context, def.id, InfoConfig.decode(def.text).briefHour)
        }
    }

    /** The alarm of tile [tileId] rang: show the summary and set tomorrow's. */
    fun fire(context: Context, tileId: String) {
        val def = ShortcutStore(context).load().firstOrNull { it.id == tileId && it.kind == ShortcutKind.Info } ?: return
        val config = InfoConfig.decode(def.text)
        if (config.briefHour !in 0..23) return
        schedule(context, tileId, config.briefHour)
        val permitted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permitted) return
        val lines = lines(context, config, System.currentTimeMillis())
        if (lines.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.infot_brief_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_metro)
            .setContentTitle(context.getString(R.string.infot_brief_title))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION + (tileId.hashCode() and 0xFFFF), notification) }
    }

    /** What the tile says now, one line for each thing, for the faces it has (blocking: call it off the main thread, or from a receiver that waits for it). */
    fun lines(context: Context, config: InfoConfig, now: Long): List<String> {
        val zone = ZoneId.systemDefault()
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val out = ArrayList<String>()
        if (InfoPart.Calendar in config.parts || InfoPart.Day in config.parts || InfoPart.Week in config.parts) {
            val events = CalendarSearch(context).upcoming(now, 24 * 60 * 60_000L, 4, config.calendars)
            events.forEach { event ->
                val at = if (event.allDay) "" else Instant.ofEpochMilli(event.startMillis).atZone(zone).format(time) + " "
                out += at + if (config.hideTitles) context.getString(R.string.infot_event_hidden) else event.title
            }
        }
        if (InfoPart.Alarm in config.parts) {
            val alarm = NextAlarm.read(context)
            if (alarm > 0L) out += context.getString(R.string.infot_sum_alarm, Instant.ofEpochMilli(alarm).atZone(zone).format(time))
        }
        if (InfoPart.Weather in config.parts) {
            WeatherRepository(context).cached()?.let { out += context.getString(R.string.infot_sum_weather, it.temperature, context.getString(it.condition.label)) }
        }
        if (InfoPart.Todo in config.parts) {
            val open = Todos.open(TodoStore(context).load(), null, now).values.flatten().size
            if (open > 0) out += context.resources.getQuantityString(R.plurals.infot_sum_todo, open, open)
        }
        return out
    }
}
