package com.metrowin.launcher.data

import org.json.JSONObject

/**
 * A theme file: how the launcher looks (the colours, dark or light, the clock, the tile size, the icon pack),
 * nothing about what is on Start, so it can be shared. JSON with a format name and a version.
 */
object ThemeCodec {
    const val FORMAT = "metrowin-theme"
    const val VERSION = 1

    /** The settings a theme carries; the rest of the settings are the user's and a theme never touches them. */
    private val keys = listOf(
        "themeMode", "darkFromHour", "darkToHour", "accentIndex", "themeStyle", "customAccent", "dynamicColor", "oledMode", "moreTiles",
        "parallax", "showClock", "iconPack", "highContrast", "launchAnimation",
    )

    fun encode(settings: LauncherSettings): String {
        val all = JSONObject(SettingsCodec.encode(settings, portable = true))
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("theme", JSONObject().apply { keys.forEach { put(it, all.get(it)) } })
        }.toString(2)
    }

    /**
     * [current] with the theme of [json] applied, or null when [json] is not a theme of a version this one reads. A
     * theme with an accent colour turns off the accent that follows the photo, which would hide it.
     */
    fun decode(json: String, current: LauncherSettings): LauncherSettings? = runCatching {
        val o = JSONObject(json)
        if (o.optString("format") != FORMAT || o.optInt("version", Int.MAX_VALUE) > VERSION) return null
        val theme = o.getJSONObject("theme")
        // Only the keys of a theme: a file cannot smuggle other settings in.
        val picked = JSONObject().apply { keys.filter(theme::has).forEach { put(it, theme.get(it)) } }
        val applied = SettingsCodec.decode(picked.toString(), base = current) ?: return null
        if (picked.has("accentIndex") || picked.has("customAccent")) applied.copy(autoAccent = false) else applied
    }.getOrNull()
}
