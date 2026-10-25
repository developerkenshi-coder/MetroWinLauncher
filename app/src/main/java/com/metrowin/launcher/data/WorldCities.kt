package com.metrowin.launcher.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A city for the world clock and for "what time is it in Tokyo": its name in English and Italian and its time zone. */
data class City(val name: String, val zone: String, val aliases: List<String> = emptyList()) {
    val zoneId: ZoneId get() = ZoneId.of(zone)
}

/** What time it is somewhere, and on which day compared with here. */
data class TimeThere(val time: LocalTime, /** -1 yesterday, 0 the same day, 1 tomorrow. */ val dayOffset: Int)

object WorldCities {
    val all: List<City> = listOf(
        City("London", "Europe/London", listOf("londra")),
        City("Dublin", "Europe/Dublin", listOf("dublino")),
        City("Lisbon", "Europe/Lisbon", listOf("lisbona")),
        City("Madrid", "Europe/Madrid"),
        City("Paris", "Europe/Paris", listOf("parigi")),
        City("Rome", "Europe/Rome", listOf("roma")),
        City("Berlin", "Europe/Berlin", listOf("berlino")),
        City("Amsterdam", "Europe/Amsterdam"),
        City("Brussels", "Europe/Brussels", listOf("bruxelles")),
        City("Zurich", "Europe/Zurich", listOf("zurigo")),
        City("Vienna", "Europe/Vienna", listOf("vienna")),
        City("Prague", "Europe/Prague", listOf("praga")),
        City("Warsaw", "Europe/Warsaw", listOf("varsavia")),
        City("Stockholm", "Europe/Stockholm", listOf("stoccolma")),
        City("Oslo", "Europe/Oslo"),
        City("Copenhagen", "Europe/Copenhagen", listOf("copenaghen")),
        City("Helsinki", "Europe/Helsinki"),
        City("Athens", "Europe/Athens", listOf("atene")),
        City("Istanbul", "Europe/Istanbul"),
        City("Moscow", "Europe/Moscow", listOf("mosca")),
        City("Kyiv", "Europe/Kyiv", listOf("kiev")),
        City("Cairo", "Africa/Cairo", listOf("il cairo")),
        City("Lagos", "Africa/Lagos"),
        City("Nairobi", "Africa/Nairobi"),
        City("Johannesburg", "Africa/Johannesburg"),
        City("Casablanca", "Africa/Casablanca"),
        City("Dubai", "Asia/Dubai"),
        City("Tehran", "Asia/Tehran", listOf("teheran")),
        City("Karachi", "Asia/Karachi"),
        City("Delhi", "Asia/Kolkata", listOf("nuova delhi", "new delhi", "mumbai", "bombay")),
        City("Dhaka", "Asia/Dhaka"),
        City("Bangkok", "Asia/Bangkok"),
        City("Jakarta", "Asia/Jakarta", listOf("giacarta")),
        City("Singapore", "Asia/Singapore", listOf("singapore")),
        City("Hong Kong", "Asia/Hong_Kong"),
        City("Shanghai", "Asia/Shanghai", listOf("pechino", "beijing")),
        City("Taipei", "Asia/Taipei"),
        City("Seoul", "Asia/Seoul"),
        City("Tokyo", "Asia/Tokyo"),
        City("Manila", "Asia/Manila"),
        City("Sydney", "Australia/Sydney"),
        City("Melbourne", "Australia/Melbourne"),
        City("Perth", "Australia/Perth"),
        City("Auckland", "Pacific/Auckland"),
        City("Honolulu", "Pacific/Honolulu"),
        City("Anchorage", "America/Anchorage"),
        City("Los Angeles", "America/Los_Angeles", listOf("san francisco", "seattle")),
        City("Vancouver", "America/Vancouver"),
        City("Denver", "America/Denver"),
        City("Mexico City", "America/Mexico_City", listOf("citta del messico", "città del messico")),
        City("Chicago", "America/Chicago"),
        City("Houston", "America/Chicago", listOf("dallas")),
        City("New York", "America/New_York", listOf("nuova york", "washington", "boston", "miami")),
        City("Toronto", "America/Toronto"),
        City("Bogota", "America/Bogota"),
        City("Lima", "America/Lima"),
        City("Santiago", "America/Santiago"),
        City("Buenos Aires", "America/Argentina/Buenos_Aires"),
        City("Sao Paulo", "America/Sao_Paulo", listOf("san paolo", "rio de janeiro", "rio")),
        City("Reykjavik", "Atlantic/Reykjavik"),
    )

    /** The city whose name (or an alias) is [text], ignoring case and accents; null when there is none. */
    fun find(text: String): City? {
        val needle = AppSections.fold(text)
        if (needle.isEmpty()) return null
        return all.firstOrNull { c -> AppSections.fold(c.name) == needle || c.aliases.any { AppSections.fold(it) == needle } }
    }

    /** The cities whose name or alias starts with, or has a word that starts with, [query]; every city for an empty query. */
    fun search(query: String): List<City> {
        val needle = AppSections.fold(query)
        if (needle.isEmpty()) return all.sortedBy { AppSections.fold(it.name) }
        return all.filter { c ->
            (listOf(c.name) + c.aliases).any { name -> AppSections.fold(name).split(' ').any { it.startsWith(needle) } || AppSections.fold(name).startsWith(needle) }
        }.sortedBy { AppSections.fold(it.name) }
    }

    /** The time in [zone] at [instant], and whether that is the same day as in [here]. */
    fun timeIn(zone: ZoneId, instant: Instant, here: ZoneId): TimeThere {
        val there = instant.atZone(zone)
        val local: LocalDate = instant.atZone(here).toLocalDate()
        val offset = (there.toLocalDate().toEpochDay() - local.toEpochDay()).toInt().coerceIn(-1, 1)
        return TimeThere(there.toLocalTime().withSecond(0).withNano(0), offset)
    }
}
