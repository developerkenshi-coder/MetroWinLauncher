package com.metrowin.launcher.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import java.time.LocalTime
import java.util.Locale
import org.json.JSONObject

/** Where the car was left: the numbers of the place, the street when it was found, and when (epoch milliseconds). */
@Immutable
data class ParkedCar(val latitude: Double, val longitude: Double, val address: String?, val savedAt: Long)

/** The place of the car, kept on the phone: one place, until a new one is saved or it is forgotten. */
class ParkedCarStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun load(): ParkedCar? = prefs.getString(KEY, null)?.let(::decode)

    fun save(car: ParkedCar?) = prefs.edit { if (car == null) remove(KEY) else putString(KEY, encode(car)) }

    companion object {
        private const val KEY = "parked_car"

        fun encode(car: ParkedCar): String = JSONObject().put("la", car.latitude).put("lo", car.longitude).put("at", car.savedAt)
            .also { o -> car.address?.let { o.put("ad", it) } }.toString()

        fun decode(json: String): ParkedCar? = runCatching {
            val o = JSONObject(json)
            ParkedCar(o.getDouble("la"), o.getDouble("lo"), o.optString("ad").ifBlank { null }, o.getLong("at"))
        }.getOrNull()

        /** Walking directions to the car, for a map app that understands them; [mapUri] is the fallback. */
        fun walkUri(car: ParkedCar) = "google.navigation:q=${coordinates(car)}&mode=w"

        fun mapUri(car: ParkedCar) = "geo:${coordinates(car)}?q=${coordinates(car)}"

        private fun coordinates(car: ParkedCar) = String.format(Locale.ROOT, "%.6f,%.6f", car.latitude, car.longitude)
    }
}

/** The hands of an analog clock: the turn of each from twelve o'clock, in degrees. Pure. */
object ClockHands {
    /** The hour hand moves on with the minutes; the minute hand with the seconds. */
    fun angles(time: LocalTime): Pair<Float, Float> {
        val minute = time.minute + time.second / 60f
        return ((time.hour % 12) * 30f + minute * 0.5f) to minute * 6f
    }
}
