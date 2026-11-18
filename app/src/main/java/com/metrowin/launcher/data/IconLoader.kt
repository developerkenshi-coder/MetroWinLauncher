package com.metrowin.launcher.data

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import android.graphics.Bitmap
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Loads app icons as flat squares, the shape of every Metro tile and app list entry. */
class IconLoader(context: Context) {
    private val appContext = context.applicationContext
    private val pm = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val owner = Process.myUserHandle()
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun clear() {
        cache.evictAll()
        tileColors.clear()
    }

    /** The colour of each icon as a tile colour (0: the icon has none worth a colour), kept so an icon is read once. */
    private val tileColors = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** The colour the icon of [item] is about, darkened to carry white text; null for a grey icon or one that is not loaded. */
    suspend fun tileColor(item: AppItem): Int? {
        tileColors[item.id]?.let { return it.takeIf { c -> c != 0 } }
        val bitmap = load(item, COLOR_SAMPLE) ?: return null
        val color = withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
            DominantColor.of(pixels)?.let(DominantColor::forTile) ?: 0
        }
        tileColors[item.id] = color
        return color.takeIf { it != 0 }
    }

    /** The icon pack in use, or null for the icons of the apps. */
    @Volatile
    private var pack: IconPack? = null

    /** Changes with the icon pack, so what shows icons loads them again. */
    var revision by mutableIntStateOf(0)
        private set

    /** Icons drawn as translucent glass (see [GlassIcon]). */
    @Volatile
    private var glass = false

    fun useGlass(on: Boolean) {
        if (glass == on) return
        glass = on
        cache.evictAll()
        revision++
    }

    private fun key(item: AppItem, sizePx: Int) = "${item.id}@$sizePx${if (glass) "g" else ""}"

    /** The packs the apps with an icon of their own choice use, by package, and the choices (see [IconPicks]). */
    @Volatile
    private var others: Map<String, IconPack> = emptyMap()

    @Volatile
    private var picks: Map<String, String> = emptyMap()

    fun usePack(newPack: IconPack?, picked: Map<String, String> = emptyMap(), otherPacks: Map<String, IconPack> = emptyMap()) {
        pack = newPack
        picks = picked
        others = otherPacks
        cache.evictAll()
        tileColors.clear()
        revision++
    }

    /** The phone is short of memory: half of the icons go, or all of them at the worst levels (they are read again from the disk copy). */
    fun trim(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) cache.evictAll()
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) cache.trimToSize(cache.size() / 2)
    }

    /** Forgets the icons of [ids] only (an app was updated, added or removed), and makes what shows them load them again. */
    fun evict(ids: Set<String>) {
        if (ids.isEmpty()) return
        cache.snapshot().keys.filter { it.substringBeforeLast('@') in ids }.forEach(cache::remove)
        ids.forEach(tileColors::remove)
        revision++
    }

    /**
     * The disk copy of the icon of [item] at [sizePx], or null when it is not kept: only the plain icon of the app is (no icon pack, no glass,
     * no icon of the user's choice), as it changes with nothing but the app. The name has the time the app was updated, so an update makes a new file.
     */
    private fun diskFile(item: AppItem, sizePx: Int, packageName: String): File? {
        if (pack != null || glass || picks[item.id] != null) return null
        val updated = runCatching { pm.getPackageInfo(packageName, 0).lastUpdateTime }.getOrNull() ?: return null
        return File(diskDir, "${item.id.replace(Regex("[^A-Za-z0-9.]"), "-")}_${updated}_$sizePx.png")
    }

    /** Where the copies are: one folder for each version of the launcher, so the icons of an older one (drawn another way) are not used. */
    private val diskDir: File by lazy {
        val version = runCatching { pm.getPackageInfo(appContext.packageName, 0).longVersionCode }.getOrDefault(0L)
        File(appContext.cacheDir, "icons-$version").also { dir ->
            appContext.cacheDir.listFiles { f -> f.name.startsWith("icons-") && f != dir }?.forEach { it.deleteRecursively() }
            dir.mkdirs()
        }
    }

    private fun readDisk(file: File?): Bitmap? = file?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    private fun writeDisk(file: File?, bitmap: Bitmap) {
        if (file == null) return
        runCatching {
            val (id, updated) = file.name.split('_').let { it[0] to it[1] }
            // An older copy of the same app (before an update) is not wanted any more.
            file.parentFile?.listFiles { f -> f.name.startsWith("${id}_") && f.name.split('_').getOrNull(1) != updated }?.forEach { it.delete() }
            val part = File(file.path + ".part")
            part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            part.renameTo(file)
        }
    }

    /** The icon if it is already in memory, so a list can draw it in its first frame. */
    fun peek(item: AppItem, sizePx: Int): Bitmap? = cache.get(key(item, sizePx))

    /** Null for entries that have no icon of their own (stock apps drawn with a glyph). */
    suspend fun load(item: AppItem, sizePx: Int): Bitmap? {
        val activity = item.launch as? Launch.Activity ?: return loadPackageIcon(item, sizePx)
        val key = key(item, sizePx)
        cache.get(key)?.let { return it }
        val file = activity.component.packageName.let { diskFile(item, sizePx, it) }
        val bitmap = withContext(Dispatchers.IO) {
            readDisk(file) ?: runCatching {
                val pick = IconPick.parse(picks[item.id])
                val current = IconPicks.packFor(pick, pack, others)
                val own = (pick?.drawable?.let { current?.drawable(it) } ?: current?.iconFor(activity.component))?.let { render(it, sizePx) }
                val icon = own ?: render(iconOf(activity), sizePx).let { original -> current?.dress(original, sizePx, activity.component.packageName) ?: original }
                val shown = if (glass) glassOf(icon) else icon
                shown.also { if (item.isWork) drawWorkBadge(it) else if (item.isPrivate) drawPrivateBadge(it) }
            }.getOrNull()?.also { if (pack == null && !glass && picks[item.id] == null) writeDisk(file, it) }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /** [name] of [pack] as a square of [sizePx] for the grid that picks an icon; null if the pack has no such drawable. */
    suspend fun packIcon(pack: IconPack, name: String, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) { runCatching { pack.drawable(name)?.let { render(it, sizePx) } }.getOrNull() }

    /** A shortcut of an app put on Start has the icon of its app (the launcher icon of the package). */
    private suspend fun loadPackageIcon(item: AppItem, sizePx: Int): Bitmap? {
        val pkg = item.packageName ?: return null
        if (!item.pinOnly) return null
        val key = key(item, sizePx)
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { runCatching { render(pm.getApplicationIcon(pkg), sizePx).let { if (glass) glassOf(it) else it } }.getOrNull() }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private fun iconOf(activity: Launch.Activity): Drawable =
        if (activity.user == owner) {
            pm.getActivityIcon(activity.component)
        } else {
            launcherApps.getActivityList(activity.component.packageName, activity.user)
                .first { it.componentName == activity.component }
                .getIcon(0)
        }

    /** The blue briefcase corner that Android puts on work profile apps. */
    private fun drawWorkBadge(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val size = bitmap.width.toFloat()
        val badge = size * WORK_BADGE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WORK_BLUE }
        canvas.drawRect(size - badge, size - badge, size, size, paint)
        paint.apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = badge * 0.09f }
        val pad = badge * 0.22f
        canvas.drawRect(size - badge + pad, size - badge + pad * 1.9f, size - pad, size - pad, paint)
        canvas.drawRect(size - badge * 0.62f, size - badge + pad, size - badge * 0.38f, size - badge + pad * 1.9f, paint)
    }

    /** A purple corner with a keyhole, for the apps of the private space. */
    private fun drawPrivateBadge(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val size = bitmap.width.toFloat()
        val badge = size * WORK_BADGE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRIVATE_PURPLE }
        canvas.drawRect(size - badge, size - badge, size, size, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(size - badge / 2, size - badge * 0.58f, badge * 0.16f, paint)
        canvas.drawRect(size - badge * 0.56f, size - badge * 0.52f, size - badge * 0.44f, size - badge * 0.22f, paint)
    }

    private fun glassOf(icon: Bitmap): Bitmap {
        val pixels = IntArray(icon.width * icon.height).also { icon.getPixels(it, 0, icon.width, 0, 0, icon.width, icon.height) }
        return createBitmap(icon.width, icon.height).also { it.setPixels(GlassIcon.of(pixels, icon.width, icon.height), 0, icon.width, 0, 0, icon.width, icon.height) }
    }

    private fun render(drawable: Drawable, size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            // An adaptive icon is 108dp wide and only the centre 72dp is guaranteed to show. Draw the
            // layers across that centre without the system mask: square, like a Metro tile.
            val full = (size * ADAPTIVE_CANVAS / ADAPTIVE_VISIBLE).roundToInt()
            val offset = -(full - size) / 2
            // As glass only the picture is kept: the themed (monochrome) layer when the app has one, else the foreground.
            val mono = if (glass && Build.VERSION.SDK_INT >= 33) drawable.monochrome?.mutate()?.apply { setTint(Color.WHITE) } else null
            (if (mono != null) listOf(mono) else if (glass) listOfNotNull(drawable.foreground) else listOfNotNull(drawable.background, drawable.foreground)).forEach { layer ->
                layer.setBounds(offset, offset, offset + full, offset + full)
                layer.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        return bitmap
    }

    private companion object {
        private const val COLOR_SAMPLE = 24

        // A sixteenth of the heap: 300 apps do not fit in a fixed 16 MB, and icons drawn again while scrolling stutter on weak phones.
        val CACHE_BYTES = (Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(16 * 1024 * 1024, 48 * 1024 * 1024)
        const val ADAPTIVE_CANVAS = 108f
        const val ADAPTIVE_VISIBLE = 72f
        const val WORK_BADGE = 0.36f
        const val WORK_BLUE = 0xFF1A73E8.toInt()
        const val PRIVATE_PURPLE = 0xFF6A00FF.toInt()
    }
}
