package dev.valhalla.sinhalax.engine

/**
 * SinhalaX Singlish → Sinhala phonetic scheme.
 *
 * Keys are case-sensitive (the keyboard's Shift key gives the uppercase variants).
 * Matching is greedy (longest key first), so "thh" wins over "th", which wins over "t".
 *
 * Rules:
 *  - "a" after a consonant is the inherent vowel (no sign): ma → ම, maa → මා
 *  - a consonant with no vowel after it gets al-lakuna (්): man → මන්
 *  - ් + ZWJ is added before ර / ය to form rakaransaya / yansaya: kra → ක්‍ර, dya → ද්‍ය
 */
object SinglishScheme {

    const val HAL = "\u0DCA"   // ්  al-lakuna
    const val ZWJ = "\u200D"   // zero-width joiner

    /** Consonants (base form, inherent "a"). */
    val consonants: Map<String, String> = mapOf(
        // Velars
        "k" to "ක", "kh" to "ඛ", "K" to "ඛ",
        "g" to "ග", "gh" to "ඝ", "G" to "ඝ",
        "X" to "ඞ", "zg" to "ඟ",
        // Palatals
        "c" to "ච", "ch" to "ච", "chh" to "ඡ", "C" to "ඡ",
        "j" to "ජ", "jh" to "ඣ", "J" to "ඣ",
        "zk" to "ඤ", "zh" to "ඥ", "zj" to "ඦ",
        // Retroflex
        "t" to "ට", "T" to "ඨ",
        "d" to "ඩ", "D" to "ඪ",
        "N" to "ණ", "zd" to "ඬ",
        // Dentals
        "th" to "ත", "thh" to "ථ",
        "dh" to "ද", "q" to "ද", "dhh" to "ධ", "Q" to "ධ",
        "n" to "න", "zdh" to "ඳ", "zq" to "ඳ",
        // Labials
        "p" to "ප", "ph" to "ඵ", "P" to "ඵ",
        "b" to "බ", "bh" to "භ", "B" to "භ",
        "m" to "ම", "zb" to "ඹ",
        // Others
        "y" to "ය", "r" to "ර", "l" to "ල", "L" to "ළ",
        "v" to "ව", "w" to "ව",
        "s" to "ස", "sh" to "ශ", "S" to "ෂ", "Sh" to "ෂ",
        "h" to "හ", "f" to "ෆ",
    )

    /** Vowel signs applied after a consonant. "a" = inherent vowel (empty sign). */
    val vowelSigns: Map<String, String> = mapOf(
        "a" to "",
        "aa" to "ා",
        "A" to "ැ",
        "Aa" to "ෑ", "AA" to "ෑ",
        "i" to "ි", "ii" to "ී",
        "u" to "ු", "uu" to "ූ",
        "e" to "ෙ", "ee" to "ේ",
        "ai" to "ෛ",
        "o" to "ො", "oo" to "ෝ",
        "au" to "ෞ",
        "R" to "ෘ", "RR" to "ෲ",
    )

    /** Stand-alone vowels (start of word, or after another vowel). */
    val independentVowels: Map<String, String> = mapOf(
        "a" to "අ", "aa" to "ආ",
        "A" to "ඇ", "Aa" to "ඈ", "AA" to "ඈ",
        "i" to "ඉ", "ii" to "ඊ",
        "u" to "උ", "uu" to "ඌ",
        "e" to "එ", "ee" to "ඒ",
        "ai" to "ඓ",
        "o" to "ඔ", "oo" to "ඕ",
        "au" to "ඖ",
        "R" to "ඍ", "RR" to "ඎ",
    )

    /** Signs that follow a syllable without killing its vowel. */
    val specials: Map<String, String> = mapOf(
        "x" to "ං",   // anusvara  (sixhala → සිංහල)
        "H" to "ඃ",   // visarga
    )

    /** Consonants that form rakaransaya / yansaya when preceded by ් + ZWJ. */
    val joinTargets: Set<String> = setOf("ර", "ය")

    /**
     * Lowercase keys that are often typed when the user meant a different letter.
     * Used to generate alternative candidates for the live preview strip.
     */
    val ambiguous: Map<Char, String> = mapOf(
        'n' to "N",   // න → ණ
        'l' to "L",   // ල → ළ
        's' to "S",   // ස → ෂ
    )
}
