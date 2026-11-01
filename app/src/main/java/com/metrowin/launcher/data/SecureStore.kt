package com.metrowin.launcher.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Text kept encrypted at rest (passwords of mail accounts, drafts of messages): AES-256-GCM with a key that lives in the Android Keystore,
 * so the text in the preferences is of no use outside this app on this phone.
 */
object SecureStore {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "metrowin_secure_store"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    /** [plain] as text that only [decrypt] reads back; the empty text when the Keystore fails, so nothing is ever stored in the clear. */
    fun encrypt(plain: String): String = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }.getOrDefault("")

    /** What [encrypt] made of a text, or null for text that is not its own (or a key that is gone). */
    fun decrypt(text: String): String? = runCatching {
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)) }
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }.getOrNull()
}
