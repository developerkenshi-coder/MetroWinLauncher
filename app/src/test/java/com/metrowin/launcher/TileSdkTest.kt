package com.metrowin.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.tilesdk.MetroTile
import com.metrowin.launcher.tilesdk.MetroTileResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the launcher answers in this test: set by each test, read by [Answering]. */
private object Script {
    var answer: Bundle? = Bundle().apply { putBoolean("accepted", true) }
    val calls = ArrayList<Triple<String, String?, Bundle?>>()
}

/** A provider that answers as the launcher does (the real one only knows its caller on a phone) and records what the SDK asked. */
class Answering : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        Script.calls += Triple(method, arg, extras?.let { Bundle(it) })
        return Script.answer
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** The SDK of docs/progetti/tile-sdk.md: what it sends, how it reads the answer, and that a missing launcher is an answer, not a crash. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TileSdkTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun reset() {
        Script.calls.clear()
        Script.answer = Bundle().apply { putBoolean("accepted", true) }
    }

    private fun installed() { Robolectric.buildContentProvider(Answering::class.java).create(MetroTile.AUTHORITY) }

    @Test
    fun thePublishCallCarriesTheThreeFieldsAndIsAccepted() {
        installed()
        assertEquals(MetroTileResult.Accepted, MetroTile.publish(context, "Inbox", "3 new messages", 3))
        val (method, _, extras) = Script.calls.single()
        assertEquals("publish", method)
        assertEquals("Inbox", extras!!.getString("title"))
        assertEquals("3 new messages", extras.getString("text"))
        assertEquals(3, extras.getInt("count"))
    }

    @Test
    fun textIsCutAndTheNumberKeptInRangeBeforeItIsSent() {
        installed()
        MetroTile.publish(context, "t".repeat(500), "x".repeat(500), 1_000_000)
        val extras = Script.calls.single().third!!
        assertEquals(MetroTile.MAX_TITLE, extras.getString("title")!!.length)
        assertEquals(MetroTile.MAX_TEXT, extras.getString("text")!!.length)
        assertEquals(MetroTile.MAX_COUNT, extras.getInt("count"))
        Script.calls.clear()
        MetroTile.publish(context, null, null, -5)
        val none = Script.calls.single().third!!
        assertEquals("", none.getString("title"))
        assertEquals(0, none.getInt("count"))
    }

    @Test
    fun aRefusalComesBackWithItsReason() {
        installed()
        Script.answer = Bundle().apply { putBoolean("accepted", false); putString("reason", "turned off by the user") }
        assertEquals(MetroTileResult.Refused("turned off by the user"), MetroTile.publish(context, "a", "b"))
        Script.answer = Bundle().apply { putBoolean("accepted", false); putString("reason", "too fast") }
        assertEquals(MetroTileResult.Refused("too fast"), MetroTile.clear(context))
        assertEquals("clear", Script.calls.last().first)
    }

    @Test
    fun theVersionIsReadFromTheAnswer() {
        installed()
        Script.answer = Bundle().apply { putBoolean("accepted", true); putInt("version", 1) }
        assertEquals(1, MetroTile.version(context))
        assertEquals("version", Script.calls.single().first)
    }

    @Test
    fun withoutTheLauncherEveryCallIsNotInstalledAndNothingCrashes() {
        // No provider is registered for this authority: "Unknown URI" (the real launcher is in the manifest of this test app, so the default one answers).
        val nobody = "com.nobody.tiles"
        assertEquals(MetroTileResult.NotInstalled, MetroTile.publish(context, "a", "b", 1, nobody))
        assertEquals(MetroTileResult.NotInstalled, MetroTile.clear(context, nobody))
        assertEquals(null, MetroTile.version(context, nobody))
        assertTrue(!MetroTile.isInstalled(context, nobody))
    }

    @Test
    fun aDifferentAuthorityIsForADebugBuildWithAnIdSuffix() {
        Robolectric.buildContentProvider(Answering::class.java).create("com.metrowin.launcher.test.tiles")
        assertEquals(MetroTileResult.Accepted, MetroTile.publish(context, "a", "b", authority = "com.metrowin.launcher.test.tiles"))
        assertEquals("publish", Script.calls.single().first)
    }

    @Test
    fun theRealProviderRefusesACallerItDoesNotKnow() {
        // On a phone Android names the caller; in this test nobody does, and the answer is a refusal with its reason, not a crash.
        assertEquals(MetroTileResult.Refused("unknown caller"), MetroTile.publish(context, "a", "b"))
    }
}
