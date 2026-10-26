package com.metrowin.launcher.data

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The click that goes with a tap, made here instead of taken from Android: Android plays its own only while the "touch sounds"
 * switch of the phone is on, and many phones ship with it off, which made the switch of the launcher seem broken. A few
 * thousandths of a second of a tone that dies away at once, so it is a tick and not a beep. Pure, so it is tested.
 */
object ClickSound {
    const val SAMPLE_RATE = 22_050
    const val MILLIS = 16
    const val HERTZ = 2_000.0

    /** The samples (16 bit, mono): a sine that decays quickly, and goes to nothing at the end so there is no pop. */
    fun pcm(rate: Int = SAMPLE_RATE, millis: Int = MILLIS, hertz: Double = HERTZ, volume: Double = VOLUME): ShortArray {
        val count = rate * millis / 1000
        val fade = (count / 4).coerceAtLeast(1)
        return ShortArray(count) { i ->
            val t = i.toDouble() / rate
            val decay = exp(-t * DECAY_PER_SECOND)
            val tail = if (i >= count - fade) (count - 1 - i).toDouble() / fade else 1.0
            (sin(2.0 * PI * hertz * t) * decay * tail * volume * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** [pcm] as the bytes of a WAVE file (a SoundPool loads sounds from files). */
    fun wav(pcm: ShortArray = pcm(), rate: Int = SAMPLE_RATE): ByteArray {
        val data = pcm.size * 2
        val out = ByteArrayOutputStream(44 + data)
        fun int(value: Int) = repeat(4) { out.write(value shr (8 * it) and 0xFF) }
        fun short(value: Int) = repeat(2) { out.write(value shr (8 * it) and 0xFF) }
        out.write("RIFF".toByteArray())
        int(36 + data)
        out.write("WAVEfmt ".toByteArray())
        int(16)
        short(1)
        short(1)
        int(rate)
        int(rate * 2)
        short(2)
        short(16)
        out.write("data".toByteArray())
        int(data)
        pcm.forEach { short(it.toInt()) }
        return out.toByteArray()
    }

    /** How loud the tick is, as a share of the loudest sound: a click is heard and does not startle. */
    private const val VOLUME = 0.55
    private const val DECAY_PER_SECOND = 280.0
}
