package com.metrowin.launcher

import com.metrowin.launcher.data.SettingSynonyms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingSynonymsTest {
    @Test fun aWordOfAnotherLanguageGivesTheWordsThatMeanTheSame() {
        val queries = SettingSynonyms.expand("dunkel")
        assertEquals("dunkel", queries.first())
        assertTrue("scuro" in queries && "dark" in queries && "oscuro" in queries)
    }

    @Test fun onlyTheWordThatHasSynonymsIsReplaced() {
        val queries = SettingSynonyms.expand("tema scuro")
        assertTrue("theme scuro" in queries)
        assertTrue("tema dark" in queries)
    }

    @Test fun aWordWithoutSynonymsIsAskedAsItIs() {
        assertEquals(listOf("zzzz"), SettingSynonyms.expand("zzzz"))
    }

    @Test fun neverMoreThanTheLimit() {
        assertTrue(SettingSynonyms.expand("reset", max = 5).size <= 5)
    }
}
