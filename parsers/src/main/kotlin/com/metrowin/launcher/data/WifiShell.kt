package com.metrowin.launcher.data

/** Reads what `cmd wifi` prints: the networks around and the networks the phone knows. Plain text in, plain values out, so it can be unit tested. */
object WifiShell {
    class Net(val ssid: String, val dbm: Int, val mhz: Int, val secured: Boolean, val sae: Boolean)

    /** The networks of `cmd wifi list-scan-results`, one per name (the strongest), the strongest first; a hidden network (no name) is left out. */
    fun scan(text: String): List<Net> = text.lineSequence().mapNotNull { line ->
        val m = SCAN.matchEntire(line.trim()) ?: return@mapNotNull null
        val flags = m.groupValues[5]
        val ssid = m.groupValues[4].trim()
        if (ssid.isEmpty()) return@mapNotNull null
        Net(ssid, m.groupValues[3].toInt(), m.groupValues[2].toInt(), secured = flags.contains("WPA") || flags.contains("WEP") || flags.contains("SAE") || flags.contains("PSK"), sae = flags.contains("SAE") && !flags.contains("PSK"))
    }.groupBy { it.ssid }.values.map { list -> list.maxBy { it.dbm } }.sortedByDescending { it.dbm }

    /** The networks of `cmd wifi list-networks`: the id to forget it by and the name, once each. */
    fun saved(text: String): List<Pair<Int, String>> = text.lineSequence().mapNotNull { line ->
        val m = SAVED.matchEntire(line.trim()) ?: return@mapNotNull null
        m.groupValues[1].toInt() to m.groupValues[2].trim()
    }.distinctBy { it.first }.toList()

    /**
     * The command that connects to [net]; [password] is only used for a secured one.
     * shortcut: WEP networks (sent as WPA2, so they fail) and hidden networks (left out of the scan) are not supported, upgrade if someone still has one.
     */
    fun connect(net: Net, password: String): Array<String> =
        if (!net.secured) arrayOf("cmd", "wifi", "connect-network", net.ssid, "open")
        else arrayOf("cmd", "wifi", "connect-network", net.ssid, if (net.sae) "wpa3" else "wpa2", password)

    private val SCAN = Regex("([0-9a-fA-F:]{17})\\s+(\\d+)\\s+(-?\\d+)\\s+[\\d.]+\\s+(.*?)\\s*((?:\\[[^\\]]*\\])*)")
    private val SAVED = Regex("(\\d+)\\s+(.*?)\\s+[A-Za-z0-9^_-]+")
}
