package com.metrowin.launcher.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.abs

/** An installed icon pack. */
@Immutable
data class IconPackInfo(val packageName: String, val label: String)

/**
 * What the appfilter.xml of an icon pack says (the format ADW, Nova, Apex and Go Launcher share): the icon of each
 * app by its component, and how to dress the apps the pack has no icon for.
 */
data class IconPackMap(
    /** `package/class` of an activity to the name of the pack's drawable. */
    val icons: Map<String, String> = emptyMap(),
    /** Backgrounds for the icons of apps the pack does not know; one is picked for each app. */
    val backs: List<String> = emptyList(),
    /** Drawn over the icon, if the pack has one. */
    val upon: String? = null,
    /** How much of the background the app's own icon fills (1 = all of it). */
    val scale: Float = 1f,
) {
    /** The pack can dress icons it has no drawable for. */
    val dressesOthers: Boolean get() = backs.isNotEmpty()
}

object AppFilterXml {
    /** `ComponentInfo{com.a/com.a.Main}` (or `com.a/.Main`) as `com.a/com.a.Main`, or null if it is not a component. */
    fun componentKey(raw: String): String? {
        val inner = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}")
        val slash = inner.indexOf('/')
        if (slash <= 0 || slash == inner.length - 1) return null
        val pkg = inner.substring(0, slash)
        val cls = inner.substring(slash + 1)
        return pkg + "/" + (if (cls.startsWith(".")) pkg + cls else cls)
    }

    fun parse(parser: XmlPullParser): IconPackMap {
        val icons = LinkedHashMap<String, String>()
        val backs = mutableListOf<String>()
        var upon: String? = null
        var scale = 1f
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                fun attribute(name: String) = parser.getAttributeValue(null, name)
                when (parser.name) {
                    "item" -> {
                        val key = attribute("component")?.let(::componentKey)
                        val drawable = attribute("drawable")
                        // The first entry for a component wins; packs list calendar and clock variants after it.
                        if (key != null && !drawable.isNullOrBlank()) icons.putIfAbsent(key, drawable)
                    }
                    "iconback" -> for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i).startsWith("img")) backs += parser.getAttributeValue(i)
                    }
                    "iconupon" -> upon = attribute("img1") ?: upon
                    "scale" -> scale = attribute("factor")?.toFloatOrNull()?.coerceIn(MIN_SCALE, MAX_SCALE) ?: scale
                }
            }
            event = parser.next()
        }
        return IconPackMap(icons, backs, upon, scale)
    }

    private const val MIN_SCALE = 0.3f
    private const val MAX_SCALE = 1.5f
}

/** One icon pack, opened: its drawables by name, and how it dresses the apps it has no icon for. */
class IconPack(private val resources: Resources, val packageName: String, private val map: IconPackMap) {
    val size: Int get() = map.icons.size

    // An icon pack is another app: its drawables can only be found by name.
    @SuppressLint("DiscouragedApi")
    private fun drawable(name: String): Drawable? {
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(resources, id, null) }.getOrNull()
    }

    /** The pack's icon for [component], if it has one. */
    fun iconFor(component: ComponentName): Drawable? =
        map.icons[component.flattenToString()]?.let(::drawable)

    /**
     * [original] (already drawn, [size] square) of the app [appPackage] on one of the pack's backgrounds, with its
     * overlay, for an app the pack has no icon for. Null if the pack gives no backgrounds.
     */
    fun dress(original: Bitmap, size: Int, appPackage: String): Bitmap? {
        if (!map.dressesOthers) return null
        // Not random: the same app always gets the same background.
        val back = map.backs[abs(appPackage.hashCode()) % map.backs.size].let(::drawable)
        val result = createBitmap(size, size)
        val canvas = Canvas(result)
        back?.apply { setBounds(0, 0, size, size) }?.draw(canvas)
        val inner = (size * map.scale).toInt()
        val offset = (size - inner) / 2
        canvas.drawBitmap(original, null, Rect(offset, offset, offset + inner, offset + inner), Paint(Paint.FILTER_BITMAP_FLAG))
        map.upon?.let(::drawable)?.apply { setBounds(0, 0, size, size) }?.draw(canvas)
        return result
    }
}

/** Finds the icon packs of the phone and opens one. */
class IconPackRepository(private val context: Context) {
    private val pm = context.packageManager

    /** The installed packs, by name. A pack declares itself with one of the intents the big launchers use. */
    @Suppress("DEPRECATION")
    fun installed(): List<IconPackInfo> =
        answering(ACTIONS, { pm.resolveActivity(Intent(it), 0) != null }, { pm.queryIntentActivities(Intent(it), 0) })
            .distinctBy { it.activityInfo.packageName }
            .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }

    /** Null when the pack is gone or has no readable appfilter. */
    fun load(packageName: String): IconPack? = runCatching {
        val resources = pm.getResourcesForApplication(packageName)
        val parser = openFilter(resources, packageName) ?: return null
        IconPack(resources, packageName, AppFilterXml.parse(parser))
    }.getOrNull()

    @SuppressLint("DiscouragedApi") // the appfilter of another app is only found by name
    private fun openFilter(resources: Resources, packageName: String): XmlPullParser? {
        val id = resources.getIdentifier("appfilter", "xml", packageName)
        if (id != 0) return resources.getXml(id)
        // Some packs ship it as an asset instead.
        val stream = runCatching { resources.assets.open("appfilter.xml") }.getOrNull() ?: return null
        return XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, null) }
    }


    companion object {
        val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK",
            "com.gau.go.launcherex.theme",
        )

        /**
         * The activities that answer [actions]. The list is only asked for an action that [anyAnswer] says someone answers: on a
         * phone with no icon pack (nearly all of them) the five lists would all come back empty, and each empty list costs a call
         * to the system and, on some phones, an error line in the log. When the check itself fails the list is asked for anyway.
         */
        internal fun answering(
            actions: List<String>,
            anyAnswer: (String) -> Boolean,
            listAnswers: (String) -> List<ResolveInfo>,
        ): List<ResolveInfo> =
            actions
                .filter { runCatching { anyAnswer(it) }.getOrDefault(true) }
                .flatMap { runCatching { listAnswers(it) }.getOrDefault(emptyList()) }
    }
}
