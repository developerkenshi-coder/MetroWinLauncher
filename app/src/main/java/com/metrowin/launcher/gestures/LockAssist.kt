package com.metrowin.launcher.gestures

/**
 * What the Metro lock screen can tell about the lock of Android under it, so that it does not promise a fingerprint that cannot work. Pure,
 * so the decision is tested; [LockOverlay] fills in what Android says.
 *
 * What Android lets an app know: whether the phone has a lock ([android.app.KeyguardManager.isDeviceSecure]), whether a fingerprint is saved
 * (`BiometricManager.canAuthenticate`), when the PIN (the device credential) was last entered (`BiometricManager.getLastAuthenticationTime`,
 * Android 15 and later) and the state of the SIM. What it does not tell: that Android asks for the PIN now (every 72 hours it must, a
 * lockdown or too many wrong fingers do the same), so that is worked out from the time of the last PIN, and where that time is not known
 * the lock screen does not claim a fingerprint either way ([Way.Unsure]).
 */
internal object LockAssist {
    /** After this long without the PIN Android does not take a fingerprint any more (its default; a work policy can make it shorter). */
    const val STRONG_AUTH_TIMEOUT_MS = 72L * 60 * 60 * 1000

    /** How the phone is expected to be unlocked. */
    enum class Way {
        /** A fingerprint is saved and Android takes it now: the hint and the spot for the finger are shown. */
        Fingerprint,

        /** A fingerprint is saved, but whether Android takes it now is not known (older Android): the spot stays, no promise is written. */
        Unsure,

        /** A fingerprint is saved, but Android asks for the PIN first: no spot, no fingerprint, the line says to swipe up and use the PIN. */
        Pin,

        /** Nothing to promise: no lock, or no fingerprint saved. */
        Swipe,
    }

    /** [showOverlay] false: another screen of the system (the PIN of the SIM) must be reachable, our lock screen is not put over it. */
    data class Decision(val showOverlay: Boolean, val way: Way) {
        val fingerprintSpot get() = way == Way.Fingerprint || way == Way.Unsure
    }

    /** [sinceCredentialMs]: how long ago the PIN was entered, null when not known. */
    fun decide(secure: Boolean, fingerprintSaved: Boolean, sinceCredentialMs: Long?, simLocked: Boolean): Decision {
        if (simLocked) return Decision(false, Way.Swipe)
        val way = when {
            !secure || !fingerprintSaved -> Way.Swipe
            sinceCredentialMs == null -> Way.Unsure
            sinceCredentialMs >= STRONG_AUTH_TIMEOUT_MS -> Way.Pin
            else -> Way.Fingerprint
        }
        return Decision(true, way)
    }

    /** How long ago the PIN was entered, from the time Android reports (on the clock of [now]); null for "never since boot" (-1) or a time that cannot be. */
    fun sinceCredential(last: Long, now: Long): Long? = if (last < 0 || last > now) null else now - last
}
