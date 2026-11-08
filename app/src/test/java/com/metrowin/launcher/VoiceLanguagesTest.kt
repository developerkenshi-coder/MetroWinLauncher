package com.metrowin.launcher

import com.metrowin.launcher.data.VoiceCommand
import com.metrowin.launcher.data.VoiceCommands
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import org.junit.Assert.assertEquals
import org.junit.Test

/** The verbs of the commands in German, Spanish, French and Portuguese, next to the Italian and English ones of VoiceCommandsTest. */
class VoiceLanguagesTest {
    private val apps = listOf(AppItem("id:Kamera", "Kamera", Launch.Action("x")), AppItem("id:Maps", "Maps", Launch.Action("x")))
    private fun parse(heard: String) = VoiceCommands.parse(heard, apps) { it.id }

    private fun assertOpens(vararg phrases: String) = phrases.forEach {
        assertEquals(it, "Kamera", (parse(it) as? VoiceCommand.OpenApp)?.app?.label)
    }

    @Test
    fun anAppIsOpenedInEveryLanguage() = assertOpens(
        "Öffne Kamera", "starte Kamera", "abre Kamera", "abrir la Kamera", "ouvre Kamera", "lance Kamera", "abre a Kamera", "inicia Kamera", "mostra Kamera",
    )

    @Test
    fun aContactIsCalled() {
        listOf("ruf Anna", "llama a Anna", "appelle Anna", "liga para Anna", "ligar para Anna").forEach {
            assertEquals(it, VoiceCommand.Call("anna"), parse(it))
        }
        assertEquals(VoiceCommand.CallNumber("3331234567"), parse("llama a 333 1234567"))
    }

    @Test
    fun theWebIsSearched() {
        listOf("suche nach Pizza", "busca pizza", "cherche pizza", "pesquisa pizza", "procura pizza").forEach {
            assertEquals(it, VoiceCommand.Web("pizza"), parse(it))
        }
    }

    @Test
    fun theTorchIsSwitchedInEveryLanguage() {
        assertEquals(VoiceCommand.Torch(false), parse("Taschenlampe ausschalten"))
        assertEquals(VoiceCommand.Torch(true), parse("enciende la linterna"))
        assertEquals(VoiceCommand.Torch(false), parse("apaga la linterna"))
        assertEquals(VoiceCommand.Torch(true), parse("allume la lampe de poche"))
        assertEquals(VoiceCommand.Torch(false), parse("eteins la lampe torche"))
        assertEquals(VoiceCommand.Torch(true), parse("ligar a lanterna"))
        assertEquals(VoiceCommand.Torch(false), parse("desliga a lanterna"))
    }

    @Test
    fun theWeatherIsAskedInEveryLanguage() {
        listOf("Wetter", "clima", "météo", "previsão do tempo", "previsiones").forEach { assertEquals(it, VoiceCommand.Weather, parse(it)) }
    }
}
