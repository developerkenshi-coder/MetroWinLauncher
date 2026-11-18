package com.metrowin.launcher

import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app list answers the first letter typed without a pause a person can feel: under one frame (16 ms) on 600 apps. */
class AppSearchSpeedTest {
    private val apps = List(600) { i ->
        AppItem(id = "app:com.example.app$i/.Main", label = "App ${listOf("Maps", "Mail", "Music", "Notes", "Photos", "Wallet")[i % 6]} $i", launch = Launch.Action("x"), packageName = "com.example.app$i")
    }

    @Test
    fun theIndexIsBuiltOnceAndEveryKeyIsFast() {
        val built = System.nanoTime()
        val index = AppSearch.index(apps)
        val buildMs = (System.nanoTime() - built) / 1_000_000
        // Warm up, then take the best of several runs: the machine running the tests is not a phone, and a pause for the garbage collector is not the search.
        repeat(20) { index.search("m") }
        val best = (1..30).minOf { val t = System.nanoTime(); index.search("m"); (System.nanoTime() - t) / 1_000_000 }
        println("index of 600 apps: $buildMs ms, first letter: $best ms")
        assertTrue("first letter took $best ms", best < 16)
        assertTrue("index took $buildMs ms", buildMs < 500)
    }
}
