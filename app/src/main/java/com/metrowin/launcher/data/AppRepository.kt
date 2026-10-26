package com.metrowin.launcher.data

import android.app.ActivityOptions
import android.view.View
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
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind

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
    private val profiles = ProfilesController(context)

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
                category = AppCategory.fromAndroid(info.applicationInfo.category),
                installedAt = info.firstInstallTime,
            )
        }

        items += AppItem(
            id = SETTINGS_ID,
            label = context.getString(R.string.app_launcher_settings),
            launch = Launch.Internal(ROUTE_SETTINGS),
            glyph = Glyph.Settings,
        )
        items += AppItem(
            id = WEATHER_ID,
            label = context.getString(R.string.weather_title),
            launch = Launch.Internal(ROUTE_WEATHER),
            glyph = Glyph.Weather,
            live = LiveKind.Weather,
        )
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            items += AppItem(
                id = TORCH_ID,
                label = context.getString(R.string.torch_title),
                launch = Launch.Internal(ROUTE_TORCH),
                glyph = Glyph.Flashlight,
                live = LiveKind.Torch,
            )
        }
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
        val type = if (isOwner) null else profiles.typeOf(user)
        val component = info.componentName
        return AppItem(
            id = "app:${component.flattenToShortString()}" +
                if (isOwner) "" else "@${userManager.getSerialNumberForUser(user)}",
            label = info.label?.toString()?.takeIf { it.isNotBlank() } ?: component.packageName,
            launch = Launch.Activity(component, user),
            packageName = component.packageName,
            removable = isOwner && !info.isSystemApp(),
            isWork = !isOwner && type != ProfileType.Private,
            isPrivate = type == ProfileType.Private,
            category = AppCategory.fromAndroid(info.applicationInfo.category),
            installedAt = info.firstInstallTime,
        )
    }

    /**
     * [growFrom] is the place on screen (in the window of [anchor]) the app grows out of, for the expand animation;
     * without it the app opens with the fade of the launcher.
     */
    fun launch(item: AppItem, sourceBounds: Rect?, growFrom: Rect? = null, anchor: View? = null): Boolean {
        val options = if (growFrom != null && anchor != null) {
            ActivityOptions.makeScaleUpAnimation(anchor, growFrom.left, growFrom.top, growFrom.width(), growFrom.height()).toBundle()
        } else {
            ActivityOptions.makeCustomAnimation(context, R.anim.app_enter, R.anim.launcher_hold).toBundle()
        }
        return runCatching {
            when (val target = item.launch) {
                is Launch.Activity -> startActivity(target, sourceBounds, options)
                is Launch.Action ->
                    context.startActivity(Intent(target.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
                is Launch.Internal -> Unit // handled by the UI, there is no activity to start
                is Launch.Widget -> Unit // a widget tile has nothing to open
                is Launch.Link -> {
                    val shortcut = ShortcutDef.appShortcutParts(target.uri)
                    val number = PhoneCalls.numberOf(target.uri)
                    if (shortcut != null) {
                        // A shortcut of an app, pinned to Start: the system starts it (only for the app that is the phone's Home).
                        val (pkg, serial, id) = shortcut
                        launcherApps.startShortcut(pkg, id, null, options, userManager.getUserForSerialNumber(serial) ?: owner)
                    } else if (number != null) {
                        // A speed dial tile: it calls at once when the phone permission was given, else it opens the dialer.
                        val direct = LivePermissions.isGranted(context, PhoneCalls.permission)
                        context.startActivity(PhoneCalls.intent(number, direct), options)
                    } else {
                        // A navigation address tries Google Maps, then any map app, then the page of Google Maps; the others open as they are.
                        val opened = Places.fallbacks(target.uri).any { address ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, address.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
                            }.isSuccess
                        }
                        check(opened) { "nothing opens ${target.uri}" }
                    }
                }
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
        const val TORCH_ID = "launcher:torch"
        const val ROUTE_TORCH = "torch"
        /** The tiles of a note and of a countdown open their editor; these routes tell them apart from the launcher's pages. */
        const val ROUTE_NOTE = "note"
        const val ROUTE_COUNTDOWN = "countdown"

        /** A counter goes up by a touch, a checklist opens its list, and the world clock and the moon do nothing when touched. */
        const val ROUTE_COUNTER = "counter"
        const val ROUTE_CHECKLIST = "checklist"
        const val ROUTE_NONE = "none"

        /** The tiles that open a page of the launcher itself: an album, a group of people, a timer, the news… */
        const val ROUTE_ALBUM = "album"
        const val ROUTE_GROUP = "group"
        const val ROUTE_TIMER = "timer"
        const val ROUTE_STOPWATCH = "stopwatch"
        const val ROUTE_NEWS = "news"
        const val ROUTE_FINANCE = "finance"
        const val ROUTE_DIALGRID = "dialgrid"
        const val ROUTE_PEOPLEHUB = "peoplehub"
        const val ROUTE_REMINDERS = "reminders"
        const val WEATHER_ID = "launcher:weather"
        const val ROUTE_WEATHER = "weather"
    }
}
