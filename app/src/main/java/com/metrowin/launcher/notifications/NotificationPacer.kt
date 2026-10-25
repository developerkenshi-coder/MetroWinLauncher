package com.metrowin.launcher.notifications

/**
 * When the notification service reads the notifications again. Every read asks Android for all the notifications that are up
 * (each one copied across processes) and, for the live tiles, decodes their pictures. Done at every post, that kept the phone
 * busy for apps that post all the time: a download, the music, a step counter. So nothing is read while no screen of the
 * launcher is looking, the change is only remembered; when a screen comes back it is read once. And while a screen is looking,
 * a burst of changes is read once, [minGapMs] after the last read at the earliest.
 *
 * Pure, with the clock passed in, so it can be tested; the service runs what it says.
 */
class NotificationPacer(private val minGapMs: Long = MIN_GAP_MS) {
    /** What the service does about a change. */
    sealed interface Step {
        /** Read the notifications now. */
        data object Now : Step

        /** Read them after [millis]; more changes meanwhile are covered by that read. */
        data class After(val millis: Long) : Step

        /** Nothing: nobody is looking, or a read is already waiting. */
        data object Idle : Step
    }

    private var lastReadAt = NEVER
    private var scheduled = false

    /** Whether something changed since the last read. */
    var stale = true
        private set

    /** A notification came, changed or went away. [watched]: a screen of the launcher is on. */
    fun changed(now: Long, watched: Boolean): Step {
        stale = true
        if (!watched || scheduled) return Step.Idle
        val wait = lastReadAt + minGapMs - now
        if (lastReadAt == NEVER || wait <= 0) return Step.Now
        scheduled = true
        return Step.After(wait)
    }

    /** A screen of the launcher came on: what changed while none was looking is read now, once. */
    fun watched(): Step = if (stale && !scheduled) Step.Now else Step.Idle

    /** The last screen of the launcher went off: a read that was waiting is not needed any more. */
    fun away() {
        scheduled = false
    }

    /** The notifications were read at [now]. */
    fun read(now: Long) {
        lastReadAt = now
        stale = false
        scheduled = false
    }

    companion object {
        /** Reads closer than this are one read. */
        const val MIN_GAP_MS = 600L

        /** A media notification is read again after this long even if it was not posted again: the picture can come late. */
        const val MEDIA_FRESH_MS = 5_000L

        private const val NEVER = Long.MIN_VALUE
    }
}
