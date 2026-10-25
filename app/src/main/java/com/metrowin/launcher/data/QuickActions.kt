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
    Screenshot("screenshot", R.string.qa_screenshot, Glyph.Photos);

    companion object {
        /** The four buttons on top until the user chooses others. */
        val defaults = listOf(Wifi, Bluetooth, Flashlight, LauncherSettings)

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

        const val SLOTS = 4
    }
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
            QuickAction.QuietHours ->
                context.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter !=
                    android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            QuickAction.Rotation ->
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
            QuickAction.Location ->
                LocationManagerCompat.isLocationEnabled(context.getSystemService(android.location.LocationManager::class.java))
            else -> null
        }
    }.getOrNull()

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
    fun activate(action: QuickAction): Boolean {
        val torch = torchId
        if (action == QuickAction.Flashlight && torch != null) {
            runCatching { cameras.setTorchMode(torch, !_torchOn.value) }
            return true
        }
        if (action == QuickAction.QuietHours) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            if (manager.isNotificationPolicyAccessGranted) {
                val on = manager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                // Quiet hours on lets the calls and alarms the user allowed through; off lets everything through.
                runCatching {
                    manager.setInterruptionFilter(
                        if (on) android.app.NotificationManager.INTERRUPTION_FILTER_ALL else android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    )
                }
                return true
            }
            return start(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).let { false }
        }
        val intentAction = when (action) {
            QuickAction.Wifi -> Settings.ACTION_WIFI_SETTINGS
            QuickAction.Bluetooth -> Settings.ACTION_BLUETOOTH_SETTINGS
            QuickAction.Airplane -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            QuickAction.QuietHours -> "android.settings.ZEN_MODE_SETTINGS"
            QuickAction.Rotation, QuickAction.Brightness, QuickAction.Flashlight -> Settings.ACTION_DISPLAY_SETTINGS
            QuickAction.Location -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            QuickAction.Hotspot -> "android.settings.TETHER_SETTINGS"
            QuickAction.Vpn -> Settings.ACTION_VPN_SETTINGS
            QuickAction.Cast -> Settings.ACTION_CAST_SETTINGS
            QuickAction.Camera -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            // Not screens of Android: the launcher opens its own settings and the recent apps itself (see LauncherApp).
            QuickAction.LauncherSettings, QuickAction.Recents, QuickAction.Screenshot -> return false
        }
        if (!start(intentAction)) start(Settings.ACTION_SETTINGS)
        return false
    }

    private fun start(intentAction: String): Boolean =
        runCatching { context.startActivity(Intent(intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
