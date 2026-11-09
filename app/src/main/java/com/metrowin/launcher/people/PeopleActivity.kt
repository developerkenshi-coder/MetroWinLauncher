package com.metrowin.launcher.people

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The People hub: all the contacts, what is new, the favourites, and the card of each person with the history. */
class PeopleActivity : MiniActivity() {
    @Composable
    override fun Content() = PeopleScreen()
}
