package com.metrowin.launcher.data

import com.metrowin.launcher.model.InfoPart

/** Which faces an info tile turns through right now, when the user asked it to put first what matters. */
object InfoPriority {
    /** An event or an alarm this near (from now on) is what matters. */
    const val SOON_MS = 60 * 60_000L

    /** From 23:00 to 06:00 only the time is shown. */
    fun isNight(hour: Int) = hour >= 23 || hour < 6

    /**
     * [faces] are the faces that have something to say, in the order of the tile. Smart: at night only the time; an event or an alarm within
     * the hour stays alone (both: the nearer first, both turning); otherwise all of them, as they are. Never empty when [faces] is not.
     */
    fun order(faces: List<InfoPart>, smart: Boolean, nowMs: Long, hour: Int, eventStartMs: Long?, alarmMs: Long): List<InfoPart> {
        if (!smart || faces.size < 2) return faces
        if (isNight(hour) && InfoPart.Time in faces) return listOf(InfoPart.Time)
        val soon = buildList {
            if (InfoPart.Calendar in faces && eventStartMs != null && eventStartMs in (nowMs - 15 * 60_000L)..(nowMs + SOON_MS)) add(InfoPart.Calendar to eventStartMs)
            if (InfoPart.Alarm in faces && alarmMs > nowMs && alarmMs <= nowMs + SOON_MS) add(InfoPart.Alarm to alarmMs)
        }.sortedBy { it.second }.map { it.first }
        return soon.ifEmpty { faces }
    }
}

/** The face each info tile is showing now, so that a touch can open what it shows. */
object InfoTileState {
    private val shown = java.util.concurrent.ConcurrentHashMap<String, InfoPart>()

    fun show(id: String, part: InfoPart) { shown[id] = part }

    fun current(id: String): InfoPart? = shown[id]
}
