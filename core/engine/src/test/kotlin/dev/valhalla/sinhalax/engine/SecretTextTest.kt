package dev.valhalla.sinhalax.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.Random
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecretTextTest {

    @Test
    fun base64urlMatchesJdk() {
        val r = Random(1)
        for (len in 0..70) {
            val data = ByteArray(len).also(r::nextBytes)
            val ours = Base64Url.encode(data)
            assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(data), ours)
            assertArrayEquals(data, Base64Url.decode(ours))
        }
    }

    @Test
    fun pbkdf2MatchesJdk() {
        val salt = "saltsaltsaltsalt".toByteArray()
        val jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("1234".toCharArray(), salt, 1000, 256)).encoded
        assertArrayEquals(jdk, SecretText.deriveKey("1234", salt, 1000))
    }

    @Test
    fun lockAndUnlock() {
        val message = "මම ගෙදර යනවා 🏠 secret!"
        val locked = SecretText.lock(message, "2468")
        assertTrue(locked.startsWith(SecretText.PREFIX))
        assertEquals(message, SecretText.unlock(locked, "2468"))
        assertEquals(message, SecretText.unlock("Look: $locked (sent)", "2468"))
        assertNull(SecretText.unlock(locked, "2469"))
        assertNull(SecretText.unlock(locked.dropLast(3) + "AAA", "2468"))
        assertNotEquals(SecretText.lock("a", "1111"), SecretText.lock("a", "1111"))
        assertFalse(SecretText.containsSecret("hello"))
        assertFalse(SecretText.isValidPin("12a4"))
    }
}
