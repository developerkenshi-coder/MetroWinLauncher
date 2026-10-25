package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import org.json.JSONArray
import org.json.JSONObject

enum class ShortcutKind(val code: String) {
    /** A person from the contacts. */
    Contact("contact"),

    /** A web page. */
    Site("site"),

    /** An Android widget placed on Start. */
    Widget("widget");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/**
 * Something pinned to Start that is not an app: a contact, a web page or a widget. It has a tile and no
 * entry in the app list. [uri] is the contact's lookup address or the page's address; a widget has none and
 * is known by [widgetId] (given by Android on this phone only) and the [widgetProvider] it comes from.
 */
data class ShortcutDef(
    val id: String,
    val kind: ShortcutKind,
    val label: String,
    val uri: String = "",
    /** File name (inside the app's storage) of the picture shown on a contact's tile. */
    val photoFile: String? = null,
    val widgetProvider: String = "",
    val widgetId: Int = -1,
) {
    /** The tile's entry. Shortcuts only exist on Start ([AppItem.pinOnly]). */
    fun toItem(photoPath: String?): AppItem = AppItem(
        id = id,
        label = label,
        launch = if (kind == ShortcutKind.Widget) Launch.Widget(widgetId) else Launch.Link(uri),
        glyph = when (kind) {
            ShortcutKind.Contact -> Glyph.People
            ShortcutKind.Site -> Glyph.Globe
            ShortcutKind.Widget -> Glyph.Widget
        },
        pinOnly = true,
        photoPath = photoPath,
    )

    companion object {
        fun newId(kind: ShortcutKind, seed: String): String = "shortcut:${kind.code}:${seed.hashCode().toUInt().toString(16)}:${System.nanoTime().toString(36)}"
    }
}

/** JSON form of the shortcuts, apart from Android storage so it can be tested. */
object ShortcutCodec {
    fun encode(shortcuts: List<ShortcutDef>): String = JSONArray().apply {
        shortcuts.forEach { s ->
            put(
                JSONObject().apply {
                    put("id", s.id)
                    put("kind", s.kind.code)
                    put("label", s.label)
                    if (s.uri.isNotEmpty()) put("uri", s.uri)
                    s.photoFile?.let { put("photo", it) }
                    if (s.kind == ShortcutKind.Widget) {
                        put("provider", s.widgetProvider)
                        put("widgetId", s.widgetId)
                    }
                },
            )
        }
    }.toString()

    /** Null when [json] is not what [encode] writes. A shortcut of an unknown kind is skipped, not an error. */
    fun decode(json: String): List<ShortcutDef>? = runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val kind = ShortcutKind.fromCode(o.optString("kind")) ?: continue
                add(
                    ShortcutDef(
                        id = o.getString("id"),
                        kind = kind,
                        label = o.optString("label"),
                        uri = o.optString("uri"),
                        photoFile = o.optString("photo").ifEmpty { null },
                        widgetProvider = o.optString("provider"),
                        widgetId = o.optInt("widgetId", -1),
                    ),
                )
            }
        }
    }.getOrNull()

    /** What goes into a backup: the shortcuts that mean the same on another phone. A widget's id belongs to this one. */
    fun portable(shortcuts: List<ShortcutDef>): List<ShortcutDef> =
        shortcuts.filter { it.kind != ShortcutKind.Widget }.map { it.copy(photoFile = null) }
}

class ShortcutStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)
    private val photoDir = java.io.File(context.filesDir, "shortcut_photos").apply { mkdirs() }

    fun load(): List<ShortcutDef> = prefs.getString(KEY, null)?.let(ShortcutCodec::decode).orEmpty()

    fun save(shortcuts: List<ShortcutDef>) = prefs.edit { putString(KEY, ShortcutCodec.encode(shortcuts)) }

    /** Full path of a stored picture, or null when [file] is null or gone. */
    fun photoPath(file: String?): String? = file?.let { java.io.File(photoDir, it) }?.takeIf { it.exists() }?.path

    /** Keeps [bytes] as the picture of shortcut [id] and returns its file name. */
    fun savePhoto(id: String, bytes: ByteArray): String {
        val name = id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg"
        java.io.File(photoDir, name).writeBytes(bytes)
        return name
    }

    fun deletePhoto(file: String?) {
        if (file != null) java.io.File(photoDir, file).delete()
    }

    private companion object {
        const val KEY = "shortcuts"
    }
}
