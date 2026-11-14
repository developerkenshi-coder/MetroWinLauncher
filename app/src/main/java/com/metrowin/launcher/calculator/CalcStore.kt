package com.metrowin.launcher.calculator

import com.metrowin.launcher.data.Prefs
import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.CalcLimits
import com.metrowin.launcher.data.CalcSession
import org.json.JSONArray
import org.json.JSONObject

/** The history, the memory, the angle and the expression of the calculator, kept between two visits. */
class CalcStore(context: Context) {
    private val prefs = Prefs.open(context, Prefs.CALC)

    /** Blocking (it reads the file): call it off the main thread. */
    fun load(): CalcSession {
        val history = runCatching {
            val array = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
            List(array.length()) { array.getJSONObject(it).let { o -> CalcSession.Entry(o.getString("e"), o.getString("r"), o.optLong("t", 0L)) } }
        }.getOrDefault(emptyList()).take(CalcLimits.HISTORY)
        val angle = if (prefs.getBoolean(KEY_RADIANS, false)) CalcEngine.Angle.Radians else CalcEngine.Angle.Degrees
        return CalcSession(angle, prefs.getString(KEY_MEMORY, null)?.toDoubleOrNull() ?: 0.0, history).also {
            it.restore(
                prefs.getString(KEY_EXPRESSION, "").orEmpty(), prefs.getBoolean(KEY_FINISHED, false), prefs.getString(KEY_LAST, "").orEmpty(),
                prefs.getString(KEY_ANS, null)?.toDoubleOrNull() ?: 0.0,
            )
        }
    }

    fun save(session: CalcSession) {
        val array = JSONArray()
        session.history.forEach { array.put(JSONObject().put("e", it.expression).put("r", it.result).put("t", it.at)) }
        prefs.edit {
            putString(KEY_HISTORY, array.toString())
            putString(KEY_MEMORY, session.memory.toString())
            putBoolean(KEY_RADIANS, session.angle == CalcEngine.Angle.Radians)
            putString(KEY_EXPRESSION, session.expression)
            putBoolean(KEY_FINISHED, session.finished)
            putString(KEY_LAST, session.lastExpression)
            putString(KEY_ANS, session.ans.toString())
        }
    }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_MEMORY = "memory"
        const val KEY_RADIANS = "radians"
        const val KEY_EXPRESSION = "expression"
        const val KEY_FINISHED = "finished"
        const val KEY_LAST = "last_expression"
        const val KEY_ANS = "ans"
    }
}
