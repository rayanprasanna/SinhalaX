package dev.valhalla.sinhalax.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test

class SinglishTransliteratorTest {

    private val engine = SinglishTransliterator()

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        // Inherent vowel and long vowels
        "mama, මම",
        "amma, අම්ම",
        "ammaa, අම්මා",
        "aayubowan, ආයුබොවන්",
        // Common words
        "kohomadha, කොහොමද",
        "kohedha, කොහෙද",
        "gedhara, ගෙදර",
        "laxkaava, ලංකාව",
        "sixhala, සිංහල",
        // Vowel signs
        "Adha, ඇද",
        "kAAma, කෑම",
        "kai, කෛ",
        "kRthi, කෘති",
        // Aspirates and specials
        "shaantha, ශාන්ත",
        "Shaantha, ෂාන්ත",
        "thhaana, ථාන",
        "dhhanaya, ධනය",
        "azgala, අඟල",
        "kazdu, කඬු",
        "hazdha, හඳ",
        "azba, අඹ",
        // Anusvara keeps the inherent vowel
        "kx, කං",
        // Pass-through
        "'hello 123', 'හෙල්ලො 123'",
    )
    fun converts(input: String, expected: String) {
        assertEquals(expected, engine.transliterate(input))
    }

    @Test
    fun rakaransaya() {
        assertEquals("ක\u0DCA\u200Dර", engine.transliterate("kra"))
        assertEquals("ප\u0DCA\u200Dරධානය", engine.transliterate("pradhhaanaya"))
    }

    @Test
    fun yansaya() {
        assertEquals("විද\u0DCA\u200Dයාව", engine.transliterate("vidhyaava"))
        assertEquals("සත\u0DCA\u200Dය", engine.transliterate("sathya"))
    }

    @Test
    fun noJoinerAfterRayanna() {
        // ර් + ය stays plain (no ZWJ), so it renders as repaya / plain hal
        assertEquals("කාර\u0DCAය", engine.transliterate("kaarya"))
    }

    @Test
    fun conjunctNasalNotJoined() {
        assertEquals("කන\u0DCAද", engine.transliterate("kandha"))
    }

    @Test
    fun candidatesIncludeRetroflexAlternatives() {
        val list = engine.candidates("nama")
        assertEquals("නම", list.first())
        assertTrue("ණම" in list)
    }
}
