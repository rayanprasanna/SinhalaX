package dev.valhalla.sinhalax.prediction

import dev.valhalla.sinhalax.engine.SinglishTransliterator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PredictorTest {

    private val lexicon = Lexicon.parse(
        sequenceOf(
            "කොහොමද\t40867", "කොහොමඩ\t32", "කොහොම\t20000",
            "අද\t274282", "අඩ\t3056",
            "ලංකාව\t46435",
            "අම්ම\t10413", "අම්මා\t31718",
            "ගෙදර\t65330",
            "ප්\u200Dරධාන\t82324",
            "සිංහල\t112387", "සින්හල\t1543",
        ),
    )

    private fun predictor(user: UserDictionary = UserDictionary()) =
        Predictor(SinglishTransliterator(), lexicon, user)

    @Test
    fun correctsDentalD() {
        val r = predictor().predict("kohomada")
        assertEquals("කොහොමද", r.best)
        assertTrue(r.corrected)
        assertEquals("කොහොමඩ", r.primary)
    }

    @Test
    fun correctsUnknownSpelling() {
        assertEquals("ලංකාව", predictor().predict("lankava").best)
        assertEquals("ගෙදර", predictor().predict("gedara").best)
        assertEquals("සිංහල", predictor().predict("sinhala").best)
    }

    @Test
    fun keepsRealWordThatIsNotDominated() {
        val r = predictor().predict("amma")
        assertEquals("අම්ම", r.best)
        assertFalse(r.corrected)
        assertTrue("අම්මා" in r.suggestions)
    }

    @Test
    fun completesWordInProgress() {
        assertTrue("කොහොමද" in predictor().predict("kohom").suggestions)
    }

    @Test
    fun joinerInsensitiveLookup() {
        assertEquals(82324, lexicon.frequency("ප්රධාන"))
        assertEquals("ප්\u200Dරධාන", predictor().predict("pradhaana").best)
    }

    @Test
    fun singleLetterIsNeverCorrected() {
        assertFalse(predictor().predict("s").corrected)
    }

    @Test
    fun choosingASpellingStopsCorrection() {
        val user = UserDictionary()
        assertEquals("අද", predictor(user).predict("ada").best)
        user.learn("අඩ")
        assertEquals("අඩ", predictor(user).predict("ada").best)
    }

    @Test
    fun learnedNewWordIsSuggestedAfterTwoUses() {
        val user = UserDictionary()
        user.learn("රයන්")
        assertFalse("රයන්" in predictor(user).predict("ray").suggestions)
        user.learn("රයන්")
        assertTrue("රයන්" in predictor(user).predict("ray").suggestions)
    }

    @Test
    fun userDictionaryRoundTrip() {
        val user = UserDictionary()
        user.learn("රයන්", now = 5)
        user.learn("රයන්", now = 9)
        val copy = UserDictionary.parse(user.serialize())
        assertEquals(2, copy.count("රයන්"))
        assertEquals(9L, copy.all().first().lastUsed)
    }
}
