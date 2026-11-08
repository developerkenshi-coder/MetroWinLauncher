package com.metrowin.launcher.recorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * What the recorder does to the sound of a file: how long it is, what it looks like (the loudness along it), a piece of it as a file of
 * its own, and the raw sound a speech recognizer takes. The files are the AAC of the recordings; nothing is ever written over a source.
 */
object AudioTools {
    private fun open(file: File): Pair<MediaExtractor, MediaFormat>? {
        val extractor = MediaExtractor()
        return runCatching {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            extractor.selectTrack(track)
            extractor to extractor.getTrackFormat(track)
        }.getOrElse { extractor.release(); null }
    }

    /** The length of the sound in milliseconds, or 0 when the file cannot be read: the retriever of Android knows most formats, the extractor is the second try. */
    fun durationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        val fromRetriever = runCatching {
            retriever.setDataSource(file.path)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() else null
        }.getOrNull()
        runCatching { retriever.release() }
        if (fromRetriever != null && fromRetriever > 0) return fromRetriever
        val (extractor, format) = open(file) ?: return 0L
        return try {
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000L else 0L
        } finally {
            extractor.release()
        }
    }

    /**
     * Whether a piece of [file] can be saved: the compressed sound is copied into an MP4 file, which the muxer of Android takes only for AAC
     * (the sound of the recorder and of most .m4a files). MP3, WAV, OGG, FLAC and the others can be played and sent, not cut here.
     */
    fun canCut(file: File): Boolean {
        val (extractor, format) = open(file) ?: return false
        extractor.release()
        return canCutMime(format.getString(MediaFormat.KEY_MIME))
    }

    fun canCutMime(mime: String?): Boolean = mime == MediaFormat.MIMETYPE_AUDIO_AAC

    /** Decodes [file] and gives each block of 16-bit samples (with its time in microseconds, the rate and the number of channels) to [sink]. */
    private fun decode(file: File, sink: (samples: java.nio.ShortBuffer, timeUs: Long, rate: Int, channels: Int) -> Unit): Boolean {
        val (extractor, format) = open(file) ?: return false
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
        val codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull() ?: run { extractor.release(); return false }
        return try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var idle = 0
            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out >= 0 -> {
                        idle = 0
                        val buffer = codec.getOutputBuffer(out)!!
                        buffer.position(info.offset).limit(info.offset + info.size)
                        sink(buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(), info.presentationTimeUs, rate, channels)
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    // A decoder that gives nothing for two seconds in a row after the input is over is stuck: stop.
                    else -> if (inputDone && ++idle > 2_000_000 / TIMEOUT_US) outputDone = true
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            extractor.release()
        }
    }

    /** [buckets] numbers from 0 to 1: how loud the sound is along the file, for the picture of it. Empty when it cannot be decoded. */
    fun peaks(file: File, buckets: Int = 160): FloatArray {
        val total = durationMs(file) * 1000L
        if (total <= 0L) return FloatArray(0)
        val peaks = FloatArray(buckets)
        val ok = decode(file) { samples, timeUs, _, channels ->
            val frames = samples.remaining() / channels.coerceAtLeast(1)
            val first = (timeUs.toDouble() / total * buckets).toInt().coerceIn(0, buckets - 1)
            var max = 0
            while (samples.remaining() >= channels.coerceAtLeast(1)) {
                for (c in 0 until channels) max = maxOf(max, abs(samples.get().toInt()))
            }
            if (frames > 0) peaks[first] = maxOf(peaks[first], max / 32768f)
        }
        if (!ok) return FloatArray(0)
        val top = peaks.max().takeIf { it > 0f } ?: return peaks
        return FloatArray(buckets) { (peaks[it] / top).coerceIn(0f, 1f) }
    }

    /**
     * The sound of [source] from [startMs] to [endMs] as a file of its own, [target]. The compressed sound is copied as it is, not
     * decoded and encoded again, so nothing is lost; the cut falls on a frame (about 23 ms). False when nothing could be written.
     */
    fun cut(source: File, startMs: Long, endMs: Long, target: File): Boolean {
        if (endMs <= startMs) return false
        val (extractor, format) = open(source) ?: return false
        var muxer: MediaMuxer? = null
        var started = false
        var wrote = false
        return try {
            muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val track = muxer.addTrack(format)
            muxer.start()
            started = true
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            var origin = -1L
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (time > endUs) break
                if (time >= startUs) {
                    if (origin < 0) origin = time
                    val sync = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, size, time - origin, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(track, buffer, info)
                    wrote = true
                }
                extractor.advance()
            }
            wrote
        } catch (e: Exception) {
            false
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
            if (!wrote) target.delete()
        }
    }

    /**
     * The sound of [file] as raw 16-bit mono at 16 kHz in [target], what a speech recognizer is given as a stream. A rate is changed by
     * averaging the samples that fall in the same output sample: enough for speech. False when the file cannot be decoded.
     */
    fun toSpeechPcm(file: File, target: File): Boolean {
        val out = runCatching { FileOutputStream(target).buffered() }.getOrNull() ?: return false
        var carry = 0.0
        var carryCount = 0
        var position = 0.0
        val bytes = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        fun flush() { out.write(bytes.array(), 0, bytes.position()); bytes.clear() }
        val ok = try {
            decode(file) { samples, _, rate, channels ->
                val step = rate / SPEECH_RATE.toDouble()
                while (samples.remaining() >= channels) {
                    var sum = 0
                    for (c in 0 until channels) sum += samples.get()
                    carry += sum / channels
                    carryCount++
                    position += 1.0
                    if (position >= step) {
                        position -= step
                        if (bytes.remaining() < 2) flush()
                        bytes.putShort((carry / carryCount).toInt().coerceIn(-32768, 32767).toShort())
                        carry = 0.0
                        carryCount = 0
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
        runCatching { flush(); out.close() }
        if (!ok) target.delete()
        return ok
    }

    const val SPEECH_RATE = 16_000
    private const val TIMEOUT_US = 10_000L
}
