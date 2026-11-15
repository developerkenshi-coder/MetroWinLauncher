package com.metrowin.launcher.model

import androidx.compose.runtime.Immutable

/**
 * What an info tile can say. [key] is the name the tile keeps it by; [legacy] is the letter the first seven had before the tile could
 * turn through more (those letters are still written, so an older build reads the same tile as far as it knows it).
 */
enum class InfoPart(val key: String, val legacy: Char? = null) {
    Time("time", 't'), Calendar("calendar", 'c'), Alarm("alarm", 'a'), Weather("weather", 'w'), Battery("battery", 'b'), Photos("photos", 'p'), Memories("memories", 'm'),

    // the day
    Week("week"), Day("day"), Countdown("countdown"), Timer("timer"), Sky("sky"),

    // the phone
    Todo("todo"), Reminders("reminders"), Birthdays("birthdays"), Waiting("waiting"), Music("music"), Steps("steps"),
    BatteryGraph("batterygraph"), People("people"), Chat("chat"), Published("published"), Note("note"), Parking("parking"),
    DataUse("datause"), AppsUsed("appsused"), Toggles("toggles"), Widget("widget"),

    // the weather and the web
    WeatherChart("wxchart"), Advice("advice"), News("news"), Finance("finance");

    companion object {
        fun ofKey(key: String): InfoPart? = entries.firstOrNull { it.key == key }
    }
}

/** How fast an info tile turns from one face to the next. */
enum class InfoPace(val code: Char, val scale: Float) { Slow('1', 1.8f), Normal('2', 1f), Fast('3', 0.55f) }

/** The tile turns through its faces, or lays several of them side by side (on a wide or large tile). */
enum class InfoLayout(val code: Char) { Turn('t'), Dashboard('d') }

/** What a face may be tied to, so that it only shows while it holds. */
enum class RuleKind(val key: String) { BatteryBelow("bat"), Charging("chg"), EventWithin("evt"), Raining("rain"), NotBetween("nb") }

/** A face that shows only when [kind] holds ([a] and [b] are its numbers: a charge, minutes, or the hours of a night). */
@Immutable
data class InfoRule(val part: InfoPart, val kind: RuleKind, val a: Int = 0, val b: Int = 0)

/** From [hour] of the day the tile turns through [parts] instead of its usual ones. */
@Immutable
data class InfoSlot(val hour: Int, val parts: List<InfoPart>)

enum class TriggerKind(val key: String) { Wifi("wifi"), Bluetooth("bt") }

/** While the phone is on the Wi-Fi called [value] (or has a Bluetooth audio device connected) the tile turns through [parts]. */
@Immutable
data class InfoTrigger(val kind: TriggerKind, val value: String, val parts: List<InfoPart>)

/**
 * What an info tile shows and how: the faces it turns through (in the order the user put them) and every choice about them. Kept in the shortcut as
 * a short string: the letters of the first seven faces and of the switches, then `;key=value` pairs (the keys this build does not know are kept as
 * they are, so a tile written by a newer build is not cut down when this one saves it).
 */
@Immutable
data class InfoConfig(
    /** The faces that are on, in the order the tile turns through them. Never empty. */
    val parts: List<InfoPart> = DEFAULT_PARTS,
    /** Puts first what matters now: an event, an alarm, a low battery or rain soon stays on its own, at night only the time. */
    val smart: Boolean = false,
    /** While the phone saves battery the tile shows what matters most instead of turning. */
    val saver: Boolean = false,
    /** The time zones the time face shows, the first one is the main one; empty: the phone's. */
    val zones: List<String> = emptyList(),
    /** The names of those zones, as the user picked them (same order), to show them in the language of the app. */
    val zoneNames: List<String> = emptyList(),
    /** The album the photos come from (its id); empty: the latest photos of the phone. */
    val album: String = "",
    /** The name of that album, to show it. */
    val albumName: String = "",
    val pace: InfoPace = InfoPace.Normal,
    /** Each face stays as long as it takes to read it; off, every face stays the same time. */
    val dwell: Boolean = true,
    /** What counts as soon, in minutes, for the smart order. */
    val soonMinutes: Int = 60,
    /** The night of the smart order: from this hour to that one only the time shows. */
    val nightFrom: Int = 23,
    val nightTo: Int = 6,
    /** The smart order leaves the battery out unless it is low or charging, and puts rain that is about to fall first. */
    val smartBattery: Boolean = true,
    val smartRain: Boolean = true,
    val slots: List<InfoSlot> = emptyList(),
    val triggers: List<InfoTrigger> = emptyList(),
    val rules: List<InfoRule> = emptyList(),
    /** The calendars the events come from (their ids); empty: all of them. */
    val calendars: List<Long> = emptyList(),
    val hideTitles: Boolean = false,
    val hidePhotos: Boolean = false,
    /** The photos leave out screenshots and the folders in [excluded] (bucket ids). */
    val noShots: Boolean = false,
    val excluded: List<String> = emptyList(),
    val favorites: Boolean = false,
    /** The photos the user picked for this tile (their addresses); empty: the album or the latest. */
    val picked: List<String> = emptyList(),
    /** The memories of the days around this one: 0 only today, else this many days either side when today has none. */
    val memoryDays: Int = 0,
    /** The time face over a photo. */
    val photoBackground: Boolean = false,
    val analog: Boolean = false,
    /** Buttons for a timer of 5, 10 or 15 minutes on the time face. */
    val quickTimers: Boolean = false,
    /** The time at home, to show next to the local one while away from it. */
    val homeZone: String = "",
    val travel: Boolean = false,
    /** While Do Not Disturb is on, only the time and the alarm. */
    val dnd: Boolean = false,
    /** In the Kid's Corner or as a guest, only the time and the weather. */
    val kids: Boolean = false,
    /** Dots that say which face is on, and a touch on them turns to the next. */
    val dots: Boolean = false,
    /** A button on a memory to send the photo with "5 years ago today". */
    val shareMemory: Boolean = false,
    /** The colour of the tile follows what it says (a low battery, rain, the night). */
    val tint: Boolean = false,
    val layout: InfoLayout = InfoLayout.Turn,
    /** The city the weather is of (the key of a followed city, see SavedCity); empty: the city of the weather tile. */
    val weatherCity: String = "",
    val weatherCityName: String = "",
    /** The day (an epoch day) and the name of the countdown face; 0: none. */
    val countdownDay: Long = 0L,
    val countdownName: String = "",
    /** The note (its id) the note face shows, the app whose published text the apps face shows, the widget the widget face holds. */
    val note: String = "",
    val published: String = "",
    val widget: Int = 0,
    /** The hour of the morning summary, as a notification; -1: none. */
    val briefHour: Int = -1,
    /** The keys written by a newer build, kept as they are. */
    val extra: Map<String, String> = emptyMap(),
) {
    fun has(part: InfoPart) = part in parts

    /** The main time zone, or empty. */
    val zone: String get() = zones.firstOrNull().orEmpty()

    /** The data of the phone the tile needs read while it is on Start. */
    fun liveKinds(): Set<LiveKind> = buildSet {
        for (part in parts + slots.flatMap { it.parts } + triggers.flatMap { it.parts }) {
            when (part) {
                InfoPart.Calendar, InfoPart.Week, InfoPart.Day -> add(LiveKind.Calendar)
                InfoPart.Alarm -> add(LiveKind.Alarm)
                InfoPart.Photos -> add(LiveKind.Photos)
                InfoPart.Memories -> add(LiveKind.Memories)
                InfoPart.Todo -> add(LiveKind.Todo)
                InfoPart.Birthdays -> add(LiveKind.Birthdays)
                InfoPart.Waiting -> { add(LiveKind.Phone); add(LiveKind.Mail) }
                InfoPart.People -> add(LiveKind.People)
                else -> Unit
            }
        }
    }

    /** The faces that may show now: those of the tile and of its plans (a plan can bring a face the tile does not turn through usually). */
    fun everyPart(): Set<InfoPart> = parts.toSet() + slots.flatMap { it.parts } + triggers.flatMap { it.parts }

    fun encode(): String = buildString {
        InfoPart.entries.forEach { p -> if (p.legacy != null && p in parts) append(p.legacy) }
        if (smart) append('S')
        if (saver) append('E')
        if (parts != legacyOrder(parts)) append(";o=").append(parts.joinToString(",") { it.key })
        if (pace != InfoPace.Normal) append(";v=").append(pace.code)
        if (zones.isNotEmpty()) append(";z=").append(zones.joinToString(",") { enc(it) })
        if (zones.isNotEmpty() && zoneNames.isNotEmpty()) append(";zn=").append(zoneNames.joinToString(",") { enc(it) })
        if (album.isNotEmpty()) append(";a=").append(album)
        if (album.isNotEmpty() && albumName.isNotEmpty()) append(";n=").append(enc(albumName))
        if (!dwell) append(";dw=f")
        if (soonMinutes != 60) append(";sm=").append(soonMinutes)
        if (nightFrom != 23) append(";n0=").append(nightFrom)
        if (nightTo != 6) append(";n1=").append(nightTo)
        if (!smartBattery) append(";sb=0")
        if (!smartRain) append(";sr=0")
        if (slots.isNotEmpty()) append(";sl=").append(slots.joinToString("|") { "${it.hour}:" + it.parts.joinToString("+") { p -> p.key } })
        if (triggers.isNotEmpty()) append(";cx=").append(triggers.joinToString("|") { "${it.kind.key}~${enc(it.value)}:" + it.parts.joinToString("+") { p -> p.key } })
        if (rules.isNotEmpty()) append(";ru=").append(rules.joinToString("|") { "${it.part.key}.${it.kind.key}.${it.a}.${it.b}" })
        if (calendars.isNotEmpty()) append(";k=").append(calendars.joinToString(","))
        if (hideTitles) append(";h=1")
        if (hidePhotos) append(";hp=1")
        if (noShots) append(";ns=1")
        if (excluded.isNotEmpty()) append(";x=").append(excluded.joinToString(",") { enc(it) })
        if (favorites) append(";fv=1")
        if (picked.isNotEmpty()) append(";pk=").append(picked.take(MAX_PICKED).joinToString("|") { enc(it) })
        if (memoryDays != 0) append(";mw=").append(memoryDays)
        if (photoBackground) append(";bg=1")
        if (analog) append(";an=1")
        if (quickTimers) append(";qt=1")
        if (homeZone.isNotEmpty()) append(";hz=").append(enc(homeZone))
        if (travel) append(";tr=1")
        if (dnd) append(";dn=1")
        if (kids) append(";ks=1")
        if (dots) append(";d=1")
        if (shareMemory) append(";sp=1")
        if (tint) append(";cc=1")
        if (layout != InfoLayout.Turn) append(";ly=").append(layout.code)
        if (weatherCity.isNotEmpty()) append(";w=").append(enc(weatherCity))
        if (weatherCity.isNotEmpty() && weatherCityName.isNotEmpty()) append(";wn=").append(enc(weatherCityName))
        if (countdownDay != 0L) append(";cd=").append(countdownDay)
        if (countdownName.isNotEmpty()) append(";cn=").append(enc(countdownName))
        if (note.isNotEmpty()) append(";nt=").append(enc(note))
        if (published.isNotEmpty()) append(";tp=").append(enc(published))
        if (widget != 0) append(";wg=").append(widget)
        if (briefHour >= 0) append(";bf=").append(briefHour)
        extra.forEach { (key, value) -> append(';').append(key).append('=').append(value) }
    }

    /** [part] switched on (at the end of the order) or off; the last face on cannot be switched off: a tile with nothing to say is no tile. */
    fun toggled(part: InfoPart): InfoConfig {
        val next = if (part in parts) parts - part else parts + part
        return if (next.isEmpty()) this else copy(parts = next)
    }

    /** [part] one place earlier (-1) or later (1) in the order. */
    fun moved(part: InfoPart, by: Int): InfoConfig {
        val from = parts.indexOf(part)
        if (from < 0) return this
        val to = (from + by).coerceIn(0, parts.size - 1)
        if (to == from) return this
        val next = parts.toMutableList().also { it.add(to, it.removeAt(from)) }
        return copy(parts = next)
    }

    companion object {
        val DEFAULT_PARTS = listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Photos)
        const val MAX_PICKED = 24
        const val MAX_SLOTS = 3
        const val MAX_RULES = 8

        /** The faces in the order an older build turned through them: only the first seven, as they come in [InfoPart]. */
        private fun legacyOrder(parts: List<InfoPart>) = InfoPart.entries.filter { it.legacy != null && it in parts }

        /** What a text may hold in a value: nothing that the string is cut at. */
        internal fun enc(text: String): String = buildString {
            for (c in text) when (c) {
                '%' -> append("%25")
                ';' -> append("%3B")
                '=' -> append("%3D")
                '|' -> append("%7C")
                ',' -> append("%2C")
                '+' -> append("%2B")
                '~' -> append("%7E")
                ':' -> append("%3A")
                else -> append(c)
            }
        }

        internal fun dec(text: String): String {
            if ('%' !in text) return text
            val out = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                val code = if (c == '%' && i + 2 < text.length) text.substring(i + 1, i + 3).toIntOrNull(16) else null
                if (code != null) { out.append(code.toChar()); i += 3 } else { out.append(c); i++ }
            }
            return out.toString()
        }

        private val known = setOf(
            "o", "v", "z", "zn", "a", "n", "dw", "sm", "n0", "n1", "sb", "sr", "sl", "cx", "ru", "k", "h", "hp", "ns", "x", "fv", "pk", "mw", "bg", "an", "qt",
            "hz", "tr", "dn", "ks", "d", "sp", "cc", "ly", "w", "wn", "cd", "cn", "nt", "tp", "wg", "bf",
        )

        private fun partsOf(text: String, sep: Char) = text.split(sep).mapNotNull { InfoPart.ofKey(it) }

        /** An empty or unreadable string is a tile with the first three. */
        fun decode(raw: String): InfoConfig {
            val letters = raw.substringBefore(';')
            val pairs = raw.substringAfter(';', "").split(';').mapNotNull { pair -> pair.substringBefore('=', "").takeIf { it.isNotEmpty() }?.let { it to pair.substringAfter('=') } }
            val extras = pairs.toMap()
            val ordered = extras["o"]?.let { partsOf(it, ',') }.orEmpty()
            val fromLetters = InfoPart.entries.filter { p -> p.legacy != null && p.legacy in letters }
            val parts = ordered.distinct().ifEmpty { fromLetters }.ifEmpty { DEFAULT_PARTS }
            fun int(key: String, default: Int) = extras[key]?.toIntOrNull() ?: default
            fun flag(key: String) = extras[key] == "1"
            fun list(key: String, sep: Char = ',') = extras[key]?.takeIf { it.isNotEmpty() }?.split(sep)?.map(::dec).orEmpty()
            val zones = list("z")
            return InfoConfig(
                parts = parts,
                smart = 'S' in letters, saver = 'E' in letters,
                zones = zones, zoneNames = if (zones.isEmpty()) emptyList() else list("zn"),
                album = extras["a"].orEmpty(), albumName = dec(extras["n"].orEmpty()),
                pace = InfoPace.entries.firstOrNull { it.code.toString() == extras["v"] } ?: InfoPace.Normal,
                dwell = extras["dw"] != "f",
                soonMinutes = int("sm", 60).coerceIn(5, 240),
                nightFrom = int("n0", 23).coerceIn(0, 23), nightTo = int("n1", 6).coerceIn(0, 23),
                smartBattery = extras["sb"] != "0", smartRain = extras["sr"] != "0",
                slots = extras["sl"]?.split('|').orEmpty().mapNotNull { s ->
                    val hour = s.substringBefore(':').toIntOrNull()?.takeIf { it in 0..23 } ?: return@mapNotNull null
                    partsOf(s.substringAfter(':'), '+').takeIf { it.isNotEmpty() }?.let { InfoSlot(hour, it) }
                }.take(MAX_SLOTS),
                triggers = extras["cx"]?.split('|').orEmpty().mapNotNull { s ->
                    val head = s.substringBefore(':')
                    val kind = TriggerKind.entries.firstOrNull { it.key == head.substringBefore('~') } ?: return@mapNotNull null
                    partsOf(s.substringAfter(':'), '+').takeIf { it.isNotEmpty() }?.let { InfoTrigger(kind, dec(head.substringAfter('~', "")), it) }
                },
                rules = extras["ru"]?.split('|').orEmpty().mapNotNull { s ->
                    val f = s.split('.')
                    if (f.size < 4) return@mapNotNull null
                    val part = InfoPart.ofKey(f[0]) ?: return@mapNotNull null
                    val kind = RuleKind.entries.firstOrNull { it.key == f[1] } ?: return@mapNotNull null
                    InfoRule(part, kind, f[2].toIntOrNull() ?: 0, f[3].toIntOrNull() ?: 0)
                }.take(MAX_RULES),
                calendars = list("k").mapNotNull { it.toLongOrNull() },
                hideTitles = flag("h"), hidePhotos = flag("hp"),
                noShots = flag("ns"), excluded = list("x"), favorites = flag("fv"), picked = list("pk", '|'),
                memoryDays = int("mw", 0).coerceIn(0, 7),
                photoBackground = flag("bg"), analog = flag("an"), quickTimers = flag("qt"),
                homeZone = dec(extras["hz"].orEmpty()), travel = flag("tr"), dnd = flag("dn"), kids = flag("ks"),
                dots = flag("d"), shareMemory = flag("sp"), tint = flag("cc"),
                layout = InfoLayout.entries.firstOrNull { it.code.toString() == extras["ly"] } ?: InfoLayout.Turn,
                weatherCity = dec(extras["w"].orEmpty()), weatherCityName = dec(extras["wn"].orEmpty()),
                countdownDay = extras["cd"]?.toLongOrNull() ?: 0L, countdownName = dec(extras["cn"].orEmpty()),
                note = dec(extras["nt"].orEmpty()), published = dec(extras["tp"].orEmpty()), widget = int("wg", 0),
                briefHour = int("bf", -1).takeIf { it in 0..23 } ?: -1,
                extra = pairs.filter { it.first !in known }.toMap(),
            )
        }
    }
}
