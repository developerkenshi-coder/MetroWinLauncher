package com.metrowin.launcher.model

/** The models an info tile can start from. Each is a choice of faces and switches, nothing that belongs to one phone (an album, a note, a network). */
enum class InfoPreset(val config: InfoConfig) {
    Morning(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Weather, InfoPart.Todo), smart = true)),
    Travel(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Weather, InfoPart.Calendar, InfoPart.Photos), travel = true)),
    Night(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Alarm, InfoPart.Battery), smart = true, dnd = true)),
    Essential(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Calendar), smart = true)),
    Standard(InfoConfig()),
}

/**
 * The choice with what only means something on this phone taken out (the albums and folders, the photos picked, the note, the widget, the calendars, the
 * city, the network names, the names written): what is passed on to another phone or in a text is the shape of the tile, not what is in it.
 */
fun InfoConfig.shareable(): InfoConfig = copy(
    parts = parts.filter { it != InfoPart.Widget }.ifEmpty { InfoConfig.DEFAULT_PARTS },
    album = "", albumName = "", picked = emptyList(), excluded = emptyList(), calendars = emptyList(), note = "", published = "", widget = 0,
    weatherCity = "", weatherCityName = "", homeZone = "", countdownName = "", countdownDay = 0L,
    triggers = triggers.map { it.copy(value = "") }, slots = slots.map { it.copy(parts = it.parts.filter { p -> p != InfoPart.Widget }.ifEmpty { listOf(InfoPart.Time) }) },
    extra = emptyMap(),
)

/** What a text that says a choice looks like: this and the choice after it. */
const val INFO_SHARE_PREFIX = "metrowin-tile:"

/** The choice written as a text to pass on (see [shareable]), or null when [text] is not one. */
fun InfoConfig.toShareText(): String = INFO_SHARE_PREFIX + shareable().encode()

fun infoConfigFromShareText(text: String): InfoConfig? {
    val trimmed = text.trim()
    if (!trimmed.startsWith(INFO_SHARE_PREFIX)) return null
    return InfoConfig.decode(trimmed.removePrefix(INFO_SHARE_PREFIX)).shareable()
}
