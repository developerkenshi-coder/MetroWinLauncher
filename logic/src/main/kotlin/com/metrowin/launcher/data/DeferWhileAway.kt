package com.metrowin.launcher.data

/**
 * Work that matters only to a screen that is on, and is asked for by events that come all day (apps installed or updated,
 * notifications): while no screen is on the event is only remembered, and the work is done once when a screen comes back,
 * not at every event. The phone stays asleep; the screen is just as up to date when it is looked at.
 */
class DeferWhileAway {
    private var pending = false

    /** True when the work is to be done now ([onScreen]); otherwise it is remembered for [takePending]. */
    fun request(onScreen: Boolean): Boolean {
        if (onScreen) return true
        pending = true
        return false
    }

    /** A screen came back: whether work was asked for meanwhile. Asking again starts from nothing. */
    fun takePending(): Boolean = pending.also { pending = false }
}
