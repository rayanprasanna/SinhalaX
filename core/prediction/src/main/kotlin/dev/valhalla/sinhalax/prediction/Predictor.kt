package dev.valhalla.sinhalax.prediction

import dev.valhalla.sinhalax.engine.SinglishTransliterator
import kotlin.math.ln

/**
 * Turns the Singlish being typed into:
 *  - [Prediction.best]: what space will commit (auto-corrected when the plain conversion isn't a word)
 *  - [Prediction.suggestions]: the strip above the keys (best first)
 */
class Predictor(
    private val engine: SinglishTransliterator,
    private val lexicon: Lexicon,
    private val user: UserDictionary,
) {

    data class Prediction(
        val best: String,
        val primary: String,
        val corrected: Boolean,
        val suggestions: List<String>,
    )

    fun predict(latin: String, limit: Int = 3, autoCorrect: Boolean = true): Prediction {
        val primary = engine.transliterate(latin)
        if (latin.isEmpty()) return Prediction(primary, primary, false, emptyList())

        // 1. Whole-word alternatives that are real words.
        val maxEdits = if (latin.length >= 7) 3 else 2
        val alternatives = SinglishFuzzer.variants(latin, maxEdits, limit = 500)
            .asSequence()
            .map { engine.transliterate(it) }
            .filter { it != primary }
            .distinct()
            .map { it to score(it) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(8)
            .toList()

        // 2. Auto-correct when what they typed isn't a word — or is a rare spelling (often a corpus typo)
        //    of something far more common — unless they've chosen that spelling themselves before.
        val top = alternatives.firstOrNull()?.first
        val corrected = autoCorrect && latin.length >= 2 && top != null && isConfident(top) && shouldReplace(primary, top)
        val best = if (corrected) top!! else primary

        // 3. Completions for the word still being typed.
        val ranked = (alternatives + complete(latin))
            .groupBy({ it.first }, { it.second })
            .map { (word, scores) -> word to scores.max() }
            .sortedByDescending { it.second }
            .map { it.first }

        val suggestions = LinkedHashSet<String>()
        suggestions += best
        if (primary != best) suggestions += primary
        for (word in ranked) {
            if (suggestions.size >= limit) break
            suggestions += word
        }
        return Prediction(best, primary, corrected, suggestions.take(limit))
    }

    /** 0.0 = not a word we know. */
    fun score(word: String): Double {
        val freq = lexicon.frequency(word)
        val used = user.count(word)
        if (freq == 0 && used < MIN_USES_FOR_UNKNOWN) return 0.0
        return ln(freq + 1.0) + USER_WEIGHT * ln(used + 1.0)
    }

    private fun shouldReplace(primary: String, candidate: String): Boolean {
        if (user.count(primary) > 0) return false // they've used this spelling on purpose
        val primaryFreq = lexicon.frequency(primary)
        if (primaryFreq == 0) return true
        return lexicon.frequency(candidate) >= primaryFreq.toLong() * DOMINANCE_RATIO
    }

    private fun isConfident(word: String): Boolean =
        lexicon.frequency(word) >= MIN_FREQ_FOR_AUTOCORRECT || user.count(word) >= MIN_USES_FOR_UNKNOWN

    private fun complete(latin: String): List<Pair<String, Double>> {
        if (latin.length < 2) return emptyList()
        val prefixes = LinkedHashSet<String>()
        val bases = listOf(latin) + SinglishFuzzer.variants(latin, maxEdits = 1, limit = 20)
        for (base in bases) {
            prefixes += stem(engine.transliterate(base))
            val last = base.last()
            if (last.isLetter() && last.lowercaseChar() !in "aeiouxh") {
                prefixes += stem(engine.transliterate(base + "h")) // t → th, d → dh still to come
            }
            if (last in "aeiou") prefixes += stem(engine.transliterate(base + last)) // short → long vowel
        }

        val found = HashMap<String, Double>()
        for (prefix in prefixes) {
            if (prefix.isEmpty()) continue
            lexicon.forEachWithPrefix(prefix) { i ->
                val word = lexicon.word(i)
                if (word !in found) found[word] = score(word)
            }
            for (entry in user.withPrefix(prefix)) {
                if (entry.count >= MIN_USES_FOR_UNKNOWN || lexicon.frequency(entry.word) > 0) {
                    found[entry.word] = score(entry.word)
                }
            }
        }
        return found.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key to it.value }
    }

    /** A word still being typed: drop a trailing al-lakuna (and joiner) so "kohom" (කොහොම්) matches කොහොමද. */
    private fun stem(sinhala: String): String {
        var s = Lexicon.normalize(sinhala)
        if (s.endsWith(HAL)) s = s.dropLast(1)
        return s
    }

    companion object {
        private const val HAL = "\u0DCA"
        private const val USER_WEIGHT = 2.5
        private const val MIN_USES_FOR_UNKNOWN = 2
        private const val MIN_FREQ_FOR_AUTOCORRECT = 20
        /** A known word is only replaced by one at least this many times more common. */
        private const val DOMINANCE_RATIO = 50
    }
}
