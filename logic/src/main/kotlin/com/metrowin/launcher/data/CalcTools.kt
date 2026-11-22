package com.metrowin.launcher.data

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt

/** The small tools of the calculator app next to the keypad. Pure, so they are tested with numbers. */
object CalcTools {
    private val THOUSANDS = Regex("(?<![\\d.])\\d{4,}")

    /** The whole parts of 4 digits or more in [text] written in groups of three, with a narrow no-break space; decimals and exponents are left alone. */
    fun group(text: String): String = THOUSANDS.replace(text) { it.value.reversed().chunked(3).joinToString("\u202f").reversed() }

    // --- programmer: whole numbers of 64 bits in base 2, 8, 10 and 16

    enum class Op { Add, Sub, Mul, Div, And, Or, Xor, Shl, Shr, Not }

    /** [text] as a whole number of [base], or null when it is empty or has a digit the base does not have. A sign is allowed. */
    fun parseInt(text: String, base: Int): Long? = text.trim().replace("_", "").takeIf { it.isNotEmpty() }?.let {
        runCatching { java.lang.Long.parseLong(it, base) }.getOrNull()
    }

    fun formatInt(value: Long, base: Int): String = java.lang.Long.toString(value, base).uppercase()

    /** [a] and [b] with [op] (a shift takes [b] bits, "not" ignores it); null for a division by zero or a shift of 64 or more. */
    fun apply(a: Long, op: Op, b: Long): Long? = when (op) {
        Op.Add -> a + b
        Op.Sub -> a - b
        Op.Mul -> a * b
        Op.Div -> if (b == 0L) null else a / b
        Op.And -> a and b
        Op.Or -> a or b
        Op.Xor -> a xor b
        Op.Shl -> if (b !in 0..63) null else a shl b.toInt()
        Op.Shr -> if (b !in 0..63) null else a shr b.toInt()
        Op.Not -> a.inv()
    }

    // --- dates

    class Gap(val years: Int, val months: Int, val days: Int, val totalDays: Long)

    /** The time from [from] to [to] as years, months and days (always positive, in order) and as days (negative when [to] is before). */
    fun between(from: LocalDate, to: LocalDate): Gap {
        val (a, b) = if (to.isBefore(from)) to to from else from to to
        val p = Period.between(a, b)
        return Gap(p.years, p.months, p.days, ChronoUnit.DAYS.between(from, to))
    }

    /** One date as the tools read it: "2026-11-14". */
    fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    // --- tip and a bill split between people

    class Bill(val tip: Double, val total: Double, val perPerson: Double)

    fun bill(amount: Double, tipPercent: Double, people: Int): Bill? {
        if (amount < 0 || tipPercent < 0 || people < 1 || !amount.isFinite() || !tipPercent.isFinite()) return null
        val tip = amount * tipPercent / 100.0
        return Bill(tip, amount + tip, (amount + tip) / people)
    }

    // --- statistics

    class Stats(
        val count: Int, val sum: Double, val mean: Double, val median: Double, val min: Double, val max: Double,
        val deviationOfPopulation: Double, val deviationOfSample: Double?,
    )

    /** The numbers in [text], separated by spaces, semicolons or new lines (a comma is a decimal point); null when one is not a number. */
    fun parseList(text: String): List<Double>? {
        val parts = text.split(Regex("[\\s;]+")).filter { it.isNotEmpty() }
        val numbers = parts.map { it.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
        return numbers.takeIf { it.isNotEmpty() }
    }

    fun stats(values: List<Double>): Stats? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mean = values.sum() / values.size
        val squares = values.sumOf { (it - mean) * (it - mean) }
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        return Stats(
            values.size, values.sum(), mean, median, sorted.first(), sorted.last(),
            sqrt(squares / values.size), if (values.size > 1) sqrt(squares / (values.size - 1)) else null,
        )
    }

    class Line(val slope: Double, val intercept: Double, val correlation: Double)

    /** The least-squares line through the points ([xs], [ys]); null with fewer than two points, other lengths, or all the x the same. */
    fun regression(xs: List<Double>, ys: List<Double>): Line? {
        if (xs.size != ys.size || xs.size < 2) return null
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        val syy = ys.sumOf { (it - my) * (it - my) }
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        if (sxx == 0.0) return null
        val slope = sxy / sxx
        return Line(slope, my - slope * mx, if (syy == 0.0) 1.0 else sxy / sqrt(sxx * syy))
    }

    // --- equations

    /** Roots of ax² + bx + c = 0: two real, one real, or a complex pair (real part, imaginary part); null when it is not an equation of degree 2. */
    sealed interface Roots {
        data class Real(val x1: Double, val x2: Double) : Roots
        data class Complex(val re: Double, val im: Double) : Roots
    }

    fun quadratic(a: Double, b: Double, c: Double): Roots? {
        if (a == 0.0) return null
        val d = b * b - 4 * a * c
        return if (d >= 0) Roots.Real((-b + sqrt(d)) / (2 * a), (-b - sqrt(d)) / (2 * a)) else Roots.Complex(-b / (2 * a), sqrt(-d) / (2 * a))
    }

    /**
     * A linear system, one equation per row: the coefficients and then the constant ("2 3 5" is 2x + 3y = 5). The solution, or null when
     * the rows are not a square system of 1 to 5 unknowns or the system has no single solution.
     */
    fun solve(rows: List<List<Double>>): List<Double>? {
        val n = rows.size
        if (n !in 1..5 || rows.any { it.size != n + 1 }) return null
        val m = rows.map { it.toDoubleArray() }.toTypedArray()
        for (col in 0 until n) {
            val pivot = (col until n).maxByOrNull { abs(m[it][col]) } ?: return null
            if (abs(m[pivot][col]) < 1e-12) return null
            m[col] = m[pivot].also { m[pivot] = m[col] }
            for (r in 0 until n) if (r != col) {
                val f = m[r][col] / m[col][col]
                for (k in col..n) m[r][k] -= f * m[col][k]
            }
        }
        return List(n) { m[it][n] / m[it][it] }
    }

    // --- constants

    class Constant(val symbol: String, val value: Double, val unit: String)

    val constants = listOf(
        Constant("c", 299_792_458.0, "m/s"),
        Constant("g", 9.80665, "m/s²"),
        Constant("G", 6.6743e-11, "m³/(kg·s²)"),
        Constant("h", 6.62607015e-34, "J·s"),
        Constant("e", 1.602176634e-19, "C"),
        Constant("k", 1.380649e-23, "J/K"),
        Constant("N", 6.02214076e23, "1/mol"),
        Constant("R", 8.314462618, "J/(mol·K)"),
        Constant("mₑ", 9.1093837015e-31, "kg"),
        Constant("mₚ", 1.67262192369e-27, "kg"),
    )

    // --- graph

    /**
     * The values of [expression] (with `x` in it) at [count] points from [from] to [to]: NaN where it has no value, so the line breaks there.
     * A hundred samples at the least would be a line; the screen asks for one per two pixels.
     */
    fun sample(expression: String, from: Double, to: Double, count: Int, angle: CalcEngine.Angle): DoubleArray {
        val out = DoubleArray(count)
        for (i in 0 until count) {
            val x = from + (to - from) * i / (count - 1).coerceAtLeast(1)
            out[i] = (CalcEngine.evaluate(expression, angle, 0.0, x) as? CalcEngine.Result.Value)?.number ?: Double.NaN
        }
        return out
    }
}
