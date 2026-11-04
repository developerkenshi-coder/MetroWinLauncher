package com.metrowin.launcher.calculator

import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.CalcSession
import org.json.JSONArray
import org.json.JSONObject

/** The history, the memory and the angle of the calculator, kept between two visits. */
class CalcStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin_calc", Context.MODE_PRIVATE)

    fun load(): CalcSession {
        val history = runCatching {
            val array = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
            List(array.length()) { array.getJSONObject(it).let { o -> CalcSession.Entry(o.getString("e"), o.getString("r")) } }
        }.getOrDefault(emptyList())
        val angle = if (prefs.getBoolean(KEY_RADIANS, false)) CalcEngine.Angle.Radians else CalcEngine.Angle.Degrees
        return CalcSession(angle, prefs.getString(KEY_MEMORY, null)?.toDoubleOrNull() ?: 0.0, history)
    }

    fun save(session: CalcSession) {
        val array = JSONArray()
        session.history.forEach { array.put(JSONObject().put("e", it.expression).put("r", it.result)) }
        prefs.edit {
            putString(KEY_HISTORY, array.toString())
            putString(KEY_MEMORY, session.memory.toString())
            putBoolean(KEY_RADIANS, session.angle == CalcEngine.Angle.Radians)
        }
    }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_MEMORY = "memory"
        const val KEY_RADIANS = "radians"
    }
}
