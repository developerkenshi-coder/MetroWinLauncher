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
import android.os.UserManager
import androidx.core.net.toUri
import com.metrowin.launcher.R
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch

/**
 * Builds the app list: every launchable app of the device, in every profile (personal, work, clone),
 * plus the Windows Phone stock apps ([MetroCatalog]) that the device can play.
 *
 * An app is always listed under its own name. When a stock entry maps onto it, the stock entry is
 * added next to it ("Internet Explorer" beside "Chrome"); when both have the same name ("Calculator")
 * they are one entry, which takes the Metro glyph.
 */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val pm = context.packageManager
    private val owner = Process.myUserHandle()

    /** Blocking, call it off the main thread. Returns the list in alphabetical order. */
    fun load(): List<AppItem> {
        val own = context.packageName
        val ownerActivities = launcherApps.getActivityList(null, owner).filter { it.componentName.packageName != own }
        // A profile can be unavailable (a paused work profile): it must not take the other apps down with it.
        val otherActivities = launcherApps.profiles.filter { it != owner }.flatMap { profile ->
            runCatching { launcherApps.getActivityList(null, profile) }.getOrDefault(emptyList())
        }

        // Stock apps are matched against the owner's apps only.
        val byPackage = ownerActivities.groupBy { it.componentName.packageName }
        val claimed = HashSet<ComponentName>()
        val merged = HashSet<ComponentName>()
        val items = ArrayList<AppItem>(ownerActivities.size + otherActivities.size + MetroCatalog.apps.size)

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
            if (AppSections.fold(info.label?.toString().orEmpty()) == AppSections.fold(label)) merged += info.componentName
            items += AppItem(
                id = id,
                label = label,
                launch = Launch.Activity(info.componentName, owner),
                glyph = stock.glyph,
                live = stock.live,
                packageName = info.componentName.packageName,
                removable = !info.isSystemApp(),
            )
        }

        items += AppItem(
            id = SETTINGS_ID,
            label = context.getString(R.string.settings_start_theme),
            launch = Launch.Internal(ROUTE_SETTINGS),
            glyph = Glyph.Theme,
        )
        items += AppItem(
            id = KIDS_ID,
            label = context.getString(R.string.kids_title),
            launch = Launch.Internal(ROUTE_KIDS),
            glyph = Glyph.Kids,
        )

        ownerActivities.filterNot { it.componentName in merged }.mapTo(items) { toItem(it, owner) }
        otherActivities.mapTo(items) { toItem(it, it.user) }
        return AppSections.sort(items)
    }

    private fun toItem(info: LauncherActivityInfo, user: UserHandle): AppItem {
        val isOwner = user == owner
        val component = info.componentName
        return AppItem(
            id = "app:${component.flattenToShortString()}" +
                if (isOwner) "" else "@${userManager.getSerialNumberForUser(user)}",
            label = info.label?.toString()?.takeIf { it.isNotBlank() } ?: component.packageName,
            launch = Launch.Activity(component, user),
            packageName = component.packageName,
            removable = isOwner && !info.isSystemApp(),
            isWork = !isOwner,
        )
    }

    fun launch(item: AppItem, sourceBounds: Rect?): Boolean {
        val options = ActivityOptions.makeCustomAnimation(context, R.anim.app_enter, R.anim.launcher_hold).toBundle()
        return runCatching {
            when (val target = item.launch) {
                is Launch.Activity -> startActivity(target, sourceBounds, options)
                is Launch.Action ->
                    context.startActivity(Intent(target.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
                is Launch.Internal -> Unit // handled by the UI, there is no activity to start
            }
        }.isSuccess
    }

    private fun startActivity(target: Launch.Activity, sourceBounds: Rect?, options: android.os.Bundle) {
        try {
            launcherApps.startMainActivity(target.component, target.user, sourceBounds, options)
        } catch (e: RuntimeException) {
            if (target.user != owner) throw e
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(target.component)
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

    companion object {
        const val SETTINGS_ID = "launcher:settings"
        const val ROUTE_SETTINGS = "settings"
        const val KIDS_ID = "launcher:kids"
        const val ROUTE_KIDS = "kids"
    }
}
