package dev.valhalla.sinhalax.prediction

/**
 * Read-only Sinhala word list with frequencies.
 *
 * Memory-compact: all lookup keys live in ONE string with an offsets array, instead of
 * 100k separate String objects. Keys are the words without zero-width joiners, so
 * "ප්‍රධාන" and "ප්රධාන" are the same word; the original spelling is kept for display.
 */
class Lexicon private constructor(
    private val keys: String,
    private val offsets: IntArray,
    private val freqs: IntArray,
    private val displays: Map<Int, String>,
) {
    val size: Int get() = freqs.size

    /** Indices of the most frequent words, used for very short prefixes. Built at load time. */
    private val byFrequency: IntArray =
        (0 until freqs.size).sortedByDescending { freqs[it] }.take(TOP_WORDS).toIntArray()

    fun word(index: Int): String = displays[index] ?: keyAt(index)

    fun frequency(index: Int): Int = freqs[index]

    /** Frequency of [word], or 0 if unknown. */
    fun frequency(word: String): Int {
        val key = normalize(word)
        val i = lowerBound(key)
        return if (i < size && compareAt(i, key) == 0) freqs[i] else 0
    }

    /** Calls [action] with the index of every word starting with [prefix] (frequent words first for big ranges). */
    fun forEachWithPrefix(prefix: String, action: (Int) -> Unit) {
        val key = normalize(prefix)
        if (key.isEmpty()) return
        val from = lowerBound(key)
        val to = lowerBound(key + '\uFFFF')
        if (to - from <= MAX_RANGE_SCAN) {
            for (i in from until to) action(i)
        } else {
            // Huge range (e.g. one letter): only look at the most frequent words.
            for (i in byFrequency) if (startsWith(i, key)) action(i)
        }
    }

    private fun keyAt(i: Int): String = keys.substring(offsets[i], offsets[i + 1])

    private fun compareAt(i: Int, key: String): Int {
        val start = offsets[i]
        val length = offsets[i + 1] - start
        val n = minOf(length, key.length)
        for (j in 0 until n) {
            val d = keys[start + j] - key[j]
            if (d != 0) return d
        }
        return length - key.length
    }

    private fun startsWith(i: Int, prefix: String): Boolean {
        val start = offsets[i]
        if (offsets[i + 1] - start < prefix.length) return false
        return keys.regionMatches(start, prefix, 0, prefix.length)
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareAt(mid, key) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private const val ZWJ = "\u200D"
        private const val MAX_RANGE_SCAN = 2_500
        private const val TOP_WORDS = 6_000

        fun normalize(word: String): String = if (word.contains(ZWJ)) word.replace(ZWJ, "") else word

        /** Parses "word<TAB>frequency" lines. Order and duplicates don't matter. */
        fun parse(lines: Sequence<String>): Lexicon {
            val merged = HashMap<String, Pair<String, Int>>()
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab).trim()
                val freq = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                if (word.isEmpty()) continue
                val key = normalize(word)
                val existing = merged[key]
                merged[key] = if (existing == null || freq > existing.second) word to freq else existing
            }
            val sortedKeys = merged.keys.sorted()
            val builder = StringBuilder()
            val offsets = IntArray(sortedKeys.size + 1)
            val freqs = IntArray(sortedKeys.size)
            val displays = HashMap<Int, String>()
            sortedKeys.forEachIndexed { i, key ->
                offsets[i] = builder.length
                builder.append(key)
                val (display, freq) = merged.getValue(key)
                freqs[i] = freq
                if (display != key) displays[i] = display
            }
            offsets[sortedKeys.size] = builder.length
            return Lexicon(builder.toString(), offsets, freqs, displays)
        }

        val EMPTY: Lexicon = parse(emptySequence())
    }
}
