package dev.valhalla.sinhalax.engine

/**
 * Repeats a piece of text, like Z Sri's Text Repeater but with a few more styles.
 */
object TextRepeater {

    /** How the copies are joined. */
    enum class Joiner(val label: String, val value: String) {
        SPACE("Space", " "),
        NEW_LINE("New line", "\n"),
        NONE("Nothing", ""),
    }

    /** Longest result we put into an app (chat apps choke on huge pastes). */
    const val MAX_LENGTH = 20_000
    const val MAX_COUNT = 500

    /**
     * [count] copies of [text] joined by [joiner]. With [numbered], each copy starts with "1. ", "2. "….
     * Returns "" for blank text; the result is cut at whole copies to stay under [MAX_LENGTH].
     */
    fun repeat(text: String, count: Int, joiner: Joiner = Joiner.SPACE, numbered: Boolean = false): String {
        if (text.isBlank() || count <= 0) return ""
        val n = count.coerceAtMost(MAX_COUNT)
        val sb = StringBuilder()
        for (i in 1..n) {
            val piece = if (numbered) "$i. $text" else text
            val extra = (if (i > 1) joiner.value.length else 0) + piece.length
            if (sb.length + extra > MAX_LENGTH) break
            if (i > 1) sb.append(joiner.value)
            sb.append(piece)
        }
        return sb.toString()
    }
}
