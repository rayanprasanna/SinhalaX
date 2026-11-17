package dev.valhalla.sinhalax.engine

/**
 * Converts Singlish (romanised Sinhala) to Sinhala Unicode.
 *
 * Pure Kotlin, no Android dependencies, safe to call on every keystroke:
 * the IME keeps the current word in a composing buffer and re-runs
 * [transliterate] on the whole buffer, which is always short.
 */
class SinglishTransliterator(
    private val scheme: SinglishScheme = SinglishScheme,
) {

    private val maxConsonantKey = scheme.consonants.keys.maxOf { it.length }
    private val maxVowelKey = maxOf(
        scheme.vowelSigns.keys.maxOf { it.length },
        scheme.independentVowels.keys.maxOf { it.length },
    )
    private val maxSpecialKey = scheme.specials.keys.maxOf { it.length }

    /** Full conversion of [input]. Characters with no mapping (digits, punctuation, spaces) pass through. */
    fun transliterate(input: String): String {
        val out = StringBuilder(input.length * 2)
        var i = 0

        while (i < input.length) {
            // 1. Consonant (+ optional vowel sign)
            val consonant = matchLongest(input, i, scheme.consonants, maxConsonantKey)
            if (consonant != null) {
                i += consonant.first.length
                out.append(consonant.second)

                val sign = matchLongest(input, i, scheme.vowelSigns, maxVowelKey)
                if (sign != null) {
                    out.append(sign.second)
                    i += sign.first.length
                    continue
                }

                // ං / ඃ keep the inherent vowel: kx → කං
                if (matchLongest(input, i, scheme.specials, maxSpecialKey) != null) continue

                // No vowel → al-lakuna
                out.append(SinglishScheme.HAL)

                // Rakaransaya / yansaya: ක් + ZWJ + ර, ද් + ZWJ + ය
                val next = matchLongest(input, i, scheme.consonants, maxConsonantKey)
                if (next != null &&
                    next.second in scheme.joinTargets &&
                    consonant.second !in scheme.joinTargets
                ) {
                    out.append(SinglishScheme.ZWJ)
                }
                continue
            }

            // 2. Stand-alone vowel
            val vowel = matchLongest(input, i, scheme.independentVowels, maxVowelKey)
            if (vowel != null) {
                out.append(vowel.second)
                i += vowel.first.length
                continue
            }

            // 3. ං / ඃ
            val special = matchLongest(input, i, scheme.specials, maxSpecialKey)
            if (special != null) {
                out.append(special.second)
                i += special.first.length
                continue
            }

            // 4. Pass through
            out.append(input[i])
            i++
        }
        return out.toString()
    }

    /**
     * Primary conversion first, then alternatives made by swapping one commonly
     * confused letter (n→ණ, l→ළ, s→ෂ). Used for the live preview strip;
     * the prediction module re-ranks these against the dictionary.
     */
    fun candidates(input: String, limit: Int = 3): List<String> {
        val results = LinkedHashSet<String>()
        results += transliterate(input)

        for (index in input.indices) {
            if (results.size >= limit) break
            val replacement = scheme.ambiguous[input[index]] ?: continue
            val variant = input.substring(0, index) + replacement + input.substring(index + 1)
            results += transliterate(variant)
        }
        return results.take(limit)
    }

    private fun matchLongest(
        input: String,
        start: Int,
        table: Map<String, String>,
        maxLen: Int,
    ): Pair<String, String>? {
        val end = minOf(input.length, start + maxLen)
        for (stop in end downTo start + 1) {
            val key = input.substring(start, stop)
            table[key]?.let { return key to it }
        }
        return null
    }
}
