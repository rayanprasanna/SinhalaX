package dev.valhalla.sinhalax.keyboard

sealed interface KeyAction {
    data class Text(val value: String) : KeyAction
    data object Shift : KeyAction
    data object Delete : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object ToLetters : KeyAction
    data object ToSymbols : KeyAction
    data object ToSymbols2 : KeyAction
    data object ToggleLanguage : KeyAction
    /** Empty gap (used to indent the middle letter row). */
    data object None : KeyAction
}

data class KeySpec(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val longPress: String? = null,
    val special: Boolean = false,
)

object KeyboardLayouts {

    private fun textRow(chars: List<String>, longPress: List<String>? = null) =
        chars.mapIndexed { i, c -> KeySpec(c, KeyAction.Text(c), longPress = longPress?.getOrNull(i)) }

    private fun split(s: String) = s.map { it.toString() }

    private val digits = split("1234567890")

    private fun bottomRow(modeKey: KeySpec) = listOf(
        modeKey,
        KeySpec("lang", KeyAction.ToggleLanguage, weight = 1f, special = true),
        KeySpec(",", KeyAction.Text(","), longPress = "?"),
        KeySpec("space", KeyAction.Space, weight = 4.5f),
        KeySpec(".", KeyAction.Text("."), longPress = "!"),
        KeySpec("enter", KeyAction.Enter, weight = 1.5f, special = true),
    )

    private val shiftKey = KeySpec("shift", KeyAction.Shift, weight = 1.5f, special = true)
    private val deleteKey = KeySpec("delete", KeyAction.Delete, weight = 1.5f, special = true)
    private val gap = KeySpec("", KeyAction.None, weight = 0.5f)

    fun letters(numberRow: Boolean): List<List<KeySpec>> = buildList {
        if (numberRow) add(textRow(digits))
        // Without a number row, long-press on the top row gives digits.
        add(textRow(split("qwertyuiop"), if (numberRow) split("%^~|[]<>{}") else digits))
        add(listOf(gap) + textRow(split("asdfghjkl"), split("@#&*-+=()")) + gap)
        add(listOf(shiftKey) + textRow(split("zxcvbnm"), split("_\"':;/?")) + deleteKey)
        add(bottomRow(KeySpec("?123", KeyAction.ToSymbols, weight = 1.5f, special = true)))
    }

    val symbols: List<List<KeySpec>> = listOf(
        textRow(digits),
        textRow(split("@#\$_&-+()/")),
        listOf(KeySpec("=\\<", KeyAction.ToSymbols2, weight = 1.5f, special = true)) +
            textRow(split("*\"':;!?")) + deleteKey,
        bottomRow(KeySpec("ABC", KeyAction.ToLetters, weight = 1.5f, special = true)),
    )

    val symbols2: List<List<KeySpec>> = listOf(
        textRow(split("~`|•√π÷×¶∆")),
        textRow(listOf("£", "€", "¥", "රු", "^", "°", "=", "{", "}", "\\")),
        listOf(KeySpec("?123", KeyAction.ToSymbols, weight = 1.5f, special = true)) +
            textRow(split("%©®™✓[]")) + deleteKey,
        bottomRow(KeySpec("ABC", KeyAction.ToLetters, weight = 1.5f, special = true)),
    )
}
