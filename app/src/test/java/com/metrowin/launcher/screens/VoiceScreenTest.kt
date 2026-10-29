package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.voice.VoiceScreen
import com.metrowin.launcher.ui.voice.VoiceUiState
import androidx.compose.foundation.lazy.rememberLazyListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The voice screen in each of its states, the microphone in the search box, and the settings page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class VoiceScreenTest : ScreenshotBase() {
    private var retried = 0
    private var cancelled = 0
    private val searched = mutableListOf<String>()

    private fun showState(state: VoiceUiState) = show {
        VoiceScreen(state, onRetry = { retried++ }, onCancel = { cancelled++ }, onSearchWeb = { searched += it })
    }

    @Test
    fun listeningShowsTheHintUntilSomethingIsHeard() {
        showState(VoiceUiState.Listening())
        advance(200)
        capture("voice_listening")
        compose.onNodeWithTag("voice:title").assertExists()
        compose.onNodeWithTag("voice:hint").assertExists()
        compose.onAllNodesWithTag("voice:heard").assertCountEquals(0)
    }

    @Test
    fun theWordsHeardSoFarReplaceTheHint() {
        showState(VoiceUiState.Listening(heard = "apri mappe", level = 0.6f))
        advance(300)
        capture("voice_heard")
        compose.onNodeWithTag("voice:heard").assertExists()
        compose.onAllNodesWithTag("voice:hint").assertCountEquals(0)
    }

    @Test
    fun theUnderstoodPhraseIsShown() {
        showState(VoiceUiState.Understood("accendi la torcia"))
        advance(200)
        compose.onNodeWithTag("voice:heard").assertExists()
    }

    @Test
    fun aProblemOffersToTryAgainAndToSearchTheWebForWhatWasNotFound() {
        showState(VoiceUiState.Problem(R.string.voice_not_found, "zzz", webQuery = "zzz"))
        advance(200)
        capture("voice_problem")
        compose.onNodeWithTag("voice:message").assertExists()
        compose.onNodeWithTag("voice:retry").performClick()
        compose.onNodeWithTag("voice:web").performClick()
        assertEquals(1, retried)
        assertEquals(listOf("zzz"), searched)
    }

    @Test
    fun aFinalProblemOffersNoRetry() {
        showState(VoiceUiState.Problem(R.string.voice_gave_up, final = true))
        advance(200)
        compose.onAllNodesWithTag("voice:retry").assertCountEquals(0)
        compose.onNodeWithTag("voice:mic").performClick()
        assertEquals(0, retried)
    }

    @Test
    fun aProblemWithNothingToSearchHasNoWebButton() {
        showState(VoiceUiState.Problem(R.string.voice_nothing))
        advance(200)
        compose.onAllNodesWithTag("voice:web").assertCountEquals(0)
    }

    @Test
    fun cancelClosesAndTheMicrophoneOnlyRetriesWhenNotListening() {
        showState(VoiceUiState.Listening())
        advance(200)
        compose.onNodeWithTag("voice:mic").performClick()
        assertEquals("listening already: the microphone does nothing", 0, retried)
        compose.onNodeWithTag("voice:cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun theMicrophoneOfAProblemTriesAgain() {
        showState(VoiceUiState.Problem(R.string.voice_nothing))
        advance(200)
        compose.onNodeWithTag("voice:mic").performClick()
        assertEquals(1, retried)
    }

    // --- the microphone in the search box

    private fun app(label: String) = AppItem("app:$label", label, Launch.Action("x"))

    @Composable
    private fun List(query: String, onVoice: (() -> Unit)?) {
        val model = remember { AppListModel(AppSections.group(AppSections.sort(listOf(app("Alpha"))))) }
        AppListScreen(
            model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
            onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
            query = query, onQueryChange = {}, results = emptyList(), onVoice = onVoice,
        )
    }

    @Test
    fun theSearchBoxHasAMicrophoneThatStartsTheVoice() {
        var asked = 0
        show { List("", onVoice = { asked++ }) }
        advance(1_000)
        capture("search_microphone")
        compose.onNodeWithTag("search:voice").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun theMicrophoneIsGoneWhileTypingOrWhenSwitchedOff() {
        show { List("al", onVoice = { }) }
        advance(1_000)
        compose.onAllNodesWithTag("search:voice").assertCountEquals(0)
    }

    @Test
    fun withoutAHandlerThereIsNoMicrophone() {
        show { List("", onVoice = null) }
        advance(1_000)
        compose.onAllNodesWithTag("search:voice").assertCountEquals(0)
    }

    // --- the settings page

    private var current = LauncherSettings()
    private var tried = 0

    @Composable
    private fun Page() {
        var settings by remember { mutableStateOf(current) }
        val away = remember { Animatable(0f) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                onTryVoice = { tried++ },
            ),
            section = SettingsSection.Voice,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    @Test
    fun theVoicePageHasItsSwitchesATestButtonAndExamples() {
        show { Page() }
        advance(500)
        capture("settings_voice")
        compose.onNodeWithTag("voice:button").performClick()
        advance(100)
        assertFalse(current.voiceButton)
        compose.onNodeWithTag("voice:offline").performClick()
        advance(100)
        assertFalse(current.voiceOffline)
        compose.onNodeWithTag("voice:try").performClick()
        assertEquals(1, tried)
        compose.onAllNodesWithTag("voice:example").assertCountEquals(11)
        assertTrue(LauncherSettings().voiceButton && LauncherSettings().voiceOffline)
    }
}
