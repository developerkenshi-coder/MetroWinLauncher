package com.metrowin.launcher.data

/**
 * The units of the converter of the calculator app, by kind. Each unit has its size in the base unit of its kind ([Unit.factor]) and,
 * for the temperatures, a shift ([Unit.offset], in the base unit, applied before the factor). The money is not here: its rates
 * come from the network ([Currencies]). Pure.
 */
object ConvertTable {
    enum class Kind { Length, Area, Volume, Mass, Temperature, Speed, Time, Data, Energy, Pressure, Angle }

    class Unit(val symbol: String, val factor: Double, val offset: Double = 0.0)

    val units: Map<Kind, List<Unit>> = mapOf(
        Kind.Length to listOf(
            Unit("mm", 0.001), Unit("cm", 0.01), Unit("m", 1.0), Unit("km", 1000.0), Unit("in", 0.0254), Unit("ft", 0.3048),
            Unit("yd", 0.9144), Unit("mi", 1609.344), Unit("nmi", 1852.0),
        ),
        Kind.Area to listOf(
            Unit("mm²", 1e-6), Unit("cm²", 1e-4), Unit("m²", 1.0), Unit("ha", 10_000.0), Unit("km²", 1e6), Unit("in²", 0.00064516),
            Unit("ft²", 0.09290304), Unit("ac", 4046.8564224), Unit("mi²", 2_589_988.110336),
        ),
        Kind.Volume to listOf(
            Unit("ml", 0.001), Unit("cl", 0.01), Unit("dl", 0.1), Unit("l", 1.0), Unit("m³", 1000.0), Unit("tsp", 0.00492892159375),
            Unit("tbsp", 0.01478676478125), Unit("fl oz", 0.0295735295625), Unit("cup", 0.2365882365), Unit("pt", 0.473176473),
            Unit("qt", 0.946352946), Unit("gal", 3.785411784),
        ),
        Kind.Mass to listOf(
            Unit("mg", 1e-6), Unit("g", 0.001), Unit("kg", 1.0), Unit("t", 1000.0), Unit("oz", 0.028349523125), Unit("lb", 0.45359237),
            Unit("st", 6.35029318),
        ),
        Kind.Temperature to listOf(
            // The base is the kelvin: K = (°C + 273.15) × 1 = (°F + 459.67) × 5/9.
            Unit("°C", 1.0, 273.15), Unit("°F", 5.0 / 9.0, 459.67), Unit("K", 1.0, 0.0),
        ),
        Kind.Speed to listOf(
            Unit("m/s", 1.0), Unit("km/h", 1.0 / 3.6), Unit("mph", 0.44704), Unit("kn", 1852.0 / 3600.0), Unit("ft/s", 0.3048),
        ),
        Kind.Time to listOf(
            Unit("ms", 0.001), Unit("s", 1.0), Unit("min", 60.0), Unit("h", 3600.0), Unit("d", 86_400.0), Unit("wk", 604_800.0),
            Unit("yr", 31_557_600.0),
        ),
        Kind.Data to listOf(
            Unit("bit", 0.125), Unit("B", 1.0), Unit("kB", 1e3), Unit("MB", 1e6), Unit("GB", 1e9), Unit("TB", 1e12),
            Unit("KiB", 1024.0), Unit("MiB", 1024.0 * 1024), Unit("GiB", 1024.0 * 1024 * 1024),
        ),
        Kind.Energy to listOf(
            Unit("J", 1.0), Unit("kJ", 1000.0), Unit("cal", 4.184), Unit("kcal", 4184.0), Unit("Wh", 3600.0), Unit("kWh", 3_600_000.0),
            Unit("BTU", 1055.05585262),
        ),
        Kind.Pressure to listOf(
            Unit("Pa", 1.0), Unit("kPa", 1000.0), Unit("bar", 100_000.0), Unit("atm", 101_325.0), Unit("psi", 6894.757293168),
            Unit("mmHg", 133.322387415),
        ),
        Kind.Angle to listOf(Unit("°", 1.0), Unit("rad", 180.0 / Math.PI), Unit("grad", 0.9), Unit("rev", 360.0)),
    )

    /** [value] of [from] in [to], two units of the same kind. */
    fun convert(value: Double, from: Unit, to: Unit): Double = ((value + from.offset) * from.factor) / to.factor - to.offset
}
