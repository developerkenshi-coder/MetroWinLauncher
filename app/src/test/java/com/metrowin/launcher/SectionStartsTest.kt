package com.metrowin.launcher

import com.metrowin.launcher.ui.components.sectionStarts
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionStartsTest {
    @Test
    fun everySectionStartsAfterTheHeadingAndTheRowsOfTheOnesBeforeIt() {
        // Three letters with 2, 1 and 3 people, after the one item of the search box.
        assertEquals(listOf(1, 4, 6), sectionStarts(listOf(2, 1, 3), first = 1))
    }

    @Test
    fun aListWithNoSectionsHasNoStarts() {
        assertEquals(emptyList<Int>(), sectionStarts(emptyList()))
    }
}
