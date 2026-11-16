package com.metrowin.launcher.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import rikka.shizuku.Shizuku

/**
 * Runs commands with the rights of the shell through Shizuku (see [ShizukuRadio] for when that is possible). Everything blocks: call it off the
 * main thread, or use [async]. A command that cannot run (Shizuku not there, not allowed, a crash) ends with [Result.code] below zero (-2 timeout, -3 stopped by the user), never with an exception.
 */
object ShizukuShell {
    /** [output] is what the program wrote, both streams together; [stdout] and [error] are the two apart (the Terminal paints the error). */
    class Result(val code: Int, val output: String, val stdout: String = output, val error: String = "") {
        val ok get() = code == 0
    }

    private const val LIMIT = 256 * 1024
    private const val TIMEOUT_MS = 20_000L
    private const val RETRY_MS = 300L
    private const val POLL_MS = 40L

    /** [Result.code] of a command that Shizuku could not start: Shizuku is not there (or not allowed), or it is there and the start failed. */
    const val NO_SHIZUKU = -1
    const val FAILED = -4

    fun ready(): Boolean = ShizukuRadio.running() && ShizukuRadio.granted()

    /** [args] are the program and its arguments, never a line for a shell: nothing typed by a user is ever glued into one. */
    fun run(
        args: Array<String>, limit: Int = LIMIT, timeoutMs: Long = TIMEOUT_MS, cancel: AtomicBoolean? = null,
        /** Called from a worker thread with everything written so far, while the program still runs. */
        onOutput: ((String) -> Unit)? = null,
    ): Result = runCatching {
        val process = start(args)
        var outDrain: Drain? = null
        var errDrain: Drain? = null
        val push = { onOutput?.invoke((outDrain?.text() ?: "") + (errDrain?.text() ?: "")); Unit }
        val out = Drain(process.inputStream, limit, if (onOutput != null) push else null).also { outDrain = it }
        val err = Drain(process.errorStream, limit, if (onOutput != null) push else null).also { errDrain = it }
        out.start()
        err.start()
        // Waits in short slices so that [cancel] (the Stop key of the Terminal) ends the process before the timeout does. The exit is asked for
        // directly: the waitFor(timeout) of Shizuku 13.6 on Android 17 says "done" while a short command (`settings put`) is still running.
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var exit: Int? = exitOf(process)
        while (exit == null && System.nanoTime() < end && cancel?.get() != true) {
            Thread.sleep(POLL_MS)
            exit = exitOf(process)
        }
        if (exit == null) process.destroy()
        out.join(1_000)
        err.join(1_000)
        Result(exit ?: if (cancel?.get() == true) -3 else -2, (out.text() + err.text()).trim(), out.text().trim(), err.text().trim())
    }.logged("shizuku.run", withMessage = false).getOrElse {
        // "No Shizuku" (-1) only when it really is not there: a command that could not be started while Shizuku is ready is a failure of its own, with its reason.
        Result(if (ready()) FAILED else NO_SHIZUKU, it.message.orEmpty())
    }

    /** The exit code of [process], or null while it still runs. */
    // Through Shizuku's binder the "process hasn't exited" of a local Process (IllegalThreadStateException) arrives as its superclass, a plain IllegalArgumentException.
    internal fun exitOf(process: Process): Int? = try { process.exitValue() } catch (e: IllegalArgumentException) { null } catch (e: IllegalStateException) { null }

    // shortcut: Shizuku API 13 keeps newProcess private (it wants a bound user service for anything bigger); reflection is enough for one-shot commands, upgrade if a later version drops it.
    private fun spawn(args: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        method.isAccessible = true
        return try {
            method.invoke(null, args, null, null) as Process
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    /** The process, started once more after a moment when Shizuku is there but its connection hiccuped (nothing has run yet, so nothing runs twice). */
    private fun start(args: Array<String>): Process = try {
        spawn(args)
    } catch (e: Exception) {
        if (!ready()) throw e
        Thread.sleep(RETRY_MS)
        spawn(args)
    }

    /** A line for the shell, for the terminal where the user types it himself and for the fixed pipelines (`dumpsys | grep`) of the battery doctor. */
    fun shell(
        line: String, limit: Int = LIMIT, cancel: AtomicBoolean? = null, timeoutMs: Long = TIMEOUT_MS, onOutput: ((String) -> Unit)? = null,
    ): Result = run(arrayOf("sh", "-c", line), limit, timeoutMs, cancel, onOutput)

    /** The commands one after the other off the main thread; [done] says whether all of them ended well. */
    fun async(commands: List<Array<String>>, done: (Boolean) -> Unit = {}) {
        Thread { done(commands.all { run(it).ok }) }.start()
    }

    private class Drain(private val stream: InputStream, private val limit: Int, private val onChange: (() -> Unit)? = null) : Thread() {
        private val buffer = ByteArrayOutputStream()

        override fun run() {
            runCatching {
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val count = stream.read(chunk)
                    if (count < 0) break
                    if (buffer.size() < limit) buffer.write(chunk, 0, minOf(count, limit - buffer.size()))
                    onChange?.invoke()
                }
            }
        }

        fun text(): String = String(buffer.toByteArray(), Charsets.UTF_8)
    }
}
