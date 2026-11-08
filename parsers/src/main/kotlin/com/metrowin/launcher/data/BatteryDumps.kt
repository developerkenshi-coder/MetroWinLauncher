package com.metrowin.launcher.data

/** Reads what the shell prints about the battery (dumpsys, sysfs). Plain text in, plain values out, so it can be unit tested. */
object BatteryDumps {
    /** The `key: value` lines of `dumpsys battery`, in the order they come. */
    fun dumpsysBattery(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            val colon = t.indexOf(':')
            if (colon <= 0 || t.startsWith("Time when") || t.startsWith("The last") || t.startsWith("Current Battery")) continue
            out.putIfAbsent(t.substring(0, colon).trim(), t.substring(colon + 1).trim())
        }
        return out
    }

    /** The `KEY=value` lines of each `/sys/class/power_supply/<name>/uevent`, under the name of the supply; a file the shell may not read has no lines. */
    fun sysfs(text: String): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("## ")) {
                val name = t.removePrefix("## ").substringBeforeLast("/uevent").substringAfterLast('/')
                current = LinkedHashMap<String, String>().also { out[name] = it }
            } else {
                val eq = t.indexOf('=')
                if (eq > 0) current?.put(t.substring(0, eq).removePrefix("POWER_SUPPLY_"), t.substring(eq + 1))
            }
        }
        return out.filterValues { it.isNotEmpty() }
    }

    /** What batterystats says in lines of `name: value` (the first one wins: the same names come again for the screen on and off parts). */
    fun statLines(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            val match = STAT.matchEntire(t) ?: continue
            out.putIfAbsent(match.groupValues[1], match.groupValues[2])
        }
        return out
    }

    /** The parts of the device that drew power (screen, cpu, wifi...), with the mAh, the biggest first. */
    fun components(text: String): List<Pair<String, Double>> {
        val seen = LinkedHashMap<String, Double>()
        for (line in text.lineSequence()) {
            val match = COMPONENT.matchEntire(line.trim()) ?: continue
            seen.putIfAbsent(match.groupValues[1], match.groupValues[2].toDouble())
        }
        return seen.toList().filter { it.second > 0.0 }.sortedByDescending { it.second }
    }

    /** The apps that drew power: the uid as batterystats writes it ("u0a195", "1000") with the mAh, the biggest first. */
    fun uids(text: String, limit: Int = 15): List<Pair<String, Double>> {
        val seen = LinkedHashMap<String, Double>()
        for (line in text.lineSequence()) {
            val match = UID.find(line.trim()) ?: continue
            seen.putIfAbsent(match.groupValues[1], match.groupValues[2].toDouble())
        }
        return seen.toList().filter { it.second > 0.0 }.sortedByDescending { it.second }.take(limit)
    }

    /** "u0a195" is the app with uid 10195; a plain number is that uid; anything else (a secondary user, an isolated process) is null. */
    fun uidNumber(id: String): Int? = APP_UID.matchEntire(id)?.let { 10_000 + it.groupValues[1].toInt() } ?: id.toIntOrNull()

    /** Temperatures of the sensors of the phone from `dumpsys thermalservice`: name, degrees, the first reading of each. */
    fun temperatures(text: String): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (match in TEMPERATURE.findAll(text)) out.putIfAbsent(match.groupValues[2], match.groupValues[1].toDouble())
        return out
    }

    /** The wake locks `dumpsys power` lists, as "TYPE 'tag'" (the uid and the age are left out). */
    fun wakeLocks(text: String): List<String> = text.lineSequence().map { it.trim() }.mapNotNull { WAKE.find(it)?.let { m -> "${m.groupValues[1]} ${m.groupValues[2]}" } }.toList()

    private val STAT = Regex("(Estimated battery capacity|Min learned battery capacity|Max learned battery capacity|Time on battery|Time on battery screen off|Time on battery screen doze|Total run time|Screen on|Amount discharged \\(lower bound\\)|Amount discharged \\(upper bound\\)|Amount discharged while screen on|Amount discharged while screen off|Computed drain|Capacity): (.*)")
    private val COMPONENT = Regex("([A-Za-z_]+): ([0-9.]+) apps: .*")
    private val UID = Regex("^UID (\\S+): ([0-9.]+)")
    private val APP_UID = Regex("u0a(\\d+)")
    private val TEMPERATURE = Regex("Temperature\\{mValue=(-?[0-9.]+), mType=\\d+, mName=([^,}]+)")
    private val WAKE = Regex("(PARTIAL_WAKE_LOCK|SCREEN_[A-Z_]*WAKE_LOCK|FULL_WAKE_LOCK|PROXIMITY_SCREEN_OFF_WAKE_LOCK|DOZE_WAKE_LOCK|DRAW_WAKE_LOCK)\\s+('[^']*')")
}
