package com.metrowin.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.metrowin.launcher.model.TileSize

/** A widget that an app offers, as the picker lists it. */
@Immutable
data class WidgetProvider(
    val label: String,
    val appLabel: String,
    val component: ComponentName,
    val minWidthDp: Int,
    val minHeightDp: Int,
    /** Android asks the user to set the widget up (choose an account, a city...) before it can be shown. */
    val needsSetup: Boolean,
)

/** Which tile size suits a widget. Pure, so it can be tested. */
object WidgetSizing {
    /** A medium tile is about two columns of small tiles wide: a widget that wants more than that gets a wide tile. */
    private const val MEDIUM_MAX_DP = 190

    fun tileSizeFor(minWidthDp: Int, @Suppress("UNUSED_PARAMETER") minHeightDp: Int): TileSize =
        if (minWidthDp > MEDIUM_MAX_DP) TileSize.Wide else TileSize.Medium
}

/**
 * Hosts Android widgets on Start: lists what the apps offer, binds a widget (Android may ask the user to allow
 * it first), and creates the view that shows it. The widgets are kept as long as the launcher keeps their ids.
 */
class WidgetService(private val context: Context) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)

    fun providers(): List<WidgetProvider> {
        val pm = context.packageManager
        return manager.installedProviders
            .map { info ->
                WidgetProvider(
                    label = info.loadLabel(pm).orEmpty(),
                    appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                        .getOrDefault(info.provider.packageName),
                    component = info.provider,
                    minWidthDp = pxToDp(info.minWidth),
                    minHeightDp = pxToDp(info.minHeight),
                    needsSetup = info.configure != null,
                )
            }
            .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    private fun pxToDp(px: Int) = (px / context.resources.displayMetrics.density).toInt()

    /** A new id for a widget to come. Give it back with [delete] when the widget is not added after all. */
    fun allocateId(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, provider: ComponentName): Boolean = manager.bindAppWidgetIdIfAllowed(id, provider)

    /** The screen that asks the user to let the launcher show this widget. */
    fun bindRequest(id: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun delete(id: Int) = host.deleteAppWidgetId(id)

    /** The view of the widget, or null when its app is gone. */
    fun createView(viewContext: Context, id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(viewContext, id, info).also { it.setAppWidget(id, info) }
    }

    /** Starts the widget's own setup screen. The result arrives in the activity's onActivityResult. */
    fun configure(activity: android.app.Activity, id: Int, requestCode: Int) =
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)

    /** The widgets update their views only while the host listens. */
    fun startListening() = runCatching { host.startListening() }

    fun stopListening() = runCatching { host.stopListening() }

    companion object {
        const val HOST_ID = 0x4D57
    }
}

/** The widgets, for the tiles that show one. Null where there is no host (the previews and the tests). */
val LocalWidgetHost = staticCompositionLocalOf<WidgetService?> { null }
