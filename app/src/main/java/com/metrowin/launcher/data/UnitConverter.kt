package com.metrowin.launcher.data

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormatSymbols
import java.util.Locale

/** "5 km in miles", "30 c in f", "70 kg to lb": a conversion between units of the same kind, offline. Pure. */
object UnitConverter {
    private enum class Kind { Length, Mass, Temperature, Volume, Speed, Data, Time }

    /**
     * A unit: its [symbol] as written in the answer, its size in the base unit of its kind ([factor]; [offset] for the
     * temperatures), and the [names] it is known by (lower case, no accents or degree signs, English and Italian).
     */
    private class Unit(val symbol: String, val kind: Kind, val factor: Double, val names: List<String>, val offset: Double = 0.0)

    private val units = listOf(
        Unit("mm", Kind.Length, 0.001, listOf("mm", "millimetro", "millimetri", "millimeter", "millimeters", "millimetre", "millimetres")),
        Unit("cm", Kind.Length, 0.01, listOf("cm", "centimetro", "centimetri", "centimeter", "centimeters", "centimetre", "centimetres")),
        Unit("m", Kind.Length, 1.0, listOf("m", "metro", "metri", "meter", "meters", "metre", "metres")),
        Unit("km", Kind.Length, 1000.0, listOf("km", "chilometro", "chilometri", "kilometer", "kilometers", "kilometre", "kilometres")),
        Unit("in", Kind.Length, 0.0254, listOf("in", "inch", "inches", "pollice", "pollici")),
        Unit("ft", Kind.Length, 0.3048, listOf("ft", "foot", "feet", "piede", "piedi")),
        Unit("yd", Kind.Length, 0.9144, listOf("yd", "yard", "yards", "iarda", "iarde")),
        Unit("mi", Kind.Length, 1609.344, listOf("mi", "mile", "miles", "miglio", "miglia")),
        Unit("mg", Kind.Mass, 0.000001, listOf("mg", "milligrammo", "milligrammi", "milligram", "milligrams")),
        Unit("g", Kind.Mass, 0.001, listOf("g", "grammo", "grammi", "gram", "grams")),
        Unit("kg", Kind.Mass, 1.0, listOf("kg", "chilo", "chili", "chilogrammo", "chilogrammi", "kilo", "kilos", "kilogram", "kilograms")),
        Unit("lb", Kind.Mass, 0.45359237, listOf("lb", "lbs", "libbra", "libbre", "pound", "pounds")),
        Unit("oz", Kind.Mass, 0.028349523125, listOf("oz", "oncia", "once", "ounce", "ounces")),
        Unit("°C", Kind.Temperature, 1.0, listOf("c", "celsius", "centigradi", "gradi c", "gradi celsius", "degrees c", "degrees celsius")),
        Unit("°F", Kind.Temperature, 5.0 / 9.0, listOf("f", "fahrenheit", "gradi f", "gradi fahrenheit", "degrees f", "degrees fahrenheit"), offset = -32.0 * 5.0 / 9.0),
        Unit("K", Kind.Temperature, 1.0, listOf("k", "kelvin"), offset = -273.15),
        Unit("ml", Kind.Volume, 0.001, listOf("ml", "millilitro", "millilitri", "milliliter", "milliliters", "millilitre", "millilitres")),
        Unit("cl", Kind.Volume, 0.01, listOf("cl", "centilitro", "centilitri", "centiliter", "centiliters")),
        Unit("dl", Kind.Volume, 0.1, listOf("dl", "decilitro", "decilitri", "deciliter", "deciliters")),
        Unit("l", Kind.Volume, 1.0, listOf("l", "litro", "litri", "liter", "liters", "litre", "litres")),
        Unit("gal", Kind.Volume, 3.785411784, listOf("gal", "gallon", "gallons", "gallone", "galloni")),
        Unit("km/h", Kind.Speed, 1.0 / 3.6, listOf("km/h", "kmh", "kph", "chilometri orari", "chilometri all ora", "kilometers per hour")),
        Unit("mph", Kind.Speed, 0.44704, listOf("mph", "miglia orarie", "miglia all ora", "miles per hour")),
        Unit("m/s", Kind.Speed, 1.0, listOf("m/s", "ms", "metri al secondo", "meters per second")),
        Unit("kn", Kind.Speed, 0.514444, listOf("kn", "kt", "nodo", "nodi", "knot", "knots")),
        Unit("B", Kind.Data, 1.0, listOf("b", "byte", "bytes")),
        Unit("kB", Kind.Data, 1_000.0, listOf("kb", "kilobyte", "kilobytes")),
        Unit("MB", Kind.Data, 1_000_000.0, listOf("mb", "megabyte", "megabytes")),
        Unit("GB", Kind.Data, 1_000_000_000.0, listOf("gb", "gigabyte", "gigabytes")),
        Unit("TB", Kind.Data, 1_000_000_000_000.0, listOf("tb", "terabyte", "terabytes")),
        Unit("s", Kind.Time, 1.0, listOf("s", "sec", "secs", "secondo", "secondi", "second", "seconds")),
        Unit("min", Kind.Time, 60.0, listOf("min", "minuto", "minuti", "minute", "minutes")),
        Unit("h", Kind.Time, 3_600.0, listOf("h", "hr", "hrs", "ora", "ore", "hour", "hours")),
        Unit("d", Kind.Time, 86_400.0, listOf("d", "giorno", "giorni", "day", "days")),
    )

    private val byName: Map<String, Unit> = units.flatMap { u -> u.names.map { it to u } }.toMap()

    /** A conversion that was understood: what was asked, as it is shown, and the result. */
    data class Conversion(val from: String, val to: String, val result: String) {
        /** "5 km = 3.1069 mi". */
        val text: String get() = "$from = $to"
    }

    private val PATTERN = Regex("""^([-+]?\d+(?:[.,]\d+)?)\s*([\p{L}°/ ]+?)\s+(?:in|to|a|into|=|->|=>)\s*([\p{L}°/ ]+)$""")
    private val PATTERN_NO_SPACE = Regex("""^([-+]?\d+(?:[.,]\d+)?)([\p{L}°/]+)\s*(?:in|to|a|into|=|->|=>)\s*([\p{L}°/ ]+)$""")

    fun convert(query: String, locale: Locale = Locale.getDefault()): Conversion? {
        val text = AppSections.fold(query).replace("°", "").replace("?", "").trim()
        val match = PATTERN.matchEntire(text) ?: PATTERN_NO_SPACE.matchEntire(text) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = byName[match.groupValues[2].trim()] ?: return null
        val to = byName[match.groupValues[3].trim()] ?: return null
        if (from.kind != to.kind || from === to) return null
        // Every unit is `base = value * factor + offset`; only the temperatures have an offset.
        val base = value * from.factor + from.offset
        val result = (base - to.offset) / to.factor
        if (!result.isFinite()) return null
        return Conversion("${trim(value, locale)} ${from.symbol}", "${trim(result, locale)} ${to.symbol}", trim(result, locale))
    }

    /** Up to six digits, no trailing zeros, in the decimal separator of [locale]. */
    private fun trim(value: Double, locale: Locale): String {
        val plain = BigDecimal(value).round(MathContext(6)).stripTrailingZeros().toPlainString()
        return plain.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator).let { if (it == "-0") "0" else it }
    }
}
