package com.metrowin.launcher.systemsettings

import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** The pages of the app, in the order the pivot shows them. */
internal enum class Page { Wifi, Cellular, Bluetooth, DateTime, Sounds, Display, System, Info }

/** Plain rules of the screens, apart from Android so they can be tested. */
internal object SettingsLogic {
    /** The pages for the options in use: the reduced set (no Shizuku) only has what public APIs can read or change. */
    fun pages(reduced: Boolean, more: Boolean): List<Page> = when {
        reduced -> listOf(Page.Sounds, Page.Info)
        more -> Page.entries - Page.Info
        else -> listOf(Page.Wifi, Page.Cellular, Page.Bluetooth, Page.DateTime, Page.Sounds)
    }

    /** 0..4 bars, the way WifiManager counts them between -100 and -55 dBm. */
    fun signalBars(dbm: Int): Int = (dbm.coerceIn(-100, -55) + 100) * 4 / 45

    /** A secured network needs a passphrase of 8 characters or more; an open one needs none. */
    fun passwordOk(secured: Boolean, password: String): Boolean = !secured || password.length >= 8

    /** The name of the network the phone is on, from `cmd wifi status`; null when it is on none. */
    fun connectedSsid(status: String): String? = Regex("Wifi is connected to \"(.*)\"").find(status)?.groupValues?.get(1)

    /** The city of a zone id ("Europe/Rome" is "Rome", "America/Argentina/Buenos_Aires" is "Buenos Aires"). */
    fun city(id: String): String = id.substringAfterLast('/').replace('_', ' ')

    /** "Rome (UTC+1)", "Kolkata (UTC+5:30)": the offset the zone has at [at]. */
    fun zoneLabel(id: String, at: Long): String {
        val minutes = TimeZone.getTimeZone(id).getOffset(at) / 60_000
        val abs = abs(minutes)
        val offset = "UTC" + (if (minutes < 0) "-" else "+") + abs / 60 + (if (abs % 60 != 0) String.format(Locale.ROOT, ":%02d", abs % 60) else "")
        return "${city(id)} ($offset)"
    }

    /** The ids whose city or id has [query] in it, by city, at most [limit]; nothing for a query of one letter. */
    fun zoneMatches(ids: Collection<String>, query: String, limit: Int = 40): List<String> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return ids.filter { it.contains('/') && (it.contains(q, true) || city(it).contains(q, true)) }.sortedBy { city(it) }.take(limit)
    }

    /** [raw] is the value of the roaming key, [Int.MIN_VALUE] when it could not be read: then [phone] says, and a refusal of it means off. */
    fun roamingOn(raw: Int, phone: () -> Boolean): Boolean =
        if (raw == Int.MIN_VALUE) runCatching(phone).getOrDefault(false) else raw == 1

    /** AOSP keeps roaming per subscription as `data_roaming<id>` when the maker uses that; otherwise the plain key. */
    fun roamingKey(subId: Int, perSubscription: Boolean): String = if (subId > 0 && perSubscription) "data_roaming$subId" else "data_roaming"

    /** What goes into the log for a command: never the password of a network. */
    fun describe(args: Array<String>): String {
        val line = if ("connect-network" in args) args.take(4).joinToString(" ") else args.joinToString(" ")
        return line.replace('\n', ' ')
    }

    fun appendHistory(old: List<String>, line: String, max: Int = 50): List<String> = (listOf(line) + old).take(max)
}
