package com.metrowin.launcher

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.GestureAction
import com.metrowin.launcher.data.LevelChange
import com.metrowin.launcher.data.RingerMode
import com.metrowin.launcher.data.VoiceActions
import com.metrowin.launcher.data.VoiceCommand
import com.metrowin.launcher.data.VoiceCommands
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The voice commands of the second round: volume, brightness, ringer, directions, text messages and system actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceMoreTest {
    private fun app(label: String) = AppItem("id:$label", label, Launch.Action("x"))
    private val apps = listOf(app("Messaggi"), app("Fotocamera"), app("Maps"), app("Text Editor"), app("Recenti Foto"))
    private fun parse(heard: String) = VoiceCommands.parse(heard, apps) { it.id }

    // --- the volume and the brightness

    @Test
    fun theVolumeGoesUpAndDownInBothLanguages() {
        listOf("alza il volume", "aumenta il volume", "volume su", "turn up the volume", "volume up", "raise the volume", "volume più alto").forEach {
            assertEquals(it, VoiceCommand.Volume(LevelChange.Up), parse(it))
        }
        listOf("abbassa il volume", "diminuisci il volume", "volume giù", "turn down the volume", "volume down", "lower the volume").forEach {
            assertEquals(it, VoiceCommand.Volume(LevelChange.Down), parse(it))
        }
    }

    @Test
    fun theVolumeCanBeMaximumMinimumOrAPercentage() {
        assertEquals(VoiceCommand.Volume(LevelChange.Max), parse("volume al massimo"))
        assertEquals(VoiceCommand.Volume(LevelChange.Max), parse("max volume"))
        assertEquals(VoiceCommand.Volume(LevelChange.Min), parse("volume al minimo"))
        assertEquals(VoiceCommand.Volume(LevelChange.Min), parse("mute the volume"))
        assertEquals(VoiceCommand.Volume(LevelChange.To(50)), parse("volume al 50 per cento"))
        assertEquals(VoiceCommand.Volume(LevelChange.To(30)), parse("imposta il volume al 30%"))
        assertEquals(VoiceCommand.Volume(LevelChange.To(70)), parse("set volume to 70 percent"))
        assertEquals(VoiceCommand.Volume(LevelChange.To(0)), parse("volume 0"))
        assertEquals("a number above a hundred is not a level", VoiceCommand.Volume(LevelChange.Up), parse("alza il volume 150"))
    }

    @Test
    fun theBrightnessWorksTheSameWay() {
        assertEquals(VoiceCommand.Brightness(LevelChange.Up), parse("alza la luminosità"))
        assertEquals(VoiceCommand.Brightness(LevelChange.Down), parse("abbassa la luminosità"))
        assertEquals(VoiceCommand.Brightness(LevelChange.Max), parse("luminosità al massimo"))
        assertEquals(VoiceCommand.Brightness(LevelChange.To(40)), parse("luminosità al 40 per cento"))
        assertEquals(VoiceCommand.Brightness(LevelChange.Down), parse("turn down the brightness"))
        assertEquals(VoiceCommand.Brightness(LevelChange.Max), parse("brightness to maximum"))
    }

    @Test
    fun aVolumeWordWithoutWhatToDoIsNotACommandOfItsOwn() {
        assertFalse(parse("volume") is VoiceCommand.Volume)
        assertFalse(parse("luminosità") is VoiceCommand.Brightness)
    }

    @Test
    fun aStepMovesAFifthAndStaysInside() {
        assertEquals(0.7f, LevelChange.Up.apply(0.5f), 1e-6f)
        assertEquals(0.3f, LevelChange.Down.apply(0.5f), 1e-6f)
        assertEquals(1f, LevelChange.Up.apply(0.9f), 0f)
        assertEquals(0f, LevelChange.Down.apply(0.1f), 0f)
        assertEquals(1f, LevelChange.Max.apply(0.2f), 0f)
        assertEquals(0f, LevelChange.Min.apply(0.2f), 0f)
        assertEquals(0.42f, LevelChange.To(42).apply(0.9f), 1e-6f)
        assertEquals(1f, LevelChange.To(400).apply(0.1f), 0f)
    }

    // --- the ringer

    @Test
    fun theRingerIsSilencedVibratedOrBroughtBack() {
        listOf("modalità silenziosa", "silenzioso", "silent mode", "metti in silenzioso", "metti in silenzio il telefono").forEach {
            assertEquals(it, VoiceCommand.Ringer(RingerMode.Silent), parse(it))
        }
        listOf("vibrazione", "solo vibrazione", "vibrate", "metti in vibrazione").forEach {
            assertEquals(it, VoiceCommand.Ringer(RingerMode.Vibrate), parse(it))
        }
        listOf("attiva la suoneria", "suoneria attiva", "turn on the ringer", "sound on", "disattiva il silenzioso", "esci dal silenzioso").forEach {
            assertEquals(it, VoiceCommand.Ringer(RingerMode.Normal), parse(it))
        }
    }

    // --- directions

    @Test
    fun directionsAreAskedInBothLanguages() {
        assertEquals(VoiceCommand.Navigate("roma"), parse("portami a Roma"))
        assertEquals(VoiceCommand.Navigate("stazione centrale"), parse("indicazioni per la stazione centrale"))
        assertEquals(VoiceCommand.Navigate("piazza duomo milano"), parse("naviga verso piazza duomo milano"))
        assertEquals(VoiceCommand.Navigate("airport"), parse("take me to the airport"))
        assertTrue(parse("take me to the airport") is VoiceCommand.Navigate)
        assertTrue(parse("how do I get to Times Square") is VoiceCommand.Navigate)
        assertTrue(parse("navigate to home") is VoiceCommand.Navigate)
        assertFalse("nothing to go to", parse("portami a") is VoiceCommand.Navigate)
    }

    @Test
    fun theMapsAppIsStillOpenedByName() {
        assertEquals("Maps", (parse("apri maps") as VoiceCommand.OpenApp).app.label)
    }

    @Test
    fun theAddressesForDirectionsAreEncodedAndTriedInOrder() {
        val uris = VoiceActions.navigationUris("Via Roma 1, Torino")
        assertEquals("google.navigation:q=Via%20Roma%201%2C%20Torino", uris[0])
        assertEquals("geo:0,0?q=Via%20Roma%201%2C%20Torino", uris[1])
    }

    // --- text messages

    @Test
    fun aMessageNamesTheContactAndKeepsTheWordsAsSaid() {
        val command = parse("Scrivi a Marco sono già in ritardo, grazie") as VoiceCommand.Message
        assertEquals("marco", command.name)
        assertEquals("sono già in ritardo grazie", command.text)
    }

    @Test
    fun aConnectorSplitsALongNameFromTheMessage() {
        val command = parse("manda un messaggio a Maria Rossi dicendo che arrivo tra dieci minuti") as VoiceCommand.Message
        assertEquals("maria rossi", command.name)
        assertEquals("arrivo tra dieci minuti", command.text)
        val english = parse("send a message to Sam saying I will be late") as VoiceCommand.Message
        assertEquals("sam", english.name)
        assertEquals("I will be late", english.text)
    }

    @Test
    fun aMessageCanBeJustForSomeoneAndTheFillersAreSkipped() {
        val command = parse("ok per favore manda un sms a Anna") as VoiceCommand.Message
        assertEquals("anna", command.name)
        assertEquals("", command.text)
        assertEquals("luca", (parse("scrivi al Luca ciao") as VoiceCommand.Message).name)
    }

    @Test
    fun aWordThatOnlyLooksLikeAMessageIsNot() {
        assertEquals("Messaggi", (parse("messaggi") as VoiceCommand.OpenApp).app.label)
        assertEquals("Text Editor", (parse("text editor") as VoiceCommand.OpenApp).app.label)
        assertFalse(parse("scrivi") is VoiceCommand.Message)
        assertFalse(parse("scrivi a") is VoiceCommand.Message)
        assertFalse("opening an app that has the word is not writing", parse("apri messaggi") is VoiceCommand.Message)
    }

    @Test
    fun theMessageIsWrittenInTheMessagesAppAndNotSent() {
        val intent = VoiceActions.smsIntent("+39 333 123 4567", "ciao")
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto", intent.data!!.scheme)
        assertEquals("+393331234567", intent.data!!.schemeSpecificPart)
        assertEquals("ciao", intent.getStringExtra("sms_body"))
    }

    // --- system actions

    @Test
    fun theSystemActionsAreAskedInBothLanguages() {
        assertEquals(VoiceCommand.System(GestureAction.Screenshot), parse("fai uno screenshot"))
        assertEquals(VoiceCommand.System(GestureAction.Screenshot), parse("take a screenshot"))
        assertEquals(VoiceCommand.System(GestureAction.PowerMenu), parse("menu di spegnimento"))
        assertEquals(VoiceCommand.System(GestureAction.PowerMenu), parse("spegni il telefono"))
        assertEquals(VoiceCommand.System(GestureAction.PowerMenu), parse("power menu"))
        assertEquals(VoiceCommand.System(GestureAction.SplitScreen), parse("schermo diviso"))
        assertEquals(VoiceCommand.System(GestureAction.Recents), parse("app recenti"))
        assertEquals(VoiceCommand.System(GestureAction.Recents), parse("show recent apps"))
        assertEquals(VoiceCommand.System(GestureAction.AndroidNotifications), parse("notifiche di android"))
        assertEquals(VoiceCommand.System(GestureAction.AndroidQuickSettings), parse("impostazioni rapide di android"))
    }

    @Test
    fun theEarlierCommandsAreNotTaken() {
        assertEquals(VoiceCommand.ActionCenter, parse("notifiche"))
        assertEquals(VoiceCommand.Torch(false), parse("spegni la torcia"))
        assertEquals(VoiceCommand.Torch(true), parse("accendi la torcia"))
        assertEquals(VoiceCommand.Weather, parse("meteo"))
        assertEquals(VoiceCommand.LockScreen, parse("blocca lo schermo"))
        assertTrue(parse("apri impostazioni rapide") !is VoiceCommand.Unclear)
        assertEquals("Fotocamera", (parse("apri fotocamera") as VoiceCommand.OpenApp).app.label)
        assertNull((parse("chiama anna") as? VoiceCommand.Message))
    }

    // --- what the view model does with them

    @Test
    fun aSpokenVolumeMovesTheMediaVolume() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val audio = application.getSystemService(android.media.AudioManager::class.java)
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val vm = LauncherViewModel(application)
        shadowOf(Looper.getMainLooper()).idle()
        vm.changeVolume(LevelChange.To(100))
        assertEquals(max, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        vm.changeVolume(LevelChange.Min)
        assertEquals(0, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        vm.changeVolume(LevelChange.Up)
        assertTrue(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) > 0)
    }

    @Test
    fun aSpokenSilenceWithoutTheAccessOpensTheScreenThatGivesIt() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val vm = LauncherViewModel(application)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("vibrating needs no access", vm.trySetRinger(RingerMode.Vibrate))
        shadowOf(application).clearNextStartedActivities()
        shadowOf(application.getSystemService(android.app.NotificationManager::class.java)).setNotificationPolicyAccessGranted(false)
        assertFalse(vm.trySetRinger(RingerMode.Silent))
        assertEquals(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, shadowOf(application).nextStartedActivity.action)
        shadowOf(application.getSystemService(android.app.NotificationManager::class.java)).setNotificationPolicyAccessGranted(true)
        assertTrue(vm.trySetRinger(RingerMode.Silent))
    }

    @Test
    fun aSpokenBrightnessWithoutTheAccessOpensTheScreenThatGivesIt() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val vm = LauncherViewModel(application)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(application).clearNextStartedActivities()
        // The test phone does not let the launcher write the settings.
        if (!android.provider.Settings.System.canWrite(application)) {
            assertFalse(vm.changeBrightness(LevelChange.Up))
            assertEquals(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, shadowOf(application).nextStartedActivity.action)
        }
    }

    @Test
    fun directionsAndMessagesStartTheirApps() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val vm = LauncherViewModel(application)
        shadowOf(Looper.getMainLooper()).idle()
        vm.navigate("Roma")
        val maps = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, maps.action)
        assertEquals("google.navigation:q=Roma", maps.dataString)
        vm.composeMessage("+39 333 1234567", "ciao")
        val sms = shadowOf(application).nextStartedActivity
        assertEquals("+393331234567", sms.data!!.schemeSpecificPart)
        assertEquals("ciao", sms.getStringExtra("sms_body"))
    }
}
