package com.metrowin.launcher.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What changes the sound itself: a piece of any format written again as AAC, the same sound made louder, faded or without its silences,
 * several recordings joined, and the sound as WAV or Opus. The sound is decoded, changed and encoded in a stream, a few thousand samples at a
 * time, so a long recording never has to fit in memory. A source is never written over: the result is a new file.
 */
object AudioEdit {
    /** What [render] does on its way. A cut is [startMs] to [endMs]; everything else is off until asked for. */
    data class Plan(
        val startMs: Long = 0L,
        val endMs: Long = Long.MAX_VALUE,
        val normalise: Boolean = false,
        val fadeMs: Long = 0L,
        val dropSilence: Boolean = false,
        val bitRate: Int = 96_000,
        /** The type to encode ([MediaFormat.MIMETYPE_AUDIO_AAC] or Opus) and the container that holds it. */
        val mime: String = MediaFormat.MIMETYPE_AUDIO_AAC,
        val container: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        val rate: Int = 0,
    )

    private const val TIMEOUT_US = 10_000L
    const val SILENCE_WINDOW_MS = 20
    const val SILENCE_KEEP_MS = 250

    /** Whether this phone can encode Opus (Android 10 and later, and a codec for it): the export to Opus is offered only then. */
    fun opusAvailable(): Boolean = Build.VERSION.SDK_INT >= 29 &&
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_AUDIO_OPUS, true) } }

    // ---- the pieces of the stream

    /** Changes the sampling rate of interleaved 16-bit frames by a straight line between two samples (enough for speech and for editing). */
    class Resampler(private val inRate: Int, private val outRate: Int, private val channels: Int) {
        private var pos = 0.0
        private val prev = ShortArray(channels)
        private val step = inRate.toDouble() / outRate

        fun push(frames: ShortArray, count: Int, out: (ShortArray, Int) -> Unit) {
            if (inRate == outRate) { out(frames, count); return }
            val room = ((count - pos) / step).toInt() + 2
            val result = ShortArray(room.coerceAtLeast(1) * channels)
            var n = 0
            while (pos < count - 1) {
                val i = Math.floor(pos).toInt()
                val f = (pos - i).toFloat()
                for (c in 0 until channels) {
                    val a = if (i < 0) prev[c].toInt() else frames[i * channels + c].toInt()
                    val b = frames[(i + 1) * channels + c].toInt()
                    result[n * channels + c] = (a + (b - a) * f).toInt().toShort()
                }
                n++
                pos += step
            }
            if (count > 0) for (c in 0 until channels) prev[c] = frames[(count - 1) * channels + c]
            pos -= count
            out(result, n)
        }
    }

    /** Keeps the last [capacity] samples back until more come, so the end of the sound can be changed (faded) once it is known. */
    private class Holdback(private val capacity: Int, private val sink: (ShortArray, Int) -> Unit) {
        private val buffer = ShortArray(capacity)
        var held = 0
            private set

        fun push(samples: ShortArray, count: Int) {
            if (capacity == 0) { sink(samples, count); return }
            var from = 0
            var left = count
            if (left >= capacity) {
                // Everything held goes out, and all of the new but the last [capacity].
                if (held > 0) sink(buffer, held)
                sink(samples, left - capacity)
                System.arraycopy(samples, count - capacity, buffer, 0, capacity)
                held = capacity
                return
            }
            val over = held + left - capacity
            if (over > 0) {
                sink(buffer, over)
                System.arraycopy(buffer, over, buffer, 0, held - over)
                held -= over
            }
            System.arraycopy(samples, from, buffer, held, left)
            held += left
        }

        fun tail(channels: Int, fadeOut: Boolean) {
            if (held == 0) return
            if (fadeOut) {
                val frames = held / channels
                for (f in 0 until frames) {
                    val g = (frames - 1 - f).toFloat() / frames
                    for (c in 0 until channels) buffer[f * channels + c] = (buffer[f * channels + c] * g).toInt().toShort()
                }
            }
            sink(buffer, held)
            held = 0
        }
    }

    /** Drops the silences: every window of [windowFrames] quieter than [threshold] is kept only for the first [keepWindows] of a run. */
    private class Gate(private val windowFrames: Int, private val channels: Int, private val threshold: Int, private val keepWindows: Int, private val sink: (ShortArray, Int) -> Unit) {
        private val window = ShortArray(windowFrames * channels)
        private var filled = 0
        private var run = 0

        fun push(samples: ShortArray, count: Int) {
            var at = 0
            while (at < count * channels) {
                val take = min(window.size - filled, count * channels - at)
                System.arraycopy(samples, at, window, filled, take)
                filled += take
                at += take
                if (filled == window.size) { emit(); filled = 0 }
            }
        }

        private fun emit() {
            if (rms(window, window.size) >= threshold) { run = 0; sink(window, window.size) }
            else if (++run <= keepWindows) sink(window, window.size)
        }

        fun flush() { if (filled > 0) sink(window, filled); filled = 0 }
    }

    private fun rms(samples: ShortArray, count: Int): Int {
        if (count == 0) return 0
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / count).toInt()
    }

    /** The AAC (or Opus) encoder and the muxer that writes it into [target]. */
    private class Encoder(target: File, mime: String, container: Int, private val rate: Int, private val channels: Int, bitRate: Int) {
        private val codec = MediaCodec.createEncoderByType(mime)
        private val muxer = MediaMuxer(target.path, container)
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        private var muxing = false
        private var frames = 0L
        private var ended = false

        init {
            val format = MediaFormat.createAudioFormat(mime, rate, channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        }

        fun write(samples: ShortArray, count: Int) {
            var offset = 0
            var spins = 0
            while (offset < count) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index < 0) {
                    drain(false)
                    // An encoder that takes nothing for ten seconds is stuck.
                    if (++spins > 10_000_000 / TIMEOUT_US) throw java.io.IOException("encoder stuck")
                    continue
                }
                spins = 0
                val buffer = codec.getInputBuffer(index)!!
                buffer.clear()
                val n = min(count - offset, buffer.capacity() / 2 / channels * channels)
                buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples, offset, n)
                codec.queueInputBuffer(index, 0, n * 2, frames * 1_000_000L / rate, 0)
                frames += n / channels
                offset += n
                drain(false)
            }
        }

        /** Takes what the encoder has made and gives it to the muxer; with [untilEnd] waits for the end of the stream. */
        private fun drain(untilEnd: Boolean) {
            var idle = 0
            while (true) {
                val out = codec.dequeueOutputBuffer(info, if (untilEnd) TIMEOUT_US else 0L)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    out >= 0 -> {
                        idle = 0
                        val data = codec.getOutputBuffer(out)!!
                        if (info.size > 0 && muxing && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            data.position(info.offset).limit(info.offset + info.size)
                            muxer.writeSampleData(track, data, info)
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { ended = true; return }
                    }
                    out == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!untilEnd) return
                        if (++idle > 5_000_000 / TIMEOUT_US) throw java.io.IOException("encoder did not end")
                    }
                }
            }
        }

        /** Ends the stream and closes the file; false when nothing was written. */
        fun finish(): Boolean {
            var spins = 0
            while (true) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) { codec.queueInputBuffer(index, 0, 0, frames * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM); break }
                drain(false)
                if (++spins > 10_000_000 / TIMEOUT_US) throw java.io.IOException("encoder stuck")
            }
            drain(true)
            return muxing && ended
        }

        fun close() {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxing) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    // ---- reading

    /** Hands the sound of [file] to [sink] as interleaved frames in [outChannels] channels at the rate [outRate] (0 keeps the rate it has). */
    private fun stream(file: File, startUs: Long, endUs: Long, outChannels: Int, outRate: () -> Int, halt: () -> Boolean, sink: (ShortArray, Int) -> Unit): Boolean {
        var resampler: Resampler? = null
        var past = false
        val ok = AudioTools.decode(file, { halt() || past }) { samples, timeUs, rate, channels ->
            val c = channels.coerceAtLeast(1)
            val total = samples.remaining() / c
            if (total == 0) return@decode
            val targetRate = outRate().takeIf { it > 0 } ?: rate
            if (resampler == null) resampler = Resampler(rate, targetRate, outChannels)
            // The frames of the range only: a buffer holds about 23 ms, so the edge falls to a frame of the sound.
            val first = if (timeUs >= startUs) 0 else (((startUs - timeUs) * rate) / 1_000_000L).toInt().coerceAtMost(total)
            val last = if (endUs == Long.MAX_VALUE) total else (((endUs - timeUs) * rate) / 1_000_000L).toInt().coerceIn(0, total)
            if (last <= first) { if (timeUs >= endUs) past = true; return@decode }
            val frames = ShortArray((last - first) * outChannels)
            val sum = IntArray(1)
            for (i in 0 until total) {
                var mono = 0
                val base = samples.position() + i * c
                if (i < first || i >= last) continue
                if (c == outChannels) for (k in 0 until c) frames[(i - first) * outChannels + k] = samples.get(base + k)
                else {
                    for (k in 0 until c) mono += samples.get(base + k)
                    mono /= c
                    for (k in 0 until outChannels) frames[(i - first) * outChannels + k] = mono.toShort()
                }
                sum[0]++
            }
            resampler!!.push(frames, last - first, sink)
            if (endUs != Long.MAX_VALUE && last < total) past = true
        }
        return ok || past
    }

    /** The loudest sample, and the loudness of each window of 20 ms (for a gate), over the same range [render] will use. Null when it cannot be read. */
    private fun analyse(sources: List<File>, plan: Plan, channels: Int, halt: () -> Boolean): Pair<Int, IntArray>? {
        var peak = 0
        val windows = ArrayList<Int>()
        for (source in sources) {
            val rate = AudioTools.info(source)?.first ?: return null
            val frames = (rate * SILENCE_WINDOW_MS / 1000).coerceAtLeast(1)
            val ok = stream(source, plan.startMs * 1000L, if (plan.endMs == Long.MAX_VALUE) Long.MAX_VALUE else plan.endMs * 1000L, channels, { 0 }, halt) { samples, count ->
                for (i in 0 until count * channels) peak = maxOf(peak, abs(samples[i].toInt()))
                var at = 0
                while (at + frames * channels <= count * channels) {
                    windows += rmsRange(samples, at, frames * channels)
                    at += frames * channels
                }
            }
            if (!ok) return null
        }
        return peak to windows.toIntArray()
    }

    private fun rmsRange(samples: ShortArray, from: Int, count: Int): Int {
        var sum = 0.0
        for (i in from until from + count) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / count).toInt()
    }

    /** The loudness under which a window counts as silence: a little above the quiet of the recording, never above half its loud parts. */
    fun silenceThreshold(windows: IntArray): Int {
        if (windows.isEmpty()) return 0
        val sorted = windows.sortedArray()
        val floor = sorted[sorted.size / 10]
        val loud = sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.size - 1)]
        return minOf(floor * 5 / 2 + 100, loud / 2).coerceAtLeast(60)
    }

    // ---- writing

    /**
     * The sound of [sources], one after the other, written as a new file [target] and changed as [plan] says. The first source gives the
     * sampling rate and the channels; the others are brought to them. False when anything fails (nothing is left behind) or [cancelled].
     */
    fun render(sources: List<File>, target: File, plan: Plan, cancelled: () -> Boolean = { false }): Boolean {
        if (sources.isEmpty()) return false
        val (rate0, ch0) = AudioTools.info(sources[0]) ?: return false
        val channels = ch0.coerceIn(1, 2)
        val rate = if (plan.rate > 0) plan.rate else rate0
        val startUs = plan.startMs * 1000L
        val endUs = if (plan.endMs == Long.MAX_VALUE) Long.MAX_VALUE else plan.endMs * 1000L
        var encoder: Encoder? = null
        var done = false
        try {
            var gain = 1f
            var threshold = 0
            if (plan.normalise || plan.dropSilence) {
                val (peak, windows) = analyse(sources, plan, channels, cancelled) ?: return false
                if (plan.normalise && peak > 0) gain = (0.95f * 32767f / peak).coerceIn(1f, 12f)
                if (plan.dropSilence) threshold = silenceThreshold(windows)
            }
            val enc = Encoder(target, plan.mime, plan.container, rate, channels, plan.bitRate)
            encoder = enc
            val fadeFrames = (rate * plan.fadeMs / 1000L).toInt()
            var emitted = 0L
            val hold = Holdback(fadeFrames * channels) { s, n -> enc.write(s, n) }
            // Gain and the fade in, then the held-back end.
            val shape: (ShortArray, Int) -> Unit = { samples, count ->
                val frames = count / channels
                if (gain != 1f || (fadeFrames > 0 && emitted < fadeFrames)) {
                    for (f in 0 until frames) {
                        val g = gain * (if (fadeFrames > 0 && emitted + f < fadeFrames) (emitted + f).toFloat() / fadeFrames else 1f)
                        for (c in 0 until channels) samples[f * channels + c] = (samples[f * channels + c] * g).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
                emitted += frames
                hold.push(samples, count)
            }
            val gate = if (threshold > 0) Gate((rate * SILENCE_WINDOW_MS / 1000).coerceAtLeast(1), channels, threshold, SILENCE_KEEP_MS / SILENCE_WINDOW_MS, shape) else null
            for (source in sources) {
                val ok = stream(source, startUs, endUs, channels, { rate }, cancelled) { samples, count ->
                    // The buffer is reused by the stream: a copy goes down the chain.
                    val copy = samples.copyOf(count * channels)
                    if (gate != null) gate.push(copy, count) else shape(copy, count * channels)
                }
                if (!ok) return false
            }
            gate?.flush()
            hold.tail(channels, fadeFrames > 0)
            done = enc.finish()
            return done && !cancelled()
        } catch (e: Exception) {
            return false
        } finally {
            encoder?.close()
            if (!done || cancelled()) target.delete()
        }
    }

    /** The sound of [file] as a WAV file (16 bits, the rate and channels it has). */
    fun toWav(file: File, target: File, cancelled: () -> Boolean = { false }): Boolean {
        var rate = 0
        var channels = 0
        var bytes = 0L
        val out = runCatching { java.io.FileOutputStream(target).buffered() }.getOrNull() ?: return false
        val ok = try {
            out.write(ByteArray(44))
            AudioTools.decode(file, cancelled) { samples, _, r, c ->
                if (rate == 0) { rate = r; channels = c }
                val buffer = ByteBuffer.allocate(samples.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
                buffer.asShortBuffer().put(samples)
                out.write(buffer.array())
                bytes += buffer.capacity()
            }
        } catch (e: Exception) {
            false
        }
        runCatching { out.close() }
        if (!ok || bytes == 0L) { target.delete(); return false }
        val written = runCatching {
            RandomAccessFile(target, "rw").use { f ->
                f.write(wavHeader(rate, channels, bytes))
            }
        }.isSuccess
        if (!written) target.delete()
        return written
    }

    fun wavHeader(rate: Int, channels: Int, dataBytes: Long): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36 + dataBytes).toInt()).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        h.put("data".toByteArray()).putInt(dataBytes.toInt())
        return h.array()
    }

    // ---- looking

    /** How loud each frequency is along the sound, for a picture: [columns] times [bins] numbers from 0 to 1 (column after column, low to high). Null when it cannot be read. */
    fun spectrogram(file: File, columns: Int = 320, bins: Int = 72, cancelled: () -> Boolean = { false }): FloatArray? {
        val total = AudioTools.durationMs(file) * 1000L
        if (total <= 0L) return null
        val size = 512
        val data = FloatArray(columns * bins)
        val re = DoubleArray(size)
        val im = DoubleArray(size)
        val window = DoubleArray(size) { 0.5 - 0.5 * cos(2 * Math.PI * it / (size - 1)) }
        var last = -1
        val ok = AudioTools.decode(file, cancelled) { samples, timeUs, _, channels ->
            val c = channels.coerceAtLeast(1)
            val column = (timeUs.toDouble() / total * columns).toInt().coerceIn(0, columns - 1)
            val frames = samples.remaining() / c
            if (column == last || frames < 16) return@decode
            last = column
            val n = min(size, frames)
            val base = samples.position() + (frames - n) * c
            for (i in 0 until size) {
                var v = 0.0
                if (i < n) { for (k in 0 until c) v += samples.get(base + i * c + k); v /= c * 32768.0 }
                re[i] = v * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            // 256 useful bins folded into [bins] with the low ones kept finer: speech lives under 4 kHz.
            for (b in 0 until bins) {
                val from = (Math.pow((b).toDouble() / bins, 2.0) * (size / 2 - 1)).toInt().coerceAtLeast(1)
                val to = (Math.pow((b + 1).toDouble() / bins, 2.0) * (size / 2 - 1)).toInt().coerceAtLeast(from + 1).coerceAtMost(size / 2)
                var power = 0.0
                for (k in from until to) power += re[k] * re[k] + im[k] * im[k]
                data[column * bins + b] = (10 * ln(power / (to - from) + 1e-12) / ln(10.0)).toFloat()
            }
        }
        if (!ok) return null
        // Decibels to 0..1: the loudest is 1 and 70 dB under it is 0.
        val top = data.max()
        for (i in data.indices) data[i] = ((data[i] - (top - 70f)) / 70f).coerceIn(0f, 1f)
        return data
    }

    /** A Fourier transform of [re] and [im] in place; the length must be a power of two. */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * Math.PI / len
            val wr = cos(angle)
            val wi = sin(angle)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
