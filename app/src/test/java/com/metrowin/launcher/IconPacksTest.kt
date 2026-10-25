package com.metrowin.launcher

import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.AppFilterXml
import com.metrowin.launcher.data.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconPacksTest {
    private fun parse(xml: String) = AppFilterXml.parse(Xml.newPullParser().apply { setInput(StringReader(xml)) })

    @Test
    fun aComponentIsReducedToPackageAndClass() {
        assertEquals("com.a/com.a.Main", AppFilterXml.componentKey("ComponentInfo{com.a/com.a.Main}"))
        assertEquals("com.a/com.a.Main", AppFilterXml.componentKey("ComponentInfo{com.a/.Main}"))
        assertEquals("com.a/com.b.Other", AppFilterXml.componentKey(" ComponentInfo{com.a/com.b.Other} "))
    }

    @Test
    fun somethingThatIsNoComponentIsNull() {
        assertNull(AppFilterXml.componentKey(""))
        assertNull(AppFilterXml.componentKey("ComponentInfo{}"))
        assertNull(AppFilterXml.componentKey("ComponentInfo{com.a}"))
        assertNull(AppFilterXml.componentKey("ComponentInfo{/Main}"))
        assertNull(AppFilterXml.componentKey("ComponentInfo{com.a/}"))
    }

    @Test
    fun theItemsOfAnAppfilterBecomeAMapAndTheFirstEntryWins() {
        val map = parse(
            """
            <resources>
              <item component="ComponentInfo{com.a/com.a.Main}" drawable="a_icon" />
              <item component="ComponentInfo{com.b/.Home}" drawable="b_icon" />
              <item component="ComponentInfo{com.a/com.a.Main}" drawable="a_other" />
              <item component="broken" drawable="x" />
              <item component="ComponentInfo{com.c/com.c.Main}" />
            </resources>
            """.trimIndent(),
        )
        assertEquals(mapOf("com.a/com.a.Main" to "a_icon", "com.b/com.b.Home" to "b_icon"), map.icons)
        assertFalse(map.dressesOthers)
    }

    @Test
    fun theBackgroundsTheOverlayAndTheScaleAreRead() {
        val map = parse(
            """
            <resources>
              <iconback img1="back1" img2="back2" />
              <iconupon img1="upon" />
              <scale factor="0.8" />
            </resources>
            """.trimIndent(),
        )
        assertEquals(listOf("back1", "back2"), map.backs)
        assertEquals("upon", map.upon)
        assertEquals(0.8f, map.scale, 0f)
        assertTrue(map.dressesOthers)
    }

    @Test
    fun theScaleIsKeptSane() {
        assertEquals(1.5f, parse("""<resources><scale factor="9" /></resources>""").scale, 0f)
        assertEquals(0.3f, parse("""<resources><scale factor="0" /></resources>""").scale, 0f)
        assertEquals(1f, parse("""<resources><scale factor="wide" /></resources>""").scale, 0f)
    }

    @Test
    fun anEmptyDocumentIsAnEmptyPack() {
        val map = parse("<resources/>")
        assertTrue(map.icons.isEmpty())
        assertTrue(map.backs.isEmpty())
    }

    @Test
    fun changingThePackMakesTheIconsLoadAgain() {
        val loader = IconLoader(ApplicationProvider.getApplicationContext())
        val before = loader.revision
        loader.usePack(null)
        assertEquals(before + 1, loader.revision)
    }
}
