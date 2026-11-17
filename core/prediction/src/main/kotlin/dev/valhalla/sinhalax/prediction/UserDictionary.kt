package dev.valhalla.sinhalax.prediction

/**
 * Words the user has typed, kept on the device. Not thread-safe: use from the main thread.
 * Saved as plain text lines "word<TAB>count<TAB>lastUsedMillis".
 */
class UserDictionary(private val maxWords: Int = 5_000) {

    data class Entry(val word: String, val count: Int, val lastUsed: Long)

    private val entries = HashMap<String, Entry>()

    val size: Int get() = entries.size

    fun count(word: String): Int = entries[Lexicon.normalize(word)]?.count ?: 0

    fun learn(word: String, now: Long = System.currentTimeMillis()) {
        if (word.isBlank() || word.length > 40) return
        val key = Lexicon.normalize(word)
        val old = entries[key]
        entries[key] = Entry(word, (old?.count ?: 0) + 1, now)
        if (entries.size > maxWords) evict()
    }

    fun remove(word: String) {
        entries.remove(Lexicon.normalize(word))
    }

    fun clear() = entries.clear()

    fun all(): List<Entry> = entries.values.sortedWith(compareByDescending<Entry> { it.count }.thenBy { it.word })

    fun withPrefix(prefix: String): List<Entry> {
        val key = Lexicon.normalize(prefix)
        if (key.isEmpty()) return emptyList()
        return entries.filterKeys { it.startsWith(key) }.values.toList()
    }

    fun serialize(): String = buildString {
        for (e in entries.values) append(e.word).append('\t').append(e.count).append('\t').append(e.lastUsed).append('\n')
    }

    /** Least-used, oldest words go first. */
    private fun evict() {
        val drop = entries.values.sortedWith(compareBy<Entry> { it.count }.thenBy { it.lastUsed }).take(entries.size - maxWords + maxWords / 10)
        drop.forEach { entries.remove(Lexicon.normalize(it.word)) }
    }

    companion object {
        fun parse(text: String, maxWords: Int = 5_000): UserDictionary {
            val dict = UserDictionary(maxWords)
            text.lineSequence().forEach { line ->
                val parts = line.split('\t')
                if (parts.size >= 3) {
                    val count = parts[1].toIntOrNull() ?: return@forEach
                    val last = parts[2].toLongOrNull() ?: 0L
                    dict.entries[Lexicon.normalize(parts[0])] = Entry(parts[0], count, last)
                }
            }
            return dict
        }
    }
}
