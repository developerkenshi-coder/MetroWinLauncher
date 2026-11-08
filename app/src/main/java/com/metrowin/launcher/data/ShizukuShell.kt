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
    class Result(val code: Int, val output: String) {
        val ok get() = code == 0
    }

    private const val LIMIT = 256 * 1024
    private const val TIMEOUT_MS = 20_000L

    fun ready(): Boolean = ShizukuRadio.running() && ShizukuRadio.granted()

    /** [args] are the program and its arguments, never a line for a shell: nothing typed by a user is ever glued into one. */
    fun run(args: Array<String>, limit: Int = LIMIT, timeoutMs: Long = TIMEOUT_MS, cancel: AtomicBoolean? = null): Result = runCatching {
        // shortcut: Shizuku API 13 keeps newProcess private (it wants a bound user service for anything bigger); reflection is enough for one-shot commands, upgrade if a later version drops it.
        val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        method.isAccessible = true
        val process = method.invoke(null, args, null, null) as Process
        val out = Drain(process.inputStream, limit).also { it.start() }
        val err = Drain(process.errorStream, limit).also { it.start() }
        // Waits in short slices so that [cancel] (the Stop key of the Terminal) ends the process before the timeout does.
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var done = false
        while (!done && System.nanoTime() < end && cancel?.get() != true) done = process.waitFor(200, TimeUnit.MILLISECONDS)
        if (!done) process.destroy()
        out.join(1_000)
        err.join(1_000)
        Result(if (done) process.exitValue() else if (cancel?.get() == true) -3 else -2, (out.text() + err.text()).trim())
    }.logged("shizuku.run", withMessage = false).getOrElse { Result(-1, it.message.orEmpty()) }

    /** A line for the shell, for the terminal where the user types it himself and for the fixed pipelines (`dumpsys | grep`) of the battery doctor. */
    fun shell(line: String, limit: Int = LIMIT, cancel: AtomicBoolean? = null): Result = run(arrayOf("sh", "-c", line), limit, cancel = cancel)

    /** The commands one after the other off the main thread; [done] says whether all of them ended well. */
    fun async(commands: List<Array<String>>, done: (Boolean) -> Unit = {}) {
        Thread { done(commands.all { run(it).ok }) }.start()
    }

    private class Drain(private val stream: InputStream, private val limit: Int) : Thread() {
        private val buffer = ByteArrayOutputStream()

        override fun run() {
            runCatching {
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val count = stream.read(chunk)
                    if (count < 0) break
                    if (buffer.size() < limit) buffer.write(chunk, 0, minOf(count, limit - buffer.size()))
                }
            }
        }

        fun text(): String = String(buffer.toByteArray(), Charsets.UTF_8)
    }
}
