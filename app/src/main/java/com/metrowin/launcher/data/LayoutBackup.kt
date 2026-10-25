package com.metrowin.launcher.data

import com.metrowin.launcher.model.StartEntry
import org.json.JSONArray
import org.json.JSONObject

/** A copy of how the launcher is set up: what is on each Start screen, the shortcuts, and the settings. */
data class LayoutBackup(
    val pages: List<List<StartEntry>>,
    val shortcuts: List<ShortcutDef>,
    val settings: LauncherSettings,
)

/**
 * The file of a layout backup: JSON, with a format name and a version so another file is never taken for one.
 * What belongs to this phone alone (backup folder, passphrase, a widget's id, the weather position) is left
 * out, so a backup can be copied to another phone or shared.
 */
object LayoutBackupCodec {
    const val FORMAT = "metrowin-layout"
    const val VERSION = 1

    fun encode(backup: LayoutBackup, exportedAt: Long): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", exportedAt)
        put("pages", JSONArray(TileCodec.encodePages(backup.pages)))
        put("shortcuts", JSONArray(ShortcutCodec.encode(ShortcutCodec.portable(backup.shortcuts))))
        put("settings", JSONObject(SettingsCodec.encode(backup.settings, portable = true)))
    }.toString(2)

    /**
     * Null when [json] is not a layout backup of a version this one can read. Settings the file lacks keep
     * the values of [current]; so do the ones that belong to this phone.
     */
    fun decode(json: String, current: LauncherSettings): LayoutBackup? = runCatching {
        val o = JSONObject(json)
        if (o.optString("format") != FORMAT || o.optInt("version", Int.MAX_VALUE) > VERSION) return null
        val pages = TileCodec.decodePages(o.getJSONArray("pages").toString()) ?: return null
        val shortcuts = ShortcutCodec.decode(o.optJSONArray("shortcuts")?.toString() ?: "[]") ?: emptyList()
        val settings = o.optJSONObject("settings")?.let { SettingsCodec.decode(it.toString(), base = current) } ?: current
        LayoutBackup(pages, shortcuts, settings)
    }.getOrNull()
}
