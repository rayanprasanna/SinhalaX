package dev.valhalla.sinhalax.prediction

/**
 * Generates spellings the user may have *meant* when typing Singlish, e.g.
 * "kohomada" → "kohomadha" (ද), "lankava" → "laxkaavaa"… (ලංකාව), "amma" → "ammaa" (අම්මා).
 * The predictor keeps only the ones that turn out to be real words.
 */
object SinglishFuzzer {

    private class Rule(val from: String, val to: String, val applies: (String, Int) -> Boolean = { _, _ -> true })

    private const val VOWELS = "aeiou"

    private fun String.at(i: Int): Char? = getOrNull(i)

    private val rules: List<Rule> = buildList {
        // Dental / retroflex: people type d, t for ද, ත
        add(Rule("d", "dh") { s, i -> s.at(i + 1) != 'h' })
        add(Rule("t", "th") { s, i -> s.at(i + 1) != 'h' })
        // Aspirates: ධ භ
        add(Rule("dh", "dhh") { s, i -> s.at(i + 2) != 'h' })
        add(Rule("b", "bh") { s, i -> s.at(i + 1) != 'h' })
        // ණ, ළ, ශ, ෂ
        add(Rule("n", "N"))
        add(Rule("l", "L"))
        add(Rule("s", "sh") { s, i -> s.at(i + 1) != 'h' })
        add(Rule("s", "S") { s, i -> s.at(i + 1) != 'h' })
        // Anusvara: "lanka" → ලංකා
        add(Rule("n", "x") { s, i -> s.at(i + 1)?.let { it.isLetter() && it.lowercaseChar() !in VOWELS } == true })
        // Sanyaka letters: ඳ ඟ ඹ ඬ
        add(Rule("ndh", "zdh"))
        add(Rule("nd", "zdh") { s, i -> s.at(i + 2) != 'h' })
        add(Rule("ng", "zg"))
        add(Rule("mb", "zb"))
        // ඇ written as "ae"
        add(Rule("ae", "A"))
        // Long vowels: a → aa, e → ee …
        for (v in VOWELS) {
            add(Rule("$v", "$v$v") { s, i -> s.at(i + 1) != v && s.at(i - 1) != v })
        }
    }

    private data class Edit(val start: Int, val end: Int, val replacement: String)

    /**
     * Variants with 1..[maxEdits] non-overlapping edits, fewest edits first.
     * Does not include [latin] itself.
     */
    fun variants(latin: String, maxEdits: Int, limit: Int): List<String> {
        val edits = ArrayList<Edit>()
        for (i in latin.indices) {
            for (rule in rules) {
                if (latin.startsWith(rule.from, i) && rule.applies(latin, i)) {
                    edits += Edit(i, i + rule.from.length, rule.to)
                }
            }
        }
        val out = LinkedHashSet<String>()
        for (k in 1..maxEdits) {
            combine(edits, k, 0, ArrayList(), latin, out, limit)
            if (out.size >= limit) break
        }
        out.remove(latin)
        return out.take(limit)
    }

    private fun combine(
        edits: List<Edit>,
        k: Int,
        from: Int,
        chosen: ArrayList<Edit>,
        latin: String,
        out: MutableSet<String>,
        limit: Int,
    ) {
        if (out.size >= limit) return
        if (chosen.size == k) {
            val sb = StringBuilder(latin)
            for (e in chosen.sortedByDescending { it.start }) sb.replace(e.start, e.end, e.replacement)
            out += sb.toString()
            return
        }
        for (j in from until edits.size) {
            val e = edits[j]
            if (chosen.any { e.start < it.end && it.start < e.end }) continue
            chosen += e
            combine(edits, k, j + 1, chosen, latin, out, limit)
            chosen.removeAt(chosen.size - 1)
            if (out.size >= limit) return
        }
    }
}
