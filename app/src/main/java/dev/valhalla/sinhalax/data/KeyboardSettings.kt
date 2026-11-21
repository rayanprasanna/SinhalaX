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
    // Tools (v0.7)
    /** Incognito: no learning, no clipboard history and no recents while on. */
    val incognito: Boolean = false,
    /** One-hand mode: "" (off), "left" or "right". */
    val oneHand: String = "",
    /** Toolbar buttons in order, comma-separated ToolId names; "" = the default set. */
    val toolbarItems: String = "",
    /** Shortcuts (text expansion): "gm" + space → your saved text. */
    val shortcutsEnabled: Boolean = true,
    /** Slide on space to move the cursor; slide left on ⌫ to select words and delete them. */
    val slideGestures: Boolean = true,
    // Phase 3 (v0.9)
    /** Emoji panel style: "system", "google", "twemoji" or "openmoji" (see EmojiStyle). */
    val emojiStyle: String = "system",
    /** Floating keyboard: a movable card over the app instead of docked at the bottom. */
    val floating: Boolean = false,
    /** Floating card position as fractions of the free space (0 = left / top, 1 = right / bottom). */
    val floatX: Float = 0.5f,
    val floatY: Float = 0.6f,
    /** Floating card width as a fraction of the screen width. */
    val floatWidth: Float = 0.85f,
    /** "My theme" from the DIY theme studio (JSON, see CustomThemeSpec); used when themeId == "custom". */
    val customTheme: String = "",
)
