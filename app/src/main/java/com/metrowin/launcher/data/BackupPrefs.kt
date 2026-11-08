package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * The choices of the small apps that live in their own preference files (which Shizuku buttons are on, the switches of the Shizuku watch and
 * of the battery history, the limit and billing day of Data Sense), so a backup brings them back. Only what the user chose: never the
 * saved terminal commands, the state of an alert or the history itself. The news source is a launcher setting, already in the backup.
 */
object BackupPrefs {
    private enum class Kind { Bool, Int, Long, Set }

    private class Key(val file: String, val name: String, val kind: Kind) {
        val id get() = "$file:$name"
    }

    private val choices = listOf(
        Key(Prefs.SHIZUKU, "buttons", Kind.Set),
        Key(Prefs.SHIZUKU, "watch", Kind.Bool),
        Key(Prefs.SHIZUKU, "history", Kind.Bool),
        Key(Prefs.DATA_SENSE, "limit_mb", Kind.Long),
        Key(Prefs.DATA_SENSE, "bill_day", Kind.Int),
    )

    /** The choices that are set on this phone, by `file:name`. */
    fun read(context: Context): Map<String, Any> = buildMap {
        choices.forEach { key ->
            val prefs = Prefs.open(context, key.file)
            if (!prefs.contains(key.name)) return@forEach
            when (key.kind) {
                Kind.Bool -> put(key.id, prefs.getBoolean(key.name, false))
                Kind.Int -> put(key.id, prefs.getInt(key.name, 0))
                Kind.Long -> put(key.id, prefs.getLong(key.name, 0L))
                Kind.Set -> put(key.id, prefs.getStringSet(key.name, null).orEmpty().sorted())
            }
        }
    }

    /** Writes the choices of [values] that this version knows, each only if it has the right type; the rest is left alone. */
    fun apply(context: Context, values: Map<String, Any>) {
        choices.forEach { key ->
            val value = values[key.id] ?: return@forEach
            Prefs.open(context, key.file).edit {
                when {
                    key.kind == Kind.Bool && value is Boolean -> putBoolean(key.name, value)
                    key.kind == Kind.Int && value is Number -> putInt(key.name, value.toInt())
                    key.kind == Kind.Long && value is Number -> putLong(key.name, value.toLong())
                    key.kind == Kind.Set && value is List<*> -> putStringSet(key.name, value.filterIsInstance<String>().toSet())
                }
            }
        }
    }

    fun encode(values: Map<String, Any>): JSONObject = JSONObject().also { o -> values.forEach { (k, v) -> o.put(k, if (v is List<*>) JSONArray(v) else v) } }

    fun decode(o: JSONObject): Map<String, Any> = buildMap {
        o.keys().forEach { k ->
            when (val v = o.get(k)) {
                is JSONArray -> put(k, (0 until v.length()).mapNotNull { v.optString(it, null) })
                is Boolean, is Number -> put(k, v)
            }
        }
    }
}
