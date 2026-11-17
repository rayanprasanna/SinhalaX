package dev.valhalla.sinhalax.data

data class KeyboardSettings(
    val themeId: String = "navy_gold",
    val startInSinhala: Boolean = true,
    val livePreview: Boolean = true,
    val autoCapitalize: Boolean = true,
    val wordSuggestions: Boolean = true,
    val autoCorrect: Boolean = true,
    val learnWords: Boolean = true,
    val showHints: Boolean = true,
    val numberRow: Boolean = false,
    val heightScale: Float = 1f,
    val keyFontScale: Float = 1f,
    val bottomPaddingDp: Int = 0,
    val vibrate: Boolean = true,
    val sound: Boolean = false,
    val clipboardHistory: Boolean = true,
    // Theme store: background image (empty = none) and key sound pack (empty = system click)
    val backgroundId: String = "",
    val backgroundPath: String = "",
    val backgroundKeyTextColor: String = "",
    val backgroundDominantColor: String = "",
    val soundPackId: String = "",
    val soundPackPath: String = "",
    val soundVolume: Float = 0.6f,
)
