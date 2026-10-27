package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/** The three volumes kept under a name, in percent: what the ringtone, the media and the alarms are set to when it is applied. */
@Immutable
data class VolumeProfile(val name: String, val ring: Int, val media: Int, val alarm: Int)

object VolumeProfiles {
    const val MAX = 6
    const val MAX_NAME = 20

    /** The volumes the phone has now (fractions, null where the phone cannot tell) as a profile; null with a blank name. */
    fun capture(name: String, ring: Float?, media: Float?, alarm: Float?): VolumeProfile? {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return null
        fun percent(fraction: Float?) = ((fraction ?: 0f).coerceIn(0f, 1f) * 100).toInt()
        return VolumeProfile(clean, percent(ring), percent(media), percent(alarm))
    }

    /** [list] with [profile] added; a profile with a name that is already there takes its place; the list holds at most [MAX]. */
    fun add(list: List<VolumeProfile>, profile: VolumeProfile): List<VolumeProfile> {
        val i = list.indexOfFirst { it.name.equals(profile.name, ignoreCase = true) }
        return if (i >= 0) list.toMutableList().also { it[i] = profile } else (list + profile).take(MAX)
    }

    fun encode(list: List<VolumeProfile>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("n", it.name).put("r", it.ring).put("m", it.media).put("a", it.alarm)) }
    }.toString()

    fun decode(json: String?): List<VolumeProfile> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val name = o.optString("n").trim().take(MAX_NAME).ifEmpty { return@mapNotNull null }
            VolumeProfile(name, o.optInt("r").coerceIn(0, 100), o.optInt("m").coerceIn(0, 100), o.optInt("a").coerceIn(0, 100))
        }.take(MAX)
    }.getOrDefault(emptyList())

    /** Sets the three volumes of the phone to those of [profile]. */
    fun apply(sound: SoundController, profile: VolumeProfile) {
        sound.setRingVolume(profile.ring / 100f)
        sound.setVolume(profile.media / 100f)
        sound.setAlarmVolume(profile.alarm / 100f)
    }
}
