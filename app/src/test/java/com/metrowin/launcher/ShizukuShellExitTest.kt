package com.metrowin.launcher

import com.metrowin.launcher.data.ShizukuShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

/** A process that has not ended says so with an exception when asked for its exit; the shell must read that as "still running", not as a failure. */
class ShizukuShellExitTest {
    private class Fake(private var runsFor: Int, private val code: Int, private val plain: Boolean = false) : Process() {
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = code
        override fun exitValue(): Int {
            if (runsFor-- > 0) throw if (plain) IllegalArgumentException("process hasn't exited") else IllegalThreadStateException("process hasn't exited")
            return code
        }
        override fun destroy() {}
    }

    @Test
    fun aProcessStillRunningIsNullNotAnError() {
        val process = Fake(runsFor = 2, code = 0)
        assertNull(ShizukuShell.exitOf(process))
        assertNull(ShizukuShell.exitOf(process))
        assertEquals(0, ShizukuShell.exitOf(process))
    }

    @Test
    fun theSameThroughTheBinderWhereItIsAPlainIllegalArgument() {
        val process = Fake(runsFor = 1, code = 0, plain = true)
        assertNull(ShizukuShell.exitOf(process))
        assertEquals(0, ShizukuShell.exitOf(process))
    }

    @Test
    fun theExitCodeComesThroughOnceTheProcessEnded() {
        assertEquals(3, ShizukuShell.exitOf(Fake(runsFor = 0, code = 3)))
    }
}
