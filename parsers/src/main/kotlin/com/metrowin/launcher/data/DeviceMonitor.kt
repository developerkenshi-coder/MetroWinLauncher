package com.metrowin.launcher.data

/** Reads what the shell prints about the processor, the memory, the services and the crashes. Plain text in, plain values out, so it can be unit tested. */
object DeviceMonitor {
    /** One line of /proc/stat: the time the processor was busy and the time in all, in ticks. */
    class Ticks(val busy: Long, val total: Long)

    /** The `cpu` and `cpuN` lines of /proc/stat: the first is the whole processor, then each core. */
    fun ticks(text: String): List<Ticks> = text.lineSequence().mapNotNull { line ->
        if (!line.startsWith("cpu")) return@mapNotNull null
        val numbers = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (numbers.size < 4) return@mapNotNull null
        val total = numbers.take(8).sum()
        val idle = numbers[3] + numbers.getOrElse(4) { 0L }
        Ticks(total - idle, total)
    }.toList()

    /** The load, in percent, of each line between two readings of [ticks]; 0 where nothing ran or the lines do not match. */
    fun usage(before: List<Ticks>, after: List<Ticks>): List<Float> = after.mapIndexed { i, now ->
        val old = before.getOrNull(i) ?: return@mapIndexed 0f
        val total = now.total - old.total
        if (total <= 0) 0f else ((now.busy - old.busy) * 100f / total).coerceIn(0f, 100f)
    }

    /** A core as sysfs gives it; frequencies in kHz. */
    class Core(val name: String, val cur: Long?, val min: Long?, val max: Long?, val governor: String, val online: Boolean)

    /** Lines "cpuN cur min max governor online" (what the script of the monitor prints); a core that is off has no frequency. */
    fun cores(text: String): List<Core> = text.lineSequence().mapNotNull { line ->
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 6 || !p[0].startsWith("cpu")) return@mapNotNull null
        Core(p[0], p[1].toLongOrNull(), p[2].toLongOrNull(), p[3].toLongOrNull(), p[4].takeIf { it.any(Char::isLetter) && !it.contains(':') } ?: "-", p[5] != "0")
    }.toList()

    /** The values of `Key:   123 kB` lines (/proc/meminfo), in kB. */
    fun memInfo(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val m = MEM.matchEntire(line.trim()) ?: return@mapNotNull null
        m.groupValues[1] to m.groupValues[2].toLong()
    }.toMap()

    /** The processes of "Total PSS by process" in `dumpsys meminfo`: the app, the memory in kB, the biggest first. */
    fun processMemory(text: String, limit: Int = 15): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        var inBlock = false
        for (line in text.lineSequence()) {
            if (line.startsWith("Total PSS by process")) {
                inBlock = true
                continue
            }
            if (inBlock) {
                val m = PSS.find(line) ?: break
                out += m.groupValues[2] to m.groupValues[1].replace(",", "").toLong()
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** The services that run, counted per app: from the `ServiceRecord{id user package/.Service}` lines of `dumpsys activity services`. */
    fun servicesByApp(text: String, limit: Int = 15): List<Pair<String, Int>> =
        text.lineSequence().mapNotNull { SERVICE.find(it)?.groupValues?.get(1) }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(limit)

    /** A crash or a blocked app (ANR) found in the event log. */
    class Fault(val time: String, val anr: Boolean, val packageName: String, val detail: String)

    /** The `am_crash` and `am_anr` lines of `logcat -b events -v time`, newest last. */
    fun faults(text: String): List<Fault> = text.lineSequence().mapNotNull { line ->
        val m = FAULT.find(line) ?: return@mapNotNull null
        val fields = m.groupValues[3].split(',')
        if (fields.size < 3) return@mapNotNull null
        val anr = m.groupValues[2] == "am_anr"
        // am_crash: user, pid, package, flags, exception, message...; am_anr: user, pid, package, flags, reason.
        Fault(m.groupValues[1], anr, fields[2].trim(), fields.getOrNull(4)?.trim().orEmpty())
    }.toList()

    /** The values of `getprop` ("[key]: [value]"). */
    fun props(text: String): Map<String, String> = text.lineSequence().mapNotNull { PROP.matchEntire(it.trim())?.let { m -> m.groupValues[1] to m.groupValues[2] } }.toMap()

    /** What the Android verified boot state says, as one word. */
    fun bootState(state: String?, locked: String?): String? = when (state) {
        "green" -> if (locked == "0") "green, bootloader open" else "green (locked, original system)"
        "yellow" -> "yellow (locked, other key)"
        "orange" -> "orange (bootloader unlocked)"
        "red" -> "red (system not verified)"
        else -> state?.takeIf { it.isNotBlank() }
    }

    private val MEM = Regex("([A-Za-z_()0-9]+):\\s+(\\d+) kB")
    private val PSS = Regex("^\\s*([\\d,]+)K: (\\S+) \\(pid")
    private val SERVICE = Regex("ServiceRecord\\{\\S+ u\\d+ ([^/\\s]+)/")
    private val FAULT = Regex("^(\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d)\\.\\d+\\s+\\S/(am_crash|am_anr)\\(\\s*\\d+\\):\\s+\\[(.*)]")
    private val PROP = Regex("\\[([^\\]]+)]: \\[(.*)]")

    /** One shell call for the processor and the memory: the lines of /proc/stat, a marker, the interesting ones of /proc/meminfo, a marker, then each core. */
    const val SCRIPT =
        "grep '^cpu' /proc/stat; echo '=='; grep -E '^(MemTotal|MemAvailable|MemFree|Cached|Buffers|SwapTotal|SwapFree|Dirty|Shmem):' /proc/meminfo; echo '=='; " +
            "for c in /sys/devices/system/cpu/cpu[0-9]*; do n=\${c##*/}; echo \"\$n \$(cat \$c/cpufreq/scaling_cur_freq 2>/dev/null || echo -) \$(cat \$c/cpufreq/cpuinfo_min_freq 2>/dev/null || echo -) " +
            "\$(cat \$c/cpufreq/cpuinfo_max_freq 2>/dev/null || echo -) \$(cat \$c/cpufreq/scaling_governor 2>/dev/null || echo -) \$(cat \$c/online 2>/dev/null || echo 1)\"; done"
}
