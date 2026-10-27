package com.metrowin.launcher.data

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.location.LocationManagerCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The buttons of the Action Center. Four of them sit on top, the user chooses which ones. Android does not let
 * an app switch Wi-Fi, Bluetooth and the like any more, so those buttons show the state and open the matching
 * system panel; the flashlight is switched on the spot.
 */
enum class QuickAction(val id: String, @param:StringRes val label: Int, val glyph: Glyph) {
    Wifi("wifi", R.string.qa_wifi, Glyph.Wifi),
    Bluetooth("bluetooth", R.string.qa_bluetooth, Glyph.Bluetooth),
    Airplane("airplane", R.string.qa_airplane, Glyph.Airplane),
    Flashlight("flashlight", R.string.qa_flashlight, Glyph.Flashlight),
    QuietHours("quiet", R.string.qa_quiet_hours, Glyph.Moon),

    /** Quiet hours that switch themselves off after the length chosen in the settings (see [LauncherSettings.dndMinutes]). */
    QuietTimed("quiettimed", R.string.qa_quiet_timed, Glyph.Moon),
    Rotation("rotation", R.string.qa_rotation, Glyph.Rotation),
    Location("location", R.string.qa_location, Glyph.Maps),
    Hotspot("hotspot", R.string.qa_hotspot, Glyph.Hotspot),
    Vpn("vpn", R.string.qa_vpn, Glyph.Vpn),
    Cast("cast", R.string.qa_cast, Glyph.Cast),
    Brightness("brightness", R.string.qa_brightness, Glyph.Brightness),
    Camera("camera", R.string.qa_camera, Glyph.Camera),

    /** Opens the settings of the launcher itself, which are otherwise only an entry in the app list. */
    LauncherSettings("launcher", R.string.qa_launcher_settings, Glyph.Settings),

    /** Opens the recent apps at once, through the accessibility service (the same one that locks the screen). */
    Recents("recents", R.string.qa_recents, Glyph.Recents),

    /** Takes a screenshot once the panel is out of the way, through the accessibility service. */
    Screenshot("screenshot", R.string.qa_screenshot, Glyph.Photos),

    /** How long the screen stays on: each touch goes to the next length (15 seconds to 10 minutes), and the button says which. */
    ScreenTimeout("timeout", R.string.qa_timeout, Glyph.Timeout),

    /** The brightness that follows the light of the room, on or off. */
    AutoBrightness("autobrightness", R.string.qa_auto_brightness, Glyph.AutoBrightness);

    companion object {
        /** The four buttons on top until the user chooses others. */
        val defaults = listOf(Wifi, Bluetooth, Flashlight, LauncherSettings)

        /** The buttons that can be kept on Start as a switch: the ones that have a state to show. */
        val toggles = listOf(Wifi, Bluetooth, Airplane, Flashlight, QuietHours, QuietTimed, Rotation, Location, AutoBrightness)

        /** What the four buttons were before the launcher's settings joined them: nobody chose that, so it is replaced. */
        private val previousDefaults = listOf("wifi", "bluetooth", "airplane", "flashlight")

        /** The saved buttons, with the first version's untouched default moved on to the current one. */
        fun migrate(saved: List<String>): List<String> = if (saved == previousDefaults) defaults.map { it.id } else saved

        fun fromId(id: String): QuickAction? = entries.firstOrNull { it.id == id }

        /** [ids] as the four buttons: unknown ids are dropped, the gaps filled from the defaults. */
        fun slots(ids: List<String>): List<QuickAction> {
            val chosen = ids.mapNotNull(::fromId).distinct().take(SLOTS)
            return (chosen + (defaults + entries).filter { it !in chosen }).take(SLOTS)
        }

        /** Puts [action] in [slot], swapping with the slot it was in so no button shows twice. */
        fun assign(current: List<QuickAction>, slot: Int, action: QuickAction): List<QuickAction> {
            val slots = current.toMutableList()
            val previous = slots.indexOf(action)
            if (previous >= 0) slots[previous] = slots[slot]
            slots[slot] = action
            return slots
        }

        /** [current] with the button at [from] moved to [to], the ones between moving over by one. */
        fun move(current: List<QuickAction>, from: Int, to: Int): List<QuickAction> {
            if (from !in current.indices || to !in current.indices) return current
            return current.toMutableList().also { it.add(to, it.removeAt(from)) }
        }

        const val SLOTS = 4
    }
}

/** The lengths of time the screen may stay on, that the screen timeout button goes through. */
object ScreenTimeouts {
    val STEPS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000)

    /** The step after [current] (the first one after the longest); a value that is not a step goes to the next longer step. */
    fun next(current: Int): Int = STEPS.firstOrNull { it > current } ?: STEPS.first()

    /** The length as the button says it: 15 and 30 seconds, then minutes. */
    fun parts(millis: Int): Pair<Int, Boolean> = if (millis < 60_000) millis / 1000 to true else millis / 60_000 to false
}

class QuickActionsController(private val context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val torchId: String? = runCatching {
        cameras.cameraIdList.firstOrNull { id ->
            val c = cameras.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) _torchOn.value = enabled
        }
    }

    private var listening = false

    /**
     * Follows the flashlight (the callback also fires whenever a camera app opens or closes, which wakes the process),
     * so it is only registered while the launcher is on screen. Android reports the current state right away.
     */
    fun start() {
        if (listening || torchId == null) return
        listening = true
        cameras.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        if (!listening) return
        listening = false
        cameras.unregisterTorchCallback(torchCallback)
    }

    fun close() = stop()

    /** On or off, or null for the buttons that have no state to show. */
    fun isOn(action: QuickAction): Boolean? = runCatching {
        when (action) {
            QuickAction.Wifi -> context.getSystemService(WifiManager::class.java).isWifiEnabled
            QuickAction.Bluetooth -> context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled
            QuickAction.Airplane -> Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            QuickAction.Flashlight -> if (torchId == null) null else _torchOn.value
            QuickAction.QuietHours, QuickAction.QuietTimed ->
                context.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter !=
                    android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            QuickAction.Rotation ->
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
            QuickAction.Location ->
                LocationManagerCompat.isLocationEnabled(context.getSystemService(android.location.LocationManager::class.java))
            QuickAction.AutoBrightness ->
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            else -> null
        }
    }.getOrNull()

    /** How long the screen stays on, in milliseconds, or null when it cannot be read. */
    fun screenTimeout(): Int? = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) }.getOrNull()

    /** How many strengths the flashlight has (1 when it only goes on and off, and before Android 13). */
    private val torchLevels: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && torchId != null) strengthLevelsOf(torchId) else 1

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun strengthLevelsOf(id: String): Int =
        runCatching { cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) }.getOrNull() ?: 1

    /** The strength of the lit flashlight as a fraction, or null when it is off or has only one strength. */
    fun torchStrength(): Float? {
        val torch = torchId ?: return null
        if (torchLevels <= 1 || !_torchOn.value || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching { Levels.fraction(cameras.getTorchStrengthLevel(torch), torchLevels) }.getOrNull()
    }

    /** Lights the flashlight at [fraction] of its strength (never off: the lowest strength is the first). */
    fun setTorchStrength(fraction: Float) {
        val torch = torchId ?: return
        if (torchLevels <= 1 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching { cameras.turnOnTorchWithStrengthLevel(torch, Levels.level(fraction, torchLevels, min = 1)) }
    }

    /**
     * Switches the flashlight or the quiet hours on the spot, or opens the system screen of the button. True when it was
     * done on the spot. Quiet hours need the "do not disturb" access: without it the screen that gives it opens.
     */
    fun activate(action: QuickAction, quietMinutes: Int = 60, torchMinutes: Int = 0): Boolean {
        val torch = torchId
        if (action == QuickAction.Flashlight && torch != null) {
            val turnOn = !_torchOn.value
            runCatching { cameras.setTorchMode(torch, turnOn) }
            // An alarm of Android puts it out when the time is up; switching it off by hand cancels that.
            if (turnOn && torchMinutes > 0) com.metrowin.launcher.notifications.Alerts.scheduleTorchOff(context, torchMinutes)
            else com.metrowin.launcher.notifications.Alerts.cancelTorchOff(context)
            return true
        }
        if (action == QuickAction.QuietHours || action == QuickAction.QuietTimed) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            if (manager.isNotificationPolicyAccessGranted) {
                val on = manager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                // Quiet hours on lets the calls and alarms the user allowed through; off lets everything through.
                runCatching {
                    manager.setInterruptionFilter(
                        if (on) android.app.NotificationManager.INTERRUPTION_FILTER_ALL else android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    )
                }
                // The timed one asks Android to wake the launcher when the time is up; any other switch-off cancels that.
                if (action == QuickAction.QuietTimed && !on) com.metrowin.launcher.notifications.Alerts.scheduleQuietEnd(context, quietMinutes)
                else com.metrowin.launcher.notifications.Alerts.cancelQuietEnd(context)
                return true
            }
            return start(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).let { false }
        }
        if (action == QuickAction.ScreenTimeout || action == QuickAction.AutoBrightness) {
            // Both are system settings an app may only write once the user has allowed it, in a screen of Android (never in a dialog).
            if (!Settings.System.canWrite(context)) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                return false
            }
            runCatching {
                val resolver = context.contentResolver
                if (action == QuickAction.AutoBrightness) {
                    val auto = isOn(QuickAction.AutoBrightness) == true
                    Settings.System.putInt(
                        resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                    )
                } else {
                    Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, ScreenTimeouts.next(screenTimeout() ?: 0))
                }
            }
            return true
        }
        val intentAction = when (action) {
            QuickAction.Wifi -> Settings.ACTION_WIFI_SETTINGS
            QuickAction.Bluetooth -> Settings.ACTION_BLUETOOTH_SETTINGS
            QuickAction.Airplane -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            QuickAction.QuietHours, QuickAction.QuietTimed -> "android.settings.ZEN_MODE_SETTINGS"
            QuickAction.Rotation, QuickAction.Brightness, QuickAction.Flashlight -> Settings.ACTION_DISPLAY_SETTINGS
            QuickAction.Location -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            QuickAction.Hotspot -> "android.settings.TETHER_SETTINGS"
            QuickAction.Vpn -> Settings.ACTION_VPN_SETTINGS
            QuickAction.Cast -> Settings.ACTION_CAST_SETTINGS
            QuickAction.Camera -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            // Not screens of Android: the launcher opens its own settings and the recent apps itself (see LauncherApp).
            QuickAction.LauncherSettings, QuickAction.Recents, QuickAction.Screenshot -> return false
            QuickAction.ScreenTimeout, QuickAction.AutoBrightness -> return false
        }
        if (!start(intentAction)) start(Settings.ACTION_SETTINGS)
        return false
    }

    private fun start(intentAction: String): Boolean =
        runCatching { context.startActivity(Intent(intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/** Puts the flashlight out, from the alarm that was set when it was lit. Nothing happens when the phone has no flashlight. */
fun switchTorchOff(context: Context) {
    runCatching {
        val cameras = context.getSystemService(CameraManager::class.java) ?: return
        val id = cameras.cameraIdList.firstOrNull { id ->
            val c = cameras.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return
        cameras.setTorchMode(id, false)
    }
}
