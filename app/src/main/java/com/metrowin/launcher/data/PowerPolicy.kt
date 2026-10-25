package com.metrowin.launcher.data

import androidx.annotation.StringRes
import com.metrowin.launcher.R
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.StartEntry

/** When the launcher saves battery: by itself when the phone asks for it, always, or never. */
enum class BatterySaving(val code: String, @param:StringRes val label: Int, @param:StringRes val description: Int) {
    Auto("auto", R.string.saving_auto, R.string.saving_auto_desc),
    Always("always", R.string.saving_always, R.string.saving_always_desc),
    Never("never", R.string.saving_never, R.string.saving_never_desc);

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: Auto
    }
}

/**
 * What the launcher does differently to spare the battery. Saving is on when the user says so, or, in the automatic
 * mode, when Android's own battery saver is on or the battery is low and not charging. Outside of saving nothing is
 * given up except what no one sees: the launcher does no work while it is not on screen, saving or not.
 */
object PowerPolicy {
    /** The automatic mode starts saving at this charge, when the phone is not charging. */
    const val LOW_BATTERY_PERCENT = 20

    fun saving(mode: BatterySaving, androidBatterySaver: Boolean, battery: BatteryInfo?): Boolean = when (mode) {
        BatterySaving.Never -> false
        BatterySaving.Always -> true
        BatterySaving.Auto ->
            androidBatterySaver || (battery != null && battery.state == BatteryState.Discharging && battery.percent <= LOW_BATTERY_PERCENT)
    }

    /**
     * What the contacts, calendar, photos and call log say is read again after this long when the launcher comes back, kind by
     * kind: the next event and the missed calls change within minutes, the photos and the faces seldom, the birthdays almost never
     * (and reading them goes through every contact).
     */
    fun liveMaxAgeMs(kind: LiveKind, saving: Boolean): Long {
        val base = when (kind) {
            LiveKind.Photos -> 15 * MINUTE
            LiveKind.People -> 30 * MINUTE
            LiveKind.Storage -> 10 * MINUTE
            LiveKind.Birthdays -> 12 * HOUR
            else -> 2 * MINUTE
        }
        return if (saving) minOf(base * 6, 24 * HOUR) else base
    }

    /** The weather is fetched again when older than this. */
    fun weatherMaxAgeMs(saving: Boolean): Long = if (saving) 2 * HOUR else OpenMeteo.MAX_AGE_MILLIS

    /** A weather fetch is not tried again sooner than this, whether the last one worked or not: no connection must not mean a try at every return. */
    fun weatherRetryMs(saving: Boolean): Long = if (saving) 30 * MINUTE else 10 * MINUTE

    /** The icon packs (a search through every installed app) are looked for again after this long. */
    fun packagesMaxAgeMs(saving: Boolean): Long = if (saving) 30 * MINUTE else 5 * MINUTE

    /** An automatic sync waits this long after the last one: [baseGapMs] normally, four times as much when saving. */
    fun syncGapMs(baseGapMs: Long, saving: Boolean): Long = if (saving) baseGapMs * 4 else baseGapMs

    /** Whether tiles flip and photos cycle by themselves. Never while saving. */
    fun idleAnimations(saving: Boolean): Boolean = !saving

    /** Whether to ask the screen for its fastest refresh rate. Saving gives the 120 Hz up, whatever the setting says. */
    fun highRefreshRate(wanted: Boolean, saving: Boolean): Boolean = wanted && !saving

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
}

/** Which of the data behind the live tiles Start really shows, so the others are not read at all. */
object LiveSources {
    private val sources = setOf(LiveKind.Calendar, LiveKind.Photos, LiveKind.People, LiveKind.Phone, LiveKind.Storage, LiveKind.Alarm, LiveKind.Birthdays)

    /** The kinds of live tile among the apps pinned to Start (inside folders too) that need to read data from the phone. */
    fun wanted(pages: List<List<StartEntry>>, items: List<AppItem>): Set<LiveKind> {
        val pinned = StartPages.pinnedIds(pages)
        return items.filter { it.id in pinned }.mapNotNullTo(HashSet()) { it.live?.takeIf { kind -> kind in sources } }
    }

    val all: Set<LiveKind> get() = sources
}

/** Remembers when each kind of work last ran, so the launcher coming to the screen again and again does not redo it. */
class Throttle(private val now: () -> Long = System::currentTimeMillis) {
    private val last = HashMap<String, Long>()

    /** True (and the work counts as done now) when [key] never ran, or ran at least [minGapMs] ago, or the clock went back. */
    fun due(key: String, minGapMs: Long): Boolean {
        val t = now()
        val before = last[key]
        if (before != null && t >= before && t - before < minGapMs) return false
        last[key] = t
        return true
    }

    /** The next [due] for [key] is true. */
    fun reset(key: String) {
        last.remove(key)
    }
}
