package com.metrowin.launcher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.core.net.toUri
import com.metrowin.launcher.R
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch

/**
 * Builds the app list: every launchable app of the device, with the Windows Phone stock apps
 * ([MetroCatalog]) standing in for the Android apps that play their role.
 */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val pm = context.packageManager
    private val user = Process.myUserHandle()

    /** Blocking, call it off the main thread. Returns the list in alphabetical order. */
    fun load(): List<AppItem> {
        val activities = launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
        val byPackage = activities.groupBy { it.componentName.packageName }
        val claimed = HashSet<ComponentName>()
        val items = ArrayList<AppItem>(activities.size + MetroCatalog.apps.size)

        for (stock in MetroCatalog.apps) {
            val id = MetroCatalog.idOf(stock)
            val label = context.getString(stock.label)
            val action = stock.settingsAction
            if (action != null) {
                if (queryActivities(Intent(action)).isNotEmpty()) {
                    items += AppItem(id, label, Launch.Action(action), stock.glyph, stock.live)
                }
                continue
            }
            val info = findLauncherActivity(stock, byPackage, claimed) ?: continue
            claimed += info.componentName
            items += AppItem(
                id = id,
                label = label,
                launch = Launch.Activity(info.componentName),
                glyph = stock.glyph,
                live = stock.live,
                packageName = info.componentName.packageName,
                removable = !info.isSystemApp(),
            )
        }

        activities.filterNot { it.componentName in claimed }.mapTo(items) { info ->
            AppItem(
                id = "app:${info.componentName.flattenToShortString()}",
                label = info.label?.toString()?.takeIf { it.isNotBlank() } ?: info.componentName.packageName,
                launch = Launch.Activity(info.componentName),
                packageName = info.componentName.packageName,
                removable = !info.isSystemApp(),
            )
        }
        return AppSections.sort(items)
    }

    fun launch(item: AppItem, sourceBounds: Rect?): Boolean {
        val options = ActivityOptions.makeCustomAnimation(context, R.anim.app_enter, R.anim.launcher_hold).toBundle()
        return runCatching {
            when (val target = item.launch) {
                is Launch.Activity -> startActivity(target.component, sourceBounds, options)
                is Launch.Action ->
                    context.startActivity(Intent(target.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
            }
        }.isSuccess
    }

    private fun startActivity(component: ComponentName, sourceBounds: Rect?, options: android.os.Bundle) {
        try {
            launcherApps.startMainActivity(component, user, sourceBounds, options)
        } catch (_: RuntimeException) {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent, options)
        }
    }

    fun uninstall(item: AppItem): Boolean {
        val pkg = item.packageName ?: return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, "package:$pkg".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    /** Calls [onChange] whenever apps are installed, updated or removed. Returns the stop function. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()
            override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()
            override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                onChange()

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                onChange()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return { launcherApps.unregisterCallback(callback) }
    }

    private fun findLauncherActivity(
        stock: StockApp,
        byPackage: Map<String, List<LauncherActivityInfo>>,
        claimed: Set<ComponentName>,
    ): LauncherActivityInfo? {
        for (pkg in candidatePackages(stock)) {
            byPackage[pkg]?.firstOrNull { it.componentName !in claimed }?.let { return it }
        }
        return null
    }

    private fun candidatePackages(stock: StockApp): Sequence<String> = sequence {
        for (target in stock.targets) {
            when (target) {
                is Target.Pkg -> yield(target.name)
                is Target.Category ->
                    yieldAll(handlers(Intent(Intent.ACTION_MAIN).addCategory(target.category)))
                is Target.Action -> {
                    val intent = Intent(target.action)
                    target.data?.let { intent.data = it.toUri() }
                    if (target.defaultOnly) {
                        defaultHandler(intent)?.let { yield(it) }
                    } else {
                        yieldAll(handlers(intent))
                    }
                }
            }
        }
    }.distinct()

    /** Packages that handle [intent], preinstalled apps first. */
    private fun handlers(intent: Intent): List<String> =
        queryActivities(intent)
            .sortedByDescending { it.activityInfo.applicationInfo.isSystemApp() }
            .map { it.activityInfo.packageName }

    /** The app the user chose as default for [intent], or null if they never chose (or it is the chooser). */
    private fun defaultHandler(intent: Intent): String? {
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName?.takeIf { it != "android" }
    }

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun LauncherActivityInfo.isSystemApp() = applicationInfo.isSystemApp()

    private fun ApplicationInfo.isSystemApp() = flags and ApplicationInfo.FLAG_SYSTEM != 0
}
