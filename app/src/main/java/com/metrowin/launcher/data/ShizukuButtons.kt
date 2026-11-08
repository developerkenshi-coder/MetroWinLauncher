package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit

/**
 * Which buttons of the Action Center switch on the spot through Shizuku, which one the user chose in the Shizuku Terminal, and the command
 * each one runs. Android 10 and later stopped apps from switching the radios and the rest; the shell can.
 */
object ShizukuButtons {
    /** What a press does: the commands to run in turn, and the state the button will have once they ran (null for a button without a state). */
    class Plan(val commands: List<Array<String>>, val becomes: Boolean? = null)

    /** The buttons that have a command, in the order the Terminal lists them. */
    val direct = listOf(
        QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Airplane, QuickAction.MobileData, QuickAction.Location,
        QuickAction.QuietHours, QuickAction.QuietTimed, QuickAction.Rotation, QuickAction.AutoBrightness,
        QuickAction.ScreenTimeout, QuickAction.Brightness, QuickAction.Recents, QuickAction.Screenshot,
    )

    /** The buttons Android gives no command for (checked on Android 17): the shell has no way to start the hotspot or to connect a VPN. */
    val none = listOf(QuickAction.Hotspot, QuickAction.Vpn)

    private val BRIGHTNESS_STEPS = listOf(26, 77, 128, 179, 255)

    private fun enableWord(on: Boolean) = if (on) "enable" else "disable"

    /**
     * The commands for [action], or null when it has none. [on] is the state now (a toggle goes to the other one), [timeoutMs] and
     * [brightness] the settings now, for the two buttons that go through steps.
     */
    fun plan(action: QuickAction, on: Boolean?, timeoutMs: Int?, brightness: Int?): Plan? {
        val toOn = on != true
        fun cmd(vararg args: String) = args.toList().toTypedArray()
        fun toggle(vararg args: String) = Plan(listOf(cmd(*args)), toOn)
        return when (action) {
            QuickAction.Wifi -> toggle("svc", "wifi", enableWord(toOn))
            QuickAction.Bluetooth -> toggle("svc", "bluetooth", enableWord(toOn))
            QuickAction.Airplane -> toggle("cmd", "connectivity", "airplane-mode", enableWord(toOn))
            QuickAction.MobileData -> toggle("svc", "data", enableWord(toOn))
            QuickAction.Location -> toggle("cmd", "location", "set-location-enabled", toOn.toString())
            QuickAction.QuietHours, QuickAction.QuietTimed -> toggle("cmd", "notification", "set_dnd", if (toOn) "priority" else "off")
            // On means the rotation is locked.
            QuickAction.Rotation -> toggle("settings", "put", "system", "accelerometer_rotation", if (toOn) "0" else "1")
            QuickAction.AutoBrightness -> toggle("settings", "put", "system", "screen_brightness_mode", if (toOn) "1" else "0")
            QuickAction.ScreenTimeout -> Plan(listOf(cmd("settings", "put", "system", "screen_off_timeout", ScreenTimeouts.next(timeoutMs ?: 0).toString())))
            QuickAction.Brightness -> {
                val next = BRIGHTNESS_STEPS.firstOrNull { it > (brightness ?: 0) + 4 } ?: BRIGHTNESS_STEPS.first()
                Plan(listOf(cmd("settings", "put", "system", "screen_brightness_mode", "0"), cmd("settings", "put", "system", "screen_brightness", next.toString())))
            }
            QuickAction.Recents -> Plan(listOf(cmd("input", "keyevent", "KEYCODE_APP_SWITCH")))
            QuickAction.Screenshot -> Plan(listOf(cmd("input", "keyevent", "KEYCODE_SYSRQ")))
            else -> null
        }
    }

    // --- what the user chose

    private fun prefs(context: Context) = Prefs.open(context, Prefs.SHIZUKU)

    /** The ids of the buttons that go through Shizuku. Before the user ever chose, the old switch (Wi-Fi and Bluetooth) decides. */
    fun chosen(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, null) ?: if (SettingsStore.of(context).useShizukuOn()) setOf("wifi", "bluetooth") else emptySet()

    fun set(context: Context, action: QuickAction, on: Boolean) {
        val now = chosen(context)
        prefs(context).edit { putStringSet(KEY, if (on) now + action.id else now - action.id) }
        ShizukuWatch.schedule(context)
    }

    fun setAll(context: Context, on: Boolean) {
        prefs(context).edit { putStringSet(KEY, if (on) direct.map { it.id }.toSet() else emptySet()) }
        ShizukuWatch.schedule(context)
    }

    /** True when [action] was chosen and Shizuku runs and allows the launcher: the one place that decides. */
    fun allowed(context: Context, action: QuickAction): Boolean =
        ShizukuRadio.route(action.id in chosen(context), ShizukuRadio.running(), ShizukuRadio.granted())

    // --- for what has no QuickActionsController at hand (the accessibility service)

    @Volatile
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext ?: context
        ShizukuWatch.attach(context)
    }

    /** Presses [action] through Shizuku if the user chose that; true when it was sent (the gesture of the recent apps and the screenshot use this). */
    fun pressIfChosen(action: QuickAction): Boolean {
        val context = appContext ?: return false
        if (!allowed(context, action)) return false
        val plan = plan(action, null, null, null) ?: return false
        ShizukuShell.async(plan.commands)
        return true
    }

    private const val KEY = "buttons"
}
