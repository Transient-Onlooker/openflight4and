package com.example.openflight4and.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object FocusLockPinSecurity {
    private const val SaltSizeBytes = 16
    private const val Iterations = 120_000
    private const val KeyLengthBits = 256
    private const val Algorithm = "PBKDF2WithHmacSHA256"

    fun createHash(pin: String): PinHash {
        val salt = ByteArray(SaltSizeBytes).also(SecureRandom()::nextBytes)
        val hash = hash(pin, salt)
        return PinHash(
            saltBase64 = salt.encodeBase64(),
            hashBase64 = hash.encodeBase64()
        )
    }

    fun verify(pin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        val salt = runCatching { Base64.decode(saltBase64, Base64.NO_WRAP) }.getOrElse { return false }
        val expectedHash = runCatching { Base64.decode(expectedHashBase64, Base64.NO_WRAP) }.getOrElse { return false }
        return hash(pin, salt).contentEquals(expectedHash)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(pin.toCharArray(), salt, Iterations, KeyLengthBits)
        return try {
            SecretKeyFactory.getInstance(Algorithm).generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
}

data class PinHash(
    val saltBase64: String,
    val hashBase64: String
)
