package com.andy.calculator

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The locked folder's files are encrypted with a random master key. The master key is
 * stored wrapped (AES-GCM) under a key derived from the password, so a wrong password
 * fails to unwrap it, and changing the password doesn't re-encrypt every file.
 */
object VaultCrypto {
    private const val ITERATIONS = 120_000
    private val rng = SecureRandom()

    fun random(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    fun keyFrom(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    /** Returns iv + ciphertext. */
    fun seal(key: SecretKey, data: ByteArray): ByteArray {
        val iv = random(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + c.doFinal(data)
    }

    /** Throws if the key is wrong or the data was changed. */
    fun open(key: SecretKey, sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        return c.doFinal(sealed, 12, sealed.size - 12)
    }

    /** Streams a file of any size: 16-byte IV, then AES-CTR ciphertext. */
    fun encrypt(key: SecretKey, input: InputStream, output: OutputStream, onBytes: (Long) -> Unit = {}) {
        val iv = random(16)
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        output.write(iv)
        pump(c, input, output, onBytes)
    }

    fun decrypt(key: SecretKey, input: InputStream, output: OutputStream) {
        val iv = ByteArray(16)
        var read = 0
        while (read < 16) {
            val n = input.read(iv, read, 16 - read)
            if (n < 0) throw IllegalStateException("File is damaged")
            read += n
        }
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        pump(c, input, output) {}
    }

    private fun pump(c: Cipher, input: InputStream, output: OutputStream, onBytes: (Long) -> Unit) {
        val buf = ByteArray(256 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n == 0) continue
            c.update(buf, 0, n)?.let { output.write(it) }
            total += n
            onBytes(total)
        }
        c.doFinal()?.let { output.write(it) }
        output.flush()
    }
}
