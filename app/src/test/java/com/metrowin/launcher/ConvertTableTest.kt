package com.metrowin.launcher

import com.metrowin.launcher.data.ConvertTable
import com.metrowin.launcher.data.ConvertTable.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertTableTest {
    private fun convert(kind: Kind, value: Double, from: String, to: String): Double {
        val list = ConvertTable.units.getValue(kind)
        return ConvertTable.convert(value, list.first { it.symbol == from }, list.first { it.symbol == to })
    }

    @Test
    fun temperatures() {
        assertEquals(212.0, convert(Kind.Temperature, 100.0, "°C", "°F"), 1e-9)
        assertEquals(37.0, convert(Kind.Temperature, 98.6, "°F", "°C"), 1e-9)
        assertEquals(273.15, convert(Kind.Temperature, 0.0, "°C", "K"), 1e-9)
        assertEquals(-40.0, convert(Kind.Temperature, -40.0, "°F", "°C"), 1e-9)
    }

    @Test
    fun `lengths, masses, data, angles and times`() {
        assertEquals(1609.344, convert(Kind.Length, 1.0, "mi", "m"), 1e-9)
        assertEquals(2.2046226, convert(Kind.Mass, 1.0, "kg", "lb"), 1e-6)
        assertEquals(1024.0, convert(Kind.Data, 1.0, "KiB", "B"), 1e-9)
        assertEquals(180.0, convert(Kind.Angle, Math.PI, "rad", "°"), 1e-9)
        assertEquals(1.0, convert(Kind.Time, 3600.0, "s", "h"), 1e-12)
    }

    @Test
    fun `every unit converts to every other and back`() {
        for ((_, list) in ConvertTable.units) for (a in list) for (b in list) {
            val there = ConvertTable.convert(123.456, a, b)
            assertEquals(123.456, ConvertTable.convert(there, b, a), 1e-6)
        }
    }
}
