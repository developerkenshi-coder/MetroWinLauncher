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
    /** The smallest the widget says it can be made (0 when it does not say): it adapts its layout down to this. */
    val minResizeWidthDp: Int = 0,
    val minResizeHeightDp: Int = 0,
    /** Android asks the user to set the widget up (choose an account, a city...) before it can be shown. */
    val needsSetup: Boolean,
)

/** Which tile size suits a widget, and how much a tile that is too small for it must shrink it. Pure, so it can be tested. */
object WidgetSizing {
    /** What a widget needs at least: the smallest size it says it can be resized to when it says one (it adapts down to it), else its default minimum. */
    fun needed(minDp: Int, minResizeDp: Int): Int = if (minResizeDp in 1 until minDp) minResizeDp else minDp

    /** The size in dp of a tile of [size], as the grid lays it out for a small tile of [unitDp] with [gapDp] between tiles. */
    fun tileDp(size: TileSize, unitDp: Float, gapDp: Float): Pair<Float, Float> =
        (unitDp * size.cols + gapDp * (size.cols - 1)) to (unitDp * size.rows + gapDp * (size.rows - 1))

    /** The smallest tile the widget fits whole in (the largest when none does: it is then shrunk, see [fitScale]). */
    fun tileSizeFor(provider: WidgetProvider, unitDp: Float, gapDp: Float): TileSize =
        tileSizeFor(needed(provider.minWidthDp, provider.minResizeWidthDp), needed(provider.minHeightDp, provider.minResizeHeightDp), unitDp, gapDp)

    fun tileSizeFor(neededWidthDp: Int, neededHeightDp: Int, unitDp: Float, gapDp: Float): TileSize =
        TileSize.entries.firstOrNull { size -> tileDp(size, unitDp, gapDp).let { (w, h) -> w + 0.5f >= neededWidthDp && h + 0.5f >= neededHeightDp } } ?: TileSize.Large

    /** How much to shrink a widget that needs [neededWidthDp] x [neededHeightDp] to show it whole in a tile of [widthDp] x [heightDp]: 1 when it fits. */
    fun fitScale(widthDp: Float, heightDp: Float, neededWidthDp: Float, neededHeightDp: Float): Float {
        if (neededWidthDp <= 0f || neededHeightDp <= 0f || widthDp <= 0f || heightDp <= 0f) return 1f
        return minOf(1f, widthDp / neededWidthDp, heightDp / neededHeightDp)
    }
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
                    minResizeWidthDp = pxToDp(info.minResizeWidth),
                    minResizeHeightDp = pxToDp(info.minResizeHeight),
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
