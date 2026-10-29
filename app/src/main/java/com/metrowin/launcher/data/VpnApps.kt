package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent

/** A VPN app of the phone: its package and the name it shows. */
data class VpnApp(val packageName: String, val label: String)

/** The VPN apps of the phone, and which one the VPN button opens. Android has no way to connect another app's VPN, so the button opens the app. */
object VpnApps {
    /** The best known VPN apps, the most used first: the ones installed are listed in this order, the others after them by name. */
    val KNOWN = listOf(
        "com.wireguard.android", "com.nordvpn.android", "com.expressvpn.vpn", "com.surfshark.vpnclient.android", "ch.protonvpn.android",
        "de.blinkt.openvpn", "net.openvpn.openvpn", "net.mullvad.mullvadvpn", "de.mobileconcepts.cyberghost", "com.privateinternetaccess.android",
        "com.ixolit.ipvanish", "com.tunnelbear.android", "com.windscribe.vpn", "hotspotshield.android.vpn", "free.vpn.unblock.proxy.turbovpn",
        "com.psiphon3", "com.cloudflare.onedotonedotonedotone", "com.tailscale.ipn", "com.freevpnintouch", "com.hidemyass.hidemyassprovpn",
        "com.avast.android.vpn", "com.kaspersky.secure.connection", "com.bitdefender.vpn", "com.symantec.securewifi", "com.gaditek.purevpnics",
        "com.atlasvpn.free.android.proxy.secure", "com.zenmate.android", "com.vpn.free.hotspot.secure.vpnify", "org.torproject.android",
        "org.getlantern.lantern", "org.outline.android.client", "org.strongswan.android", "com.zerotier.one", "app.hiddify.com", "com.v2ray.ang",
        "com.github.shadowsocks", "io.nekohasekai.sagernet", "org.amnezia.vpn", "com.cisco.anyconnect.vpn.android.avf", "net.openvpn.privatetunnel",
        "com.paloaltonetworks.globalprotect", "com.fortinet.forticlient_vpn", "com.adguard.android", "com.celzero.bravedns", "com.speedify.speedifyandroid",
        "com.simplexsolutionsinc.vpn_unlimited", "com.fast.free.unblock.secure.vpn", "com.mcafee.vpn", "com.opera.vpn",
    )

    /** The apps that declare a VPN service, the known ones first (see [KNOWN]), without the launcher itself. */
    fun installed(context: Context): List<VpnApp> {
        val pm = context.packageManager
        val found = runCatching { pm.queryIntentServices(Intent("android.net.VpnService"), 0) }.getOrDefault(emptyList())
        return sorted(
            found.mapNotNull { it.serviceInfo?.packageName }.distinct()
                .filter { it != context.packageName && pm.getLaunchIntentForPackage(it) != null }
                .map { pkg -> VpnApp(pkg, runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)) },
        )
    }

    fun sorted(apps: List<VpnApp>): List<VpnApp> =
        apps.sortedWith(compareBy({ KNOWN.indexOf(it.packageName).let { i -> if (i < 0) KNOWN.size else i } }, { it.label.lowercase() }))

    /** The app the button opens: the one chosen if it is still there, else the only one the phone has. Null when the user has to choose (or there is none). */
    fun pick(saved: String, installed: List<VpnApp>): VpnApp? = installed.firstOrNull { it.packageName == saved } ?: installed.singleOrNull()
}
