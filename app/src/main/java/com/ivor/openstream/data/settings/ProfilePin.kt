package com.ivor.openstream.data.settings

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Profile PINs: 4+ digit codes stored as salted SHA-256, never plaintext.
 * Brute force is slowed by [PinLockout] (see [AppSettingsStore]).
 */
object ProfilePin {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, hash: String, salt: String): Boolean {
        if (!isValidPin(pin)) return false
        val candidate = runCatching { hash(pin, salt) }.getOrNull() ?: return false
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            hash.toByteArray(Charsets.UTF_8)
        )
    }
}
