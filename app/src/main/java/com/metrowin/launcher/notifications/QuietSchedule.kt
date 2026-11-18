package com.metrowin.launcher.notifications

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.core.content.edit
import com.metrowin.launcher.MainActivity
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.SettingsStore
import java.time.LocalDateTime

/**
 * "Do not disturb" at the hours the user chose, every day, with the alarm and (if asked) the calls and messages of starred contacts let through.
 * From Android 10 it is a rule of Android's own do-not-disturb (shown in its settings too) that the launcher switches on and off at the two hours; before
 * that only alarms get through. Two alarms of Android a day, nothing runs in between. Needs the "do not disturb" access the quiet buttons already ask for.
 */
object QuietSchedule {
    const val ACTION = "com.metrowin.launcher.action.QUIET_SCHEDULE"
    private const val KEY_RULE = "quiet_rule_id"
    private const val KEY_APPLIED = "quiet_applied"

    /** Whether the hour [hour] (0-23) is inside the window that starts at [from] and ends at [to]; the window may pass midnight, and the same hour twice is no window. Pure. */
    fun inside(hour: Int, from: Int, to: Int): Boolean = when {
        from == to -> false
        from < to -> hour in from until to
        else -> hour >= from || hour < to
    }

    /** The next whole hour after [now] at which the window starts or ends. Pure. */
    fun nextChange(now: LocalDateTime, from: Int, to: Int): LocalDateTime {
        val top = now.withMinute(0).withSecond(0).withNano(0)
        return (1..24).map { top.plusHours(it.toLong()) }.first { inside(it.hour, from, to) != inside(now.hour, from, to) }
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 2, Intent(ACTION).setClass(context, AlertReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Makes what Android does match the settings now (switched on or off, the people let through) and sets the alarm of the next change. Call it after a setting changes, at boot and when the alarm rings. */
    fun sync(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alarms = context.getSystemService(AlarmManager::class.java)
        val settings = SettingsStore(context).load()
        val state = Prefs.main(context)
        if (!settings.quietSchedule || settings.quietFromHour == settings.quietToHour) {
            runCatching { alarms?.cancel(pending(context)) }
            release(manager, state)
            return
        }
        if (!manager.isNotificationPolicyAccessGranted) return
        val active = inside(now.hour, settings.quietFromHour, settings.quietToHour)
        runCatching { apply(context, manager, state, active, settings.quietPeople) }
        val at = nextChange(now, settings.quietFromHour, settings.quietToHour).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context)) }
    }

    private fun apply(context: Context, manager: NotificationManager, state: android.content.SharedPreferences, active: Boolean, people: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // shortcut: before Android 10 the rule cannot say who gets through, so only alarms do; the users of those phones are few.
            val applied = state.getBoolean(KEY_APPLIED, false)
            if (active) manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            else if (applied) manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            state.edit { putBoolean(KEY_APPLIED, active) }
            return
        }
        val who = if (people) ZenPolicy.PEOPLE_TYPE_STARRED else ZenPolicy.PEOPLE_TYPE_NONE
        val policy = ZenPolicy.Builder().allowAlarms(true).allowCalls(who).allowMessages(who).allowRepeatCallers(people)
            .allowMedia(false).allowSystem(false).allowReminders(false).allowEvents(false).build()
        val condition = Uri.parse("condition://${context.packageName}/quiet")
        val name = context.getString(R.string.quiet_rule_name)
        val rule = AutomaticZenRule(name, null, ComponentName(context, MainActivity::class.java), condition, policy, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
        var id = state.getString(KEY_RULE, null)
        if (id != null && manager.getAutomaticZenRule(id) == null) id = null
        if (id == null) id = manager.addAutomaticZenRule(rule).also { state.edit { putString(KEY_RULE, it) } }
        else manager.updateAutomaticZenRule(id, rule)
        manager.setAutomaticZenRuleState(id, Condition(condition, name, if (active) Condition.STATE_TRUE else Condition.STATE_FALSE))
    }

    /** The schedule is off (or its hours are the same): the rule goes away, and with it the quiet it made. */
    private fun release(manager: NotificationManager, state: android.content.SharedPreferences) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                state.getString(KEY_RULE, null)?.let { manager.removeAutomaticZenRule(it) }
            } else if (state.getBoolean(KEY_APPLIED, false) && manager.isNotificationPolicyAccessGranted) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
        state.edit { remove(KEY_RULE); remove(KEY_APPLIED) }
    }
}
