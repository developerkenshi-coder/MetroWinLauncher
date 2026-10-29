package com.metrowin.launcher

import android.provider.Settings
import com.metrowin.launcher.data.GlobalSearch
import com.metrowin.launcher.data.VoiceCommand
import com.metrowin.launcher.data.VoiceCommands
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {
    private fun app(label: String) = AppItem("id:$label", label, Launch.Action("x"))

    private val apps = listOf(app("Calendario"), app("Fotocamera"), app("Google Maps"), app("Maps Go"), app("Messaggi"), app("WhatsApp"))

    // The names of the settings in the tests are their ids; the keywords are English.
    private fun parse(heard: String) = VoiceCommands.parse(heard, apps) { it.id }

    private fun opened(heard: String): String? = (parse(heard) as? VoiceCommand.OpenApp)?.app?.label

    @Test
    fun anAppIsOpenedByItsNameInBothLanguages() {
        assertEquals("Fotocamera", opened("apri fotocamera"))
        assertEquals("Fotocamera", opened("Open Fotocamera"))
        assertEquals("WhatsApp", opened("avvia WhatsApp per favore"))
        assertEquals("Calendario", opened("ok apri il calendario"))
    }

    @Test
    fun anAppCanJustBeNamed() {
        assertEquals("WhatsApp", opened("whatsapp"))
        assertEquals("Messaggi", opened("messaggi"))
    }

    @Test
    fun theExactNameBeatsAMoreLooseMatch() {
        assertEquals("Google Maps", opened("apri google maps"))
        // A name that starts with the words outranks one that only has them inside.
        assertEquals("Maps Go", opened("apri maps"))
    }

    @Test
    fun accentsAndPunctuationDoNotMatter() {
        assertEquals("Calendario", opened("Apri... CALENDARIO!"))
    }

    @Test
    fun aPhraseCanAskTheWebToSearch() {
        assertEquals(VoiceCommand.Web("pizza napoletana"), parse("cerca pizza napoletana"))
        assertEquals(VoiceCommand.Web("best pizza near me"), parse("search for best pizza near me"))
        assertEquals(VoiceCommand.Web("meteo roma"), parse("cerca il meteo roma"))
    }

    @Test
    fun aBarePhraseThatIsNoAppGoesToTheWeb() {
        assertEquals(VoiceCommand.Web("capitale della francia"), parse("capitale della francia"))
    }

    @Test
    fun callingNamesAContact() {
        assertEquals(VoiceCommand.Call("anna"), parse("chiama Anna"))
        assertEquals(VoiceCommand.Call("mamma"), parse("telefona a mamma"))
        assertEquals(VoiceCommand.Call("john smith"), parse("call John Smith please"))
    }

    @Test
    fun aNumberSaidInDigitsIsDialedAndNotLookedForAmongTheNames() {
        assertEquals(VoiceCommand.CallNumber("3331234567"), parse("chiama 333 1234567"))
        assertEquals(VoiceCommand.CallNumber("0114567890"), parse("telefona a 011 456 7890"))
        // A short run of digits or a name with a digit in it is still a name.
        assertEquals(VoiceCommand.Call("42"), parse("chiama 42"))
        assertEquals(VoiceCommand.Call("ufficio 2"), parse("chiama ufficio 2"))
    }

    @Test
    fun everyHearingOfTheNameIsTriedWhenLookingForAContact() {
        val heard = listOf("chiama Mario", "chiama Maria", "apri fotocamera", "scrivi a Marco ciao")
        assertEquals(
            listOf("mario", "maria", "marco"),
            VoiceCommands.contactNames(heard, apps, { it.id }, first = "mario"),
        )
    }

    @Test
    fun theTorchHasOnOffAndToggle() {
        assertEquals(VoiceCommand.Torch(true), parse("accendi la torcia"))
        assertEquals(VoiceCommand.Torch(false), parse("spegni la torcia"))
        assertEquals(VoiceCommand.Torch(null), parse("torcia"))
        assertEquals(VoiceCommand.Torch(true), parse("turn on the flashlight"))
        assertEquals(VoiceCommand.Torch(false), parse("turn off the torch"))
    }

    @Test
    fun theLaunchersOwnCommands() {
        assertEquals(VoiceCommand.Weather, parse("meteo"))
        assertEquals(VoiceCommand.Weather, parse("che tempo fa"))
        assertEquals(VoiceCommand.Weather, parse("what's the weather"))
        assertEquals(VoiceCommand.LockScreen, parse("blocca lo schermo"))
        assertEquals(VoiceCommand.LockScreen, parse("lock the screen"))
        assertEquals(VoiceCommand.ActionCenter, parse("apri il centro notifiche"))
        assertEquals(VoiceCommand.AppList, parse("mostra tutte le app"))
        assertEquals(VoiceCommand.Start, parse("vai alla home"))
        assertEquals(VoiceCommand.LauncherSettings, parse("impostazioni del launcher"))
    }

    @Test
    fun aSettingOfAndroidIsFoundByItsKeywords() {
        val wifi = parse("apri wifi") as VoiceCommand.OpenSetting
        assertEquals("wifi", wifi.setting.id)
        assertEquals(Settings.ACTION_WIFI_SETTINGS, wifi.setting.action)
        assertEquals("bluetooth", (parse("open bluetooth") as VoiceCommand.OpenSetting).setting.id)
    }

    @Test
    fun openingSomethingThatIsNotThereSaysSo() {
        assertEquals(VoiceCommand.NotFound("zzzzzz"), parse("apri zzzzzz"))
    }

    @Test
    fun silenceOrFillerIsNotACommand() {
        assertEquals(VoiceCommand.Unclear, parse(""))
        assertEquals(VoiceCommand.Unclear, parse("   "))
        assertEquals(VoiceCommand.Unclear, parse("ok per favore"))
        assertEquals(VoiceCommand.Unclear, parse("cerca"))
        assertEquals(VoiceCommand.Unclear, parse("chiama"))
    }

    @Test
    fun aWordInsideAnotherIsNotACommand() {
        // "flash" inside "flashback" and "lock" inside "block" are not the torch or the lock.
        assertTrue(parse("flashback") !is VoiceCommand.Torch)
        assertTrue(parse("block party") !is VoiceCommand.LockScreen)
    }

    @Test
    fun normalizeKeepsLettersAndDigitsOnly() {
        assertEquals("l ora 24 h", VoiceCommands.normalize("L'ora, 24-H!"))
    }

    @Test
    fun findAppReturnsNullForNothing() {
        assertNull(VoiceCommands.findApp(apps, ""))
        assertNull(VoiceCommands.findApp(apps, "nonexistent"))
        assertEquals(GlobalSearch.systemSettings.first().id, "wifi")
    }
}
