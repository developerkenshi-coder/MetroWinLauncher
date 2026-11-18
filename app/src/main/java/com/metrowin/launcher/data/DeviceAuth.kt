package com.metrowin.launcher.data

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/** Asks the owner to prove it is them: the fingerprint or face the phone has, or else the screen lock. */
object DeviceAuth {
    /** The system prompt can take the fingerprint and the screen lock together only from Android 11 on; before, the caller asks for the screen lock alone. */
    val canPrompt: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Shows the prompt; [onSuccess] runs once, on the main thread, when the owner passed. Nothing happens when they cancel. Call only when [canPrompt]. */
    fun prompt(context: Context, title: String, subtitle: String? = null, onSuccess: () -> Unit) {
        if (!canPrompt) return
        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        subtitle?.let { builder.setSubtitle(it) }
        runCatching {
            builder.build().authenticate(
                CancellationSignal(), context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                },
            )
        }
    }
}
