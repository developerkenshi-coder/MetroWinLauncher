package com.metrowin.launcher.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R

/**
 * What the Today card takes from the phone and the launcher does not already hold: the next event of the calendar and the next
 * alarm, read together when the app list comes up ([readAt] is when).
 */
@Immutable
data class TodayExtras(val readAt: Long = 0L, val event: LiveEvent? = null, val alarmMillis: Long? = null)

/** What the Today card writes at [nowMillis]. */
@Immutable
data class TodayInfo(
    val nowMillis: Long,
    val event: LiveEvent? = null,
    val alarmMillis: Long? = null,
    val weather: WeatherData? = null,
    /** The reminders still to come, the nearest first, no more than [Today.MAX_REMINDERS]. */
    val reminders: List<Reminder> = emptyList(),
    /** How many reminders have passed their time and are not done. */
    val missed: Int = 0,
    /** The tip of the day (a place in the list of tips), or -1 for none. */
    val tip: Int = -1,
    /** The date and the places near me are lines of their own that the user may switch off (the weather and the alarm are simply left out). */
    val showDate: Boolean = true,
    val showNear: Boolean = true,
) {
    /** Nothing but the date: no event, no alarm, no weather and no reminder. */
    val isQuiet: Boolean get() = event == null && alarmMillis == null && weather == null && reminders.isEmpty() && missed == 0
}

/** What a touch on the Today card does. The caller opens the page, the app or the map. */
sealed interface TodayAction {
    data object Calendar : TodayAction
    data object Weather : TodayAction
    data object Reminders : TodayAction
    data object Clock : TodayAction

    /** A search "near me" for the words in [what]. */
    data class Near(val what: String) : TodayAction

    /** A long press on a part of the card: it is switched off (it comes back in the settings of the search). */
    data class Hide(val part: TodayPart) : TodayAction
}

/** The parts of the Today card the user can switch off. */
enum class TodayPart { Weather, Near, Date, Alarm }

/** The places the chips of the Today card look for, around where the user is. */
enum class NearPlace(@param:StringRes val label: Int) {
    Pharmacy(R.string.near_pharmacy),
    Restaurants(R.string.near_restaurants),
    Petrol(R.string.near_petrol),
    Atm(R.string.near_atm),
}

/** What the Today card shows, kept pure so it is tested. */
object Today {
    const val MAX_REMINDERS = 2

    /** An event that began longer ago than this has surely ended: the calendar is asked again whenever the list comes up. */
    const val EVENT_STALE_MS = 6L * 60 * 60 * 1000

    /** The card from what is known now. An event that must be over, and an alarm that rang, are left out. */
    fun info(extras: TodayExtras, weather: WeatherData?, reminders: List<Reminder>, nowMillis: Long): TodayInfo = TodayInfo(
        nowMillis = nowMillis,
        event = extras.event?.takeIf { it.allDay || it.startMillis > nowMillis - EVENT_STALE_MS },
        alarmMillis = extras.alarmMillis?.takeIf { it > nowMillis },
        weather = weather,
        reminders = Reminders.upcoming(reminders, nowMillis).take(MAX_REMINDERS),
        missed = Reminders.missed(reminders, nowMillis).size,
    )
}
