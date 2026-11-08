package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R
import java.util.Locale
import kotlin.math.abs

@Immutable
class BatterySection(val title: String, val rows: List<Pair<String, String>>)

/** What the shell printed about the battery; null parts are what it could not read. */
class BatteryDeep(val battery: String, val sysfs: String, val stats: String, val thermal: String, val power: String) {
    companion object {
        /** Reads everything through Shizuku (a few seconds), or null when Shizuku is not ready. Call off the main thread. */
        fun read(): BatteryDeep? {
            if (!ShizukuShell.ready()) return null
            fun sh(line: String) = ShizukuShell.shell(line).output
            return BatteryDeep(
                battery = sh("dumpsys battery"),
                sysfs = sh("for f in /sys/class/power_supply/*/uevent; do echo \"## \$f\"; cat \$f 2>/dev/null; done"),
                stats = sh(
                    "dumpsys batterystats | grep -E '^ +(Estimated battery|Min learned|Max learned|Time on battery|Total run time|Screen on|Amount discharged|Capacity:)|^ +[A-Za-z_]+: [0-9.]+ apps: |^ +UID '",
                ),
                thermal = sh("dumpsys thermalservice | grep 'Temperature{'"),
                power = sh("dumpsys power | grep -E 'WAKE_LOCK'"),
            )
        }
    }
}

/**
 * Every fact about the battery the phone gives, in groups. What an app may read comes first and always; what only the shell can read ([BatteryDeep])
 * comes in when Shizuku is there, and a group without it says so instead of leaving a gap.
 */
object BatteryDoctor {
    private val locale: Locale get() = Locale.getDefault()

    private fun Context.s(id: Int, vararg args: Any): String = getString(id, *args)

    private fun num(value: Double, decimals: Int = 1) = String.format(locale, "%.${decimals}f", value)

    fun collect(context: Context, deep: BatteryDeep?): List<BatterySection> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(BatteryManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val info = BatteryInfo.read(context)
        val dump = deep?.let { BatteryDumps.dumpsysBattery(it.battery) }.orEmpty()
        val sysfs = deep?.let { BatteryDumps.sysfs(it.sysfs) }.orEmpty()
        val stats = deep?.let { BatteryDumps.statLines(it.stats) }.orEmpty()
        val supply = sysfs["battery"] ?: sysfs.values.firstOrNull { "CAPACITY" in it }.orEmpty()
        val needs = context.s(R.string.bd_needs_shizuku)

        fun property(id: Int): Long? = runCatching { manager.getLongProperty(id) }.getOrNull()?.takeIf { it != Long.MIN_VALUE && it != 0L }
        fun extra(key: String): Int? = intent?.takeIf { it.hasExtra(key) }?.getIntExtra(key, 0)
        fun yes(value: Boolean) = context.s(if (value) R.string.ds_yes else R.string.ds_no)
        fun micro(value: Long?, unit: String, div: Long = 1000) = value?.let { "${it / div} $unit" }

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level < 0 || scale <= 0) null else level * 100 / scale
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        val counter = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentNow = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAvg = property(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val energy = property(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        val design = supply["CHARGE_FULL_DESIGN"]?.toLongOrNull()
        val full = supply["CHARGE_FULL"]?.toLongOrNull()
        val cycles = if (Build.VERSION.SDK_INT >= 34) extra(BatteryManager.EXTRA_CYCLE_COUNT)?.takeIf { it > 0 } else null
        val sysCycles = supply["CYCLE_COUNT"]?.toIntOrNull()?.takeIf { it > 0 }

        val charge = listOfNotNull(
            percent?.let { context.s(R.string.bd_level) to "$it %" },
            context.s(R.string.ds_status) to statusText(context, status),
            if (Build.VERSION.SDK_INT >= 36) extra(BatteryManager.EXTRA_CAPACITY_LEVEL)?.let { context.s(R.string.bd_capacity_level) to capacityLevel(context, it) } else null,
            info?.minutesToFull?.let { context.s(R.string.bd_time_to_full) to BatteryInfo.duration(it) },
            info?.minutesLeft?.let { context.s(R.string.bd_time_left) to BatteryInfo.duration(it) },
            micro(counter, "mAh")?.let { context.s(R.string.bd_charge_now) to it },
            if (counter != null && percent != null && percent > 0) context.s(R.string.bd_capacity_estimate) to "${counter * 100 / percent / 1000} mAh" else null,
            energy?.let { context.s(R.string.bd_energy) to "${num(it / 1_000_000.0, 2)} Wh" },
        )

        val health = listOfNotNull(
            context.s(R.string.ds_health) to healthText(context, intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: 1),
            if (design != null && full != null && design > 0) context.s(R.string.bd_health_computed) to "${full * 100 / design} %" else null,
            (cycles ?: sysCycles)?.let { context.s(R.string.bd_cycles) to it.toString() },
            micro(design, "mAh")?.let { context.s(R.string.bd_design_capacity) to it },
            micro(full, "mAh")?.let { context.s(R.string.bd_full_capacity) to it },
            stats["Estimated battery capacity"]?.let { context.s(R.string.bd_android_capacity) to it },
            stats["Min learned battery capacity"]?.let { context.s(R.string.bd_learned_min) to it },
            stats["Max learned battery capacity"]?.let { context.s(R.string.bd_learned_max) to it },
            if (deep == null && design == null) context.s(R.string.bd_more_with_shizuku) to needs else null,
        )

        val supplyRows = listOfNotNull(
            context.s(R.string.bd_source) to pluggedText(context, plugged),
            (extra("max_charging_current")?.takeIf { it > 0 } ?: dump["Max charging current"]?.toIntOrNull()?.takeIf { it > 0 })?.let { context.s(R.string.bd_max_current) to "${it / 1000} mA" },
            (extra("max_charging_voltage")?.takeIf { it > 0 } ?: dump["Max charging voltage"]?.toIntOrNull()?.takeIf { it > 0 })?.let { context.s(R.string.bd_max_voltage) to "${num(it / 1_000_000.0, 1)} V" },
            dump["AC powered"]?.let { context.s(R.string.bd_ac) to it },
            dump["USB powered"]?.let { context.s(R.string.bd_usb) to it },
            dump["Wireless powered"]?.let { context.s(R.string.bd_wireless) to it },
            dump["Dock powered"]?.let { context.s(R.string.bd_dock) to it },
            dump["Charging state"]?.let { context.s(R.string.bd_charging_state) to it },
            dump["Charging policy"]?.let { context.s(R.string.bd_charging_policy) to it },
        )

        val electrical = listOfNotNull(
            if (voltage > 0) context.s(R.string.ds_voltage) to "$voltage mV" else null,
            micro(currentNow?.let { abs(it) }, "mA")?.let { context.s(R.string.bd_current_now) to it },
            micro(currentAvg?.let { abs(it) }, "mA")?.let { context.s(R.string.bd_current_avg) to it },
            if (currentNow != null && voltage > 0) context.s(R.string.bd_power_now) to "${num(abs(currentNow) / 1000.0 * voltage / 1_000_000.0, 2)} W" else null,
            supply["VOLTAGE_NOW"]?.toLongOrNull()?.let { context.s(R.string.bd_voltage_sys) to "${it / 1000} mV" },
            supply["CURRENT_NOW"]?.toLongOrNull()?.let { context.s(R.string.bd_current_sys) to "${it / 1000} mA" },
            supply["CONSTANT_CHARGE_CURRENT_MAX"]?.toLongOrNull()?.let { context.s(R.string.bd_limit_current) to "${it / 1000} mA" },
            supply["TIME_TO_FULL_NOW"]?.toLongOrNull()?.takeIf { it > 0 }?.let { context.s(R.string.bd_time_to_full) to BatteryInfo.duration((it / 60).toInt()) },
        )

        val thermal = BatteryDumps.temperatures(deep?.thermal.orEmpty())
        val temperature = listOfNotNull(
            tenths?.let { context.s(R.string.ds_temperature) to "${num(it / 10.0)} °C · ${num(it / 10.0 * 9 / 5 + 32)} °F" },
            if (Build.VERSION.SDK_INT >= 29) context.s(R.string.bd_thermal_status) to thermalStatus(context, power.currentThermalStatus) else null,
            if (Build.VERSION.SDK_INT >= 30) runCatching { power.getThermalHeadroom(10) }.getOrNull()?.takeIf { !it.isNaN() }?.let { context.s(R.string.bd_headroom) to "${num(it * 100.0, 0)} %" } else null,
        ) + thermal.map { (name, value) -> name to "${num(value)} °C" } +
            listOfNotNull(if (deep == null) context.s(R.string.bd_sensors) to needs else null)

        val system = listOfNotNull(
            context.s(R.string.ds_technology) to intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty().ifEmpty { context.s(R.string.ds_unknown) },
            context.s(R.string.bd_present) to yes(intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true),
            context.s(R.string.bd_saver) to yes(power.isPowerSaveMode),
            if (Build.VERSION.SDK_INT >= 28) context.s(R.string.bd_location_saver) to context.s(R.string.bd_mode_n, power.locationPowerSaveMode) else null,
            context.s(R.string.bd_doze) to yes(power.isDeviceIdleMode),
            if (Build.VERSION.SDK_INT >= 33) context.s(R.string.bd_light_doze) to yes(power.isDeviceLightIdleMode) else null,
            if (Build.VERSION.SDK_INT >= 33) context.s(R.string.bd_standby) to yes(power.isLowPowerStandbyEnabled) else null,
            context.s(R.string.bd_screen_on) to yes(power.isInteractive),
            context.s(R.string.bd_optimized) to yes(!power.isIgnoringBatteryOptimizations(context.packageName)),
            context.s(R.string.bd_sustained) to yes(power.isSustainedPerformanceModeSupported),
            runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255 }.getOrNull()?.let { context.s(R.string.bd_brightness) to "$it %" },
            runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000 }.getOrNull()?.let { context.s(R.string.bd_timeout) to "$it s" },
        )

        val session = listOfNotNull(
            stats["Time on battery"]?.let { context.s(R.string.bd_on_battery) to it },
            stats["Time on battery screen off"]?.let { context.s(R.string.bd_on_battery_off) to it },
            stats["Screen on"]?.let { context.s(R.string.bd_screen_time) to it },
            stats["Total run time"]?.let { context.s(R.string.bd_run_time) to it },
            stats["Amount discharged while screen on"]?.let { context.s(R.string.bd_discharged_on) to "$it %" },
            stats["Amount discharged while screen off"]?.let { context.s(R.string.bd_discharged_off) to "$it %" },
            stats["Amount discharged (lower bound)"]?.let { context.s(R.string.bd_discharged) to "$it %" },
            stats["Capacity"]?.let { context.s(R.string.bd_drain) to it },
        ) + BatteryDumps.components(deep?.stats.orEmpty()).map { (name, mah) -> name to "${num(mah, 2)} mAh" } +
            listOfNotNull(if (deep == null) context.s(R.string.bd_session) to needs else null)

        val pm = context.packageManager
        val apps = BatteryDumps.uids(deep?.stats.orEmpty()).map { (id, mah) ->
            val uid = BatteryDumps.uidNumber(id)
            val name = uid?.let { runCatching { pm.getPackagesForUid(it)?.firstOrNull()?.let { pkg -> pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } }.getOrNull() }
            (name ?: id) to "${num(mah, 2)} mAh"
        } + listOfNotNull(if (deep == null) context.s(R.string.bd_apps) to needs else null)

        val locks = BatteryDumps.wakeLocks(deep?.power.orEmpty()).distinct().map { "" to it }
            .ifEmpty { listOfNotNull(if (deep == null) context.s(R.string.bd_wakelocks) to needs else context.s(R.string.bd_wakelocks) to context.s(R.string.bd_none)) }

        // Everything else the system broadcast carries and what the shell printed whole: nothing the phone says is left out.
        val extras = intent?.extras?.let { bundle -> bundle.keySet().sorted().map { it to bundle.get(it).toString() } }.orEmpty()
        val raw = dump.map { (k, v) -> k to v }
        val files = sysfs.flatMap { (name, values) -> values.map { (k, v) -> "$name · $k" to v } }

        return listOf(
            BatterySection(context.s(R.string.bd_g_charge), charge),
            BatterySection(context.s(R.string.bd_g_health), health),
            BatterySection(context.s(R.string.bd_g_supply), supplyRows),
            BatterySection(context.s(R.string.bd_g_electrical), electrical),
            BatterySection(context.s(R.string.bd_g_temperature), temperature),
            BatterySection(context.s(R.string.bd_g_system), system),
            BatterySection(context.s(R.string.bd_g_session), session),
            BatterySection(context.s(R.string.bd_g_apps), apps),
            BatterySection(context.s(R.string.bd_g_wakelocks), locks),
            BatterySection(context.s(R.string.bd_g_raw_broadcast), extras),
            BatterySection(context.s(R.string.bd_g_raw_dumpsys), raw.ifEmpty { listOf(context.s(R.string.bd_dumpsys) to needs) }),
            BatterySection(context.s(R.string.bd_g_raw_sysfs), files.ifEmpty { listOf(context.s(R.string.bd_sysfs) to needs) }),
        ).filter { it.rows.isNotEmpty() }
    }

    private fun statusText(context: Context, status: Int) = context.s(
        when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> R.string.ds_charging
            BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.ds_discharging
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.bd_not_charging
            BatteryManager.BATTERY_STATUS_FULL -> R.string.bd_full
            else -> R.string.ds_unknown
        },
    )

    private fun healthText(context: Context, health: Int) = context.s(
        when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> R.string.ds_health_good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.ds_health_hot
            BatteryManager.BATTERY_HEALTH_DEAD -> R.string.ds_health_dead
            BatteryManager.BATTERY_HEALTH_COLD -> R.string.ds_health_cold
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.bd_health_voltage
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.bd_health_failure
            else -> R.string.ds_unknown
        },
    )

    private fun pluggedText(context: Context, plugged: Int): String {
        val names = buildList {
            if (plugged and BatteryManager.BATTERY_PLUGGED_AC != 0) add("AC")
            if (plugged and BatteryManager.BATTERY_PLUGGED_USB != 0) add("USB")
            if (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) add(context.s(R.string.bd_wireless))
            if (plugged and BATTERY_PLUGGED_DOCK != 0) add(context.s(R.string.bd_dock))
        }
        return names.joinToString(" + ").ifEmpty { context.s(R.string.bd_unplugged) }
    }

    private fun capacityLevel(context: Context, level: Int) = context.s(
        when (level) {
            1 -> R.string.bd_level_critical
            2 -> R.string.bd_level_low
            3 -> R.string.bd_level_normal
            4 -> R.string.bd_level_high
            5 -> R.string.bd_full
            else -> R.string.ds_unknown
        },
    )

    private fun thermalStatus(context: Context, status: Int) = context.s(
        when (status) {
            PowerManager.THERMAL_STATUS_NONE -> R.string.bd_thermal_none
            PowerManager.THERMAL_STATUS_LIGHT -> R.string.bd_thermal_light
            PowerManager.THERMAL_STATUS_MODERATE -> R.string.bd_thermal_moderate
            PowerManager.THERMAL_STATUS_SEVERE -> R.string.bd_thermal_severe
            PowerManager.THERMAL_STATUS_CRITICAL -> R.string.bd_thermal_critical
            PowerManager.THERMAL_STATUS_EMERGENCY -> R.string.bd_thermal_emergency
            PowerManager.THERMAL_STATUS_SHUTDOWN -> R.string.bd_thermal_shutdown
            else -> R.string.ds_unknown
        },
    )

    /** BatteryManager.BATTERY_PLUGGED_DOCK, which the SDK has only since Android 14 (value 8). */
    private const val BATTERY_PLUGGED_DOCK = 8
}
