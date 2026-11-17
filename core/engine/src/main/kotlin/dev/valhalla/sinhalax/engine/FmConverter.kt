package dev.valhalla.sinhalax.engine

import java.text.Normalizer

/*
 * Sinhala Unicode <-> FM Abhaya (and the rest of the FM family: FM Malithi, FM Bindumathi, FM Emanee ...).
 *
 * Kotlin port of src/fmabhaya.ts from legacy-fonts-to-unicode
 * (https://github.com/osamamumtaz01/legacy-fonts-to-unicode), Copyright (c) 2026 Osama Mumtaz, MIT License,
 * whose glyph table is taken from the "Pandukabhaya" FM Abhaya mapping
 * (https://github.com/akuruAI/pandukabhaya), Copyright (c) 2024 akuruAI, MIT License,
 * a cleaned-up version of the UCSC Language Technology Research Laboratory converter
 * (https://github.com/Open-SL/sinhala-unicode-converter, Janaka Chathuranga, MIT License).
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions: The above copyright notice and this
 *   permission notice shall be included in all copies or substantial portions of
 *   the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * FM fonts are "ASCII fonts": the text is Latin-1 letters and punctuation that the font draws as
 * Sinhala glyphs, typed in visual order (the kombuva ෙ before its consonant, the repaya after it).
 */
object FmConverter {

    private const val ZWJ = "‍"
    private const val KOMBU = ""   // placeholder: kombuva typed before its consonant
    private const val REPAYA = ""  // placeholder: repaya typed after its consonant
    private const val SANYAKA = "" // placeholder: the ` prefix glyph
    private const val AL = "්"
    private const val JOIN = AL + ZWJ

    // ------------------------------------------------------------------ glyph table: FM key -> Unicode

    private val CONSONANTS: Map<String, String> = linkedMapOf(
        "l" to "ක", "L" to "ඛ", "." to "ග", ">" to "ඝ", "X" to "ඞ", "Õ" to "ඟ", "p" to "ච", "P" to "ඡ", "c" to "ජ", "®" to "ඣ",
        "[" to "ඤ", "Ø" to "ඤ", "{" to "ඥ", "×" to "ඥ", "g" to "ට", "G" to "ඨ", "v" to "ඩ", "V" to "ඪ", "K" to "ණ", "~" to "ඬ",
        ";" to "ත", ":" to "ථ", "o" to "ද", "O" to "ධ", "k" to "න", "|" to "ඳ", "m" to "ප", "M" to "ඵ", "n" to "බ", "N" to "භ",
        "u" to "ම", "U" to "ඹ", "h" to "ය", "r" to "ර", "," to "ල", "j" to "ව", "Y" to "ශ", "I" to "ෂ", "i" to "ස", "y" to "හ",
        "<" to "ළ", "*" to "ෆ",
    )

    private val TO_UNICODE: Map<String, String> = LinkedHashMap<String, String>().apply {
        putAll(CONSONANTS)
        // independent vowels (ආ ඇ ඈ ඌ ඎ ඒ ඓ ඖ are composed afterwards)
        putAll(listOf("w" to "අ", "b" to "ඉ", "B" to "ඊ", "W" to "උ", "R" to "ඍ", "Ì" to "ඏ", "Ï" to "ඐ", "t" to "එ", "T" to "ඔ", "´" to "ඕ"))
        // ` turns the next letter into its sanyaka / touching form
        putAll(listOf("`" to SANYAKA, "CO" to "ඣ", "\\" to "ඳ"))
        // half letters and pre-joined conjuncts
        putAll(
            listOf(
                "C" to "ක$JOIN", "F" to "ත$JOIN", "J" to "න$JOIN", "Ë" to "ක${JOIN}ෂ", "†" to "ත${JOIN}ථ",
                "…" to "ත${JOIN}ව", "‡" to "න${JOIN}ද", "„" to "ද${JOIN}ව", "Š" to "ද${JOIN}ධ",
                "ø" to "ද${JOIN}ර", "ˆ" to "න${JOIN}දා", "‹" to "ද${JOIN}ධි", "‰" to "ද${JOIN}වි",
                "“" to "ර${JOIN}ණ", "›" to "ශ${JOIN}රී",
            ),
        )
        // yansaya, rakaransaya, repaya
        putAll(listOf("H" to "${JOIN}ය", "%" to "${JOIN}ර", "_" to REPAYA, "œ" to "${JOIN}ය$REPAYA", "¾" to "ර$AL"))
        // vowel signs and other marks
        putAll(
            listOf(
                "f" to KOMBU, "a" to AL, "A" to AL, "d" to "ා", "e" to "ැ", "E" to "ෑ", "s" to "ි", "S" to "ී", "q" to "ු", "=" to "ු",
                "Q" to "ූ", "+" to "ූ", "D" to "ෘ", "!" to "ෟ", "x" to "ං", "#" to "ඃ", "½" to "ඃ", " ‘" to "ි", " ’" to "ී",
            ),
        )
        // ra + u / uu / ae / aee have their own shapes
        putAll(listOf("re" to "රු", "rE" to "රූ", "/" to "රැ", "?" to "රෑ"))
        // consonant + vowel-sign ligatures
        putAll(
            listOf(
                "¢" to "ඳි", "£" to "ඳී", "§" to "දී", "¨" to "ලු", "ª" to "ඳූ", "¯" to "ඣි", "ˉ" to "ඣි", "°" to "ඣී",
                "±" to "දැ", "À" to "ඨි", "Á" to "ඨී", "Â" to "ඡී", "Ä" to "ඛ්", "Å" to "ඛි", "Æ" to "ලූ", "Ç" to "ඛී",
                "È" to "දි", "É" to "ච්", "Ê" to "ජ්", "Í" to "රී", "Î" to "ඪි", "Ð" to "ඪී", "Ñ" to "චි", "Ò" to "ථී",
                "Ó" to "ථි", "Ô" to "ජී", "Ö" to "චී", "Ù" to "ඞ්", "Ú" to "ඵී", "Ü" to "ට්", "Ý" to "ඵි", "Þ" to "දා",
                "ß" to "රි", "à" to "ටී", "á" to "ටි", "â" to "ඩ්", "ã" to "ඞී", "ä" to "ඩි", "å" to "ඬ්", "ç" to "ඬි",
                "è" to "ධ්", "é" to "ඬී", "ê" to "ධි", "ë" to "ධී", "ì" to "බි", "í" to "බ්", "î" to "බී", "ï" to "ම්",
                "ð" to "ජි", "ñ" to "මි", "ò" to "ඹ්", "ó" to "මී", "ô" to "ඹි", "õ" to "ව්", "ö" to "ඹී", "÷" to "ඳු",
                "ù" to "වී", "ú" to "වි", "û" to "ඤු", "ü" to "ඤූ", "ý" to "ඡි", "þ" to "ඡ්", "ÿ" to "දු", "¥" to "දූ", "ƒ" to "ඳැ",
                "Œ" to "ණී", "‚" to "ණි", "¿" to "ළු",
            ),
        )
        // punctuation: FM puts . and , on ' and " because the period and comma keys are letters
        putAll(
            listOf(
                "'" to ".", "\"" to ",", "$" to "/", "&" to ")", "(" to ":", ")" to "*", "@" to "?", "]" to "%", "^" to "(",
                "}" to "=", "¦" to ";", "æ" to "!", "¬" to "+", "«" to "×", "∙" to "×", "­" to "÷", "−" to "÷",
                "¡" to "•", "²" to "•", "¤" to "–", "³" to "⋆", "˜" to "\"", "—" to "\"", "™" to "{", "š" to "}", "Z" to "’",
                "z" to "‘", "•" to "x", "µ" to "i", "¶" to "v", "¸" to "I", "¹" to "V", "º" to "X",
            ),
        )
    }

    // Longest key first so that e.g. "`." wins over "." and "re" over "r".
    private val TOKEN = Regex(
        TO_UNICODE.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) },
    )

    // Text copied out of old Word files sometimes holds C1 control codes instead of Windows-1252 characters.
    private val CP1252 = mapOf(
        0x82 to "‚", 0x83 to "ƒ", 0x84 to "„", 0x85 to "…", 0x86 to "†", 0x87 to "‡", 0x88 to "ˆ", 0x89 to "‰",
        0x8a to "Š", 0x8b to "‹", 0x8c to "Œ", 0x91 to "‘", 0x92 to "’", 0x93 to "“", 0x94 to "”", 0x95 to "•",
        0x96 to "–", 0x97 to "—", 0x98 to "˜", 0x99 to "™", 0x9a to "š", 0x9b to "›", 0x9c to "œ",
    )

    private const val CONS = "ක-ෆ"
    private const val SIGNS = "්ා-ෟෲෳ"
    private const val CLUSTER = "[$CONS](?:$JOIN[$CONS])*"

    private val PREFIXED = mapOf(
        "ග" to "ඟ", "ජ" to "ඦ", "ඡ" to "ඦ", "ඩ" to "ඬ", "ද" to "ඳ",
        "ධ" to "ද${JOIN}ධ", "ව" to "ද${JOIN}ව", "ඨ" to "ට${JOIN}ඨ",
    )

    // Reordering rules applied after the glyphs are mapped (order matters).
    private val REORDER: List<Pair<Regex, String>> = listOf(
        // rakaransaya / yansaya typed after a vowel sign or al-lakuna
        Regex("([$SIGNS]+)($JOIN[යර])") to "\$2\$1",
        // repaya glyph typed after its consonant (and any vowel signs)
        Regex("($CLUSTER)([${SIGNS}ංඃ]*)$REPAYA") to "ර$JOIN\$1\$2",
        // two kombuvas = kombu deka (ෛ); one = ෙ
        Regex("$KOMBU$KOMBU($CLUSTER)") to "\$1ෛ",
        Regex("$KOMBU($CLUSTER)") to "\$1ෙ",
        Regex("${KOMBU}එ") to "ඓ",
    )

    private val COMPOSE = listOf(
        "ෘෘ" to "ෲ", "අා" to "ආ", "අැ" to "ඇ", "අෑ" to "ඈ", "උෟ" to "ඌ", "ඍෘ" to "ඎ",
        "එ$AL" to "ඒ", "ඔෟ" to "ඖ", "ඔ$AL" to "ඕ",
    )

    /** FM Abhaya / FM-family text -> Sinhala Unicode (NFC). */
    fun toUnicode(s: String): String {
        var t = buildString { s.forEach { c -> append(CP1252[c.code] ?: c.toString()) } }
        t = TOKEN.replace(t) { TO_UNICODE.getValue(it.value) }
        t = Regex("$SANYAKA([$CONS]?)").replace(t) { m ->
            val c = m.groupValues[1]
            PREFIXED[c] ?: "`$c"
        }
        for ((re, sub) in REORDER) t = re.replace(t, sub)
        t = t.replace(KOMBU, "ෙ").replace(REPAYA, "")
        for ((seq, sub) in COMPOSE) t = t.replace(seq, sub)
        return Normalizer.normalize(t, Normalizer.Form.NFC)
    }

    // ------------------------------------------------------------------ Unicode -> FM, syllable by syllable

    private val CONS_KEY: Map<String, String> = LinkedHashMap<String, String>().apply {
        CONSONANTS.forEach { (k, v) -> if (v !in this) put(v, k) } // first key wins
        put("ඟ", "`.")
        put("ඦ", "`P")
        put("ඣ", "®")
    }

    private val VOWEL_KEY = mapOf(
        "අ" to "w", "ආ" to "wd", "ඇ" to "we", "ඈ" to "wE", "ඉ" to "b", "ඊ" to "B", "උ" to "W", "ඌ" to "W!",
        "ඍ" to "R", "ඎ" to "RD", "ඏ" to "Ì", "ඐ" to "Ï", "එ" to "t", "ඒ" to "ta", "ඓ" to "ft", "ඔ" to "T",
        "ඕ" to "´", "ඖ" to "T!",
    )

    private val SIGN_KEY = mapOf(
        "්" to "a", "ා" to "d", "ැ" to "e", "ෑ" to "E", "ි" to "s", "ී" to "S", "ු" to "q", "ූ" to "Q", "ෘ" to "D",
        "ෲ" to "DD", "ෟ" to "!", "ං" to "x", "ඃ" to "#",
    )

    // Single consonant + one sign drawn as one glyph (or with a special sign shape).
    private val LIGATURE: Map<String, String> = LinkedHashMap<String, String>().apply {
        val pattern = Regex("^[ක-ෆ][්ැ-ූ]$")
        TO_UNICODE.forEach { (k, v) ->
            if (k.length == 1 && pattern.matches(v) && k !in setOf("Þ", "±", "ƒ", "ˉ", "¥")) put(v, k) // last wins
        }
        putAll(
            listOf(
                "රු" to "re", "රූ" to "rE", "රැ" to "/", "රෑ" to "?",
                "කු" to "l=", "කූ" to "l+", "ගු" to ".=", "ගූ" to ".+", "තු" to ";=", "තූ" to ";+",
            ),
        )
    }

    // Touching-letter pairs with their own glyph (consonant + ්‍ + consonant).
    private val CONJUNCT = mapOf(
        "ක${JOIN}ෂ" to "CI", "ක${JOIN}ව" to "Cj", "ත${JOIN}ථ" to "†", "ත${JOIN}ව" to "…",
        "න${JOIN}ද" to "‡", "න${JOIN}ධ" to "JO", "ද${JOIN}ව" to "„", "ද${JOIN}ධ" to "Š",
        "ට${JOIN}ඨ" to "`G", "ද${JOIN}ර" to "ø",
    )
    private val HALF = mapOf("ක" to "C", "ත" to "F", "න" to "J")

    private val PUNCT_KEY = mapOf(
        "." to "'", "," to "\"", "/" to "$", ")" to "&", ":" to "(", "*" to ")", "?" to "@", "%" to "]", "(" to "^",
        "=" to "}", ";" to "¦", "!" to "æ", "+" to "¬", "×" to "«", "÷" to "­", "•" to "¡", "–" to "¤",
        "⋆" to "³", "\"" to "˜", "“" to "˜", "”" to "˜", "{" to "™", "}" to "š", "‘" to "z", "’" to "Z",
    )

    private fun isCons(c: Char?): Boolean = c != null && c in 'ක'..'ෆ'

    private val SIGN_CHARS = Regex("[්ා-ෟෲෳංඃ]")

    private fun clusterToFM(cons: List<String>): String {
        var out = CONS_KEY[cons[0]] ?: cons[0]
        for (i in 1 until cons.size) {
            val prev = cons[i - 1]
            val c = cons[i]
            val prevKey = CONS_KEY[prev] ?: prev
            val pair = CONJUNCT[prev + JOIN + c]
            out = when {
                pair != null && out.endsWith(prevKey) -> out.dropLast(prevKey.length) + pair
                c == "ය" -> out + "H"
                c == "ර" -> out + "%"
                HALF[prev] != null && out.endsWith(prevKey) -> out.dropLast(prevKey.length) + HALF.getValue(prev) + CONS_KEY[c]
                else -> out + "a" + ZWJ + CONS_KEY[c]
            }
        }
        return out
    }

    private fun signsToFM(signs: String): String = signs.map { SIGN_KEY[it.toString()] ?: it.toString() }.joinToString("")

    /** Sinhala Unicode -> FM Abhaya / FM-family keystrokes. */
    fun fromUnicode(s: String): String {
        val t = Normalizer.normalize(s, Normalizer.Form.NFD) // splits ේ ො ෝ ෞ into kombuva + sign
        val out = StringBuilder()
        var i = 0
        while (i < t.length) {
            val ch = t[i]
            if (t.startsWith("ශ${JOIN}රී", i)) {
                out.append("›"); i += 5; continue
            }
            if (!isCons(ch)) {
                val k = ch.toString()
                out.append(VOWEL_KEY[k] ?: SIGN_KEY[k] ?: PUNCT_KEY[k] ?: k)
                i++
                continue
            }
            // repaya: ර්‍ + consonant (ර්‍ය is ra + yansaya instead)
            var repaya = false
            if (ch == 'ර' && t.startsWith(JOIN, i + 1) && isCons(t.getOrNull(i + 3)) && t.getOrNull(i + 3) != 'ය') {
                repaya = true
                i += 3
            }
            val cons = mutableListOf(t[i++].toString())
            while (t.startsWith(JOIN, i) && isCons(t.getOrNull(i + 2))) {
                cons += t[i + 2].toString(); i += 3
            }
            val signs = StringBuilder()
            while (i < t.length && SIGN_CHARS.matches(t[i].toString())) signs.append(t[i++])

            val base = clusterToFM(cons)
            val sig = signs.toString()
            val kombu = when (sig.firstOrNull()) {
                'ෙ' -> 1
                'ෛ' -> 2
                else -> 0
            }
            val syl: String = if (kombu > 0) {
                var rest = sig.substring(1)
                var tail = ""
                when {
                    rest.startsWith("ා්") -> { tail = "da"; rest = rest.substring(2) }
                    rest.startsWith("ා") -> { tail = "d"; rest = rest.substring(1) }
                    rest.startsWith("ෟ") -> { tail = "!"; rest = rest.substring(1) }
                }
                var body = base
                if (rest.startsWith("්")) {
                    rest = rest.substring(1)
                    val lig = if (cons.size == 1) LIGATURE[cons[0] + "්"] else null
                    body = lig ?: (base + "a")
                }
                "f".repeat(kombu) + body + tail + signsToFM(rest)
            } else {
                val lig = if (cons.size == 1 && sig.isNotEmpty()) LIGATURE[cons[0] + sig[0]] else null
                if (lig != null) lig + signsToFM(sig.substring(1)) else base + signsToFM(sig)
            }
            out.append(syl)
            if (repaya) out.append("_")
        }
        return out.toString()
    }
}
