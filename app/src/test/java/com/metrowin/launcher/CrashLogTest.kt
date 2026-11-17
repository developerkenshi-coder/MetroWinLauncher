package com.metrowin.launcher

import com.metrowin.launcher.data.CrashLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun theNewestCrashIsKeptAndRead() {
        assertNull(CrashLog.read(folder.root))
        CrashLog.save(folder.root, CrashLog.text(0L, "1.0 (1)", "main", IllegalStateException("first")))
        CrashLog.save(folder.root, CrashLog.text(0L, "1.0 (1)", "main", IllegalStateException("second")))
        val text = CrashLog.read(folder.root)!!
        assertTrue("second" in text && "first" !in text && "thread main" in text)
    }

    @Test
    fun aHugeStackIsCut() {
        val deep = RuntimeException("x".repeat(CrashLog.MAX_CHARS * 2))
        assertEquals(CrashLog.MAX_CHARS, CrashLog.text(0L, "v", "t", deep).length)
    }
}
