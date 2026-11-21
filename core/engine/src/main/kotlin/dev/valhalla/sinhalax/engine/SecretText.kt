package dev.valhalla.sinhalax.engine

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secret text: locks a message with a PIN so only someone with SinhalaX and the same PIN can read it.
 *
 * Format: `🔐SX1:` + Base64url( version(1) | salt(16) | iv(12) | AES-256-GCM ciphertext+tag ).
 * Key = PBKDF2-HMAC-SHA256(PIN, salt, 60 000 rounds). GCM means a wrong PIN or a changed message
 * is detected (unlock returns null) instead of producing garbage.
 *
 * Honest limit: a short numeric PIN can be guessed by someone determined with a computer. This is
 * for everyday privacy (people reading over your shoulder, shared phones, group screenshots),
 * not for protecting secrets from attackers. The UI says so.
 *
 * Pure JVM (works on Android 7+): PBKDF2 and Base64 are implemented here because the platform
 * versions with SHA-256 / java.util.Base64 need Android 8.
 */
object SecretText {

    const val TAG = "SX1:"
    const val PREFIX = "🔐$TAG"
    const val MIN_PIN = 4
    const val MAX_PIN = 8
    const val MAX_TEXT = 4000

    private const val VERSION: Byte = 1
    private const val ITERATIONS = 60_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    fun isValidPin(pin: String) = pin.length in MIN_PIN..MAX_PIN && pin.all { it.isDigit() }

    /** Locks [text] with [pin]. */
    fun lock(text: String, pin: String): String {
        require(isValidPin(pin)) { "PIN must be $MIN_PIN–$MAX_PIN digits" }
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(pin, salt), "AES"), GCMParameterSpec(TAG_BITS, iv))
        val sealed = cipher.doFinal(text.take(MAX_TEXT).toByteArray(Charsets.UTF_8))
        return PREFIX + Base64Url.encode(byteArrayOf(VERSION) + salt + iv + sealed)
    }

    /** The `SX1:…` part of [text] (a pasted chat message can have other words around it), or null. */
    fun find(text: String): String? {
        val start = text.indexOf(TAG)
        if (start < 0) return null
        val body = text.substring(start + TAG.length).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (body.length < 40) null else TAG + body
    }

    /** True when [text] contains a locked message. */
    fun containsSecret(text: String?) = text != null && find(text) != null

    /** Unlocks the message in [text] with [pin]; null if there is none, the PIN is wrong or it was changed. */
    fun unlock(text: String, pin: String): String? {
        val token = find(text) ?: return null
        val data = Base64Url.decode(token.removePrefix(TAG)) ?: return null
        if (data.size < 1 + SALT_LEN + IV_LEN + TAG_BITS / 8 || data[0] != VERSION) return null
        val salt = data.copyOfRange(1, 1 + SALT_LEN)
        val iv = data.copyOfRange(1 + SALT_LEN, 1 + SALT_LEN + IV_LEN)
        val sealed = data.copyOfRange(1 + SALT_LEN + IV_LEN, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(pin, salt), "AES"), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null // wrong PIN or damaged message
        }
    }

    /** PBKDF2-HMAC-SHA256 producing one 32-byte block (AES-256 key). */
    internal fun deriveKey(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pin.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1)) // block index 1
        var u = mac.doFinal()
        val out = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
        }
        return out
    }
}

/** URL-safe Base64 without padding (A–Z a–z 0–9 - _), so messages survive chat apps. */
object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 4 + 2) / 3)
        var i = 0
        while (i + 3 <= data.size) {
            val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8) or (data[i + 2].toInt() and 0xFF)
            sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63])
                .append(ALPHABET[n shr 6 and 63]).append(ALPHABET[n and 63])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val n = data[i].toInt() and 0xFF shl 16
                sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63])
            }
            2 -> {
                val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8)
                sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63]).append(ALPHABET[n shr 6 and 63])
            }
        }
        return sb.toString()
    }

    /** Null if [text] has characters outside the alphabet or an impossible length. */
    fun decode(text: String): ByteArray? {
        if (text.length % 4 == 1) return null
        val out = ByteArray(text.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var o = 0
        for (c in text) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[o++] = (buffer shr bits and 0xFF).toByte()
            }
        }
        return out.copyOf(o)
    }
}
