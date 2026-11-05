package com.metrowin.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke tests on a device: every screen opens and stays up. They are the safety net for the big refactors. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    // Without them the dialer opens the system permission dialog, which covers it.
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.READ_CALENDAR,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun open(name: String, extras: Intent.() -> Unit = {}): ActivityScenario<*> {
        val intent = Intent().setClassName(context, "com.metrowin.launcher.$name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply(extras)
        return ActivityScenario.launch<android.app.Activity>(intent)
    }

    /** Opens the screen, lets it draw for a moment and checks that it did not crash or finish by itself. */
    private fun staysUp(name: String) {
        open(name).use { scenario ->
            Thread.sleep(1500)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test fun home() = staysUp("MainActivity")
    @Test fun mail() = staysUp("mail.MailActivity")
    @Test fun calendar() = staysUp("calendar.CalendarActivity")
    @Test fun dialer() = staysUp("dialer.DialerActivity")
    @Test fun notes() = staysUp("notes.NotesActivity")
    @Test fun files() = staysUp("files.FilesActivity")
    @Test fun wallet() = staysUp("wallet.WalletActivity")
    @Test fun todo() = staysUp("todo.TodoActivity")
    @Test fun calculator() = staysUp("calculator.CalculatorActivity")
    @Test fun dataSense() = staysUp("datasense.DataSenseActivity")

    @Test fun keypadWritesTheNumber() {
        open("dialer.DialerActivity") { putExtra("page", 3) }.use {
            compose.onNodeWithTag("dialer:call").assertIsNotEnabled()
            "123".forEach { compose.onNodeWithTag("dial:key:$it").performClick() }
            compose.onNodeWithTag("dialer:number").assertTextEquals("123")
            compose.onNodeWithTag("dialer:call").assertIsEnabled()
            compose.onNodeWithTag("dialer:delete").performClick()
            compose.onNodeWithTag("dialer:number").assertTextEquals("12")
        }
    }

    @Test fun startShowsTheTiles() {
        open("MainActivity").use {
            fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
            // A fresh install starts with the tour.
            compose.waitUntil(40_000) { shown("tutorial:skip") || shown("tile:stock:phone") }
            if (shown("tutorial:skip")) compose.onNodeWithTag("tutorial:skip").performClick()
            compose.waitUntil(40_000) { shown("tile:stock:phone") }
        }
    }
}
