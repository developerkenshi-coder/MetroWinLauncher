package com.metrowin.launcher.shizukuterminal

import org.json.JSONArray
import org.json.JSONObject

/** The pure rules of the Terminal (what needs a question, what a read-only run lets through, how the lists are kept), apart from the screens so they can be tested. */

/** The commands the Terminal offers when nothing is kept yet. They are shell lines: they stay as they are in every language. */
internal val STARTERS = listOf(
    "dumpsys battery", "getprop ro.build.version.release", "pm list packages -3", "settings list global", "wm size", "wm density",
    "df -h /data", "uptime", "dumpsys deviceidle whitelist", "cmd package list packages -d", "logcat -d -t 200 *:W", "top -b -n 1 -m 10",
)

internal object Command {
    private val SEPARATORS = Regex("""[;&|\n(){}`"'$]+""")

    /** The simple commands of a line: split where a shell starts another one, and inside `sh -c "..."`, `$(...)` and backticks. */
    fun segments(line: String): List<List<String>> =
        line.split(SEPARATORS).map { part -> part.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }

    private val WRAPPERS = setOf("sudo", "su", "exec", "nohup", "time", "timeout", "env", "command", "busybox", "toybox", "xargs", "nice")

    /** The program of a segment (the wrappers that only run another program are skipped, a path is cut to its name) and what follows it. */
    private fun program(tokens: List<String>): List<String> {
        var rest = tokens
        while (rest.isNotEmpty() && (rest[0].substringAfterLast('/') in WRAPPERS || '=' in rest[0])) rest = rest.drop(1)
        return rest.mapIndexed { i, t -> if (i == 0) t.substringAfterLast('/') else t }
    }

    private val PM_BAD = setOf("clear", "uninstall", "disable", "disable-user", "hide", "suspend", "reset-permissions", "revoke", "trim-caches", "uninstall-system-updates")
    private val ALWAYS_BAD = setOf("rm", "rmdir", "reboot", "shutdown", "poweroff", "halt", "wipe", "dd", "format", "setprop", "resetprop", "mv", "truncate", "mkswap", "swapoff", "fsck", "e2fsck")

    /** A command that can lose data or leave the phone in a state that needs a computer to bring it back: the Terminal asks before it runs one. */
    fun destructive(line: String): Boolean = segments(line).any { raw ->
        val t = program(raw)
        if (t.isEmpty()) return@any false
        val name = t[0]
        val second = t.getOrNull(1)
        name in ALWAYS_BAD || name.startsWith("mkfs") ||
            (name == "settings" && second in setOf("put", "delete")) ||
            ((name == "pm" || (name == "cmd" && second == "package")) && (if (name == "pm") second else t.getOrNull(2)) in PM_BAD) ||
            (name == "svc" && second == "power" && t.getOrNull(2) in setOf("reboot", "shutdown")) ||
            (name == "wm" && second in setOf("size", "density") && t.getOrNull(2)?.let { it != "reset" } == true) ||
            ((name == "chmod" || name == "chown") && t.any { it == "-R" || it == "-r" })
    }

    private val SAFE = setOf(
        "dumpsys", "getprop", "df", "du", "uptime", "id", "whoami", "ls", "cat", "echo", "grep", "egrep", "head", "tail", "wc", "sort", "uniq", "awk", "cut", "tr",
        "uname", "date", "free", "ps", "top", "getenforce", "printenv", "ip", "ifconfig", "netstat", "lsof", "stat", "readlink", "which", "true", "test", "[",
        "logcat", "pidof", "vmstat", "mount", "getevent", "lsmod", "nproc", "hostname", "basename", "dirname", "find", "sleep", "appops",
    )

    /** Whether [line] only looks: every command of it is one that reads. Output redirection never counts as reading. */
    fun readOnly(line: String): Boolean {
        if (line.contains('>')) return false
        return segments(line).all { raw ->
            val t = program(raw)
            if (t.isEmpty()) return@all true
            val name = t[0]
            val second = t.getOrNull(1)
            when (name) {
                "wm" -> t.size == 2 && second in setOf("size", "density")
                "pm" -> second in setOf("list", "path", "dump", "resolve-activity", "get-install-location", "get-max-users")
                "cmd" -> second == "package" && t.getOrNull(2) in setOf("list", "resolve-activity", "dump")
                "settings" -> second in setOf("get", "list")
                "logcat" -> "-c" !in t && "--clear" !in t && "-G" !in t
                "appops" -> second == "get"
                "find" -> t.none { it in setOf("-delete", "-exec", "-execdir", "-ok") }
                "mount" -> t.size == 1
                else -> name in SAFE
            }
        }
    }
}

/** Package names, as the system writes them: nothing else is handed to `pm` and `am` from the Apps page. */
internal val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")

/** The commands the user keeps: one JSON array, so a command of several lines stays one. The old format (one command per line) is still read. */
internal object SavedCommands {
    fun encode(list: List<String>): String = JSONArray(list).toString()

    fun decode(text: String?): List<String> {
        if (text == null) return STARTERS
        if (text.startsWith("[")) {
            val array = runCatching { JSONArray(text) }.getOrNull() ?: return STARTERS
            return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        }
        return text.lines().filter { it.isNotBlank() }
    }
}

/** The names the user gave to kept commands (command to name), as one JSON object; a command with no name is shown as it is. */
internal object SavedNames {
    const val MAX_NAME = 30

    fun encode(names: Map<String, String>): String = JSONObject(names).toString()

    fun decode(text: String?): Map<String, String> = runCatching {
        val o = JSONObject(text ?: return emptyMap())
        o.keys().asSequence().associateWith { o.optString(it).trim().take(MAX_NAME) }.filterValues { it.isNotEmpty() }
    }.getOrDefault(emptyMap())
}

/**
 * What to offer under the line the user is typing: the kept commands that start with it first, then the ones that only contain it;
 * a command is also found by its name ([names]).
 */
internal fun suggestions(saved: List<String>, typed: String, names: Map<String, String> = emptyMap()): List<String> {
    val q = typed.trim()
    if (q.isEmpty()) return saved
    return saved.filter { it.startsWith(q, true) && !it.equals(q, true) } +
        saved.filter { !it.startsWith(q, true) && (it.contains(q, true) || names[it]?.contains(q, true) == true) }
}

/** How a run ended, apart from the number. */
internal enum class Ending { Ok, Failed, NoShizuku, Timeout, Stopped;
    companion object {
        fun of(code: Int) = when (code) { 0 -> Ok; -1 -> NoShizuku; -2 -> Timeout; -3 -> Stopped; else -> Failed }
    }
}

/** The level of a line of logcat (V D I W E F), or null for a line that is not one. */
internal fun logLevel(line: String): Char? = LOG_LINE.find(line)?.groupValues?.get(1)?.single()

private val LOG_LINE = Regex("""^(?:\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}\s+(?:\d+\s+\d+\s+)?)?([VDIWEF])(?:/|\s)""")

/** A process of `top`: its number, the load of the processor and of the memory, its name. */
internal class ProcessRow(val pid: String, val cpu: Float, val mem: Float, val name: String)

private val TOP_ROW = Regex("""^\s*(\d+)\s+.*?\s([RSDZTIXtW])\s+(\d+(?:[.,]\d+)?)\s+(\d+(?:[.,]\d+)?)\s+(\S+)\s+(.+)$""")

/** The processes of `top -b -n 1` (the toybox of Android), busiest first. The columns differ from one version to another, so each row is read from its state letter on. */
internal fun parseTop(text: String): List<ProcessRow> = text.lines().mapNotNull { line ->
    val m = TOP_ROW.find(line) ?: return@mapNotNull null
    val (pid, _, cpu, mem, _, args) = m.destructured
    val name = args.trim().substringBefore(' ').substringAfterLast('/')
    ProcessRow(pid, cpu.replace(',', '.').toFloat(), mem.replace(',', '.').toFloat(), name)
}.sortedByDescending { it.cpu }

/** One operation of `appops get`: its name and what it says (allow, ignore, deny, default, foreground). */
internal class AppOp(val name: String, val mode: String)

private val APP_OP = Regex("""^([A-Z][A-Z0-9_]+):\s*(allow|ignore|deny|default|foreground)""")

internal fun parseAppOps(text: String): List<AppOp> = text.lines().mapNotNull { line -> APP_OP.find(line.trim())?.let { AppOp(it.groupValues[1], it.groupValues[2]) } }

/** The mode that follows [mode] when the user taps an operation: allowed, then refused, then back to what the system decides. */
internal fun nextAppOpMode(mode: String) = when (mode) { "allow", "foreground" -> "ignore"; "ignore", "deny" -> "default"; else -> "allow" }
