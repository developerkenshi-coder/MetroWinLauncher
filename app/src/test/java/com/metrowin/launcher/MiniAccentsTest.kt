package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Test

/** The accent a mini app has of its own survives saving, and a bad entry in a restored file is dropped, not applied. */
class MiniAccentsTest {
    @Test
    fun theAccentsOfTheAppsAreSavedAndRead() {
        val mine = LauncherSettings(miniAccents = mapOf("todo" to 11, "mail" to 0))
        assertEquals(mapOf("todo" to 11, "mail" to 0), SettingsCodec.decode(SettingsCodec.encode(mine))!!.miniAccents)
    }

    @Test
    fun noAccentsIsTheDefault() {
        assertEquals(emptyMap<String, Int>(), SettingsCodec.decode(SettingsCodec.encode(LauncherSettings()))!!.miniAccents)
    }

    @Test
    fun anIndexOutsideThePaletteIsDropped() {
        val json = JSONObjectOf("""{"miniAccents":{"todo":3,"mail":99,"wallet":-1}}""")
        assertEquals(mapOf("todo" to 3), SettingsCodec.decode(json)!!.miniAccents)
    }

    private fun JSONObjectOf(s: String) = SettingsCodec.encode(LauncherSettings()).let { base ->
        org.json.JSONObject(base).apply { put("miniAccents", org.json.JSONObject(s).getJSONObject("miniAccents")) }.toString()
    }
}
