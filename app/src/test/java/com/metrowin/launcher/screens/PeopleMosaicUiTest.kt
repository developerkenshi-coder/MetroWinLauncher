package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.people.PeopleBook
import com.metrowin.launcher.people.PeopleMosaic
import com.metrowin.launcher.people.Person
import com.metrowin.launcher.people.loadPhoto
import com.metrowin.launcher.people.mosaicPool
import com.metrowin.launcher.people.nextFace
import com.metrowin.launcher.ui.anim.LocalPowerSaving
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The mosaic of the People hub: only faces with a photo, tiles that turn into other faces, and that they stand still when they should. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class PeopleMosaicUiTest : ScreenshotBase() {
    private fun person(name: String, photo: Boolean = true, starred: Boolean = false) =
        Person(name, name, starred, if (photo) "content://test/$name" else null, emptyList(), emptyList(), "", 1, emptySet(), null, PeopleBook.haystackOf(name, emptyList(), emptyList(), ""))

    private val ten = ('A'..'J').map { person("Person $it") }

    private fun names() = compose.onAllNodesWithTag("people:mosaic:tile").fetchSemanticsNodes()
        .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single() }

    @Test
    fun theTilesHoldOnlyPeopleWithAPhotoFavouritesFirst() {
        val people = listOf(person("A", photo = false, starred = true), person("B"), person("C", starred = true), person("D"))
        val pool = mosaicPool(people, emptySet(), listOf(people[3]))
        assertEquals(listOf("C", "D", "B"), pool.map { it.name })
    }

    @Test
    fun nextFaceSkipsWhatIsOnShowAndWrapsRound() {
        assertEquals(6, nextFace(listOf(0, 1, 2, 3, 4, 5), 10, 6))
        assertEquals(0, nextFace(listOf(6, 1, 2, 3, 4, 5), 7, 6))
    }

    @Test
    fun aPhotoIsReadOffTheMainThreadAndKept() {
        val file = File(context.cacheDir, "face.png").apply { outputStream().use { Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val uri = android.net.Uri.fromFile(file).toString()
        assertNotNull(runBlocking { loadPhoto(context, uri) })
        file.delete()
        assertNotNull("the second read comes from the cache", runBlocking { loadPhoto(context, uri) })
    }

    @Test
    fun sixTilesShowAndTurnIntoOtherFaces() {
        show { PeopleMosaic(ten, {}) }
        advance(500)
        compose.onAllNodesWithTag("people:mosaic:tile").assertCountEquals(6)
        val before = names()
        assertEquals((0..5).map { "Person ${'A' + it}" }, before)
        repeat(8) { Thread.sleep(50); advance(1_000) }
        val after = names()
        assertEquals(6, after.size)
        assertTrue("some tile turned into another face: $after", after != before)
    }

    @Test
    fun withReducedMotionOrSavingBatteryNothingTurns() {
        show(reduceMotion = true) { PeopleMosaic(ten, {}) }
        repeat(10) { Thread.sleep(20); advance(1_000) }
        assertEquals((0..5).map { "Person ${'A' + it}" }, names())
    }

    @Test
    fun whileSavingBatteryNothingTurns() {
        show { CompositionLocalProvider(LocalPowerSaving provides true) { PeopleMosaic(ten, {}) } }
        repeat(10) { Thread.sleep(20); advance(1_000) }
        assertEquals((0..5).map { "Person ${'A' + it}" }, names())
    }

    @Test
    fun fewPeopleFillOnlyTheirTilesAndATouchOpensThePerson() {
        var opened: Person? = null
        show { PeopleMosaic(ten.take(2), { opened = it }) }
        advance(500)
        compose.onAllNodesWithTag("people:mosaic:tile").assertCountEquals(2)
        compose.onNode(hasContentDescription("Person B")).performClick()
        advance(500)
        assertEquals("Person B", opened?.name)
    }
}
