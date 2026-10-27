package com.metrowin.launcher.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.metrowin.launcher.data.BluetoothPeer
import com.metrowin.launcher.data.ConnectionAccess
import com.metrowin.launcher.data.ConnectionPanel
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.WifiNetwork
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ConnectionListUiTest : ScreenshotBase() {
    private val opened = mutableListOf<QuickAction>()
    private val allowed = mutableListOf<QuickAction>()
    private val pressed = mutableListOf<QuickAction>()

    private val around = ConnectionPanel(
        wifiName = "Casa 5G",
        networks = listOf(
            WifiNetwork("Casa 5G", 4, secured = true, connected = true),
            WifiNetwork("Bar Centrale", 2, secured = false),
            WifiNetwork("Vicini", 1, secured = true),
        ),
        devices = listOf(
            BluetoothPeer("Cuffie Sony", "00:01", connected = true),
            BluetoothPeer("Auto", "00:02", connected = false),
        ),
    )

    @Composable
    private fun Center(connections: ConnectionPanel, states: Map<QuickAction, Boolean?> = mapOf(QuickAction.Wifi to true, QuickAction.Bluetooth to true)) {
        ActionCenter(
            slots = QuickAction.defaults, states = states, notifications = emptyList(), appsByPackage = emptyMap(),
            hasNotificationAccess = true, panel = rememberPullPanel(initialProgress = 1f),
            onAction = { pressed += it }, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {},
            onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {},
            connections = connections, onConnectionList = { opened += it }, onAllowConnections = { allowed += it },
        )
    }

    @Test
    fun theButtonsNameTheNetworkAndTheDeviceTheyAreOn() {
        show { Center(around) }
        advance(300)
        capture("action_center_connection_captions")
        compose.onNodeWithTag("quick:wifi").assertExists()
        compose.onNode(hasText("Casa 5G")).assertExists()
        compose.onNode(hasText("Cuffie Sony")).assertExists()
    }

    @Test
    fun theButtonsKeepTheirNameWhenTheyAreOff() {
        show { Center(around, states = mapOf(QuickAction.Wifi to false, QuickAction.Bluetooth to false)) }
        advance(300)
        compose.onNode(hasText("Casa 5G")).assertDoesNotExist()
        compose.onNode(hasText("Wi-Fi")).assertExists()
    }

    @Test
    fun aLongPressOnWifiListsTheNetworksAround() {
        show { Center(around) }
        advance(300)
        compose.onNodeWithTag("quick:wifi").performTouchInput { longClick() }
        advance(600)
        capture("action_center_wifi_list")
        assertEquals(listOf(QuickAction.Wifi), opened)
        compose.onNodeWithTag("connections:wifi").assertExists()
        compose.onNodeWithTag("network:Bar Centrale").assertExists()
        compose.onNodeWithTag("network:Vicini").assertExists()
    }

    @Test
    fun aLongPressOnBluetoothListsTheConnectedAndThePairedDevices() {
        show { Center(around) }
        advance(300)
        compose.onNodeWithTag("quick:bluetooth").performTouchInput { longClick() }
        advance(600)
        capture("action_center_bluetooth_list")
        assertEquals(listOf(QuickAction.Bluetooth), opened)
        compose.onNodeWithTag("device:00:01").assertExists()
        compose.onNodeWithTag("device:00:02").assertExists()
    }

    @Test
    fun aShortPressStillOpensTheSystemScreenAndTheHeadingClosesTheList() {
        show { Center(around) }
        advance(300)
        compose.onNodeWithTag("quick:wifi").performClick()
        assertEquals(listOf(QuickAction.Wifi), pressed)
        assertEquals(emptyList<QuickAction>(), opened)
        compose.onNodeWithTag("quick:wifi").performTouchInput { longClick() }
        advance(600)
        compose.onNodeWithTag("connections:close").performClick()
        advance(600)
        compose.onNodeWithTag("connections:wifi").assertDoesNotExist()
    }

    @Test
    fun withoutTheAccessTheListSaysSoAndOffersToAskForIt() {
        show { Center(ConnectionPanel(wifiAccess = ConnectionAccess.NeedsPermission)) }
        advance(300)
        compose.onNodeWithTag("quick:wifi").performTouchInput { longClick() }
        advance(600)
        capture("action_center_wifi_needs_access")
        compose.onNodeWithTag("connections:allow").performClick()
        assertEquals(listOf(QuickAction.Wifi), allowed)
    }
}
