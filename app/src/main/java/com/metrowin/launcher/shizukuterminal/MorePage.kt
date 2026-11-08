package com.metrowin.launcher.shizukuterminal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatterySection
import com.metrowin.launcher.data.DeviceMonitor
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the shell printed for this page. */
private class MoreShell(val events: String, val props: String, val memory: String, val totals: String, val services: String)

private fun readShell(): MoreShell {
    fun sh(line: String) = ShizukuShell.shell(line, 512 * 1024).output
    return MoreShell(
        events = sh("logcat -b events -d -v time am_crash:I am_anr:I '*:S'"),
        props = sh("getprop; echo \"[enforce]: [\$(getenforce)]\""),
        memory = sh("dumpsys meminfo | sed -n '/Total PSS by process/,/^\$/p'"),
        totals = sh("dumpsys meminfo | grep -E 'Total RAM|Free RAM|Used RAM|Lost RAM'"),
        services = sh("dumpsys activity services | grep 'ServiceRecord{'"),
    )
}

private fun label(context: Context, packageName: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

private fun mb(kb: Long) = String.format(Locale.getDefault(), "%.0f MB", kb / 1024f)

/** "More information": the crashes and blocked apps, the security state of the system, the sensors with their values, what runs and what it takes. */
@Composable
fun MorePage(state: ShizukuState, refresh: Int) {
    val context = LocalContext.current
    val shell by produceState<MoreShell?>(null, state, refresh) { value = if (state.ready) withContext(Dispatchers.IO) { readShell() } else null }
    val needs = stringResource(R.string.bd_needs_shizuku)
    val sections by produceState(emptyList<BatterySection>(), shell, needs) { value = withContext(Dispatchers.IO) { build(context, shell, needs) } }
    val live = rememberSensors(context)
    val sensorList = remember { sensorsOf(context) }
    val title = stringResource(R.string.mi_sensors, sensorList.size)
    val sensorRows = sensorList.map { sensor -> sensor.name to "${typeName(sensor)} · ${live[key(sensor)] ?: "—"}" }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        infoSections(sections, "more")
        infoSections(listOf(BatterySection(title, sensorRows)), "sensors")
        item { Row(Modifier.padding(bottom = 24.dp)) {} }
    }
}

private fun build(context: Context, shell: MoreShell?, needs: String): List<BatterySection> {
    fun s(id: Int) = context.getString(id)
    val faults = shell?.let { DeviceMonitor.faults(it.events) }.orEmpty()
    val byApp = faults.groupBy { it.packageName }.entries.sortedByDescending { it.value.size }.take(20).map { (pkg, list) ->
        val crashes = list.count { !it.anr }
        val anrs = list.count { it.anr }
        label(context, pkg) to "$crashes ${s(R.string.mi_crashes)} · $anrs ANR · ${list.last().time.substringAfter(' ')}"
    }
    val recent = faults.takeLast(10).reversed().map { f ->
        "${f.time} · ${if (f.anr) "ANR" else s(R.string.mi_crash)}" to "${label(context, f.packageName)}${if (f.detail.isNotBlank()) " — ${f.detail.take(80)}" else ""}"
    }
    val noFaults = listOf(s(R.string.mi_faults_title) to if (shell == null) needs else s(R.string.mi_no_faults))
    val props = shell?.let { DeviceMonitor.props(it.props) }.orEmpty()
    fun p(key: String) = props[key]?.takeIf { it.isNotBlank() }
    val security = listOfNotNull(
        s(R.string.mi_android) to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        s(R.string.mi_security_patch) to Build.VERSION.SECURITY_PATCH,
        p("ro.vendor.build.security_patch")?.let { s(R.string.mi_vendor_patch) to it },
        s(R.string.mi_device) to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
        s(R.string.mi_kernel) to (System.getProperty("os.version") ?: "-"),
        s(R.string.mi_build) to "${Build.ID} · ${Build.TYPE} · ${Build.TAGS}",
        s(R.string.mi_fingerprint) to Build.FINGERPRINT,
        s(R.string.mi_bootloader) to Build.BOOTLOADER,
        runCatching { Build.getRadioVersion() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { s(R.string.mi_baseband) to it },
        DeviceMonitor.bootState(p("ro.boot.verifiedbootstate"), p("ro.boot.flash.locked"))?.let { s(R.string.mi_boot_state) to it },
        p("ro.boot.vbmeta.device_state")?.let { s(R.string.mi_vbmeta) to it },
        p("ro.crypto.state")?.let { s(R.string.mi_encryption) to "$it${p("ro.crypto.type")?.let { t -> " ($t)" } ?: ""}" },
        p("enforce")?.let { "SELinux" to it },
        p("ro.debuggable")?.let { s(R.string.mi_debuggable) to if (it == "1") s(R.string.ds_yes) else s(R.string.ds_no) },
        p("ro.boot.veritymode")?.let { "dm-verity" to it },
        runCatching { context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName }.getOrNull()?.let { "Google Play services" to it },
        if (shell == null) s(R.string.bd_more_with_shizuku) to needs else null,
    )
    val memory = shell?.let { DeviceMonitor.processMemory(it.memory).map { (pkg, kb) -> label(context, pkg) to mb(kb) } }.orEmpty()
    val totals = shell?.totals?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { it.substringBefore(':') to it.substringAfter(':').trim() }.orEmpty()
    val services = shell?.let { DeviceMonitor.servicesByApp(it.services).map { (pkg, n) -> label(context, pkg) to "$n ${s(R.string.mi_services_unit)}" } }.orEmpty()
    return listOf(
        BatterySection(s(R.string.mi_faults_by_app), byApp.ifEmpty { noFaults }),
        BatterySection(s(R.string.mi_faults_recent), recent),
        BatterySection(s(R.string.mi_security), security),
        BatterySection(s(R.string.mi_memory), totals + memory.ifEmpty { listOf(s(R.string.mi_memory) to needs) }),
        BatterySection(s(R.string.mi_services), services.ifEmpty { listOf(s(R.string.mi_services) to needs) }),
    )
}

/** The sensors that report by themselves (the ones that fire once, or on a trigger, would only show an empty value). */
private fun sensorsOf(context: Context): List<Sensor> =
    context.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL)
        .filter { it.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT && it.reportingMode != Sensor.REPORTING_MODE_SPECIAL_TRIGGER }
        .sortedBy { it.type }

/** What tells one sensor from another: the id the system gives is not the same on every phone (an emulator gives them all the same one). */
private fun key(sensor: Sensor) = "${sensor.type}:${sensor.name}:${if (sensor.isWakeUpSensor) 1 else 0}"

private fun typeName(sensor: Sensor) = sensor.stringType.substringAfterLast('.').replace('_', ' ')

/** The latest values of every sensor, read twice a second while the page is on screen and let go when it is not. */
@Composable
private fun rememberSensors(context: Context): Map<String, String> {
    var shown by remember { mutableStateOf(emptyMap<String, String>()) }
    val latest = remember { HashMap<String, String>() }
    // Registered only while the launcher is STARTED: a sensor listener left on with the app in the background drains the battery.
    LaunchedWhileStarted(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latest[key(event.sensor)] = event.values.take(3).joinToString("  ") { String.format(Locale.getDefault(), "%.2f", it) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorsOf(context).forEach { runCatching { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) } }
        try {
            while (true) {
                shown = HashMap(latest)
                delay(500)
            }
        } finally {
            manager.unregisterListener(listener)
        }
    }
    return shown
}
