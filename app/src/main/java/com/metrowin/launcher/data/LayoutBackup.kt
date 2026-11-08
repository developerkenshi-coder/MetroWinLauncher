package com.metrowin.launcher.data

import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.todo.TodoItem
import com.metrowin.launcher.todo.Todos
import com.metrowin.launcher.wallet.Card
import com.metrowin.launcher.wallet.Wallet
import org.json.JSONArray
import org.json.JSONObject

/** A copy of how the launcher is set up: what is on each Start screen, the shortcuts, and the settings. [extras] is null for a file of version 1. */
data class LayoutBackup(
    val pages: List<List<StartEntry>>,
    val shortcuts: List<ShortcutDef>,
    val settings: LauncherSettings,
    val extras: BackupExtras? = null,
)

/**
 * What the user keeps apart from the layout: things to do, reminders, named layouts, the place of the car and the choices of the small apps. (Timers, the extra tiles and
 * the Finance choices are shortcuts and settings, so the backup already holds them.) The [wallet] is null unless the file is encrypted
 * (the sync file): its codes are worth stealing, so a backup in the clear never carries them.
 */
data class BackupExtras(
    val todos: List<TodoItem>,
    val reminders: List<Reminder>,
    val layouts: List<SavedLayout>,
    val car: ParkedCar?,
    val wallet: List<Card>? = null,
    /** The choices of the small apps in their own preference files, see [BackupPrefs]; empty for a file from before they were kept. */
    val prefs: Map<String, Any> = emptyMap(),
)

/**
 * The file of a layout backup: JSON, with a format name and a version so another file is never taken for one.
 * What belongs to this phone alone (backup folder, passphrase, a widget's id, the weather position) is left
 * out, so a backup can be copied to another phone or shared.
 */
object LayoutBackupCodec {
    const val FORMAT = "metrowin-layout"
    /** 2 added [BackupExtras]; a file of version 1 has none and is read as before. */
    const val VERSION = 2

    /** The wallet is written only with [secret], for a file that is encrypted afterwards. */
    fun encode(backup: LayoutBackup, exportedAt: Long, secret: Boolean = false): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", exportedAt)
        put("pages", JSONArray(TileCodec.encodePages(backup.pages)))
        put("shortcuts", JSONArray(ShortcutCodec.encode(ShortcutCodec.portable(backup.shortcuts))))
        put("settings", JSONObject(SettingsCodec.encode(backup.settings, portable = true)))
        backup.extras?.let { put("extras", encodeExtras(it, secret)) }
    }.toString(2)

    private fun encodeExtras(e: BackupExtras, secret: Boolean) = JSONObject().apply {
        put("todos", JSONArray(Todos.encode(e.todos)))
        put("reminders", JSONArray(Reminders.encode(e.reminders)))
        put("layouts", JSONArray(Layouts.encode(e.layouts)))
        e.car?.let { put("car", JSONObject(ParkedCarStore.encode(it))) }
        if (e.prefs.isNotEmpty()) put("prefs", BackupPrefs.encode(e.prefs))
        if (secret) e.wallet?.let { put("wallet", JSONArray(Wallet.encode(it))) }
    }

    private fun decodeExtras(o: JSONObject) = BackupExtras(
        todos = Todos.decode(o.optJSONArray("todos")?.toString()),
        reminders = Reminders.decode(o.optJSONArray("reminders")?.toString()),
        layouts = Layouts.decode(o.optJSONArray("layouts")?.toString()),
        car = o.optJSONObject("car")?.let { ParkedCarStore.decode(it.toString()) },
        wallet = o.optJSONArray("wallet")?.let { Wallet.decode(it.toString()) },
        prefs = o.optJSONObject("prefs")?.let(BackupPrefs::decode).orEmpty(),
    )

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
        LayoutBackup(pages, shortcuts, settings, o.optJSONObject("extras")?.let(::decodeExtras))
    }.getOrNull()
}
