package com.metrowin.launcher.data

/**
 * The switches and sound modes the search box can work by name, in English or Italian: "torch", "turn off wifi", "spegni la torcia",
 * "silent", "vibra". Only the whole text counts (an optional verb in front is ignored), so an app that has such a word in its name
 * is still found among the results. Pure, so it is tested with words.
 */
object SystemCommands {
    private class Entry(val action: QuickAction?, val ringer: RingerMode?, val words: Set<String>)

    private val ENTRIES = listOf(
        Entry(QuickAction.Flashlight, null, setOf("flashlight", "torch", "torcia", "torcia elettrica")),
        Entry(QuickAction.Wifi, null, setOf("wifi", "wi fi", "wlan")),
        Entry(QuickAction.Bluetooth, null, setOf("bluetooth", "bt")),
        Entry(QuickAction.Airplane, null, setOf("airplane mode", "airplane", "aeroplane mode", "flight mode", "modalita aereo", "modalita aeroplano", "aereo", "aeroplano")),
        Entry(QuickAction.QuietHours, null, setOf("do not disturb", "dnd", "dont disturb", "don t disturb", "non disturbare")),
        Entry(QuickAction.Location, null, setOf("location", "gps", "posizione", "localizzazione")),
        Entry(QuickAction.Hotspot, null, setOf("hotspot", "hot spot", "tethering")),
        Entry(QuickAction.Rotation, null, setOf("rotation", "auto rotate", "rotazione", "rotazione schermo")),
        Entry(QuickAction.Cast, null, setOf("cast", "trasmetti")),
        Entry(QuickAction.Vpn, null, setOf("vpn")),
        Entry(QuickAction.AutoBrightness, null, setOf("auto brightness", "adaptive brightness", "luminosita automatica")),
        Entry(null, RingerMode.Silent, setOf("silent", "silent mode", "mute", "silenzioso", "muto", "modalita silenziosa")),
        Entry(null, RingerMode.Vibrate, setOf("vibrate", "vibration", "vibra", "vibrazione")),
        Entry(null, RingerMode.Normal, setOf("ring mode", "sound on", "loud", "suoneria", "suono normale")),
    )

    private val VERB = Regex(
        "^(?:turn on|turn off|switch on|switch off|toggle|enable|disable|open|set|accendi|spegni|attiva|disattiva|apri|metti|imposta|cambia)\\s+(?:the\\s+|il\\s+|la\\s+|lo\\s+)?",
    )

    /** What the buttons do on the spot: the rest only open the screen of Android that has the switch. */
    private val IN_PLACE = setOf(QuickAction.Flashlight, QuickAction.QuietHours, QuickAction.QuietTimed, QuickAction.ScreenTimeout, QuickAction.AutoBrightness)

    fun inPlace(command: QuickAnswer.Command): Boolean = command.ringer != null || command.action in IN_PLACE

    /** The command [query] names, or null. */
    fun of(query: String): QuickAnswer.Command? {
        val text = WhenParser.fold(query).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.length < 2) return null
        val name = VERB.replace(text, "").trim()
        return ENTRIES.firstOrNull { name in it.words }?.let { QuickAnswer.Command(it.action, it.ringer) }
    }
}

/** The days' tips: one of [COUNT] short hints about what the launcher can do, a different one each day. */
object Tips {
    /** The number of tips in the strings (a test keeps the list and this number the same, in both languages). */
    const val COUNT = 24

    fun indexFor(day: java.time.LocalDate): Int = Math.floorMod(day.toEpochDay(), COUNT.toLong()).toInt()
}
