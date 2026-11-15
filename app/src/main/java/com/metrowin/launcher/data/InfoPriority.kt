package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.InfoRule
import com.metrowin.launcher.model.InfoSlot
import com.metrowin.launcher.model.RuleKind
import com.metrowin.launcher.model.TriggerKind

/** What the phone is doing now that the info tiles go by: Do Not Disturb, the Wi-Fi it is on, a Bluetooth audio device, the Kid's Corner or a guest. */
@Immutable
data class InfoContext(
    val dnd: Boolean = false,
    /** The name of the Wi-Fi network, without quotes; empty when not on one or not known. */
    val wifi: String = "",
    val btAudio: Boolean = false,
    /** The time zone of the phone now (an id). */
    val zone: String = "",
    val restricted: Boolean = false,
    /** "Hide what is written" is on for the live tiles: the chat face says who wrote, not what. */
    val hideText: Boolean = false,
)

/** Which faces an info tile turns through right now, when the user asked it to put first what matters. */
object InfoPriority {
    /** An event or an alarm this near (from now on) is what matters, unless the tile says another time. */
    const val SOON_MS = 60 * 60_000L

    /** From 23:00 to 06:00 only the time is shown, unless the tile says other hours. The end may be before the start: the night goes through midnight. */
    fun isNight(hour: Int, from: Int = 23, to: Int = 6): Boolean = when {
        from == to -> false
        from > to -> hour >= from || hour < to
        else -> hour in from until to
    }

    /** The battery counts when it is low and the phone is not charging. */
    fun lowBattery(battery: BatteryInfo?) = battery != null && battery.state == BatteryState.Discharging && battery.percent <= PowerPolicy.LOW_BATTERY_PERCENT

    /** Rain that is falling or about to (within the hour). */
    fun rainSoon(weather: WeatherData?): Boolean {
        val outlook = weather?.let { OpenMeteo.rainOutlook(it.condition, it.hours) } ?: return false
        return outlook.raining || (outlook.hours != null && outlook.hours <= 1)
    }

    /**
     * [faces] are the faces that have something to say, in the order of the tile. Smart: at night only the time; what is urgent (an event or an
     * alarm within [soonMs], a low battery, rain about to fall) stays alone (all of them: the nearer event or alarm first, then the others), the
     * battery that is neither low nor charging is left out; otherwise all of them, as they are. Never empty when [faces] is not.
     */
    fun order(
        faces: List<InfoPart>,
        smart: Boolean,
        nowMs: Long,
        hour: Int,
        eventStartMs: Long?,
        alarmMs: Long,
        soonMs: Long = SOON_MS,
        nightFrom: Int = 23,
        nightTo: Int = 6,
        battery: BatteryInfo? = null,
        rainSoon: Boolean = false,
        smartBattery: Boolean = true,
        smartRain: Boolean = true,
    ): List<InfoPart> {
        if (!smart || faces.size < 2) return faces
        if (isNight(hour, nightFrom, nightTo) && InfoPart.Time in faces) return listOf(InfoPart.Time)
        val timed = buildList {
            if (InfoPart.Calendar in faces && eventStartMs != null && eventStartMs in (nowMs - 15 * 60_000L)..(nowMs + soonMs)) add(InfoPart.Calendar to eventStartMs)
            if (InfoPart.Alarm in faces && alarmMs > nowMs && alarmMs <= nowMs + soonMs) add(InfoPart.Alarm to alarmMs)
        }.sortedBy { it.second }.map { it.first }
        val urgent = timed +
            (if (smartBattery && InfoPart.Battery in faces && lowBattery(battery)) listOf(InfoPart.Battery) else emptyList()) +
            (if (smartRain && InfoPart.Weather in faces && rainSoon) listOf(InfoPart.Weather) else emptyList())
        if (urgent.isNotEmpty()) return urgent
        // Charging or full shows the battery (that is what the user wants to see then); at any other charge it is only noise.
        if (smartBattery && InfoPart.Battery in faces && battery != null && battery.state == BatteryState.Discharging) {
            return faces.filter { it != InfoPart.Battery }.ifEmpty { faces }
        }
        return faces
    }
}

/**
 * Which faces an info tile turns through right now, from what the user chose and what the phone says: the faces of the tile, or of the plan
 * that holds now (a Wi-Fi or a Bluetooth device, an hour of the day), only the time and the weather in the Kid's Corner, only the time and the
 * alarm with Do Not Disturb; then the faces tied to a condition that does not hold, and those with nothing to say, go; then the smart order.
 */
object InfoPlan {
    /** What the plan needs to know of the moment. [hasData] says whether a face has something to show (a face with nothing is left out of the turn). */
    class Moment(
        val nowMs: Long,
        val hour: Int,
        val battery: BatteryInfo?,
        val weather: WeatherData?,
        val eventStartMs: Long?,
        val alarmMs: Long,
        val context: InfoContext = InfoContext(),
        val hasData: (InfoPart) -> Boolean = { true },
    )

    /** The slot of the day that holds at [hour]: the latest one that began, or the last of yesterday before the first of today. */
    fun slotAt(slots: List<InfoSlot>, hour: Int): InfoSlot? =
        slots.filter { it.hour <= hour }.maxByOrNull { it.hour } ?: slots.maxByOrNull { it.hour }

    /** Whether the Wi-Fi or Bluetooth plan [kind]/[value] holds now. */
    fun holds(kind: TriggerKind, value: String, context: InfoContext) = when (kind) {
        TriggerKind.Wifi -> value.isNotBlank() && context.wifi.equals(value.trim(), ignoreCase = true)
        TriggerKind.Bluetooth -> context.btAudio
    }

    /** Whether [rule] lets its face show at [moment]. */
    fun allows(rule: InfoRule, moment: Moment): Boolean = when (rule.kind) {
        RuleKind.BatteryBelow -> moment.battery != null && moment.battery.percent < rule.a
        RuleKind.Charging -> moment.battery != null && moment.battery.state != BatteryState.Discharging
        RuleKind.EventWithin -> moment.eventStartMs != null && moment.eventStartMs <= moment.nowMs + rule.a * 60_000L && moment.eventStartMs >= moment.nowMs - 15 * 60_000L
        RuleKind.Raining -> InfoPriority.rainSoon(moment.weather)
        RuleKind.NotBetween -> !InfoPriority.isNight(moment.hour, rule.a, rule.b)
    }

    fun parts(config: InfoConfig, moment: Moment, saving: Boolean): List<InfoPart> {
        val context = moment.context
        val base = when {
            config.kids && context.restricted -> listOf(InfoPart.Time, InfoPart.Weather)
            config.dnd && context.dnd -> listOf(InfoPart.Time, InfoPart.Alarm)
            else -> config.triggers.firstOrNull { holds(it.kind, it.value, context) }?.parts
                ?: slotAt(config.slots, moment.hour)?.parts
                ?: config.parts
        }
        val allowed = base.filter { part -> config.rules.filter { it.part == part }.all { allows(it, moment) } }
        val available = allowed.filter(moment.hasData).ifEmpty { listOf(InfoPart.Time) }
        if (config.saver && saving) return listOf(InfoPart.Time)
        // In the saving mode nothing turns: the face on show is the one that matters most now.
        val smart = config.smart || saving
        return InfoPriority.order(
            available, smart, moment.nowMs, moment.hour, moment.eventStartMs, moment.alarmMs,
            soonMs = config.soonMinutes * 60_000L, nightFrom = config.nightFrom, nightTo = config.nightTo,
            battery = moment.battery, rainSoon = InfoPriority.rainSoon(moment.weather), smartBattery = config.smartBattery, smartRain = config.smartRain,
        )
    }
}

/** The face each info tile is showing now, so that a touch can open what it shows, and the colour a tile that follows what it says has now. */
object InfoTileState {
    private val shown = java.util.concurrent.ConcurrentHashMap<String, InfoPart>()
    private val tints = mutableStateMapOf<String, Int>()
    private val summaries = mutableStateMapOf<String, String>()
    private val targets = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The face now on show; [target] is what a touch opens in it when it is more than the app (the address of a photo, the day of an event). */
    fun show(id: String, part: InfoPart, target: String? = null) {
        shown[id] = part
        if (target == null) targets.remove(id) else targets[id] = target
    }

    fun current(id: String): InfoPart? = shown[id]

    fun target(id: String): String? = targets[id]

    /** What the tile says, in words, for the screen reader. */
    fun summary(id: String): String? = summaries[id]

    fun setSummary(id: String, text: String) {
        if (summaries[id] != text) summaries[id] = text
    }

    /** The colour (ARGB) the tile [id] takes now, or null for its own. */
    fun tint(id: String): Int? = tints[id]

    fun setTint(id: String, color: Int?) {
        if (color == null) tints.remove(id) else if (tints[id] != color) tints[id] = color
    }

    /** The tile is gone from Start. */
    fun forget(id: String) {
        shown.remove(id)
        tints.remove(id)
        summaries.remove(id)
        targets.remove(id)
    }
}
