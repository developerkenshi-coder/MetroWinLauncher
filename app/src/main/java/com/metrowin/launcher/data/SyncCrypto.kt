package com.metrowin.launcher.data

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * What goes into the sync folder. The folder may be in a cloud, so the file is encrypted before it leaves: AES-256 in
 * GCM mode (which also notices any change to the file), with a key made from the user's passphrase by PBKDF2 and a
 * salt and nonce that are new every time. The passphrase itself is never written to the folder.
 *
 * File layout: magic (7 bytes) | iterations (4) | salt (16) | nonce (12) | encrypted data and tag. The first three
 * fields are also authenticated, so they cannot be changed without the file being refused.
 */
object SyncCrypto {
    private val magic = "MWSYNC1".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 200_000

    /** A file that asks for more than this is refused rather than made to spin: the number is in the file. */
    private const val MAX_ITERATIONS = 2_000_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER = 7 + 4 + SALT_BYTES

    fun encrypt(plain: ByteArray, passphrase: String, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.isNotEmpty()) { "a passphrase is needed" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(magic).putInt(iterations).put(salt).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
        return header + nonce + cipher.doFinal(plain)
    }

    /** The data, or null when [blob] is not one of ours, was changed, or was made with another passphrase. */
    fun decrypt(blob: ByteArray, passphrase: String): ByteArray? {
        if (passphrase.isEmpty() || blob.size < HEADER + NONCE_BYTES + TAG_BITS / 8) return null
        val buffer = ByteBuffer.wrap(blob)
        val found = ByteArray(magic.size).also(buffer::get)
        if (!found.contentEquals(magic)) return null
        val iterations = buffer.getInt()
        if (iterations !in 1..MAX_ITERATIONS) return null
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(blob, 0, HEADER)
                doFinal(encrypted)
            }
        } catch (_: AEADBadTagException) {
            null
        }
    }

    /** Whether [blob] looks like a sync file at all, whoever it belongs to. */
    fun isSyncFile(blob: ByteArray): Boolean = blob.size >= magic.size && blob.copyOfRange(0, magic.size).contentEquals(magic)

    private fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
