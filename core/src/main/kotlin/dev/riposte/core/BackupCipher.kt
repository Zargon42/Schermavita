package dev.riposte.core

import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned authenticated container: salt + nonce + AES-GCM ciphertext. */
object BackupCipher {
    private val magic = "RIPOSTE1".toByteArray(Charsets.US_ASCII)
    private const val rounds = 310_000
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, rounds, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }
    fun encrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "Use at least 8 characters for the backup password." }
        val random = SecureRandom(); val salt = ByteArray(16).also(random::nextBytes); val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce)); cipher.updateAAD(magic)
        return magic + salt + nonce + cipher.doFinal(bytes)
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size > 52 && bytes.copyOfRange(0, 8).contentEquals(magic)) { "This is not a supported Riposte backup." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, bytes.copyOfRange(8, 24)), GCMParameterSpec(128, bytes.copyOfRange(24, 36)))
        cipher.updateAAD(magic)
        return cipher.doFinal(bytes.copyOfRange(36, bytes.size))
    }
}
