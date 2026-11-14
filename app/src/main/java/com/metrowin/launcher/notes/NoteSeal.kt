package com.metrowin.launcher.notes

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The password was wrong, or the sealed file was changed or cut short. */
class SealException(message: String) : IOException(message)

/**
 * A backup kept under a password. The file is `MWNS1`, a salt, and then chunks of 64 KB each sealed with AES-GCM under a key made from the password
 * (PBKDF2, 120 000 rounds). Each chunk has its place in its nonce and a mark of whether it is the last one in what it authenticates, so chunks can
 * not be swapped, dropped or cut off the end without [SealException]. Streaming: a backup of many photos never has to fit in memory.
 */
object NoteSeal {
    private val MAGIC = "MWNS1".toByteArray()
    private const val SALT = 16
    private const val CHUNK = 64 * 1024
    private const val TAG = 16
    private const val ROUNDS = 120_000

    fun isSealed(head: ByteArray) = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    val headSize get() = MAGIC.size

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, ROUNDS, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun cipher(mode: Int, key: SecretKeySpec, counter: Long, last: Boolean) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, key, GCMParameterSpec(TAG * 8, ByteBuffer.allocate(12).putInt(0).putLong(counter).array()))
        updateAAD(byteArrayOf(if (last) 1 else 0))
    }

    /** [out] seals what is written to it; close it to finish the file. */
    fun seal(out: OutputStream, password: String): OutputStream {
        val salt = ByteArray(SALT).also { SecureRandom().nextBytes(it) }
        val key = key(password, salt)
        out.write(MAGIC)
        out.write(salt)
        return object : OutputStream() {
            private val buffer = ByteArray(CHUNK)
            private var filled = 0
            private var counter = 0L
            private var closed = false

            private fun emit(last: Boolean) {
                val sealed = cipher(Cipher.ENCRYPT_MODE, key, counter++, last).doFinal(buffer, 0, filled)
                out.write(if (last) 1 else 0)
                out.write(ByteBuffer.allocate(4).putInt(sealed.size).array())
                out.write(sealed)
                filled = 0
            }

            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                var at = off
                var left = len
                while (left > 0) {
                    // A full chunk is only sent when more comes after it: the last one is marked as the last.
                    if (filled == CHUNK) emit(false)
                    val n = minOf(left, CHUNK - filled)
                    System.arraycopy(b, at, buffer, filled, n)
                    filled += n
                    at += n
                    left -= n
                }
            }

            override fun close() {
                if (closed) return
                closed = true
                try { emit(true) } finally { out.close() }
            }
        }
    }

    /** What [input] holds, unsealed as it is read. Throws [SealException] at the first chunk that does not open. */
    fun open(input: InputStream, password: String): InputStream {
        val head = ByteArray(MAGIC.size + SALT)
        if (readFully(input, head) < head.size || !isSealed(head)) throw SealException("not sealed")
        val key = key(password, head.copyOfRange(MAGIC.size, head.size))
        return object : InputStream() {
            private var plain = ByteArray(0)
            private var at = 0
            private var counter = 0L
            private var done = false

            private fun fill(): Boolean {
                if (done) return false
                val flag = input.read()
                if (flag < 0) throw SealException("cut short")
                val size = ByteArray(4)
                if (readFully(input, size) < 4) throw SealException("cut short")
                val length = ByteBuffer.wrap(size).int
                if (length < TAG || length > CHUNK + TAG) throw SealException("bad chunk")
                val sealed = ByteArray(length)
                if (readFully(input, sealed) < length) throw SealException("cut short")
                plain = try {
                    cipher(Cipher.DECRYPT_MODE, key, counter++, flag == 1).doFinal(sealed)
                } catch (e: GeneralSecurityException) {
                    throw SealException("wrong password")
                }
                at = 0
                if (flag == 1) done = true
                return true
            }

            override fun read(): Int {
                while (at >= plain.size) if (!fill()) return -1
                return plain[at++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (at >= plain.size) if (!fill()) return -1
                val n = minOf(len, plain.size - at)
                System.arraycopy(plain, at, b, off, n)
                at += n
                return n
            }

            override fun close() = input.close()
        }
    }

    private fun readFully(input: InputStream, into: ByteArray): Int {
        var total = 0
        while (total < into.size) {
            val n = input.read(into, total, into.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
