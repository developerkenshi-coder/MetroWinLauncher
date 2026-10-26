package com.metrowin.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.IconPackRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What the launcher asks the system each time it comes back on screen, seen on a phone (vivo, Android 17): the system wrote an
 * error line for every empty list the launcher asked for (five, one for each icon pack action, on a phone with no pack), and
 * a warning that the back callback was not enabled although the application declares it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReturnToHomeNoiseTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun resolveInfo(pkg: String) = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.Theme"
            applicationInfo = context.applicationInfo
        }
    }

    // --- icon packs

    @Test
    fun theListIsNotAskedForAnActionNobodyAnswers() {
        val asked = mutableListOf<String>()
        val found = IconPackRepository.answering(
            IconPackRepository.ACTIONS,
            anyAnswer = { false },
            listAnswers = { asked += it; emptyList() },
        )
        assertTrue(found.isEmpty())
        assertEquals("no list for a phone with no icon pack", emptyList<String>(), asked)
    }

    @Test
    fun onlyTheActionsSomeoneAnswersAreListed() {
        val asked = mutableListOf<String>()
        val pack = resolveInfo("com.pack.one")
        val found = IconPackRepository.answering(
            IconPackRepository.ACTIONS,
            anyAnswer = { it == "com.novalauncher.THEME" },
            listAnswers = { asked += it; listOf(pack) },
        )
        assertEquals(listOf("com.novalauncher.THEME"), asked)
        assertEquals(listOf(pack), found)
    }

    @Test
    fun aFailingCheckFallsBackToAskingForTheList() {
        val pack = resolveInfo("com.pack.one")
        val found = IconPackRepository.answering(
            listOf("a", "b"),
            anyAnswer = { throw SecurityException("no visibility") },
            listAnswers = { listOf(pack) },
        )
        assertEquals("both lists are asked for", listOf(pack, pack), found)
    }

    @Test
    fun aFailingListIsEmptyAndDoesNotStopTheOthers() {
        val pack = resolveInfo("com.pack.two")
        val found = IconPackRepository.answering(
            listOf("a", "b"),
            anyAnswer = { true },
            listAnswers = { if (it == "a") throw IllegalStateException("gone") else listOf(pack) },
        )
        assertEquals(listOf(pack), found)
    }

    @Test
    fun withThePackManagerNoPackMeansNoPacks() {
        assertTrue(IconPackRepository(context).installed().isEmpty())
    }

    @Test
    fun withThePackManagerAnInstalledPackIsStillFound() {
        val action = IconPackRepository.ACTIONS.first()
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(action), resolveInfo(context.packageName))
        val packs = IconPackRepository(context).installed()
        assertEquals(listOf(context.packageName), packs.map { it.packageName })
    }

    // --- back

    @Test
    fun theHomeActivityDeclaresTheBackCallbackItself() {
        // The flag on the application alone was not honoured for the window of the activity on that phone.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val tag = Regex("<activity[^>]*android:name=\"\\.MainActivity\"[^>]*>").find(manifest)?.value
        assertTrue("MainActivity is declared", tag != null)
        assertTrue(tag!!.contains("android:enableOnBackInvokedCallback=\"true\""))
        val application = Regex("<application[^>]*>").find(manifest)!!.value
        assertTrue("and the application keeps it for the other windows", application.contains("android:enableOnBackInvokedCallback=\"true\""))
    }
}
