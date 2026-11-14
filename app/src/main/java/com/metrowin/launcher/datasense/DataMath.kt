package com.metrowin.launcher.datasense

import java.time.ZonedDateTime
import kotlin.math.roundToLong

/** Bytes counted by Android between [start] and [end] (epoch milliseconds, [end] exclusive). */
class Span(val start: Long, val end: Long, val bytes: Long)

/** A rectangle of the treemap, as fractions (0 to 1) of the whole. */
data class Cell(val x: Float, val y: Float, val w: Float, val h: Float)

/** The pure arithmetic of the data page: limits, trends, projections, the cut of Android's buckets into bars. Tested. */
object DataMath {
    /**
     * The limit the user wrote, in MB (decimal, as Android's own settings count): "5" and "5 GB" are 5000, "500 MB" is 500, "2 GiB" is 2147.
     * A bare number is in GB. Null when there is no number in it.
     */
    fun parseLimitMb(text: String): Long? {
        val match = Regex("""^\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*)\s*$""").matchEntire(text) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val mb = when (match.groupValues[2].lowercase()) {
            "", "g", "gb" -> number * 1_000
            "m", "mb" -> number
            "gib" -> number * 1_073.741824
            "mib" -> number * 1.048576
            "t", "tb" -> number * 1_000_000
            else -> return null
        }
        return mb.roundToLong().coerceIn(0, 100_000_000)
    }

    /** The limit of [mb] written back as the user would type it: whole GB as "5", the rest in MB. */
    fun limitText(mb: Long): String = if (mb <= 0) "" else if (mb % 1000 == 0L) (mb / 1000).toString() else "$mb MB"

    /**
     * Cuts what Android counted in [spans] (each over a stretch of time) into [buckets]: a span that covers two bars is shared between them
     * in proportion to the time each has. One query of Android for all the bars, in place of one for each.
     */
    fun distribute(spans: List<Span>, buckets: List<DataBucket>): LongArray {
        val out = DoubleArray(buckets.size)
        for (s in spans) {
            if (s.bytes <= 0L) continue
            val length = (s.end - s.start).coerceAtLeast(1L).toDouble()
            buckets.forEachIndexed { i, b ->
                val overlap = minOf(s.end, b.to) - maxOf(s.start, b.from)
                if (overlap > 0) out[i] += s.bytes * (overlap / length)
                else if (s.end <= s.start && s.start >= b.from && s.start < b.to) out[i] += s.bytes.toDouble()
            }
        }
        return LongArray(out.size) { out[it].roundToLong() }
    }

    /** Bytes the cycle will have used at its end if the pace so far goes on: [used] over [elapsed] stretched to [total]. */
    fun projection(used: Long, elapsedMs: Long, totalMs: Long): Long =
        if (elapsedMs <= 0 || totalMs <= elapsedMs) used else (used.toDouble() * totalMs / elapsedMs).roundToLong()

    /** The change from [before] to [now] in percent, or null when there is nothing to compare with. */
    fun trendPercent(now: Long, before: Long): Int? = if (before <= 0L) null else ((now - before) * 100.0 / before).roundToLong().toInt()

    /**
     * How many times the use of today is the daily average of [earlier] (the days with any use), when it is at least 2 times and at least
     * 50 MB over three days or more; null when there is nothing unusual.
     */
    fun unusualFactor(today: Long, earlier: List<Long>): Int? {
        val days = earlier.filter { it > 0 }
        if (days.size < 3 || today < 50_000_000L) return null
        val factor = today / days.average()
        return if (factor >= 2.0) factor.toInt() else null
    }

    /** Where the period before [period] starts, as long as the period is by now: a day before, 7, 30, or a month before for the cycle. */
    fun previousStart(period: DataPeriod, from: ZonedDateTime): ZonedDateTime = when (period) {
        DataPeriod.Today -> from.minusDays(1)
        DataPeriod.Week -> from.minusDays(7)
        DataPeriod.Month30 -> from.minusDays(30)
        DataPeriod.ThisMonth -> from.minusMonths(1)
        DataPeriod.History -> from.minusDays(90)
    }

    /** What the user has to pay over the [limit]: the bytes over it at [pricePerGb] each GB. */
    fun overageCost(used: Long, limit: Long, pricePerGb: Double): Double = if (limit <= 0 || used <= limit) 0.0 else (used - limit) / 1e9 * pricePerGb

    /** The text of a CSV field: quoted when it has a comma, a quote or a line break. */
    fun csvField(text: String): String = if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

    fun csv(bars: List<Triple<DataBucket, Long, Long?>>, apps: List<AppData>): String = buildString {
        append("label,from,to,wifi_bytes,mobile_bytes\n")
        bars.forEach { (b, wifi, mobile) -> append("${csvField(b.label)},${b.from},${b.to},$wifi,${mobile ?: ""}\n") }
        append("\napp,package,wifi_bytes,mobile_bytes,foreground_bytes,background_bytes\n")
        apps.forEach { append("${csvField(it.label)},${it.packageName ?: ""},${it.wifi},${it.mobile},${it.foreground},${it.background}\n") }
    }

    /**
     * The rectangles of a treemap for [values] (the biggest first): each takes a strip along the shorter side of what is left, in proportion
     * to its share. The areas are the shares of the whole.
     */
    fun treemap(values: List<Long>): List<Cell> {
        val total = values.sum().toDouble()
        if (total <= 0) return emptyList()
        var x = 0f; var y = 0f; var w = 1f; var h = 1f
        var left = total
        return values.mapIndexed { i, v ->
            val share = if (i == values.lastIndex || left <= 0) 1f else (v / left).toFloat()
            left -= v
            if (w >= h) {
                val cw = w * share
                Cell(x, y, cw, h).also { x += cw; w -= cw }
            } else {
                val ch = h * share
                Cell(x, y, w, ch).also { y += ch; h -= ch }
            }
        }
    }

    /** Merges lines "day:wifi:mobile" with the new days (the new ones win), keeps the latest [keep] days, oldest first. */
    fun mergeDaily(stored: String, fresh: Map<Long, Pair<Long, Long>>, keep: Int = 400): String {
        val days = sortedMapOf<Long, Pair<Long, Long>>()
        stored.lineSequence().forEach { line ->
            val p = line.split(':')
            if (p.size == 3) runCatching { days[p[0].toLong()] = p[1].toLong() to p[2].toLong() }
        }
        days.putAll(fresh)
        return days.entries.toList().takeLast(keep).joinToString("\n") { "${it.key}:${it.value.first}:${it.value.second}" }
    }

    fun parseDaily(stored: String): List<Triple<Long, Long, Long>> = stored.lineSequence().mapNotNull { line ->
        val p = line.split(':')
        if (p.size != 3) null else runCatching { Triple(p[0].toLong(), p[1].toLong(), p[2].toLong()) }.getOrNull()
    }.toList()

    /** Samples "day:free" of the free space, the latest [keep] of them, one for each day, oldest first. */
    fun mergeFree(stored: String, day: Long, free: Long, keep: Int = 200): String {
        val days = sortedMapOf<Long, Long>()
        stored.lineSequence().forEach { line ->
            val p = line.split(':')
            if (p.size == 2) runCatching { days[p[0].toLong()] = p[1].toLong() }
        }
        days[day] = free
        return days.entries.toList().takeLast(keep).joinToString("\n") { "${it.key}:${it.value}" }
    }

    fun parseFree(stored: String): List<Pair<Long, Long>> = stored.lineSequence().mapNotNull { line ->
        val p = line.split(':')
        if (p.size != 2) null else runCatching { p[0].toLong() to p[1].toLong() }.getOrNull()
    }.toList()
}
