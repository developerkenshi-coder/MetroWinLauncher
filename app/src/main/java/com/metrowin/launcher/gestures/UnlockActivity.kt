package com.metrowin.launcher.gestures

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle

/**
 * Asks Android to take its lock screen away once the Metro one was swiped up: with no PIN it goes at once, with one the system asks
 * for it, and that is where the fingerprint (or the face) is read. It shows nothing of its own.
 */
class UnlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val done = object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finish()
            override fun onDismissCancelled() = finish()
            override fun onDismissError() = finish()
        }
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, done)
    }
}
