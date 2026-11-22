package com.metrowin.launcher.shizukuservice

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.LruCache
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.data.logged
import rikka.shizuku.Shizuku

/** One recent task as the service lists it. */
class TaskRef(val id: Int, val packageName: String, val running: Boolean) {
    companion object {
        /** "123|com.example.app|1"; a line that does not fit is nothing (the service is another version, or Android answered oddly). */
        fun parse(line: String): TaskRef? {
            val parts = line.split('|')
            if (parts.size != 3) return null
            val id = parts[0].toIntOrNull() ?: return null
            if (parts[1].isBlank() || parts[1].any { it.isWhitespace() }) return null
            return TaskRef(id, parts[1], parts[2] == "1")
        }
    }
}

/**
 * The real pictures of the recent apps (docs/progetti/miniature-recenti.md): the user service of [ShellUserService] gives them, through Shizuku.
 * **Privacy**: they are screens of other apps, so they are only ever kept in memory (12 of them), never written to a file, thrown away when the
 * screen closes ([clear]) and only fetched when the user turned the preview on ([enabled]); an app that hides its screen gives none and shows its icon.
 */
object TaskThumbs {
    private const val FILE = "metrowin_recents_options"
    private const val KEY = "previews"
    private const val WIDTH = 360

    private val memory = LruCache<String, Bitmap>(12)

    @Volatile
    private var service: IShellService? = null
    private val lock = Object()

    fun enabled(context: Context): Boolean = Prefs.open(context, FILE).getBoolean(KEY, false)

    fun setEnabled(context: Context, on: Boolean) {
        Prefs.open(context, FILE).edit().putBoolean(KEY, on).apply()
        if (!on) clear()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            synchronized(lock) { service = binder?.let(IShellService.Stub::asInterface); lock.notifyAll() }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(lock) { service = null }
        }
    }

    private var bound = false
    private var bindArgs: Shizuku.UserServiceArgs? = null

    /** Blocking, off the main thread: binds the user service (Shizuku starts it) and waits a few seconds for it. Null without Shizuku. */
    private fun connect(context: Context): IShellService? {
        service?.let { return it }
        if (!ShizukuShell.ready()) return null
        synchronized(lock) {
            if (!bound) {
                val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
                    .daemon(false).processNameSuffix("shell").version(1)
                runCatching { Shizuku.bindUserService(args, connection); bound = true; bindArgs = args }.logged("thumbs.bind", withMessage = false)
            }
            val end = System.currentTimeMillis() + 5_000
            while (service == null && System.currentTimeMillis() < end) runCatching { lock.wait(200) }
            return service
        }
    }

    /**
     * Blocking, off the main thread. The pictures of the recent tasks by package (the newest task of a package wins), or nothing when the
     * preview is off, Shizuku is not ready or Android's hidden API does not answer.
     */
    fun load(context: Context, max: Int = 12): Map<String, Bitmap> {
        if (!enabled(context)) return emptyMap()
        val remote = connect(context) ?: return emptyMap()
        return runCatching {
            val out = LinkedHashMap<String, Bitmap>()
            remote.recentTasks(max).mapNotNull(TaskRef::parse).forEach { task ->
                if (task.packageName in out) return@forEach
                memory.get(task.packageName + ":" + task.id)?.let { out[task.packageName] = it; return@forEach }
                val bytes = remote.snapshot(task.id, WIDTH)
                if (bytes.isEmpty()) return@forEach
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                    memory.put(task.packageName + ":" + task.id, it)
                    out[task.packageName] = it
                }
            }
            out
        }.logged("thumbs.load", withMessage = false).getOrDefault(emptyMap())
    }

    /** What is kept is thrown away (the screen of the recents closes), and the service is let go. */
    fun clear() {
        memory.evictAll()
        synchronized(lock) {
            val args = bindArgs
            if (bound && args != null) runCatching { Shizuku.unbindUserService(args, connection, true) }
            bound = false
            bindArgs = null
            service = null
        }
    }
}
