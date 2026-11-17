package dev.valhalla.sinhalax.engine

/**
 * "Fancy" text made from Unicode characters, so it works anywhere text works (WhatsApp, Facebook, bios).
 * Only Latin letters and digits have styled versions in Unicode; Sinhala and other text passes through.
 */
object FancyText {

    class Style(
        val id: String,
        val name: String,
        /** True if the style maps character by character, so it can be applied while typing. */
        val keyboardSafe: Boolean = true,
        private val transform: (String) -> String,
    ) {
        fun apply(text: String): String = transform(text)
    }

    // ------------------------------------------------------------------ helpers

    private fun alphabet(
        upper: Int?,
        lower: Int?,
        digits: Int? = null,
        exceptions: Map<Char, String> = emptyMap(),
    ): (String) -> String = { text ->
        buildString {
            for (c in text) {
                val mapped = exceptions[c] ?: when (c) {
                    in 'A'..'Z' -> upper?.let { String(Character.toChars(it + (c - 'A'))) }
                    in 'a'..'z' -> lower?.let { String(Character.toChars(it + (c - 'a'))) }
                    in '0'..'9' -> digits?.let { String(Character.toChars(it + (c - '0'))) }
                    else -> null
                }
                append(mapped ?: c.toString())
            }
        }
    }

    private fun table(from: String, to: List<String>, ignoreCase: Boolean = false): (String) -> String {
        val map = from.mapIndexed { i, c -> c to to[i] }.toMap()
        return { text ->
            buildString {
                for (c in text) append(map[c] ?: (if (ignoreCase) map[c.lowercaseChar()] ?: map[c.uppercaseChar()] else null) ?: c.toString())
            }
        }
    }

    private fun combining(mark: Char): (String) -> String = { text ->
        buildString {
            for (c in text) {
                append(c)
                if (!c.isWhitespace() && !c.isLowSurrogate() && !c.isHighSurrogate()) append(mark)
            }
        }
    }

    private fun codePoints(start: Int, count: Int) = (0 until count).map { String(Character.toChars(start + it)) }

    private const val AZ = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    // ------------------------------------------------------------------ styles

    val all: List<Style> = listOf(
        Style("bold", "Bold", transform = alphabet(0x1D400, 0x1D41A, 0x1D7CE)),
        Style("italic", "Italic", transform = alphabet(0x1D434, 0x1D44E, exceptions = mapOf('h' to "ℎ"))),
        Style("bold_italic", "Bold italic", transform = alphabet(0x1D468, 0x1D482)),
        Style(
            "script", "Script",
            transform = alphabet(
                0x1D49C, 0x1D4B6,
                exceptions = mapOf(
                    'B' to "ℬ", 'E' to "ℰ", 'F' to "ℱ", 'H' to "ℋ", 'I' to "ℐ", 'L' to "ℒ", 'M' to "ℳ", 'R' to "ℛ",
                    'e' to "ℯ", 'g' to "ℊ", 'o' to "ℴ",
                ),
            ),
        ),
        Style("bold_script", "Bold script", transform = alphabet(0x1D4D0, 0x1D4EA)),
        Style(
            "fraktur", "Gothic",
            transform = alphabet(0x1D504, 0x1D51E, exceptions = mapOf('C' to "ℭ", 'H' to "ℌ", 'I' to "ℑ", 'R' to "ℜ", 'Z' to "ℨ")),
        ),
        Style("bold_fraktur", "Bold gothic", transform = alphabet(0x1D56C, 0x1D586)),
        Style(
            "double", "Outline",
            transform = alphabet(
                0x1D538, 0x1D552, 0x1D7D8,
                exceptions = mapOf('C' to "ℂ", 'H' to "ℍ", 'N' to "ℕ", 'P' to "ℙ", 'Q' to "ℚ", 'R' to "ℝ", 'Z' to "ℤ"),
            ),
        ),
        Style("sans_bold", "Sans bold", transform = alphabet(0x1D5D4, 0x1D5EE, 0x1D7EC)),
        Style("sans_italic", "Sans italic", transform = alphabet(0x1D608, 0x1D622)),
        Style("mono", "Typewriter", transform = alphabet(0x1D670, 0x1D68A, 0x1D7F6)),
        Style(
            "circled", "Bubbles",
            transform = alphabet(0x24B6, 0x24D0, null, exceptions = mapOf('0' to "⓪") + ('1'..'9').associateWith { String(Character.toChars(0x2460 + (it - '1'))) }),
        ),
        Style(
            "circled_dark", "Dark bubbles",
            transform = alphabet(0x1F150, 0x1F150, null, exceptions = mapOf('0' to "⓿") + ('1'..'9').associateWith { String(Character.toChars(0x2776 + (it - '1'))) }),
        ),
        Style("squared", "Squares", transform = alphabet(0x1F130, 0x1F130)),
        Style("squared_dark", "Dark squares", transform = alphabet(0x1F170, 0x1F170)),
        Style("wide", "Wide", transform = alphabet(0xFF21, 0xFF41, 0xFF10)),
        Style(
            "small_caps", "Small caps",
            transform = table(
                "abcdefghijklmnopqrstuvwxyz",
                "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ".map { it.toString() },
                ignoreCase = true,
            ),
        ),
        Style("strike", "Strikethrough", transform = combining('̶')),
        Style("underline", "Underline", transform = combining('̲')),
        Style(
            "upside_down", "Upside down", keyboardSafe = false,
            transform = { text ->
                val flip = table(
                    "abcdefghijklmnopqrstuvwxyz" + AZ + "0123456789.,!?'\"()[]{}<>&_",
                    ("ɐqɔpǝɟƃɥᴉɾʞlɯuodbɹsʇnʌʍxʎz" + "∀ꓭƆꓷƎℲ⅁HIſꓘ˥WNOԀΌꓤS⊥∩ΛMX⅄Z" + "0ƖᄅƐㄣϛ9ㄥ86˙'¡¿,„)(][}{><⅋‾")
                        .map { it.toString() },
                )
                flip(text).reversed()
            },
        ),
    )

    /** Decorations that wrap any text, including Sinhala. */
    val frames: List<Pair<String, String>> = listOf(
        "꧁ " to " ꧂",
        "★彡 " to " 彡★",
        "『 " to " 』",
        "【 " to " 】",
        "✿ " to " ✿",
        "♡ " to " ♡",
        "•´¯`•. " to " .•´¯`•",
        "✧･ﾟ: " to " :ﾟ･✧",
    )

    fun byId(id: String?): Style? = all.firstOrNull { it.id == id }
}
