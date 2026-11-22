package com.metrowin.launcher.shizukuservice

import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import java.io.ByteArrayOutputStream
import kotlin.system.exitProcess

/**
 * Runs in a process of its own that Shizuku starts with the identity of the shell (so it may call what an app may not). It reaches the
 * recent tasks and the pictures Android keeps of them through hidden APIs, by reflection, and **each step has a fallback to an empty
 * answer**: the signatures change between Android versions and a refusal must end in "no picture", never in a crash of this process.
 * Pictures of apps that mark their window secure come back empty by Android's own rule, and nothing here tries to get round that.
 */
class ShellUserService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun recentTasks(max: Int): List<String> = runCatching {
        val service = tasks() ?: return emptyList()
        val method = service.javaClass.methods.firstOrNull { it.name == "getRecentTasks" && it.parameterTypes.size == 3 } ?: return emptyList()
        // flags: RECENT_IGNORE_UNAVAILABLE (2); the user: the one of this process (the current one for the shell).
        val slice = method.invoke(service, max.coerceIn(1, 40), ActivityManager.RECENT_IGNORE_UNAVAILABLE, 0) ?: return emptyList()
        val list = slice.javaClass.getMethod("getList").invoke(slice) as? List<*> ?: return emptyList()
        list.mapNotNull { info ->
            val task = info as? ActivityManager.RecentTaskInfo ?: return@mapNotNull null
            val pkg = (task.baseActivity ?: task.baseIntent?.component)?.packageName ?: return@mapNotNull null
            "${task.taskId}|$pkg|${if (task.isRunning) 1 else 0}"
        }
    }.getOrDefault(emptyList())

    override fun snapshot(taskId: Int, maxWidth: Int): ByteArray = runCatching {
        val shot = takeSnapshot(taskId) ?: return ByteArray(0)
        // A placeholder that Android draws when it has no real picture (and the one for a hidden window) is not a picture of the app.
        if (shot.javaClass.methods.firstOrNull { it.name == "isRealSnapshot" }?.invoke(shot) == false) return ByteArray(0)
        val soft = bitmapOf(shot)?.copy(Bitmap.Config.ARGB_8888, false) ?: return ByteArray(0)
        val scaled = if (soft.width > maxWidth && maxWidth > 0) Bitmap.createScaledBitmap(soft, maxWidth, soft.height * maxWidth / soft.width, true) else soft
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        }.takeIf { it.size <= MAX_BYTES } ?: ByteArray(0)
    }.getOrDefault(ByteArray(0))

    /**
     * The picture Android keeps of a task. Android 17 moved it to a manager of its own (`getTaskSnapshotManager().getTaskSnapshot(taskId, id, resolution)`,
     * seen on the emulator); the versions before ask the task service itself `getTaskSnapshot(taskId, lowResolution)` (more booleans from Android 15).
     * Each way is tried, the first that gives something wins.
     */
    private fun takeSnapshot(taskId: Int): Any? {
        val service = tasks() ?: return null
        val manager = runCatching { service.javaClass.getMethod("getTaskSnapshotManager").invoke(service) }.getOrNull()
        if (manager != null) {
            val method = manager.javaClass.methods.firstOrNull { it.name == "getTaskSnapshot" && it.parameterTypes.size == 3 }
            // The third argument is the resolution: 1 is the low one, 0 is refused ("Invalidate resolution=0").
            if (method != null) runCatching { method.invoke(manager, taskId, 0L, 1) }.getOrNull()?.let { return it }
        }
        val method = service.javaClass.methods.firstOrNull { it.name == "getTaskSnapshot" && it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType } ?: return null
        val args = arrayOfNulls<Any>(method.parameterTypes.size).also { a -> a[0] = taskId; for (i in 1 until a.size) a[i] = true }
        return runCatching { method.invoke(service, *args) }.getOrNull()
    }

    /**
     * The picture of a snapshot as a bitmap. `getHardwareBuffer()` answers null on Android 17 while the buffer is there (the snapshot keeps it in
     * `mSnapshot` and wraps it itself in `wrapToBitmap()`), so the ways are tried in the order of how little they depend on the version.
     */
    private fun bitmapOf(shot: Any): Bitmap? {
        runCatching { shot.javaClass.getMethod("wrapToBitmap").invoke(shot) as? Bitmap }.getOrNull()?.let { return it }
        val buffer = runCatching { shot.javaClass.getMethod("getHardwareBuffer").invoke(shot) as? HardwareBuffer }.getOrNull()
            ?: runCatching { shot.javaClass.getDeclaredField("mSnapshot").apply { isAccessible = true }.get(shot) as? HardwareBuffer }.getOrNull()
            ?: return null
        val space = runCatching { shot.javaClass.getMethod("getColorSpace").invoke(shot) as? ColorSpace }.getOrNull()
        return Bitmap.wrapHardwareBuffer(buffer, space)
    }

    private fun tasks(): Any? = runCatching {
        Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
    }.getOrNull()

    private companion object {
        /** Well under the 1 MB a binder transaction holds, with what the rest of the answer needs. */
        const val MAX_BYTES = 600 * 1024
    }
}
