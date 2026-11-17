package dev.valhalla.sinhalax.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FancyAndFmTest {

    @Test
    fun boldLettersAndDigits() {
        assertEquals("𝐒𝐢𝐧𝐡𝐚𝐥𝐚𝐗 𝟐𝟎𝟐𝟔", FancyText.byId("bold")!!.apply("SinhalaX 2026"))
    }

    @Test
    fun scriptUsesLetterlikeExceptions() {
        assertEquals("ℋℯ𝓁𝓁ℴ", FancyText.byId("script")!!.apply("Hello"))
    }

    @Test
    fun sinhalaPassesThrough() {
        assertEquals("ආයුබෝවන් 𝐒𝐗", FancyText.byId("bold")!!.apply("ආයුබෝවන් SX"))
    }

    @Test
    fun outlineExceptions() {
        assertEquals("ℂℍℕℙℚℝℤ", FancyText.byId("double")!!.apply("CHNPQRZ"))
    }

    @Test
    fun upsideDownReverses() {
        assertEquals("¡ᴉɥ", FancyText.byId("upside_down")!!.apply("hi!"))
    }

    @Test
    fun fmKnownSentence() {
        val fm = "o<od ud,s.dj"
        assertEquals("දළදා මාලිගාව", FmConverter.toUnicode(fm))
        assertEquals(fm, FmConverter.fromUnicode("දළදා මාලිගාව"))
    }

    @Test
    fun fmRoundTripsCommonWords() {
        for (w in listOf("ශ්‍රී ලංකාව", "ප්‍රධාන", "විද්‍යාලය", "කෞතුකාගාරය", "සෛලය", "ධර්මය", "කොහොමද", "සිංහල")) {
            assertEquals(w, FmConverter.toUnicode(FmConverter.fromUnicode(w)), w)
        }
    }
}
